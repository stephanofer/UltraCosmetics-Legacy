package be.isach.ultracosmetics.cosmetics.killeffects.packetevents;

import be.isach.ultracosmetics.player.PlayerDeathAnimationPolicy;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Global visual filter, independent of cosmetic selection and scene audiences. */
public final class PlayerDeathAnimationListener implements Listener, AutoCloseable {
    private final PlayerDeathAnimationPolicy policy = new PlayerDeathAnimationPolicy();
    private final JavaPlugin plugin;
    private volatile boolean closed;
    private final PacketListenerAbstract packets = new PacketListenerAbstract(PacketListenerPriority.HIGHEST) {
        @Override
        public void onPacketSend(PacketSendEvent event) {
            if (closed || event.isCancelled()) return;
            if (event.getPacketType() == PacketType.Play.Server.ENTITY_STATUS) {
                WrapperPlayServerEntityStatus packet = new WrapperPlayServerEntityStatus(event);
                if (policy.suppress(packet.getEntityId(), packet.getStatus())) event.setCancelled(true);
            } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
                WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
                if (policy.hideBody(packet.getEntityId(), event.getUser().getEntityId())) {
                    event.setCancelled(true);
                    return;
                }
                List<EntityData<?>> metadata = visualMetadata(packet.getEntityId(), event.getUser().getEntityId(), packet.getEntityMetadata());
                if (metadata != packet.getEntityMetadata()) {
                    packet.setEntityMetadata(metadata);
                    event.markForReEncode(true);
                }
            } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_PLAYER) {
                WrapperPlayServerSpawnPlayer packet = new WrapperPlayServerSpawnPlayer(event);
                if (policy.hideBody(packet.getEntityId(), event.getUser().getEntityId())) {
                    event.setCancelled(true);
                    return;
                }
                List<EntityData<?>> metadata = visualMetadata(packet.getEntityId(), event.getUser().getEntityId(), packet.getEntityMetadata());
                if (metadata != packet.getEntityMetadata()) {
                    packet.setEntityMetadata(metadata);
                    event.markForReEncode(true);
                }
            }
        }
    };

    public PlayerDeathAnimationListener(JavaPlugin plugin) {
        this.plugin = plugin;
        if (PacketEvents.getAPI() == null || !PacketEvents.getAPI().isInitialized()
                || PacketEvents.getAPI().isTerminated()
                || !PacketEvents.getAPI().getVersion().toString().equals("2.13.0")) {
            throw new IllegalStateException("An initialized PacketEvents 2.13.0 instance is required");
        }
        try {
            for (Player player : Bukkit.getOnlinePlayers()) track(player);
            Bukkit.getPluginManager().registerEvents(this, plugin);
            PacketEvents.getAPI().getEventManager().registerListener(packets);
        } catch (RuntimeException | LinkageError e) {
            close();
            throw e;
        }
    }

    private void track(Player player) {
        policy.track(player.getUniqueId(), player.getEntityId());
    }

    private List<EntityData<?>> visualMetadata(int entityId, int viewerId, List<EntityData<?>> metadata) {
        for (int i = 0; i < metadata.size(); i++) {
            EntityData<?> data = metadata.get(i);
            // Legacy living health is float metadata 6; zero also starts deathTime without status 3.
            if (data.getIndex() != 6 || data.getType() != EntityDataTypes.FLOAT || !(data.getValue() instanceof Float)) continue;
            float health = (Float) data.getValue();
            float visual = policy.visualHealth(entityId, viewerId, health);
            if (Float.compare(health, visual) == 0) return metadata;
            // Do not mutate entries that may be shared with another recipient's packet.
            List<EntityData<?>> copy = new ArrayList<>(metadata);
            copy.set(i, new EntityData<>(6, EntityDataTypes.FLOAT, visual));
            return copy;
        }
        return metadata;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { track(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        int entityId = victim.getEntityId();
        policy.markDead(victim.getUniqueId(), entityId);
        // Remove only the remote body, not the server entity or its tab/profile entry.
        for (Player viewer : victim.getWorld().getPlayers()) {
            if (viewer.getUniqueId().equals(victim.getUniqueId())) continue;
            try {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerDestroyEntities(entityId));
            } catch (RuntimeException | LinkageError e) {
                plugin.getLogger().warning("Player death body removal failed for " + viewer.getUniqueId() + ": " + e);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        // Let the server's normal tracker spawn the living player again, including reused entity IDs.
        policy.respawn(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) { policy.remove(event.getPlayer().getUniqueId()); }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        HandlerList.unregisterAll(this);
        PacketEvents.getAPI().getEventManager().unregisterListener(packets);
        policy.clear();
    }
}
