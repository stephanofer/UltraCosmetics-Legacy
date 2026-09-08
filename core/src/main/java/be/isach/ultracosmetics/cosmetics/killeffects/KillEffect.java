package be.isach.ultracosmetics.cosmetics.killeffects;

import be.isach.ultracosmetics.UltraCosmetics;
import be.isach.ultracosmetics.cosmetics.Cosmetic;
import be.isach.ultracosmetics.cosmetics.type.KillEffectType;
import be.isach.ultracosmetics.cosmetics.killeffects.runtime.KillEffectExecution;
import be.isach.ultracosmetics.cosmetics.killeffects.runtime.KillEffectScene;
import be.isach.ultracosmetics.player.UltraPlayer;

public abstract class KillEffect extends Cosmetic<KillEffectType> {
    protected KillEffect(UltraPlayer owner, KillEffectType type, UltraCosmetics plugin) { super(owner, type, plugin); }
    protected abstract KillEffectExecution createExecution(KillEffectContext context, KillEffectScene scene);
    @Override protected final boolean registerSelectionListener() { return false; }
    @Override protected final void onEquip() { }
}
