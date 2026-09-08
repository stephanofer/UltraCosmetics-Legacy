package be.isach.ultracosmetics.cosmetics.killeffects.runtime;

public final class CapacityPolicy {
    public static final int STRUCTURAL_SEND_RESERVE = 4096;
    public enum Detail { FULL, LITE, SKIP }

    private CapacityPolicy() { }

    public static boolean supportsFullAudience(int viewers, int reserved) {
        return viewers > 0 && viewers <= 170 && reserved >= 0
                && (long) reserved + (long) viewers * 6 <= STRUCTURAL_SEND_RESERVE;
    }

    public static Detail decide(int global, int world, int chunkFull, int maxGlobal, int maxWorld, int maxChunk, boolean lite) {
        if (global >= maxGlobal || world >= maxWorld) return Detail.SKIP;
        if (chunkFull < maxChunk) return Detail.FULL;
        return lite ? Detail.LITE : Detail.SKIP;
    }
}
