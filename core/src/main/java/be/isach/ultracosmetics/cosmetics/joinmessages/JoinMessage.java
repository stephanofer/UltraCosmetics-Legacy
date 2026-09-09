package be.isach.ultracosmetics.cosmetics.joinmessages;

import be.isach.ultracosmetics.UltraCosmetics;
import be.isach.ultracosmetics.cosmetics.Cosmetic;
import be.isach.ultracosmetics.cosmetics.type.JoinMessageType;
import be.isach.ultracosmetics.player.UltraPlayer;

public final class JoinMessage extends Cosmetic<JoinMessageType> {
    public JoinMessage(UltraPlayer owner, JoinMessageType type, UltraCosmetics plugin) {
        super(owner, type, plugin);
    }

    @Override
    protected boolean registerSelectionListener() {
        return false;
    }

    @Override
    protected void onEquip() {
    }
}
