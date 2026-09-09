package be.isach.ultracosmetics.cosmetics.killeffects.effects;

import be.isach.ultracosmetics.UltraCosmetics;
import be.isach.ultracosmetics.cosmetics.killeffects.KillEffect;
import be.isach.ultracosmetics.cosmetics.killeffects.KillEffectContext;
import be.isach.ultracosmetics.cosmetics.killeffects.render.KillEffectRenderer.Particle;
import be.isach.ultracosmetics.cosmetics.killeffects.render.KillEffectRenderer.Sound;
import be.isach.ultracosmetics.cosmetics.killeffects.runtime.KillEffectExecution;
import be.isach.ultracosmetics.cosmetics.killeffects.runtime.KillEffectScene;
import be.isach.ultracosmetics.cosmetics.type.KillEffectType;
import be.isach.ultracosmetics.player.UltraPlayer;

public final class FireworkFinale extends KillEffect {
    public FireworkFinale(UltraPlayer owner, KillEffectType type, UltraCosmetics plugin) { super(owner, type, plugin); }

    @Override
    protected KillEffectExecution createExecution(KillEffectContext context, KillEffectScene scene) {
        return new CatalogExecution(context, scene, getType()) {
            private int star = -1;

            @Override
            protected void frame(double t, int elapsed) {
                if (at(0)) { victim(); sound(Sound.CHIME, 1, 0.4f, 1.4f); }
                if (t < 16) {
                    moveVictim(0, smooth(t / 16) * 0.6, 0, context.death.yaw);
                    helix(Particle.GOLD, t, 0.65 * (1 - t / 20), 0, 2.2, 0);
                }
                if (at(16)) {
                    removeVictim();
                    star = scene.spawnHead(0, 2.2, 0, context.death.yaw);
                    sound(Sound.LAUNCH, 2.2, 0.5f, 1.4f);
                }
                if (t >= 16 && t < 34) {
                    double u = progress(t, 16, 29);
                    scene.moveHead(star, 0, 2.2 + 1.8 * smooth(u), 0, (float) (context.death.yaw + u * 180));
                    helix(Particle.SPARK, t, 0.15, 1.8 + 1.8 * smooth(u), 0.5, 0);
                }
                if (at(34)) { scene.destroy(star); climax(Sound.BLAST, 4, 1.2f); }
                if (t >= 34 && t < 47) burst(Particle.GOLD, 0, 4, 0, 0.15 + 1.5 * smooth(progress(t, 34, 47)), 0);
                if (at(42)) sound(Sound.BLAST, 3.8, 0.3f, 1.7f);
                if (t >= 42 && t < 57) {
                    double u = progress(t, 42, 57);
                    // Two offset secondary blooms read as a staged finale rather than one large cloud.
                    burst(Particle.CYAN, -0.8, 3.7, 0, 0.1 + u * 0.8, u * u * 0.6);
                    if (!lite) burst(Particle.RED, 0.8, 3.7, 0, 0.1 + u * 0.8, u * u * 0.6);
                }
            }
        };
    }
}
