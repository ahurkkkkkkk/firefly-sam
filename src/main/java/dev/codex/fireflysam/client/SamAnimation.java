package dev.codex.fireflysam.client;

import dev.codex.fireflysam.SamNetwork;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/** Selects the same synced attack phase for GeckoLib third person and first person blades. */
public final class SamAnimation {
    private SamAnimation() {}
    private static final SwingPose REST = new SwingPose(0, 0, 0);
    public record SwingPose(float windup, float strike, float followThrough) {}

    private static float swingPhase(Player player, SamNetwork.State state, float partial) {
        if (state == null || !state.active()) return -1;
        double elapsed = player.level().getGameTime() - state.lastSwing() + partial;
        return state.ultimateStart() >= 0 || elapsed < 0 || elapsed >= 12 ? -1 : (float)elapsed / 12;
    }

    private static SwingPose bladePose(Player player, SamNetwork.State state, boolean right, float partial) {
        if (state == null || !state.active()) return REST;
        int step = state.swingStep();
        if (step != 2 && right != (step == 0)) return REST;
        return swingPose(swingPhase(player, state, partial), step != 2 || right);
    }

    public static SwingPose swingPose(Player player, InteractionHand hand, float partial) {
        boolean right = (hand == InteractionHand.MAIN_HAND) == (player.getMainArm() == HumanoidArm.RIGHT);
        return bladePose(player, SamClientState.PLAYERS.get(player.getId()), right, partial);
    }

    public static SwingPose swingPose(float phase, boolean lead) {
        if (phase < 0) return REST;
        float delay = lead ? 0 : .07f;
        float t = Mth.clamp((phase - delay) / (1 - delay), 0, 1);
        float strength = lead ? 1 : .90f;
        return new SwingPose(pulse(t, 0, .18f, .48f) * strength,
            pulse(t, .10f, .43f, .84f) * strength,
            pulse(t, .38f, .76f, 1) * strength);
    }

    public static float attackAmount(Player player, float partial) {
        SamNetwork.State state = SamClientState.PLAYERS.get(player.getId());
        SwingPose right = bladePose(player, state, true, partial), left = bladePose(player, state, false, partial);
        return Mth.clamp(Math.max(right.strike() + right.followThrough() * .55f + right.windup() * .1f,
            left.strike() + left.followThrough() * .55f + left.windup() * .1f), 0, 1);
    }

    public static Map<String, org.joml.Vector3f> pose(Player player, SamNetwork.State state, float partial) {
        return SamGeckoAnimation.pose(player, state, partial);
    }

    public static float walkAmount(Player player, float partial) {
        return Mth.clamp(player.walkAnimation.speed(partial) * 5f, 0, 1);
    }

    public static float walkStride(Player player, float partial) {
        return player.walkAnimation.position(partial) * .6662f;
    }

    public static float wingAmount(float age) { return ease((age - 10) / 27) * (1 - ease((age - 132) / 18)); }
    private static float pulse(float t, float start, float peak, float end) {
        if (t <= start || t >= end) return 0;
        return t < peak ? ease((t - start) / (peak - start)) : 1 - ease((t - peak) / (end - peak));
    }
    public static float ease(float value) {
        float t = Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
