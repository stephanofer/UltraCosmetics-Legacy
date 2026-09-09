package be.isach.ultracosmetics.events;

import be.isach.ultracosmetics.cosmetics.type.JoinMessageType;
import be.isach.ultracosmetics.player.UltraPlayer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class UCJoinMessageEvent extends UCEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final JoinMessageType joinMessageType;
    private final List<Component> lines;
    private final List<Player> recipients;
    private boolean cancelled;

    public UCJoinMessageEvent(UltraPlayer player, JoinMessageType joinMessageType,
                              List<Component> lines, List<Player> recipients) {
        super(player);
        this.joinMessageType = joinMessageType;
        this.lines = lines;
        this.recipients = recipients;
    }

    public JoinMessageType getJoinMessageType() {
        return joinMessageType;
    }

    public List<Component> getLines() {
        return lines;
    }

    public List<Player> getRecipients() {
        return recipients;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
