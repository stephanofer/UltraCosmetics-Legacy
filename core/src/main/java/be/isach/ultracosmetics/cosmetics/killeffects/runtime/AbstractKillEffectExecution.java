package be.isach.ultracosmetics.cosmetics.killeffects.runtime;

public abstract class AbstractKillEffectExecution implements KillEffectExecution {
    private final int duration;
    private final Runnable cleanup;
    private boolean started;
    private boolean stopped;

    protected AbstractKillEffectExecution(int duration, Runnable cleanup) {
        if (duration < 1) throw new IllegalArgumentException("Nonpositive duration");
        this.duration = duration;
        this.cleanup = java.util.Objects.requireNonNull(cleanup);
    }

    @Override
    public final void start() {
        if (started || stopped) return;
        started = true;
        try {
            onTick(0);
        } catch (RuntimeException | LinkageError e) {
            stop();
            throw e;
        }
    }

    @Override
    public final void tick(int elapsedTicks) {
        if (!started || stopped) return;
        if (elapsedTicks >= duration) {
            stop();
            return;
        }
        try {
            onTick(elapsedTicks);
        } catch (RuntimeException | LinkageError e) {
            stop();
            throw e;
        }
    }

    protected abstract void onTick(int elapsedTicks);

    @Override
    public final boolean isComplete() { return stopped; }

    @Override
    public final void stop() {
        if (stopped) return;
        stopped = true;
        cleanup.run();
    }
}
