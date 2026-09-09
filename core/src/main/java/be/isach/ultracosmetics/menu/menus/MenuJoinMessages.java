package be.isach.ultracosmetics.menu.menus;

import be.isach.ultracosmetics.UltraCosmetics;
import be.isach.ultracosmetics.cosmetics.Category;
import be.isach.ultracosmetics.cosmetics.type.JoinMessageType;
import be.isach.ultracosmetics.menu.CosmeticMenu;

public final class MenuJoinMessages extends CosmeticMenu<JoinMessageType> {
    public MenuJoinMessages(UltraCosmetics plugin) {
        super(plugin, Category.JOIN_MESSAGES);
    }
}
