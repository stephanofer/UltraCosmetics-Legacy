package be.isach.ultracosmetics.cosmetics.killeffects;

import be.isach.ultracosmetics.UltraCosmetics;
import be.isach.ultracosmetics.config.MessageManager;
import be.isach.ultracosmetics.config.SettingsManager;
import be.isach.ultracosmetics.cosmetics.Category;
import be.isach.ultracosmetics.cosmetics.Cosmetic;
import be.isach.ultracosmetics.cosmetics.killeffects.render.KillEffectRenderer;
import be.isach.ultracosmetics.cosmetics.killeffects.runtime.*;
import be.isach.ultracosmetics.cosmetics.type.KillEffectType;
import be.isach.ultracosmetics.events.UCKillEffectTriggerEvent;
import be.isach.ultracosmetics.player.UltraPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class KillEffectManager implements Listener {
    private static final class CapturedDeath {
        final VictimSnapshot victim;
        final Location location;
        CapturedDeath(VictimSnapshot victim, Location location) { this.victim = victim; this.location = location; }
    }
    private final UltraCosmetics plugin;
    private final KillEffectRenderer renderer;
    private final KillEffectSettings settings;
    private static final EntityIdAllocator ENTITY_IDS = new EntityIdAllocator();
    private final KillEffectTicker ticker;
    private final Map<UUID, Long> deaths = new HashMap<>(), previews = new HashMap<>();
    private final Set<UUID> triggering = new HashSet<>();
    private final Map<PlayerDeathEvent, CapturedDeath> pendingDeaths = new java.util.IdentityHashMap<>();
    private boolean closed;

    public KillEffectManager(UltraCosmetics plugin, KillEffectRenderer renderer) {
        this.plugin = plugin;
        this.renderer = renderer;
        settings = new KillEffectSettings(plugin.getConfig().getValues(true));
        ticker = new KillEffectTicker(plugin, settings.maxDuration);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Kill Effects API must run on the server thread");
    }

    public VictimSnapshot captureVictim(Player victim) {
        requireMainThread();
        if (closed || !realPlayer(victim)) throw new IllegalArgumentException("Victim must be an online real player");
        Set<UUID> visible = new HashSet<>();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!settings.respectVanish || viewer.canSee(victim)) visible.add(viewer.getUniqueId());
        }
        return new VictimSnapshot(victim.getUniqueId(), victim.getName(), renderer.captureSkin(victim), visible);
    }

    public boolean play(Player killer, Player victim) {
        requireMainThread();
        if (closed || !realPlayer(victim) || !realPlayer(killer) || killer.getUniqueId().equals(victim.getUniqueId())) return false;
        try {
            return play(killer, captureVictim(victim), victim.getLocation());
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().warning("Kill Effect victim capture failed: " + e);
            return false;
        }
    }

    public boolean play(Player killer, VictimSnapshot victim, Location location) {
        requireMainThread();
        if (closed || !realPlayer(killer) || victim == null || killer.getUniqueId().equals(victim.uuid)) return false;
        UltraPlayer owner = plugin.getPlayerManager().getUltraPlayer(killer);
        if (owner == null) return false;
        Cosmetic<?> equipped = owner.getCosmetic(Category.KILL_EFFECTS);
        if (!(equipped instanceof KillEffect)) return false;
        return trigger(killer, victim, location == null ? null : location.clone(), (KillEffect) equipped, false);
    }

    public boolean preview(Player player, KillEffectType type) {
        requireMainThread();
        if (closed || !realPlayer(player) || type == null || !Category.KILL_EFFECTS.isEnabled() || !type.isEnabled()) return false;
        long tick = ticker.currentTick();
        if (tick - previews.getOrDefault(player.getUniqueId(), -10000L) < settings.cooldown) {
            MessageManager.send(player, "Kill-Effects.Preview-Cooldown");
            return false;
        }
        Location at = player.getLocation();
        double yaw = Math.toRadians(at.getYaw());
        at.add(-Math.sin(yaw) * 3, 0, Math.cos(yaw) * 3);
        at.setYaw(at.getYaw() + 180);
        at.setPitch(0);
        try {
            KillEffect selection = type.getClazz().getConstructor(UltraPlayer.class, KillEffectType.class, UltraCosmetics.class)
                    .newInstance(plugin.getPlayerManager().getUltraPlayer(player), type, plugin);
            if (trigger(player, captureVictim(player), at, selection, true)) {
                previews.put(player.getUniqueId(), tick);
                player.closeInventory();
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            plugin.getLogger().warning("Kill Effect preview failed: " + e);
        }
        MessageManager.send(player, "Kill-Effects.Preview-Unavailable");
        return false;
    }

    private boolean trigger(Player killer, VictimSnapshot victim, Location location, KillEffect selection, boolean preview) {
        if (closed || !Category.KILL_EFFECTS.isEnabled() || !selection.getType().isEnabled() || !validWorld(location)) return false;
        long tick = ticker.currentTick();
        deaths.entrySet().removeIf(entry -> tick - entry.getValue() > 2);
        if ((!preview && tick - deaths.getOrDefault(victim.uuid, -10000L) <= 2) || !triggering.add(victim.uuid)) return false;
        KillEffectScene scene = null;
        try {
            if (!plugin.getWorldGuardManager().areCosmeticsAllowedAt(killer, location, Category.KILL_EFFECTS)) return false;
            Location anchor = resolveAnchor(location);
            boolean airborne = anchor == null;
            if (preview && airborne) return false;
            if (airborne) anchor = location.clone();
            if (!plugin.getWorldGuardManager().areCosmeticsAllowedAt(killer, anchor, Category.KILL_EFFECTS)) return false;
            Map<UUID, Boolean> audience = audience(killer, victim, anchor, preview);
            if (audience.isEmpty()) return false;
            UCKillEffectTriggerEvent event = new UCKillEffectTriggerEvent(killer, victim, anchor, selection.getType(), preview, audience.keySet());
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled() || closed || !killer.isOnline() || !validWorld(anchor)) return false;
            // Event consumers may move/hide viewers or change preferences. Never enlarge the initial audience.
            audience.keySet().retainAll(audience(killer, victim, anchor, preview).keySet());
            if (audience.isEmpty()) return false;
            if (preview) ticker.stopPreview(killer.getUniqueId());
            KillEffectPosition position = position(anchor);
            CapacityPolicy.Detail detail = ticker.capacity(settings, position.world, position.chunkX(), position.chunkZ());
            if (detail == CapacityPolicy.Detail.FULL && !ticker.supportsFullAudience(audience.size())) {
                detail = settings.lite ? CapacityPolicy.Detail.LITE : CapacityPolicy.Detail.SKIP;
            }
            if (detail == CapacityPolicy.Detail.SKIP) return false;
            KillEffectContext context = new KillEffectContext(killer.getUniqueId(), killer.getName(), victim, position(location), position,
                    audience, victim.uuid.getLeastSignificantBits() ^ tick ^ killer.getUniqueId().getMostSignificantBits(), tick,
                    preview, airborne, detail == CapacityPolicy.Detail.LITE);
            scene = new KillEffectScene(context, renderer, ENTITY_IDS, settings, ticker.budget, plugin.getLogger());
            boolean started = ticker.add(scene, selection.createExecution(context, scene));
            if (started && !preview) deaths.put(victim.uuid, tick);
            return started;
        } catch (RuntimeException | LinkageError e) {
            if (scene != null) scene.close();
            plugin.getLogger().warning("Kill Effect trigger failed: " + e);
            return false;
        } finally {
            triggering.remove(victim.uuid);
        }
    }

    private Map<UUID, Boolean> audience(Player killer, VictimSnapshot victim, Location anchor, boolean preview) {
        Map<UUID, Boolean> audience = new LinkedHashMap<>();
        Player liveVictim = Bukkit.getPlayer(victim.uuid);
        for (Player viewer : anchor.getWorld().getPlayers()) {
            if (!viewer.isOnline() || (preview && !viewer.getUniqueId().equals(killer.getUniqueId()))) continue;
            if (!victim.visibleTo.contains(viewer.getUniqueId())) continue;
            if (settings.respectVanish && liveVictim != null && !viewer.canSee(liveVictim)) continue;
            UltraPlayer ultra = plugin.getPlayerManager().getUltraPlayer(viewer);
            if (ultra == null || !ultra.getProfile().isLoaded() || (!preview && !ultra.getProfile().isViewKillEffects())) continue;
            double distance = viewer.getLocation().distanceSquared(anchor);
            if (distance <= settings.range * settings.range) audience.put(viewer.getUniqueId(), distance <= settings.fullRange * settings.fullRange);
        }
        return audience;
    }

    private static boolean realPlayer(Player player) {
        return player != null && player.isOnline() && !player.hasMetadata("NPC") && Bukkit.getPlayer(player.getUniqueId()) == player;
    }

    private static boolean validWorld(Location location) {
        return location != null && location.getWorld() != null && Double.isFinite(location.getX())
                && Double.isFinite(location.getY()) && Double.isFinite(location.getZ())
                && Math.abs(location.getX()) <= 30_000_000 && Math.abs(location.getZ()) <= 30_000_000
                && Bukkit.getWorld(location.getWorld().getUID()) == location.getWorld()
                && SettingsManager.isAllowedWorld(location.getWorld())
                && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    private Location resolveAnchor(Location death) {
        World world = death.getWorld();
        if (death.getY() < 1 || death.getY() > world.getMaxHeight() - 2) return null;
        for (int down = 0; down <= settings.anchorSearch; down++) {
            Location candidate = death.clone().subtract(0, down, 0);
            int floor = (int) Math.floor(candidate.getY() - 0.05);
            if (floor < 0) break;
            if (!world.getBlockAt(candidate.getBlockX(), floor, candidate.getBlockZ()).getType().isSolid()) continue;
            if (down > 0) candidate.setY(floor + 1);
            boolean clear = true;
            for (double dx : new double[]{-0.5, 0.5}) for (double dz : new double[]{-0.5, 0.5}) {
                int x = (int) Math.floor(candidate.getX() + dx), z = (int) Math.floor(candidate.getZ() + dz);
                if (!world.isChunkLoaded(x >> 4, z >> 4)) { clear = false; continue; }
                for (int y = candidate.getBlockY(); y <= Math.floor(candidate.getY() + 1.9); y++) {
                    if (world.getBlockAt(x, y, z).getType().isSolid() || world.getBlockAt(x, y, z).isLiquid()) clear = false;
                }
            }
            if (clear) return candidate;
        }
        return null;
    }

    private static KillEffectPosition position(Location location) {
        return new KillEffectPosition(location.getWorld().getUID(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void captureDeath(PlayerDeathEvent event) {
        if (closed || !Category.KILL_EFFECTS.isEnabled() || !realPlayer(event.getEntity())) return;
        try {
            pendingDeaths.put(event, new CapturedDeath(captureVictim(event.getEntity()), event.getEntity().getLocation().clone()));
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().warning("Kill Effect death snapshot failed: " + e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        CapturedDeath death = pendingDeaths.remove(event);
        if (death != null) play(event.getEntity().getKiller(), death.victim, death.location);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        ticker.removeViewer(event.getPlayer().getUniqueId());
        previews.remove(event.getPlayer().getUniqueId());
    }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) { ticker.removeViewer(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) { ticker.stopWorld(event.getWorld().getUID()); }
    @EventHandler public void onDependencyDisable(PluginDisableEvent event) {
        if (event.getPlugin().getName().equalsIgnoreCase("packetevents")) close();
    }

    public void hideFrom(UUID viewer) { requireMainThread(); ticker.removeViewer(viewer); }
    public int getActiveScenes() { requireMainThread(); return ticker.getActiveScenes(); }
    public int getAllocatedEntityCount() { requireMainThread(); return ENTITY_IDS.activeCount(); }
    public long getTotalPacketSends() { requireMainThread(); return ticker.getTotalSends(); }
    public long getTotalGeneratedPoints() { requireMainThread(); return ticker.getTotalPoints(); }
    public boolean isAvailable() { return !closed; }
    public void close() {
        requireMainThread();
        if (closed) return;
        closed = true;
        ticker.close();
        try {
            renderer.close();
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().warning("Kill Effect renderer shutdown failed: " + e);
        }
        deaths.clear();
        previews.clear();
        pendingDeaths.clear();
    }
}
