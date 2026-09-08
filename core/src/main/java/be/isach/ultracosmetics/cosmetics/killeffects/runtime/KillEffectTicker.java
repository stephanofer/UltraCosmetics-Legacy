package be.isach.ultracosmetics.cosmetics.killeffects.runtime;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class KillEffectTicker implements Runnable {
    private static final class Entry {
        final KillEffectScene scene;
        final KillEffectExecution execution;
        final long start;
        Entry(KillEffectScene scene, KillEffectExecution execution, long start) {
            this.scene = scene;
            this.execution = execution;
            this.start = start;
        }
    }

    public final PacketBudget budget = new PacketBudget(1024, 12000);
    private final List<Entry> active = new ArrayList<>();
    private final JavaPlugin plugin;
    private final int maxDuration;
    private final BukkitTask task;
    private long tick;
    private boolean closed;
    private long completedSends, completedPoints;

    public KillEffectTicker(JavaPlugin plugin, int maxDuration) {
        this.plugin = plugin;
        this.maxDuration = maxDuration;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this, 1, 1);
    }

    public long currentTick() { return tick; }

    public boolean add(KillEffectScene scene, KillEffectExecution execution) {
        if (closed) { scene.close(); return false; }
        Entry entry = new Entry(scene, execution, tick);
        active.add(entry);
        try {
            execution.start();
            if (!scene.isAvailable()) { stop(entry); return false; }
            return true;
        } catch (RuntimeException | LinkageError e) {
            stop(entry);
            plugin.getLogger().warning("Kill Effect start failed: " + e);
            return false;
        }
    }

    @Override
    public void run() {
        tick++;
        budget.reset();
        for (Entry entry : new ArrayList<>(active)) {
            if (!active.contains(entry)) continue;
            try {
                entry.scene.beginTick();
                if (!entry.scene.isAvailable() || tick - entry.start >= maxDuration) {
                    stop(entry);
                    continue;
                }
                entry.execution.tick((int) (tick - entry.start));
                if (entry.execution.isComplete()) stop(entry);
            } catch (RuntimeException | LinkageError e) {
                stop(entry);
                plugin.getLogger().warning("Kill Effect execution failed: " + e);
            }
        }
    }

    private void stop(Entry entry) {
        if (!active.remove(entry)) return;
        try {
            entry.execution.stop();
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().warning("Kill Effect stop failed: " + e);
        } finally {
            entry.scene.close();
            completedSends += entry.scene.getTotalSends();
            completedPoints += entry.scene.getTotalPoints();
        }
    }

    public CapacityPolicy.Detail capacity(KillEffectSettings settings, UUID world, int x, int z) {
        int inWorld = 0, inChunk = 0;
        for (Entry entry : active) {
            if (!entry.scene.context.anchor.world.equals(world)) continue;
            inWorld++;
            if (!entry.scene.context.lite && entry.scene.context.anchor.chunkX() == x && entry.scene.context.anchor.chunkZ() == z) inChunk++;
        }
        return CapacityPolicy.decide(active.size(), inWorld, inChunk, settings.maxGlobal, settings.maxWorld, settings.maxChunk, settings.lite);
    }

    public boolean supportsFullAudience(int viewers) {
        int reserved = 0;
        for (Entry entry : active) {
            if (!entry.scene.context.lite && !entry.scene.context.airborne) reserved += entry.scene.context.audience.size() * 6;
        }
        return CapacityPolicy.supportsFullAudience(viewers, reserved)
                && budget.permits(0, CapacityPolicy.STRUCTURAL_SEND_RESERVE + viewers * 6);
    }

    public void stopPreview(UUID player) {
        for (Entry entry : new ArrayList<>(active)) {
            if (entry.scene.context.preview && entry.scene.context.killer.equals(player)) stop(entry);
        }
    }

    public void removeViewer(UUID viewer) {
        for (Entry entry : new ArrayList<>(active)) entry.scene.removeViewer(viewer);
        stopPreview(viewer);
    }

    public void stopWorld(UUID world) {
        for (Entry entry : new ArrayList<>(active)) if (entry.scene.context.anchor.world.equals(world)) stop(entry);
    }

    public void close() {
        closed = true;
        for (Entry entry : new ArrayList<>(active)) stop(entry);
        task.cancel();
    }

    public int getActiveScenes() { return active.size(); }
    public long getTotalSends() {
        long total = completedSends;
        for (Entry entry : active) total += entry.scene.getTotalSends();
        return total;
    }
    public long getTotalPoints() {
        long total = completedPoints;
        for (Entry entry : active) total += entry.scene.getTotalPoints();
        return total;
    }
}
