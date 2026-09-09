package be.isach.ultracosmetics.cosmetics.type;

import be.isach.ultracosmetics.cosmetics.Category;
import be.isach.ultracosmetics.config.CustomConfiguration;
import be.isach.ultracosmetics.cosmetics.killeffects.KillEffect;
import be.isach.ultracosmetics.cosmetics.killeffects.effects.FreezeKill;
import be.isach.ultracosmetics.cosmetics.killeffects.effects.SquidMissile;
import be.isach.ultracosmetics.cosmetics.killeffects.effects.HeadRocket;
import be.isach.ultracosmetics.cosmetics.killeffects.effects.Frostfire;
import be.isach.ultracosmetics.cosmetics.killeffects.effects.SoulVortex;
import be.isach.ultracosmetics.cosmetics.killeffects.effects.Bloodburst;
import be.isach.ultracosmetics.cosmetics.killeffects.effects.DivineJudgment;
import be.isach.ultracosmetics.cosmetics.killeffects.effects.FireworkFinale;
import be.isach.ultracosmetics.cosmetics.killeffects.runtime.CapacityPolicy;
import com.cryptomorin.xseries.XMaterial;

public final class KillEffectType extends CosmeticType<KillEffect> {
    private KillEffectType(String name, XMaterial material, Class<? extends KillEffect> implementation) {
        super(Category.KILL_EFFECTS, name, material, implementation);
    }

    public static void register() {
        new KillEffectType("Freeze", XMaterial.ICE, FreezeKill.class);
        new KillEffectType("SquidMissile", XMaterial.INK_SAC, SquidMissile.class);
        new KillEffectType("HeadRocket", XMaterial.PLAYER_HEAD, HeadRocket.class);
        new KillEffectType("Frostfire", XMaterial.BLAZE_POWDER, Frostfire.class);
        new KillEffectType("SoulVortex", XMaterial.ENDER_PEARL, SoulVortex.class);
        new KillEffectType("Bloodburst", XMaterial.REDSTONE, Bloodburst.class);
        new KillEffectType("DivineJudgment", XMaterial.GLOWSTONE_DUST, DivineJudgment.class);
        new KillEffectType("FireworkFinale", XMaterial.FIREWORK_ROCKET, FireworkFinale.class);
    }

    public static String canonicalName(String name) {
        if (name == null) return null;
        if (name.equalsIgnoreCase("Explosion")) return "Bloodburst";
        if (name.equalsIgnoreCase("Lightning")) return "DivineJudgment";
        if (name.equalsIgnoreCase("Firework")) return "FireworkFinale";
        return name;
    }

    public String getLegacyName() {
        switch (getConfigName()) {
            case "Bloodburst": return "Explosion";
            case "DivineJudgment": return "Lightning";
            case "FireworkFinale": return "Firework";
            default: return null;
        }
    }

    @Override
    public String getStorageName() {
        String legacy = getLegacyName();
        return legacy == null ? getConfigName() : legacy;
    }

    public int structuralSendsPerViewer(boolean lite) {
        // Full entry: up to six victim setup packets plus two for the first motion frame.
        // Lite entry: stand spawn, metadata, helmet, and one position update.
        return getConfigName().equals("Freeze") ? (lite ? 0 : CapacityPolicy.FREEZE_SENDS_PER_VIEWER) : (lite ? 4 : 8);
    }

    @Override
    public boolean isEnabled() {
        return getCategory().isEnabled() && super.isEnabled();
    }

    @Override
    protected void setupConfig(CustomConfiguration config, String path) {
        super.setupConfig(config, path);
        config.addDefault(path + ".Duration", getConfigName().equals("Freeze") ? 120 : 64);
    }
}
