package be.isach.ultracosmetics.cosmetics.joinmessages;

import be.isach.ultracosmetics.UltraCosmetics;
import be.isach.ultracosmetics.chat.ChatMessageFormatter;
import be.isach.ultracosmetics.config.MessageManager;
import be.isach.ultracosmetics.config.SettingsManager;
import be.isach.ultracosmetics.cosmetics.Category;
import be.isach.ultracosmetics.cosmetics.Cosmetic;
import be.isach.ultracosmetics.cosmetics.type.JoinMessageType;
import be.isach.ultracosmetics.events.UCJoinMessageEvent;
import be.isach.ultracosmetics.player.UltraPlayer;
import be.isach.ultracosmetics.util.SmartLogger.LogLevel;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class JoinMessageCoordinator {
    private static final long DIAGNOSTIC_INTERVAL_MILLIS = 60_000L;

    private final UltraCosmetics plugin;
    private final ChatMessageFormatter formatter;
    private final boolean soundsEnabled;
    private final long previewCooldownMillis;
    private final long maximumAnnouncementDelayMillis;
    private final Map<UUID, Long> previewCooldowns = new HashMap<>();
    private final Map<JoinMessageType, Long> diagnostics = new HashMap<>();

    public JoinMessageCoordinator(UltraCosmetics plugin) {
        this.plugin = plugin;
        this.formatter = JoinMessageType.getFormatter();
        this.soundsEnabled = SettingsManager.getConfig().getBoolean("Join-Message-Settings.Sounds", true);
        this.previewCooldownMillis = nonNegativeSeconds("Join-Message-Settings.Preview-Cooldown-Seconds", 2) * 1000L;
        this.maximumAnnouncementDelayMillis = nonNegativeSeconds("Join-Message-Settings.Maximum-Announcement-Delay-Seconds", 5) * 1000L;
    }

    private long nonNegativeSeconds(String path, long fallback) {
        long configured = SettingsManager.getConfig().getLong(path, fallback);
        if (configured >= 0) return configured;
        plugin.getSmartLogger().write(LogLevel.WARNING, path + " was clamped from " + configured + " to 0");
        return 0;
    }

    public void announce(UltraPlayer ultraPlayer, long joinedAtMillis) {
        Player joiningPlayer = ultraPlayer.getBukkitPlayer();
        if (joiningPlayer == null || !joiningPlayer.isOnline()) return;
        if (System.currentTimeMillis() - joinedAtMillis > maximumAnnouncementDelayMillis) return;
        if (!Category.JOIN_MESSAGES.isEnabled() || !SettingsManager.isAllowedWorld(joiningPlayer.getWorld())) return;

        Cosmetic<?> selected = ultraPlayer.getCosmetic(Category.JOIN_MESSAGES);
        if (!(selected instanceof JoinMessage)) return;
        JoinMessageType type = ((JoinMessage) selected).getType();
        if (!type.isEnabled() || !plugin.getPermissionManager().hasPermission(ultraPlayer, type)) return;

        List<Component> lines = render(type, joiningPlayer);
        if (lines == null) return;
        List<Player> eligible = eligibleRecipients(joiningPlayer);
        if (eligible.isEmpty()) return;

        UCJoinMessageEvent event = new UCJoinMessageEvent(ultraPlayer, type,
                new ArrayList<>(lines), new ArrayList<>(eligible));
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        List<Component> eventLines = new ArrayList<>(event.getLines());
        eventLines.removeIf(line -> line == null);
        boolean containsEmbeddedLineBreak = false;
        for (Component line : eventLines) {
            String legacy = MessageManager.toLegacy(line);
            if (legacy.indexOf('\n') >= 0 || legacy.indexOf('\r') >= 0) containsEmbeddedLineBreak = true;
        }
        if (eventLines.isEmpty() || eventLines.size() > formatter.getMaximumOutputLines() || containsEmbeddedLineBreak) {
            diagnostic(type, "Join Message event output was empty or exceeded the configured line limit");
            return;
        }

        Set<UUID> eligibleIds = new HashSet<>();
        for (Player recipient : eligible) eligibleIds.add(recipient.getUniqueId());
        Set<UUID> includedIds = new HashSet<>();
        List<Player> recipients = new ArrayList<>();
        for (Player recipient : new ArrayList<>(event.getRecipients())) {
            if (recipient != null && recipient.isOnline() && eligibleIds.contains(recipient.getUniqueId())
                    && includedIds.add(recipient.getUniqueId())) {
                recipients.add(recipient);
            }
        }
        deliver(recipients, eventLines, type);
    }

    public void preview(Player player, JoinMessageType type) {
        if (!Category.JOIN_MESSAGES.isEnabled() || !type.isEnabled()) {
            MessageManager.send(player, "Join-Messages.Preview-Unavailable");
            return;
        }
        long now = System.currentTimeMillis();
        removeExpiredCooldowns(now);
        Long expires = previewCooldowns.get(player.getUniqueId());
        if (expires != null && expires > now) {
            MessageManager.send(player, "Join-Messages.Preview-Cooldown");
            return;
        }
        previewCooldowns.put(player.getUniqueId(), now + previewCooldownMillis);
        List<Component> lines = render(type, player);
        if (lines == null) {
            MessageManager.send(player, "Join-Messages.Preview-Unavailable");
            return;
        }
        List<Player> recipient = new ArrayList<>();
        recipient.add(player);
        deliver(recipient, lines, type);
    }

    private List<Component> render(JoinMessageType type, Player player) {
        try {
            return formatter.format(type.getTemplate(),
                    Placeholder.unparsed("player", player.getName()),
                    Placeholder.unparsed("world", player.getWorld().getName()),
                    Placeholder.unparsed("online", String.valueOf(Bukkit.getOnlinePlayers().size())),
                    Placeholder.unparsed("max_players", String.valueOf(Bukkit.getMaxPlayers())));
        } catch (RuntimeException exception) {
            diagnostic(type, "Could not render " + type.getConfigPath() + ": " + exception.getMessage());
            return null;
        }
    }

    private List<Player> eligibleRecipients(Player joiningPlayer) {
        List<Player> recipients = new ArrayList<>();
        for (Player recipient : Bukkit.getOnlinePlayers()) {
            if (recipient.equals(joiningPlayer) || recipient.canSee(joiningPlayer)) recipients.add(recipient);
        }
        return recipients;
    }

    private void deliver(List<Player> recipients, List<Component> lines, JoinMessageType type) {
        if (recipients.isEmpty()) return;
        BukkitAudiences audiences = MessageManager.getAudiences();
        for (Player recipient : recipients) {
            for (Component line : lines) audiences.player(recipient).sendMessage(line);
            if (soundsEnabled && type.getSound() != null) {
                recipient.playSound(recipient.getLocation(), type.getSound(), type.getVolume(), type.getPitch());
            }
        }
    }

    private void removeExpiredCooldowns(long now) {
        Iterator<Map.Entry<UUID, Long>> iterator = previewCooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= now) iterator.remove();
        }
    }

    public void removePlayer(UUID playerId) {
        previewCooldowns.remove(playerId);
    }

    private void diagnostic(JoinMessageType type, String message) {
        long now = System.currentTimeMillis();
        if (now - diagnostics.getOrDefault(type, 0L) < DIAGNOSTIC_INTERVAL_MILLIS) return;
        diagnostics.put(type, now);
        plugin.getSmartLogger().write(LogLevel.WARNING, message);
    }
}
