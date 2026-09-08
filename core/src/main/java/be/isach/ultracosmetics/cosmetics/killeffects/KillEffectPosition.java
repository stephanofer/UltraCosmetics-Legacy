package be.isach.ultracosmetics.cosmetics.killeffects;

import java.util.UUID;

public final class KillEffectPosition {
    public final UUID world;
    public final double x, y, z;
    public final float yaw, pitch;

    public KillEffectPosition(UUID world, double x, double y, double z, float yaw, float pitch) {
        this.world = java.util.Objects.requireNonNull(world);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000) {
            throw new IllegalArgumentException("Invalid effect position");
        }
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public int chunkX() { return ((int) Math.floor(x)) >> 4; }
    public int chunkZ() { return ((int) Math.floor(z)) >> 4; }
}
