package be.isach.ultracosmetics.cosmetics.killeffects.runtime;

public interface KillEffectExecution {
    void start();
    void tick(int elapsedTicks);
    boolean isComplete();
    void stop();
}
