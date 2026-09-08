package be.isach.ultracosmetics.cosmetics.killeffects.runtime;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class ExecutionTest {
    @Test
    public void startTimeoutAndStopAreIdempotent() {
        AtomicInteger ticks = new AtomicInteger(), cleanup = new AtomicInteger();
        KillEffectExecution execution = new AbstractKillEffectExecution(60, cleanup::incrementAndGet) {
            @Override protected void onTick(int elapsed) { ticks.incrementAndGet(); }
        };
        execution.tick(1);
        assertEquals(0, ticks.get());
        execution.start();
        execution.start();
        execution.tick(59);
        assertEquals(2, ticks.get());
        assertFalse(execution.isComplete());
        execution.tick(60);
        execution.stop();
        execution.start();
        execution.tick(61);
        assertTrue(execution.isComplete());
        assertEquals(1, cleanup.get());
        assertEquals(2, ticks.get());
    }

    @Test
    public void failureClosesExecutionAndPropagatesToTicker() {
        AtomicInteger cleanup = new AtomicInteger();
        KillEffectExecution execution = new AbstractKillEffectExecution(60, cleanup::incrementAndGet) {
            @Override protected void onTick(int elapsed) { throw new IllegalStateException("effect failure"); }
        };
        try { execution.start(); fail("Failure must propagate"); } catch (IllegalStateException expected) { }
        execution.stop();
        assertTrue(execution.isComplete());
        assertEquals(1, cleanup.get());
    }

    @Test
    public void stoppingBeforeStartPreventsSetup() {
        AtomicInteger cleanup = new AtomicInteger();
        KillEffectExecution execution = new AbstractKillEffectExecution(60, cleanup::incrementAndGet) {
            @Override protected void onTick(int elapsed) { fail("Stopped execution must not start"); }
        };
        execution.stop();
        execution.start();
        assertEquals(1, cleanup.get());
    }

    @Test
    public void executionsHaveIndependentState() {
        AtomicInteger first = new AtomicInteger(), second = new AtomicInteger();
        KillEffectExecution a = new AbstractKillEffectExecution(10, first::incrementAndGet) {
            @Override protected void onTick(int elapsed) { }
        };
        KillEffectExecution b = new AbstractKillEffectExecution(10, second::incrementAndGet) {
            @Override protected void onTick(int elapsed) { }
        };
        a.start(); b.start(); a.tick(10);
        assertEquals(1, first.get());
        assertFalse(b.isComplete());
        assertEquals(0, second.get());
    }
}
