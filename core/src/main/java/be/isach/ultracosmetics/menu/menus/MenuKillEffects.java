package be.isach.ultracosmetics.menu.menus;

import be.isach.ultracosmetics.UltraCosmetics;
import be.isach.ultracosmetics.cosmetics.Category;
import be.isach.ultracosmetics.cosmetics.type.KillEffectType;
import be.isach.ultracosmetics.menu.CosmeticMenu;
import be.isach.ultracosmetics.menu.buttons.ToggleKillEffectVisibilityButton;
import be.isach.ultracosmetics.player.UltraPlayer;
import org.bukkit.inventory.Inventory;

public class MenuKillEffects extends CosmeticMenu<KillEffectType> {

    public MenuKillEffects(UltraCosmetics ultraCosmetics) {
        super(ultraCosmetics, Category.KILL_EFFECTS);
    }

    @Override
    protected void putItems(Inventory inventory, UltraPlayer player, int page) {
        putItem(inventory, inventory.getSize() - 4, new ToggleKillEffectVisibilityButton(), player);
    }

}
