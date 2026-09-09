package be.isach.ultracosmetics.cosmetics.killeffects.runtime;

public final class CapacityPolicy {
    public static final int STRUCTURAL_SEND_RESERVE = 4096;
    // All sixteen stands at entry (spawn, metadata, helmet); stabilization starts later.
    public static final int FREEZE_SENDS_PER_VIEWER = 48;
    public enum Detail { FULL, LITE, SKIP }

    private CapacityPolicy() { }

    public static boolean supportsFullAudience(int viewers, int reserved) {
        return viewers > 0 && viewers <= 2048 / FREEZE_SENDS_PER_VIEWER && reserved >= 0
                && (long) reserved + (long) viewers * FREEZE_SENDS_PER_VIEWER <= STRUCTURAL_SEND_RESERVE;
    }

    public static Detail decide(int global, int world, int chunkFull, int maxGlobal, int maxWorld, int maxChunk, boolean lite) {
        if (global >= maxGlobal || world >= maxWorld) return Detail.SKIP;
        if (chunkFull < maxChunk) return Detail.FULL;
        return lite ? Detail.LITE : Detail.SKIP;
    }
}
