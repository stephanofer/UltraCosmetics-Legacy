package be.isach.ultracosmetics.menu.buttons.togglecosmetic;

import be.isach.ultracosmetics.UltraCosmetics;
import be.isach.ultracosmetics.chat.ChatMessageFormatter;
import be.isach.ultracosmetics.config.MessageManager;
import be.isach.ultracosmetics.cosmetics.type.JoinMessageType;
import be.isach.ultracosmetics.menu.ClickData;
import be.isach.ultracosmetics.player.UltraPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public final class ToggleJoinMessageButton extends ToggleCosmeticButton {
    public ToggleJoinMessageButton(UltraCosmetics plugin, JoinMessageType type) {
        super(plugin, type);
    }

    @Override
    protected boolean handleClick(ClickData data) {
        if (data.getClick().isRightClick()) {
            ultraCosmetics.getJoinMessageCoordinator().preview(
                    data.getClicker().getBukkitPlayer(), (JoinMessageType) cosmeticType);
            return false;
        }
        if (!data.getClick().isLeftClick()) return false;
        return super.handleClick(data);
    }

    @Override
    protected void modifyLore(List<String> lore, UltraPlayer ultraPlayer) {
        lore.add("");
        lore.add(MessageManager.getLegacyMessage("Join-Messages.Preview-Label"));
        Player player = ultraPlayer.getBukkitPlayer();
        ChatMessageFormatter formatter = JoinMessageType.getFormatter();
        try {
            for (Component line : formatter.format(((JoinMessageType) cosmeticType).getTemplate(),
                    Placeholder.unparsed("player", player.getName()),
                    Placeholder.unparsed("world", player.getWorld().getName()),
                    Placeholder.unparsed("online", String.valueOf(Bukkit.getOnlinePlayers().size())),
                    Placeholder.unparsed("max_players", String.valueOf(Bukkit.getMaxPlayers())))) {
                lore.add(MessageManager.toLegacy(line));
            }
        } catch (RuntimeException ignored) {
            lore.add(MessageManager.getLegacyMessage("Join-Messages.Preview-Unavailable"));
        }
        lore.add("");
        lore.add(MessageManager.getLegacyMessage("Join-Messages.Controls"));
        lore.add(MessageManager.getLegacyMessage("Join-Messages.Preview-Control"));
    }
}
