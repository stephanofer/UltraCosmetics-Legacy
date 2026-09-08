package be.isach.ultracosmetics.events;

import be.isach.ultracosmetics.cosmetics.killeffects.VictimSnapshot;
import be.isach.ultracosmetics.cosmetics.type.KillEffectType;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class UCKillEffectTriggerEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player killer;
    private final VictimSnapshot victim;
    private final Location location;
    private final KillEffectType type;
    private final boolean preview;
    private final Set<UUID> audience;
    private boolean cancelled;

    public UCKillEffectTriggerEvent(Player killer, VictimSnapshot victim, Location location, KillEffectType type,
                                   boolean preview, Set<UUID> audience) {
        this.killer = killer;
        this.victim = victim;
        this.location = location.clone();
        this.type = type;
        this.preview = preview;
        this.audience = Collections.unmodifiableSet(new HashSet<>(audience));
    }
    public Player getKiller() { return killer; }
    public VictimSnapshot getVictim() { return victim; }
    public Location getLocation() { return location.clone(); }
    public KillEffectType getType() { return type; }
    public boolean isPreview() { return preview; }
    public Set<UUID> getAudience() { return audience; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
