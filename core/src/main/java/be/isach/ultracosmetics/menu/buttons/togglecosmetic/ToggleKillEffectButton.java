package be.isach.ultracosmetics.menu.buttons.togglecosmetic;

import be.isach.ultracosmetics.UltraCosmetics;
import be.isach.ultracosmetics.config.MessageManager;
import be.isach.ultracosmetics.cosmetics.type.KillEffectType;
import be.isach.ultracosmetics.menu.ClickData;
import be.isach.ultracosmetics.player.UltraPlayer;
import java.util.List;

public final class ToggleKillEffectButton extends ToggleCosmeticButton {
    public ToggleKillEffectButton(UltraCosmetics plugin, KillEffectType type) { super(plugin, type); }

    @Override
    protected boolean handleClick(ClickData data) {
        if (data.getClick().isRightClick()) {
            if (ultraCosmetics.getKillEffectManager() != null) {
                ultraCosmetics.getKillEffectManager().preview(data.getClicker().getBukkitPlayer(), (KillEffectType) cosmeticType);
            }
            return false;
        }
        if (!data.getClick().isLeftClick()) return false;
        return super.handleClick(data);
    }

    @Override
    protected void modifyLore(List<String> lore, UltraPlayer player) {
        lore.add(MessageManager.getLegacyMessage("Kill-Effects.Controls"));
        lore.add(MessageManager.getLegacyMessage("Kill-Effects.Preview-Control"));
    }
}
