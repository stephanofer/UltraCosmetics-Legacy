package be.isach.ultracosmetics.cosmetics.type;

import be.isach.ultracosmetics.cosmetics.Category;
import be.isach.ultracosmetics.config.CustomConfiguration;
import be.isach.ultracosmetics.cosmetics.killeffects.KillEffect;
import be.isach.ultracosmetics.cosmetics.killeffects.effects.FreezeKill;
import com.cryptomorin.xseries.XMaterial;

public final class KillEffectType extends CosmeticType<KillEffect> {
    private KillEffectType(String name, XMaterial material, Class<? extends KillEffect> implementation) {
        super(Category.KILL_EFFECTS, name, material, implementation);
    }

    public static void register() {
        new KillEffectType("Freeze", XMaterial.ICE, FreezeKill.class);
    }

    @Override
    public boolean isEnabled() {
        return getCategory().isEnabled() && super.isEnabled();
    }

    @Override
    protected void setupConfig(CustomConfiguration config, String path) {
        super.setupConfig(config, path);
        config.addDefault(path + ".Duration", 120);
    }
}
