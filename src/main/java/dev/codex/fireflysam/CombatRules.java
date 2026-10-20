package dev.codex.fireflysam;

/** Deterministic combo and Break rules shared by the game implementation and executable tests. */
public final class CombatRules {
    public static final int REQUIRED_HITS = 10;
    public static final int COMBO_WINDOW = 120;
    public static final int CAST_TICKS = 160;
    public static final int DIVE_START = 112;
    public static final int IMPACT_TICK = 132;
    public static final int BREAK_TICKS = 100;
    public static final int INVISIBLE_BURN_TICKS = 200;
    public static final int INVISIBLE_BURN_INTERVAL = 20;
    public static final float INVISIBLE_BURN_DAMAGE = 1.0f;
    private CombatRules() {}

    public static final class Combo {
        private int hits;
        private long lastHit = -1000;
        private boolean casting;
        public int hits() { return hits; }
        public long lastHit() { return lastHit; }
        public boolean ready() { return hits == REQUIRED_HITS && !casting; }
        public boolean casting() { return casting; }
        public void tick(long now) {
            if (hits > 0 && hits < REQUIRED_HITS && now - lastHit > COMBO_WINDOW) hits = 0;
        }
        public boolean hit(long now, boolean mech, boolean charged, boolean confirmedPrimary) {
            tick(now);
            if (!mech || casting || !charged || !confirmedPrimary || now - lastHit < 8) return false;
            hits = Math.min(REQUIRED_HITS, hits + 1);
            lastHit = now;
            return true;
        }
        public boolean cast(boolean mech) {
            if (!mech || !ready()) return false;
            hits = 0;
            casting = true;
            return true;
        }
        public void finish() { casting = false; }
        public void reset() { hits = 0; lastHit = -1000; casting = false; }
    }

    public static float toughness(float health, float armor) {
        return Math.max(24, Math.min(240, 24 + health * .5f + armor * 2));
    }
    public static float breakDamage(float maxHealth, double breakEffectPercent) {
        return (float) ((8 + Math.min(32, maxHealth * 0.08)) * (1 + breakEffectPercent / 100));
    }
    public static float superBreakDamage(float reduction, double breakEffectPercent) {
        return (float) (reduction * 0.6 * (1 + breakEffectPercent / 100));
    }
    public static float ultimateDamage(double baseDamage, double brokenMultiplier, boolean weaknessBroken) {
        return (float) (baseDamage * 2 * (weaknessBroken ? brokenMultiplier : 1));
    }
    public static double craterFloor(double distance, double radius, double depth) {
        double t = distance / radius;
        return t >= 1 ? 0 : -depth * (1 - t * t);
    }
}
