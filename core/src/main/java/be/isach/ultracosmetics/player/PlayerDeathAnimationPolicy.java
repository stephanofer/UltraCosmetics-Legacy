package be.isach.ultracosmetics.player;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Updated on the server thread; packet callbacks only read the concurrent ID set. */
public final class PlayerDeathAnimationPolicy {
    private final Map<UUID, Integer> players = new HashMap<>();
    private final Set<Integer> entityIds = ConcurrentHashMap.newKeySet();
    private final Set<Integer> deadEntityIds = ConcurrentHashMap.newKeySet();

    public void track(UUID player, int entityId) {
        Integer previous = players.put(player, entityId);
        entityIds.add(entityId);
        if (previous != null && previous != entityId) {
            entityIds.remove(previous);
            deadEntityIds.remove(previous);
        }
    }

    public void remove(UUID player) {
        Integer entityId = players.remove(player);
        if (entityId != null) {
            entityIds.remove(entityId);
            deadEntityIds.remove(entityId);
        }
    }

    public void markDead(UUID player, int entityId) {
        track(player, entityId);
        deadEntityIds.add(entityId);
    }

    public void respawn(UUID player) {
        Integer entityId = players.get(player);
        if (entityId != null) deadEntityIds.remove(entityId);
    }

    public boolean hideBody(int entityId, int viewerEntityId) {
        return entityId != viewerEntityId && deadEntityIds.contains(entityId);
    }

    public boolean suppress(int entityId, int status) {
        // Packet-only replicas are deliberately not registered, even when they share a player's profile.
        return status == 3 && entityIds.contains(entityId);
    }

    public float visualHealth(int entityId, int viewerEntityId, float health) {
        return entityId != viewerEntityId && health <= 0 && entityIds.contains(entityId) ? 1.0f : health;
    }

    public void clear() {
        players.clear();
        entityIds.clear();
        deadEntityIds.clear();
    }
}
