package dev.codex.fireflysam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

public final class SamCombat {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<LivingEntity, Toughness> TOUGHNESS = new WeakHashMap<>();
    private static final Map<LivingEntity, InvisibleBurn> INVISIBLE_BURNS = new WeakHashMap<>();
    private static final List<CraterJob> CRATERS = new ArrayList<>();
    private static boolean extraDamage;
    private static final DustParticleOptions TEAL = new DustParticleOptions(new Vector3f(0.15f, 1, 0.7f), 1.5f);
    private SamCombat() {}

    public static final class Session {
        public final CombatRules.Combo combo = new CombatRules.Combo();
        final Set<UUID> brokenTargetsAtCast = new HashSet<>();
        public boolean active;
        public long transformStart = -1, ultimateStart = -1, lastAction = -100;
        long candidateTick = -1;
        int candidateId = -1;
        boolean candidateCharged, oldGravity, oldPhysics;
        public Vec3 target = Vec3.ZERO;
        Vec3 origin = Vec3.ZERO;
        ResourceKey<Level> dimension;
    }
    private static final class Toughness {
        final float maximum;
        float remaining;
        long brokenUntil, hitTick;
        Toughness(LivingEntity entity) { maximum = CombatRules.toughness(entity.getMaxHealth(), entity.getArmorValue()); remaining = maximum; }
    }
    private static final class InvisibleBurn {
        final long expiresAt;
        long nextDamageAt;
        InvisibleBurn(long now) {
            expiresAt = now + CombatRules.INVISIBLE_BURN_TICKS;
            nextDamageAt = now + CombatRules.INVISIBLE_BURN_INTERVAL;
        }
    }
    public static Session session(Player player) { return SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session()); }
    public static boolean holding(Player player) { return player.getMainHandItem().is(FireflySam.SWORD.get()); }
    public static boolean active(Player player) { var s = SESSIONS.get(player.getUUID()); return s != null && s.active; }
    public static boolean invisibleBurning(LivingEntity entity) { return INVISIBLE_BURNS.containsKey(entity); }
    public static int mobKills(ServerPlayer player) { return player.getStats().getValue(Stats.CUSTOM.get(Stats.MOB_KILLS)); }
    public static void toggle(ServerPlayer player) {
        Session s = session(player);
        long now = player.level().getGameTime();
        if (now - s.lastAction < 8 || s.combo.casting()) return;
        s.lastAction = now;
        if (s.active) { deactivate(player, s); return; }
        if (!holding(player) || player.isSpectator() || !player.isAlive()) { tell(player, "message.firefly_sam.hold_blade"); return; }
        AABB box = player.getBoundingBox();
        AABB tall = new AABB(box.minX - .23, box.minY, box.minZ - .23, box.maxX + .23, box.minY + 3.25, box.maxZ + .23);
        if (!player.level().noCollision(player, tall)) { tell(player, "message.firefly_sam.no_room"); return; }
        if (player.isPassenger()) player.stopRiding();
        s.active = true; s.dimension = player.level().dimension(); s.transformStart = now; s.combo.reset();
        modifier(player, Attributes.SCALE, "mech_scale", .75, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, true);
        modifier(player, Attributes.ARMOR, "mech_armor", 8, AttributeModifier.Operation.ADD_VALUE, true);
        modifier(player, Attributes.MOVEMENT_SPEED, "mech_speed", .20, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, true);
        modifier(player, Attributes.STEP_HEIGHT, "mech_step", .65, AttributeModifier.Operation.ADD_VALUE, true);
        modifier(player, Attributes.ENTITY_INTERACTION_RANGE, "mech_reach", 1.8, AttributeModifier.Operation.ADD_VALUE, true);
        player.refreshDimensions();
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.4f, .6f);
        level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.5f, .7f);
        level.sendParticles(TEAL, player.getX(), player.getY()+1.5, player.getZ(), 100, .7, 1.4, .7, .08);
        tell(player, "message.firefly_sam.online"); sync(player, s);
    }
    private static void modifier(ServerPlayer p, Holder<Attribute> attribute, String name, double amount, AttributeModifier.Operation operation, boolean add) {
        var instance = p.getAttribute(attribute);
        if (instance == null) return;
        var id = FireflySam.id(name);
        instance.removeModifier(id);
        if (add) instance.addTransientModifier(new AttributeModifier(id, amount, operation));
    }
    private static void deactivate(ServerPlayer p, Session s) {
        if (s.combo.casting()) restorePhysics(p, s);
        s.active = false; s.combo.reset(); s.ultimateStart = -1; s.brokenTargetsAtCast.clear();
        modifier(p, Attributes.SCALE, "mech_scale", 0, AttributeModifier.Operation.ADD_VALUE, false);
        modifier(p, Attributes.ARMOR, "mech_armor", 0, AttributeModifier.Operation.ADD_VALUE, false);
        modifier(p, Attributes.MOVEMENT_SPEED, "mech_speed", 0, AttributeModifier.Operation.ADD_VALUE, false);
        modifier(p, Attributes.STEP_HEIGHT, "mech_step", 0, AttributeModifier.Operation.ADD_VALUE, false);
        modifier(p, Attributes.ENTITY_INTERACTION_RANGE, "mech_reach", 0, AttributeModifier.Operation.ADD_VALUE, false);
        p.refreshDimensions(); sync(p, s);
    }
    private static void tell(ServerPlayer p, String key) { p.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.AQUA), true); }
    public static SamNetwork.State snapshot(Player p, Session s) {
        return new SamNetwork.State(p.getId(), s.active, s.combo.hits(), s.combo.lastHit(), s.transformStart, s.ultimateStart, s.target);
    }
    public static void sync(ServerPlayer p, Session s) { PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, snapshot(p,s)); }

    @SubscribeEvent
    public static void attack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !holding(p)) return;
        Session s = session(p);
        if (s.combo.casting()) { event.setCanceled(true); return; }
        s.candidateTick = p.level().getGameTime(); s.candidateId = event.getTarget().getId();
        s.candidateCharged = p.getAttackStrengthScale(.5f) >= .85f;
    }
    @SubscribeEvent
    public static void incoming(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            if (holding(p) && event.getSource().is(DamageTypeTags.IS_FIRE)) { event.setCanceled(true); return; }
            Session s = SESSIONS.get(p.getUUID());
            if (s != null && s.active) {
                if (s.combo.casting() && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) { event.setCanceled(true); return; }
                if (event.getSource().is(DamageTypes.FALL)) { event.setCanceled(true); return; }
            }
        }
        if (!extraDamage && event.getSource().getEntity() instanceof ServerPlayer p && active(p)) event.setAmount(event.getAmount()*2f);
    }
    @SubscribeEvent
    public static void confirmedHit(LivingDamageEvent.Post event) {
        if (extraDamage || event.getNewDamage() <= 0 || !(event.getSource().getEntity() instanceof ServerPlayer p)
                || event.getSource().getDirectEntity() != p || !holding(p)) return;
        LivingEntity target=event.getEntity();
        if (!(target instanceof Mob) || target == p || target instanceof Player || target instanceof ArmorStand
                || target.isAlliedTo(p) || target instanceof TamableAnimal pet && pet.isTame()) return;
        Session s = session(p);
        long now = p.level().getGameTime();
        if (s.combo.casting()) return;
        boolean primary = s.candidateTick == now && s.candidateId == target.getId();
        boolean charged = primary && s.candidateCharged;
        if (s.combo.hit(now, s.active, charged, primary)) {
            s.candidateId = -1;
            if (s.combo.ready()) {
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.2f, 1.5f);
                tell(p, "message.firefly_sam.ready");
            }
            sync(p,s);
        }
        if (charged && target.isAlive()) damageToughness(p, target, s.active ? 24 : 16);
    }
    private static void damageToughness(ServerPlayer p, LivingEntity target, float reduction) {
        Toughness t = TOUGHNESS.computeIfAbsent(target, Toughness::new);
        long now = p.level().getGameTime();
        if (t.brokenUntil > 0 && t.brokenUntil <= now) { t.remaining = t.maximum; t.brokenUntil = 0; }
        t.hitTick = now;
        if (t.brokenUntil > now) {
            float bonus = CombatRules.superBreakDamage(reduction, SamConfig.BREAK_EFFECT.get());
            bonusDamage(p, target, bonus);
            PacketDistributor.sendToPlayersTrackingEntity(target, new SamNetwork.Impact(target.position().add(0,target.getBbHeight()*.6,0), 2, 2, bonus));
        } else {
            t.remaining = Math.max(0, t.remaining - reduction);
            if (t.remaining == 0) {
                t.brokenUntil = now + CombatRules.BREAK_TICKS;
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CombatRules.BREAK_TICKS, 6));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, CombatRules.BREAK_TICKS, 1));
                target.igniteForSeconds(5);
                float bonus = CombatRules.breakDamage(target.getMaxHealth(), SamConfig.BREAK_EFFECT.get());
                bonusDamage(p,target,bonus);
                p.serverLevel().playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.6f, .55f);
                p.serverLevel().sendParticles(ParticleTypes.END_ROD,target.getX(), target.getY()+target.getBbHeight()*.6,target.getZ(),40,.4,.6,.4,.14);
                PacketDistributor.sendToPlayersTrackingEntity(target, new SamNetwork.Impact(target.position().add(0,target.getBbHeight()*.6,0), 3, 1, bonus));
            }
        }
        PacketDistributor.sendToPlayersTrackingEntity(target, new SamNetwork.Toughness(target.getId(),t.remaining,t.maximum,t.brokenUntil,t.hitTick));
    }
    private static void bonusDamage(ServerPlayer p, LivingEntity target, float damage) {
        if (!target.isAlive()) return;
        extraDamage = true;
        int invulnerability = target.invulnerableTime;
        try { target.invulnerableTime = 0; target.hurt(p.damageSources().playerAttack(p),damage); }
        finally { target.invulnerableTime = Math.max(target.invulnerableTime,invulnerability); extraDamage = false; }
    }
    public static void cast(ServerPlayer p) {
        Session s = session(p);
        if (!s.active || !holding(p) || !p.isAlive() || p.isSpectator()) { tell(p,"message.firefly_sam.hold_blade"); return; }
        if (!s.combo.ready()) { if (!s.combo.casting()) tell(p,"message.firefly_sam.need_combo"); return; }
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition(); Vec3 look = p.getLookAngle();
        HitResult hit = level.clip(new ClipContext(eye,eye.add(look.scale(48)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        Vec3 target;
        if (hit.getType() == HitResult.Type.BLOCK && eye.distanceTo(hit.getLocation()) > 4) target = hit.getLocation();
        else {
            Vec3 flat = new Vec3(look.x,0,look.z).normalize();
            if (flat.lengthSqr() < .01) flat = new Vec3(0,0,1);
            target = p.position().add(flat.scale(18));
            BlockPos column = BlockPos.containing(target);
            if (!level.hasChunkAt(column)) { tell(p,"message.firefly_sam.target_unloaded"); return; }
            target = new Vec3(target.x,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ()),target.z);
        }
        if (!level.getWorldBorder().isWithinBounds(BlockPos.containing(target)) || !level.hasChunkAt(BlockPos.containing(target))) return;
        if (!s.combo.cast(s.active)) return;
        s.brokenTargetsAtCast.clear();
        double affectedRadius = SamConfig.CRATER_RADIUS.get() + 8;
        double affectedRadiusSqr = affectedRadius * affectedRadius;
        for (var entry : TOUGHNESS.entrySet()) {
            LivingEntity entity = entry.getKey();
            if (entity.isAlive() && entity.level() == level && entry.getValue().brokenUntil > level.getGameTime()
                    && entity.position().distanceToSqr(target) <= affectedRadiusSqr) {
                s.brokenTargetsAtCast.add(entity.getUUID());
            }
        }
        s.target = target; s.origin = p.position(); s.ultimateStart = level.getGameTime();
        s.oldGravity = p.isNoGravity(); s.oldPhysics = p.noPhysics;
        p.setNoGravity(true); p.noPhysics = true; p.setDeltaMovement(Vec3.ZERO); p.fallDistance = 0;
        level.playSound(null,p.blockPosition(),SoundEvents.BEACON_POWER_SELECT,SoundSource.PLAYERS,2,.5f);
        sync(p,s);
        FireflySam.LOGGER.log(System.Logger.Level.INFO,"SAM ultimate started: player={0}, target={1}",p.getScoreboardName(),target);
    }
    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (holding(p)) p.clearFire();
        Session s = SESSIONS.get(p.getUUID());
        long now = p.level().getGameTime();
        if (s == null) {
            if (holding(p) && now%20==0) sync(p,session(p));
            return;
        }
        if (s.active && (!p.isAlive() || p.isSpectator() || s.dimension != p.level().dimension() || (!holding(p) && !s.combo.casting()))) { deactivate(p,s); return; }
        int oldHits = s.combo.hits(); s.combo.tick(now);
        if (oldHits != s.combo.hits()) sync(p,s);
        if (s.combo.casting()) {
            int age = (int)(now-s.ultimateStart);
            Vec3 pos;
            if (age < 18) pos = s.origin.add(0,.08*Math.sin(age*.25),0);
            else if (age < 46) pos = s.origin.add(0,6*smooth((age-18)/28.0),0);
            else if (age < 88) pos = s.origin.add(0,6+.1*Math.sin(age*.18),0);
            else if (age < CombatRules.DIVE_START) {
                double t=smooth((age-88)/(double)(CombatRules.DIVE_START-88));
                pos=s.origin.add(0,6,0).lerp(s.target.add(0,17,0),t);
            } else if (age < CombatRules.IMPACT_TICK) {
                double t = Math.pow((age-CombatRules.DIVE_START)/(double)(CombatRules.IMPACT_TICK-CombatRules.DIVE_START),2);
                pos = s.target.add(0,17*(1-t)+1,0);
            } else pos = s.target.add(0,1,0);
            p.teleportTo(pos.x,pos.y,pos.z); p.setDeltaMovement(Vec3.ZERO); p.fallDistance = 0;
            if (age == 18 || age == CombatRules.DIVE_START) p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.ELYTRA_FLYING,SoundSource.PLAYERS,2,age==18?.6f:1.7f);
            if (age == CombatRules.IMPACT_TICK) impact(p,s);
            if (age >= CombatRules.CAST_TICKS) {
                restorePhysics(p,s);
                s.combo.finish(); s.ultimateStart = -1; s.brokenTargetsAtCast.clear();
                Vec3 escape = s.origin;
                if (!p.serverLevel().noCollision(p,p.getBoundingBox().move(escape.subtract(p.position())))) {
                    int floor = p.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,BlockPos.containing(escape).getX(),BlockPos.containing(escape).getZ());
                    escape = new Vec3(escape.x,Math.max(floor,escape.y),escape.z);
                }
                p.teleportTo(escape.x,escape.y,escape.z); p.fallDistance=0;
                sync(p,s);
            }
        }
        if ((s.active && now%10==0) || (!s.active && holding(p) && now%20==0)) sync(p,s);
    }
    private static double smooth(double t) { return t*t*(3-2*t); }
    private static void restorePhysics(ServerPlayer p, Session s) { p.setNoGravity(s.oldGravity); p.noPhysics=s.oldPhysics; p.fallDistance=0; }
    private static void impact(ServerPlayer p, Session s) {
        ServerLevel level = p.serverLevel();
        double radius = SamConfig.CRATER_RADIUS.get();
        long now = level.getGameTime();
        float damage = CombatRules.ultimateDamage(SamConfig.ULTIMATE_DAMAGE.get(),SamConfig.ULTIMATE_BROKEN_MULTIPLIER.get(),false);
        float brokenDamage = CombatRules.ultimateDamage(SamConfig.ULTIMATE_DAMAGE.get(),SamConfig.ULTIMATE_BROKEN_MULTIPLIER.get(),true);
        int victims=0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,new AABB(s.target,s.target).inflate(radius+8,24,radius+8))) {
            if (target == p || target instanceof Player || target instanceof ArmorStand || !target.isAlive()
                    || target.distanceToSqr(s.target)>Math.pow(radius+8,2)
                    || target.isAlliedTo(p) || target instanceof TamableAnimal pet && pet.isTame()) continue;
            boolean weaknessBroken = weaknessBrokenForUltimate(s,target,now);
            bonusDamage(p,target,weaknessBroken?brokenDamage:damage);
            if (target.isAlive()) {
                damageToughness(p,target,600);
                if (target.isAlive()) applyInvisibleBurn(level,target);
            }
            Vec3 push = target.position().subtract(s.target).normalize().scale(1.6);
            target.push(push.x,.7,push.z); victims++;
        }
        level.playSound(null,BlockPos.containing(s.target),SoundEvents.GENERIC_EXPLODE.value(),SoundSource.PLAYERS,8,.55f);
        level.playSound(null,BlockPos.containing(s.target),SoundEvents.LIGHTNING_BOLT_THUNDER,SoundSource.PLAYERS,6,.7f);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,s.target.x,s.target.y+1,s.target.z,3,2,1,2,.1);
        level.sendParticles(ParticleTypes.FLAME,s.target.x,s.target.y+1,s.target.z,180,8,3,8,.45);
        PacketDistributor.sendToPlayersInDimension(level,new SamNetwork.Impact(s.target,(float)radius,0,damage));
        if (SamConfig.TERRAIN_DAMAGE.get()) CRATERS.add(new CraterJob(level,BlockPos.containing(s.target), (int)radius,SamConfig.CRATER_DEPTH.get()));
        FireflySam.LOGGER.log(System.Logger.Level.INFO,"SAM impact: {0} victims, radius {1}, terrain={2}",victims,radius,SamConfig.TERRAIN_DAMAGE.get());
    }
    static boolean weaknessBrokenForUltimate(Session session, LivingEntity target, long now) {
        return weaknessBrokenNow(target,now) || session.brokenTargetsAtCast.contains(target.getUUID());
    }
    static boolean weaknessBrokenNow(LivingEntity target, long now) {
        Toughness toughness=TOUGHNESS.get(target);
        return toughness!=null&&toughness.brokenUntil>now;
    }
    private static void applyInvisibleBurn(ServerLevel level, LivingEntity target) {
        target.clearFire();
        target.hurt(level.damageSources().onFire(),CombatRules.INVISIBLE_BURN_DAMAGE);
        if (target.isAlive()) INVISIBLE_BURNS.put(target,new InvisibleBurn(level.getGameTime()));
    }
    private static void tickInvisibleBurns() {
        Iterator<Map.Entry<LivingEntity,InvisibleBurn>> burns=INVISIBLE_BURNS.entrySet().iterator();
        while(burns.hasNext()) {
            var entry=burns.next();
            LivingEntity entity=entry.getKey(); InvisibleBurn burn=entry.getValue();
            if(entity.isRemoved()||!entity.isAlive()) { burns.remove(); continue; }
            long now=entity.level().getGameTime();
            if(now>=burn.expiresAt) { entity.clearFire(); burns.remove(); continue; }
            entity.clearFire();
            if(now>=burn.nextDamageAt) {
                burn.nextDamageAt=now+CombatRules.INVISIBLE_BURN_INTERVAL;
                entity.hurt(entity.level().damageSources().onFire(),CombatRules.INVISIBLE_BURN_DAMAGE);
            }
        }
    }
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        Iterator<CraterJob> jobs=CRATERS.iterator();
        while(jobs.hasNext()) if(jobs.next().tick(SamConfig.BLOCKS_PER_TICK.get())) jobs.remove();
        tickInvisibleBurns();
        TOUGHNESS.entrySet().removeIf(e -> e.getKey().isRemoved() || !e.getKey().isAlive());
        for (var entry : TOUGHNESS.entrySet()) {
            LivingEntity entity = entry.getKey(); Toughness t = entry.getValue();
            long now=entity.level().getGameTime();
            if (t.brokenUntil>now && entity instanceof Mob) entity.setDeltaMovement(0,entity.getDeltaMovement().y,0);
            if (t.brokenUntil>0 && t.brokenUntil<=now || t.brokenUntil==0 && t.remaining<t.maximum && now-t.hitTick>160) {
                t.remaining=t.maximum; t.brokenUntil=0;
                PacketDistributor.sendToPlayersTrackingEntity(entity,new SamNetwork.Toughness(entity.getId(),t.remaining,t.maximum,0,t.hitTick));
            }
        }
        SamCommands.testTick(event);
    }
    @SubscribeEvent
    public static void tracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (event.getTarget() instanceof Player target) {
            Session s=SESSIONS.get(target.getUUID()); if(s!=null) PacketDistributor.sendToPlayer(p,snapshot(target,s));
        }
        if(event.getTarget() instanceof LivingEntity target) {
            Toughness t=TOUGHNESS.get(target); if(t!=null) PacketDistributor.sendToPlayer(p,new SamNetwork.Toughness(target.getId(),t.remaining,t.maximum,t.brokenUntil,t.hitTick));
        }
    }
    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer p) { Session s=SESSIONS.get(p.getUUID()); if(s!=null) deactivate(p,s); SESSIONS.remove(p.getUUID()); }
    }
    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event) {
        Session s=SESSIONS.remove(event.getOriginal().getUUID());
        if(s!=null&&event.getOriginal() instanceof ServerPlayer oldPlayer)deactivate(oldPlayer,s);
    }
    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) { SESSIONS.clear(); TOUGHNESS.clear(); INVISIBLE_BURNS.clear(); CRATERS.clear(); }
}
