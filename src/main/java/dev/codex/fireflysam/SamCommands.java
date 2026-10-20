package dev.codex.fireflysam;

import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class SamCommands {
    private static long testStart=-1;
    private static long combatStart=-1,castStart=-1;
    private static boolean finished,hovered,reported,postCastChecked,postCastDisengaged;
    private static ServerPlayer tester;
    private static IronGolem dummy,burnDummy;
    private static int hits;
    private static float beforeFirst;
    private static float burnHealthAtImpact=-1;
    private static float brokenDummyHealthAtCast=-1;
    private static boolean burnAppliedReported,burnDamageReported,fireExtinguishReported,brokenUltimateReported,brokenWindowExpiryReported;
    private static int breakEffects;
    private static BlockPos sentinelBedrock,sentinelChest,sentinelWater;
    private static final Map<String,Object> REPORT=new LinkedHashMap<>();
    private SamCommands(){}
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("sam")
            .then(Commands.literal("help").executes(c->{c.getSource().sendSuccess(()->Component.literal("Firefly Blade: R toggles unlimited SAM mode. Land 10 charged enemy hits to ready the ultimate; G/right-click = Deathstar Overload. Shift-right-click toggles SAM. Break toughness to stun/burn; hit broken foes for Super Break. /sam give and /sam training require operator permission."),false);return 1;}))
            .then(Commands.literal("give").requires(s->s.hasPermission(2)).executes(c->{c.getSource().getPlayerOrException().getInventory().add(new ItemStack(FireflySam.SWORD.get()));return 1;}))
            .then(Commands.literal("training").requires(s->s.hasPermission(2)).executes(c->{ServerPlayer p=c.getSource().getPlayerOrException();spawnDummy(p.serverLevel(),p.getX(),p.getY(),p.getZ()+4);c.getSource().sendSuccess(()->Component.literal("Spawned a 600 HP practice target. Charged hits reduce its toughness; 10 SAM hits unlock the ultimate."),false);return 1;})));
    }
    private static IronGolem spawnDummy(ServerLevel level,double x,double y,double z){return spawnDummy(level,x,y,z,600,"SAM combat trial");}
    private static IronGolem spawnDummy(ServerLevel level,double x,double y,double z,double health,String name){
        IronGolem golem=EntityType.IRON_GOLEM.create(level);
        if(golem==null)throw new IllegalStateException("Cannot create practice golem");
        golem.moveTo(x,y,z,180,0);golem.setNoAi(true);golem.setPersistenceRequired();
        golem.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);golem.setHealth((float)health);
        golem.setCustomName(Component.literal(name));golem.setCustomNameVisible(true);level.addFreshEntity(golem);return golem;
    }
    public static void testTick(ServerTickEvent.Post event){
        if(!Boolean.getBoolean("fireflysam.test"))return;
        var server=event.getServer();
        if(tester==null){
            if(server.getPlayerList().getPlayers().isEmpty())return;
            tester=server.getPlayerList().getPlayers().getFirst();testStart=tester.level().getGameTime();
            REPORT.clear();REPORT.put("minecraft","1.21.1");REPORT.put("loader","NeoForge "+net.neoforged.fml.ModList.get().getModContainerById("neoforge").map(c->c.getModInfo().getVersion().toString()).orElse("unknown"));
        }
        if(!tester.isAlive()||tester.hasDisconnected())return;
        long age=tester.level().getGameTime()-testStart;
        try{
            if(age==20){
                ServerLevel level=tester.serverLevel();
                // Only executed in the dedicated, automatically created lab world.
                if(!server.getWorldData().getLevelName().startsWith("SAM-Lab-"))throw new IllegalStateException("Refusing to build test arena outside SAM-Lab world");
                for(int x=-36;x<=36;x++)for(int z=-24;z<=56;z++)for(int y=65;y<=80;y++){
                    var pos=new BlockPos(x,y,z);level.getChunkAt(pos);level.setBlock(pos,(y==80?Blocks.GRASS_BLOCK:y>76?Blocks.DIRT:Blocks.STONE).defaultBlockState(),2);
                }
                level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                tester.setGameMode(GameType.CREATIVE);tester.connection.teleport(0,81,0,0,12);
                tester.getInventory().setItem(tester.getInventory().selected,new ItemStack(FireflySam.SWORD.get()));
                tester.setGameMode(GameType.SURVIVAL);
                float healthBeforeFire=tester.getHealth();
                tester.hurt(tester.damageSources().onFire(),4.0f);
                check("blade_holder_fire_damage_immune",Math.abs(tester.getHealth()-healthBeforeFire)<.001f);
                tester.setHealth(tester.getMaxHealth());tester.setRemainingFireTicks(100);tester.setGameMode(GameType.CREATIVE);
                tester.resetStat(Stats.CUSTOM.get(Stats.MOB_KILLS));
                check("test_starts_with_zero_mob_kills",SamCombat.mobKills(tester)==0);
                SamCombat.cast(tester);check("ultimate_locked_outside_sam",!SamCombat.session(tester).combo.casting());
                SamCombat.toggle(tester);check("zero_kill_transform_allowed",SamCombat.active(tester));
                REPORT.put("test_mob_kills",SamCombat.mobKills(tester));
                dummy=spawnDummy(level,0,81,3.6);beforeFirst=dummy.getHealth();
                check("crafting_recipe_registered",server.getRecipeManager().byKey(FireflySam.id("firefly_blade")).isPresent());
                REPORT.put("test_started",testStart);
            }
            if(age==29&&SamCombat.active(tester)){SamCombat.toggle(tester);check("manual_toggle_disengages_sam",!SamCombat.active(tester));}
            if(age==35){float health=dummy.getHealth();tester.attack(dummy);REPORT.put("normal_melee_damage",health-dummy.getHealth());check("normal_form_no_combo",SamCombat.session(tester).combo.hits()==0);}
            // Wait for the registered keybind packet, rather than racing first-frame
            // shader/texture compilation on a cold client. No transform is injected here.
            if(age>=55&&combatStart<0){
                if(SamCombat.active(tester)){
                    combatStart=age;check("transform_keybind_packet",true);check("mech_active",SamCombat.active(tester));check("large_physical_scale",Math.abs(tester.getScale()-1.75)<.01);
                    check("transform_with_zero_mob_kills",SamCombat.mobKills(tester)==0);
                    SamCombat.session(tester).transformStart=tester.level().getGameTime()-(20*60*2+1);
                    SamCombat.sync(tester,SamCombat.session(tester));
                    SamCombat.cast(tester);check("ultimate_locked_at_zero",!SamCombat.session(tester).combo.casting());
                }else if(age>300)throw new AssertionError("Timed out waiting for transform keybind packet");
            }
            if(age==21&&!fireExtinguishReported){fireExtinguishReported=true;check("blade_holder_fire_extinguished",!tester.isOnFire());}
            long combatAge=age-combatStart;
            if(combatStart>=0&&combatAge==1)check("sam_remains_active_after_two_minutes",SamCombat.active(tester));
            if(combatStart>=0&&combatAge>=35&&combatAge<=195&&(combatAge-35)%16==0){
                if(dummy==null||!dummy.isAlive())throw new IllegalStateException("Practice target died before combat validation");
                float before=dummy.getHealth();tester.attack(dummy);tester.swing(net.minecraft.world.InteractionHand.MAIN_HAND);hits++;
                if(hits==1){double expected=((Number)REPORT.get("normal_melee_damage")).doubleValue()*2;check("damage_doubled",Math.abs((before-dummy.getHealth())-expected)<.25);REPORT.put("melee_damage",before-dummy.getHealth());}
                if(hits==9){check("nine_hits_no_ultimate",SamCombat.session(tester).combo.hits()==9);SamCombat.cast(tester);check("nine_hits_gate_enforced",!SamCombat.session(tester).combo.casting());}
                if(hits==10){check("ten_hits_ready",SamCombat.session(tester).combo.ready());check("weakness_break_stuns",dummy.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN));}
                if(hits==11){double normal=tester.getAttributeValue(Attributes.ATTACK_DAMAGE)*2;check("super_break_bonus",before-dummy.getHealth()>normal+5);REPORT.put("super_break_hit_total",before-dummy.getHealth());}
            }
            if(castStart<0&&SamCombat.session(tester).combo.casting()){
                castStart=SamCombat.session(tester).ultimateStart-testStart;
                check("ultimate_keybind_packet",SamCombat.session(tester).combo.casting());check("cast_consumes_charge",SamCombat.session(tester).combo.casting()&&SamCombat.session(tester).combo.hits()==0);
                brokenDummyHealthAtCast=dummy.getHealth();
                check("broken_target_eligible_when_ultimate_starts",SamCombat.weaknessBrokenForUltimate(SamCombat.session(tester),dummy,tester.level().getGameTime()));
                BlockPos center=BlockPos.containing(SamCombat.session(tester).target);
                sentinelBedrock=center.offset(1,-2,0);sentinelChest=center.offset(2,-2,0);sentinelWater=center.offset(-3,-4,0);
                tester.serverLevel().setBlock(sentinelBedrock,Blocks.BEDROCK.defaultBlockState(),2);
                tester.serverLevel().setBlock(sentinelChest,Blocks.CHEST.defaultBlockState(),2);
                tester.serverLevel().setBlock(sentinelWater,Blocks.WATER.defaultBlockState(),2);
                for(var direction:net.minecraft.core.Direction.values())tester.serverLevel().setBlock(sentinelWater.relative(direction),Blocks.BEDROCK.defaultBlockState(),2);
                var target=SamCombat.session(tester).target;
                burnDummy=spawnDummy(tester.serverLevel(),target.x+2,target.y,target.z,1000,"SAM invisible-burn trial");
            }
            long castAge=castStart<0?-1:age-castStart;
            if(castAge==CombatRules.IMPACT_TICK-1&&!brokenWindowExpiryReported){
                brokenWindowExpiryReported=true;
                long now=tester.level().getGameTime();
                check("weakness_window_expires_during_dive",!SamCombat.weaknessBrokenNow(dummy,now));
                check("cast_snapshot_keeps_broken_target_eligible",SamCombat.weaknessBrokenForUltimate(SamCombat.session(tester),dummy,now));
            }
            if(castAge==CombatRules.IMPACT_TICK+1&&!burnAppliedReported){
                burnAppliedReported=true;burnHealthAtImpact=burnDummy.getHealth();
                if(!brokenUltimateReported){
                    brokenUltimateReported=true;
                    long now=tester.level().getGameTime();
                    REPORT.put("ultimate_base_damage",CombatRules.ultimateDamage(SamConfig.ULTIMATE_DAMAGE.get(),SamConfig.ULTIMATE_BROKEN_MULTIPLIER.get(),false));
                    REPORT.put("ultimate_broken_damage",CombatRules.ultimateDamage(SamConfig.ULTIMATE_DAMAGE.get(),SamConfig.ULTIMATE_BROKEN_MULTIPLIER.get(),true));
                    REPORT.put("broken_dummy_health_before_ultimate",brokenDummyHealthAtCast);
                    check("broken_ultimate_massive_damage",!dummy.isAlive());
                    REPORT.put("broken_ultimate_massive_damage",!dummy.isAlive());
                }
                check("ultimate_applies_hidden_fire",SamCombat.invisibleBurning(burnDummy));
                check("ultimate_burn_has_no_flame_visual",!burnDummy.isOnFire());
            }
            if(castAge>=CombatRules.IMPACT_TICK+23&&!burnDamageReported){
                burnDamageReported=true;
                check("hidden_fire_deals_damage",burnDummy.getHealth()<burnHealthAtImpact);
                check("hidden_fire_stays_invisible",!burnDummy.isOnFire());
            }
            if(castAge>=CombatRules.CAST_TICKS+20&&!finished){finished=true;check("cast_finished",!SamCombat.session(tester).combo.casting());check("physics_restored",!tester.noPhysics&&!tester.isNoGravity());}
            if(castAge>=CombatRules.CAST_TICKS+50&&!hovered){hovered=true;tester.getAbilities().flying=true;tester.onUpdateAbilities();tester.connection.teleport(0,96,-18,0,38);}
            if(castAge>=CombatRules.CAST_TICKS+90&&!reported){
                reported=true;
                int holes=0;for(int x=-8;x<=8;x++)for(int z=10;z<=26;z++)if(tester.serverLevel().getBlockState(new BlockPos(x,80,z)).isAir())holes++;
                check("physical_crater",holes>200);check("bedrock_preserved",tester.serverLevel().getBlockState(sentinelBedrock).is(Blocks.BEDROCK));check("block_entity_preserved",tester.serverLevel().getBlockState(sentinelChest).is(Blocks.CHEST));check("fluid_preserved",!tester.serverLevel().getFluidState(sentinelWater).isEmpty());
                REPORT.put("removed_surface_samples",holes);REPORT.put("attacks",hits);REPORT.put("status","PASS");writeReport(server.getServerDirectory().resolve("sam-test-report.json"));
                FireflySam.LOGGER.log(System.Logger.Level.INFO,"SAM_IN_GAME_TEST_PASS "+REPORT);
            }
            if(castAge>=CombatRules.CAST_TICKS+120&&!postCastChecked){
                postCastChecked=true;
                check("sam_remains_active_after_ultimate",SamCombat.active(tester));
                writeReport(server.getServerDirectory().resolve("sam-test-report.json"));
            }
            if(castAge>=CombatRules.CAST_TICKS+120&&postCastChecked&&!postCastDisengaged){
                postCastDisengaged=true;SamCombat.toggle(tester);
                check("test_manual_toggle_after_ultimate",!SamCombat.active(tester));
                writeReport(server.getServerDirectory().resolve("sam-test-report.json"));
            }
        }catch(Throwable error){REPORT.put("status","FAIL");REPORT.put("error",error.toString());writeReport(server.getServerDirectory().resolve("sam-test-report.json"));FireflySam.LOGGER.log(System.Logger.Level.ERROR,"SAM_IN_GAME_TEST_FAIL",error);testStart-=100000;}
    }
    private static void check(String name,boolean result){REPORT.put(name,result);if(!result)throw new AssertionError(name);}
    private static void writeReport(Path file){try{Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));}catch(Exception e){FireflySam.LOGGER.log(System.Logger.Level.ERROR,"Cannot write test report",e);}}
}
