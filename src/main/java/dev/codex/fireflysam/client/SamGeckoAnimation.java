package dev.codex.fireflysam.client;

import dev.codex.fireflysam.FireflySam;
import dev.codex.fireflysam.SamNetwork;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Drives the imported PMX skeleton from editable Blockbench/GeckoLib clips. */
public final class SamGeckoAnimation {
    private static final ResourceModel MODEL = new ResourceModel();
    private static final Map<Integer, Rig> RIGS = new HashMap<>();
    private static final Map<String, RawAnimation> ANIMATIONS = new HashMap<>();
    private static final float WALK_CYCLE_TICKS = 26.666668f;
    private static final float SPRINT_CYCLE_TICKS = 13.333334f;
    private static final String[] BONES = {"root", "hips", "torso", "head", "right_arm", "right_forearm", "right_hand",
        "right_leg", "right_shin", "right_foot", "left_arm", "left_forearm", "left_hand", "left_leg", "left_shin",
        "left_foot", "right_blade", "left_blade"};

    private SamGeckoAnimation() {}

    public static Map<String, Vector3f> pose(Player player, SamNetwork.State state, float partial) {
        String clip = select(player, state, partial);
        if (clip == null) return Map.of();
        long token = state == null ? -1 : clip.startsWith("attack_") ? state.lastSwing()
            : clip.equals("ultimate") ? state.ultimateStart() : 0;
        double now = player.level().getGameTime() + partial;
        boolean distanceClock = clip.equals("walk") || clip.equals("sprint") || clip.equals("backpedal")
            || clip.equals("strafe_left") || clip.equals("strafe_right");
        double clock = distanceClock ? cycleTicks(player, partial, clip.equals("sprint")) : now;
        double start = distanceClock || clip.equals("idle") ? 0
            : clip.equals("ultimate") ? state.ultimateStart() : state.lastSwing();
        Rig rig = RIGS.get(player.getId());
        if (rig == null || !rig.clip.equals(clip) || rig.token != token || rig.distanceClock != distanceClock) {
            rig = new Rig(clip, token, distanceClock, start);
            RIGS.put(player.getId(), rig);
        }

        var manager = rig.getAnimatableInstanceCache().getManagerForId(player.getId());
        if (manager.getFirstTickTime() < 0) manager.startedAt(start);
        AnimationState<Rig> animationState = new AnimationState<>(rig,
            player.walkAnimation.position(partial), SamAnimation.walkAmount(player, partial), partial,
            SamAnimation.walkAmount(player, partial) > .01f);
        // GeckoLib normally derives TICK from an entity or the game renderer.
        // Supplying it here lets locomotion advance from traveled distance while
        // attacks and the ultimate stay synchronized to their server tick.
        animationState.setData(DataTickets.TICK, clock - partial);
        MODEL.getBakedModel(MODEL.getModelResource(rig));
        MODEL.handleAnimations(rig, player.getId(), animationState, partial);

        Map<String, Vector3f> pose = new HashMap<>(BONES.length);
        for (GeoBone bone : MODEL.getAnimationProcessor().getRegisteredBones()) {
            pose.put(bone.getName(), new Vector3f(bone.getRotX(), bone.getRotY(), bone.getRotZ()));
        }
        for (String bone : BONES) pose.putIfAbsent(bone, new Vector3f());
        if (distanceClock) pose.put("__offset_hips", new Vector3f(0, -.09f * SamAnimation.walkAmount(player, partial), 0));
        return pose;
    }

    public static void clear() { RIGS.clear(); }
    public static String currentClip(int entityId) {
        Rig rig = RIGS.get(entityId);
        return rig == null ? "none" : rig.clip;
    }

    private static String select(Player player, SamNetwork.State state, float partial) {
        double now = player.level().getGameTime() + partial;
        if (state != null && state.ultimateStart() >= 0) return "ultimate";
        if (state != null && state.active()) {
            double elapsed = now - state.lastSwing();
            if (elapsed >= 0 && elapsed < 12) {
                return switch (state.swingStep()) {
                    case 1 -> "attack_left";
                    case 2 -> "attack_pair";
                    default -> "attack_right";
                };
            }
        }
        float movement = SamAnimation.walkAmount(player, partial);
        if (movement < .06f) return "idle";
        Vec3 delta = player.getDeltaMovement();
        double yaw = Math.toRadians(player.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(forward.z, 0, -forward.x);
        double forwardTravel = delta.x * forward.x + delta.z * forward.z;
        double sideTravel = delta.x * right.x + delta.z * right.z;
        if (Math.abs(sideTravel) > Math.abs(forwardTravel) * 1.15) return sideTravel < 0 ? "strafe_left" : "strafe_right";
        if (forwardTravel < -.025) return "backpedal";
        return player.isSprinting() ? "sprint" : "walk";
    }

    private static final Map<Player, Travel> TRAVEL = new java.util.WeakHashMap<>();
    private static final class Travel { Vec3 position; double cycles; }

    private static double cycleTicks(Player player, float partial, boolean sprint) {
        Vec3 position = player.getPosition(partial);
        Travel travel = TRAVEL.computeIfAbsent(player, ignored -> new Travel());
        if (travel.position != null) {
            double distance = Math.hypot(position.x - travel.position.x, position.z - travel.position.z);
            if (distance < 1) travel.cycles += distance / (sprint ? 1.96 : 1.44);
        }
        travel.position = position;
        double cycles = travel.cycles;
        return cycles * (sprint ? SPRINT_CYCLE_TICKS : WALK_CYCLE_TICKS);
    }

    private static RawAnimation animation(String clip) {
        return ANIMATIONS.computeIfAbsent(clip, name -> switch (name) {
            case "idle", "walk", "sprint", "backpedal", "strafe_left", "strafe_right" ->
                RawAnimation.begin().thenLoop("animation.sam." + name);
            default -> RawAnimation.begin().thenPlay("animation.sam." + name);
        });
    }

    private static final class Rig implements GeoAnimatable {
        private final String clip;
        private final long token;
        private final boolean distanceClock;
        private final double start;
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

        private Rig(String clip, long token, boolean distanceClock, double start) {
            this.clip = clip;
            this.token = token;
            this.distanceClock = distanceClock;
            this.start = start;
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
            registrar.add(new AnimationController<>(this, "sam_rig", 0, state -> {
                state.setControllerSpeed(1);
                return state.setAndContinue(animation(this.clip));
            }));
        }

        @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
        @Override public double getTick(Object relatedObject) { return start; }
    }

    private static final class ResourceModel extends GeoModel<Rig> {
        @Override public net.minecraft.resources.ResourceLocation getModelResource(Rig animatable) {
            return FireflySam.id("geo/sam_rig.geo.json");
        }
        @Override public net.minecraft.resources.ResourceLocation getTextureResource(Rig animatable) {
            return FireflySam.id("textures/white.png");
        }
        @Override public net.minecraft.resources.ResourceLocation getAnimationResource(Rig animatable) {
            return FireflySam.id("animations/sam_rig.animation.json");
        }
    }
}
