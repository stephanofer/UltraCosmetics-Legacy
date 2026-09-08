package be.isach.ultracosmetics.cosmetics.killeffects.runtime;

/** Counts points separately from actual per-viewer packet sends. Cleanup never uses this budget. */
public final class PacketBudget {
    private final int maxPoints;
    private final int maxSends;
    private int points;
    private int sends;

    public PacketBudget(int maxPoints, int maxSends) {
        if (maxPoints < 0 || maxSends < 0) throw new IllegalArgumentException("Negative budget");
        this.maxPoints = maxPoints;
        this.maxSends = maxSends;
    }

    public boolean permits(int points, int sends) {
        return points >= 0 && sends >= 0 && points <= maxPoints - this.points && sends <= maxSends - this.sends;
    }

    public boolean spend(int points, int sends) {
        if (!permits(points, sends)) return false;
        this.points += points;
        this.sends += sends;
        return true;
    }

    public void reset() {
        points = 0;
        sends = 0;
    }

    public int getPoints() { return points; }
    public int getSends() { return sends; }
}
