package be.isach.ultracosmetics.menu.buttons;

import be.isach.ultracosmetics.config.MessageManager;
import be.isach.ultracosmetics.menu.Button;
import be.isach.ultracosmetics.menu.ClickData;
import be.isach.ultracosmetics.player.UltraPlayer;
import be.isach.ultracosmetics.util.ItemFactory;
import com.cryptomorin.xseries.XMaterial;
import org.bukkit.inventory.ItemStack;

public final class ToggleKillEffectVisibilityButton implements Button {
    @Override
    public ItemStack getDisplayItem(UltraPlayer player) {
        boolean visible = player.getProfile().isViewKillEffects();
        return ItemFactory.rename((visible ? XMaterial.ENDER_EYE : XMaterial.ENDER_PEARL).parseItem(),
                MessageManager.getMessage("Kill-Effects.Visibility-" + (visible ? "On" : "Off")),
                MessageManager.getLegacyMessage("Kill-Effects.Visibility-Lore"));
    }

    @Override
    public void onClick(ClickData data) {
        UltraPlayer player = data.getClicker();
        if (!player.getProfile().isLoaded()) return;
        player.getProfile().setViewKillEffects(!player.getProfile().isViewKillEffects());
        data.getMenu().refresh(player);
    }
}
