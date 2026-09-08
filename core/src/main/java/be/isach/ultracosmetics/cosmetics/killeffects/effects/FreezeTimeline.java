package be.isach.ultracosmetics.cosmetics.killeffects.effects;

public final class FreezeTimeline {
    public enum Phase { IMPACT, FREEZE, SEAL, HOLD, FRACTURE, SHATTER, CLEANUP }
    private FreezeTimeline() { }

    public static int animationTick(int elapsed, int duration) {
        // Extend only the frozen hold; formation and shattering retain their original timing.
        if (elapsed <= 42) return elapsed;
        return Math.max(42, elapsed - (duration - 60));
    }

    public static Phase phase(int tick) {
        if (tick <= 0) return Phase.IMPACT;
        if (tick <= 6) return Phase.FREEZE;
        if (tick <= 12) return Phase.SEAL;
        if (tick <= 42) return Phase.HOLD;
        if (tick <= 52) return Phase.FRACTURE;
        if (tick <= 58) return Phase.SHATTER;
        return Phase.CLEANUP;
    }
}
