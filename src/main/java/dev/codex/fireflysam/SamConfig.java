package dev.codex.fireflysam;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SamConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue TERRAIN_DAMAGE;
    public static final ModConfigSpec.IntValue CRATER_RADIUS;
    public static final ModConfigSpec.IntValue CRATER_DEPTH;
    public static final ModConfigSpec.IntValue BLOCKS_PER_TICK;
    public static final ModConfigSpec.DoubleValue ULTIMATE_DAMAGE;
    public static final ModConfigSpec.DoubleValue ULTIMATE_BROKEN_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue BREAK_EFFECT;
    static {
        var b = new ModConfigSpec.Builder();
        b.comment("The ultimate destroys terrain. Use a spare world for testing.").push("ultimate");
        TERRAIN_DAMAGE = b.define("terrainDamage", true);
        CRATER_RADIUS = b.defineInRange("craterRadius", 24, 6, 48);
        CRATER_DEPTH = b.defineInRange("craterDepth", 10, 2, 24);
        BLOCKS_PER_TICK = b.defineInRange("blocksPerTick", 768, 64, 2048);
        ULTIMATE_DAMAGE = b.comment("Base damage before SAM's 2x multiplier.").defineInRange("baseDamage", 70.0, 1.0, 1000.0);
        ULTIMATE_BROKEN_MULTIPLIER = b.comment("Extra multiplier when the ultimate hits a weakness-broken target; broken enemies at cast time stay eligible through the dive.").defineInRange("brokenDamageMultiplier", 10.0, 1.0, 100.0);
        b.pop().push("break");
        BREAK_EFFECT = b.comment("Bonus Break Effect, in percent.").defineInRange("breakEffectPercent", 150.0, 0.0, 1000.0);
        b.pop();
        SPEC = b.build();
    }
    private SamConfig() {}
}
