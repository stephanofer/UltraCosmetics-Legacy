package be.isach.ultracosmetics.cosmetics.killeffects.runtime;

import be.isach.ultracosmetics.cosmetics.killeffects.KillEffectContext;
import be.isach.ultracosmetics.cosmetics.killeffects.render.KillEffectRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

/** Owns setup, per-viewer delivery, and cleanup; effects never receive the raw renderer. */
public final class KillEffectScene {
    private static final double ICE_SIZE = 0.625;
    // 1.8.9: living-render translation - armor-stand head pivot + helmet translation.
    private static final double ICE_HEAD_CENTER = 1.5078125 - 0.0625 + 0.25;
    // Skull helmets use the legacy skull renderer's 1.1875 scale, not block-helmet translation.
    private static final double SKULL_HEAD_CENTER = 1.5078125 - 0.0625 + 1.1875 * 0.25;
    private static final class Viewer {
        final boolean full;
        final Set<Integer> entities = new HashSet<>();
        UUID lease;
        boolean nameTag;
        Viewer(boolean full) { this.full = full; }
    }

    public final KillEffectContext context;
    private final KillEffectRenderer renderer;
    private final EntityIdAllocator allocator;
    private final KillEffectSettings settings;
    private final PacketBudget global;
    private final PacketBudget budget = new PacketBudget(128, 2048);
    private final Logger logger;
    private final Map<UUID, Viewer> viewers = new LinkedHashMap<>();
    private final UUID[] audienceIds;
    private final int structuralReserve;
    private final Set<Integer> entities = new HashSet<>();
    private final Map<Integer, double[]> iceOffsets = new LinkedHashMap<>();
    private final Set<Integer> quarantined = new HashSet<>();
    private boolean closed;
    private boolean playerSpawned;
    private int playerEntity = -1;
    private String nameTagTeam;
    private long totalSends, totalPoints, cleanupSends;

    public KillEffectScene(KillEffectContext context, KillEffectRenderer renderer, EntityIdAllocator allocator,
                           KillEffectSettings settings, PacketBudget global, Logger logger) {
        this(context, renderer, allocator, settings, global, logger,
                context.lite ? 0 : CapacityPolicy.FREEZE_SENDS_PER_VIEWER);
    }

    public KillEffectScene(KillEffectContext context, KillEffectRenderer renderer, EntityIdAllocator allocator,
                           KillEffectSettings settings, PacketBudget global, Logger logger, int sendsPerViewer) {
        this.context = context;
        this.renderer = renderer;
        this.allocator = allocator;
        this.settings = settings;
        this.global = global;
        this.logger = logger;
        context.audience.forEach((id, full) -> viewers.put(id, new Viewer(full)));
        audienceIds = context.audience.keySet().toArray(new UUID[0]);
        structuralReserve = audienceIds.length * sendsPerViewer;
    }

    public void beginTick() {
        budget.reset();
        for (UUID id : audienceIds) {
            if (!viewers.containsKey(id)) continue;
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline() || !player.getWorld().getUID().equals(context.anchor.world)) {
                removeViewer(id);
                continue;
            }
            Location location = player.getLocation();
            double dx = location.getX() - context.anchor.x, dy = location.getY() - context.anchor.y, dz = location.getZ() - context.anchor.z;
            if (dx * dx + dy * dy + dz * dz > settings.range * settings.range) removeViewer(id);
        }
    }

    public boolean isAvailable() {
        World world = Bukkit.getWorld(context.anchor.world);
        return !closed && !viewers.isEmpty() && world != null
                && world.isChunkLoaded(context.anchor.chunkX(), context.anchor.chunkZ());
    }

    private void send(UUID viewer, Consumer<UUID> packet) {
        if (!viewers.containsKey(viewer)) return;
        if (!budget.permits(0, 1) || !global.permits(0, 1)) throw new IllegalStateException("Scene packet budget exceeded");
        budget.spend(0, 1);
        global.spend(0, 1);
        totalSends++;
        try {
            packet.accept(viewer);
        } catch (RuntimeException | LinkageError e) {
            removeViewer(viewer);
            logger.warning("Kill Effect viewer delivery failed: " + e);
        }
    }

    private int allocate() {
        if (closed || entities.size() >= settings.maxEntities) throw new IllegalStateException("Scene entity limit exceeded");
        int id = allocator.allocate();
        entities.add(id);
        return id;
    }

    public int spawnPlayer(double yOffset) {
        if (playerSpawned) throw new IllegalStateException("A scene can own only one victim replica");
        playerSpawned = true;
        int id = allocate();
        playerEntity = id;
        nameTagTeam = "uc_" + Integer.toUnsignedString(id, 36);
        Player victim = Bukkit.getPlayer(context.victim.uuid);
        for (UUID viewer : audienceIds) {
            Viewer state = viewers.get(viewer);
            if (state == null) continue;
            Player player = Bukkit.getPlayer(viewer);
            if (context.victim.hasNameTag) {
                state.nameTag = true; // Track before sending so partial setup is always cleaned.
                send(viewer, v -> renderer.createNameTag(v, nameTagTeam, context.victim));
            }
            UUID profile = context.victim.uuid;
            if (victim == null || !victim.isOnline() || player == null || !player.canSee(victim)
                    || !renderer.knowsProfile(viewer, context.victim.uuid)) {
                profile = UUID.randomUUID();
                state.lease = profile;
                UUID lease = profile;
                send(viewer, v -> renderer.profile(v, lease, context.victim, true));
            }
            UUID identity = profile;
            state.entities.add(id); // Track before sending so partial setup is always cleaned.
            send(viewer, v -> renderer.spawnPlayer(v, id, identity, context.death.x, context.death.y + yOffset, context.death.z,
                    context.death.yaw, context.death.pitch));
            send(viewer, v -> renderer.headRotation(v, id, context.death.yaw));
        }
        return id;
    }

    public int[] spawnIce(int layer) {
        int[] ids = new int[4];
        // Axis-aligned 0.625-block edges occupy exactly 20 units of the 1.8 packet grid.
        // Rotating the grid would round centers and helmet angles independently, breaking shared faces.
        float yaw = 0;
        for (int i = 0; i < ids.length; i++) {
            int id = ids[i] = allocate();
            double dx = (i % 2 - 0.5) * ICE_SIZE;
            double dz = (i / 2 - 0.5) * ICE_SIZE;
            double dy = (layer + 0.5) * ICE_SIZE - ICE_HEAD_CENTER;
            iceOffsets.put(id, new double[]{dx, dy, dz, yaw});
            for (UUID viewer : audienceIds) {
                Viewer state = viewers.get(viewer);
                if (state == null) continue;
                state.entities.add(id);
                send(viewer, v -> renderer.spawnArmorStand(v, id, context.death.x + dx, context.death.y + dy,
                        context.death.z + dz, yaw));
                send(viewer, v -> renderer.hideArmorStand(v, id));
                send(viewer, v -> renderer.equipIceHelmet(v, id));
            }
        }
        return ids;
    }

    /** Head coordinates refer to the visible skull center, not the hidden stand's feet. */
    public int spawnHead(double x, double y, double z, float yaw) {
        int id = allocate();
        for (UUID viewer : audienceIds) {
            Viewer state = viewers.get(viewer);
            if (state == null) continue;
            state.entities.add(id);
            send(viewer, v -> renderer.spawnArmorStand(v, id, context.anchor.x + x,
                    context.anchor.y + y - SKULL_HEAD_CENTER, context.anchor.z + z, yaw));
            send(viewer, v -> renderer.hideFloatingHeadStand(v, id));
            send(viewer, v -> renderer.equipVictimHead(v, id, context.victim));
        }
        return id;
    }

    public int spawnSquid(double y) {
        int id = allocate();
        for (UUID viewer : audienceIds) {
            Viewer state = viewers.get(viewer);
            if (state == null) continue;
            state.entities.add(id);
            send(viewer, v -> renderer.spawnSquid(v, id, context.anchor.x, context.anchor.y + y,
                    context.anchor.z, context.death.yaw));
        }
        return id;
    }

    public void moveHead(int id, double x, double y, double z, float yaw) {
        move(id, x, y - SKULL_HEAD_CENTER, z, yaw, 0);
    }

    public void move(int id, double x, double y, double z, float yaw, float pitch) {
        if (!entities.contains(id)) return;
        for (UUID viewer : audienceIds) {
            send(viewer, v -> renderer.teleportEntity(v, id, context.anchor.x + x, context.anchor.y + y,
                    context.anchor.z + z, yaw, pitch));
            if (id == playerEntity) send(viewer, v -> renderer.headRotation(v, id, yaw));
        }
    }

    public void sound(KillEffectRenderer.Sound sound, double height, float volume, float pitch) {
        if (!settings.sounds || closed) return;
        for (UUID viewer : audienceIds) {
            if (!budget.permits(0, 1 + structuralReserve)
                    || !global.permits(0, 1 + CapacityPolicy.STRUCTURAL_SEND_RESERVE)) break;
            send(viewer, v -> renderer.playSound(v, sound, context.anchor.x, context.anchor.y + height,
                    context.anchor.z, volume, pitch));
        }
    }

    public void stabilizeIce(int[] ids, double vibration) {
        if (ids == null) return;
        for (int id : ids) {
            double[] offset = iceOffsets.get(id);
            if (offset == null || !entities.contains(id)) continue;
            for (UUID viewer : audienceIds) {
                send(viewer, v -> renderer.teleportEntity(v, id, context.death.x + offset[0] + vibration,
                        context.death.y + offset[1], context.death.z + offset[2], (float) offset[3], 0));
                send(viewer, v -> renderer.stopEntityVelocity(v, id));
            }
        }
    }

    public void status(int id, byte status) {
        if (!entities.contains(id)) return;
        for (UUID viewer : audienceIds) send(viewer, v -> renderer.playEntityStatus(v, id, status));
    }

    public void particle(KillEffectRenderer.Particle particle, double dx, double dy, double dz, int ordinal) {
        if (closed || !budget.permits(1, 0) || !global.permits(1, 0)) return;
        budget.spend(1, 0);
        global.spend(1, 0);
        totalPoints++;
        for (UUID viewer : audienceIds) {
            Viewer state = viewers.get(viewer);
            if (state == null || (!state.full && ordinal % 3 != 0)) continue;
            if (!budget.permits(0, 1 + structuralReserve)
                    || !global.permits(0, 1 + CapacityPolicy.STRUCTURAL_SEND_RESERVE)) break;
            send(viewer, v -> renderer.spawnParticle(v, particle, context.anchor.x + dx, context.anchor.y + dy, context.anchor.z + dz));
        }
    }

    public void sound(float volume, float pitch) {
        if (!settings.sounds || closed) return;
        for (UUID viewer : audienceIds) {
            if (!budget.permits(0, 1 + structuralReserve)
                    || !global.permits(0, 1 + CapacityPolicy.STRUCTURAL_SEND_RESERVE)) break;
            send(viewer, v -> renderer.playSound(v, context.anchor.x, context.anchor.y + 1, context.anchor.z, volume, pitch));
        }
    }

    public void destroy(int id) {
        if (!entities.remove(id)) return;
        iceOffsets.remove(id);
        for (UUID viewer : audienceIds) {
            Viewer state = viewers.get(viewer);
            if (state != null && state.entities.remove(id)) destroyFor(viewer, new int[]{id});
            if (id == playerEntity && state != null) removeNameTagFor(viewer, state);
        }
        if (id == playerEntity) playerEntity = -1;
        if (!quarantined.contains(id)) allocator.release(id);
    }

    public void destroy(int[] ids) {
        if (ids == null) return;
        for (int id : ids) destroy(id);
    }

    private void destroyFor(UUID viewer, int[] ids) {
        Player player = Bukkit.getPlayer(viewer);
        if (player == null || !player.isOnline()) return;
        try {
            cleanupSends++;
            renderer.destroyEntities(viewer, ids);
        } catch (RuntimeException | LinkageError e) {
            // Do not recycle IDs whose destruction could not be delivered.
            for (int id : ids) quarantined.add(id);
            logger.warning("Kill Effect cleanup delivery failed; IDs quarantined: " + e);
        }
    }

    private void removeNameTagFor(UUID viewer, Viewer state) {
        if (!state.nameTag) return;
        state.nameTag = false;
        Player player = Bukkit.getPlayer(viewer);
        if (player == null || !player.isOnline()) return;
        try {
            cleanupSends++;
            renderer.removeNameTag(viewer, nameTagTeam);
        } catch (RuntimeException | LinkageError e) {
            logger.warning("Kill Effect temporary name tag cleanup failed: " + e);
        }
    }

    public void removeViewer(UUID viewer) {
        Viewer state = viewers.remove(viewer);
        if (state == null) return;
        if (!state.entities.isEmpty()) destroyFor(viewer, state.entities.stream().mapToInt(Integer::intValue).toArray());
        removeNameTagFor(viewer, state);
        if (state.lease != null && Bukkit.getPlayer(viewer) != null) {
            try {
                cleanupSends++;
                renderer.profile(viewer, state.lease, context.victim, false);
            } catch (RuntimeException | LinkageError e) {
                logger.warning("Kill Effect temporary profile cleanup failed: " + e);
            }
        }
    }

    public void close() {
        if (closed) return;
        closed = true;
        for (UUID viewer : audienceIds) removeViewer(viewer);
        for (int id : entities) if (!quarantined.contains(id)) allocator.release(id);
        entities.clear();
        iceOffsets.clear();
    }

    public long getTotalSends() { return totalSends + cleanupSends; }
    public long getTotalPoints() { return totalPoints; }
    public int getStructuralReserve() { return structuralReserve; }
}
