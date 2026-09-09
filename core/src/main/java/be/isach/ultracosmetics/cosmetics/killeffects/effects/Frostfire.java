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

public final class Frostfire extends KillEffect {
    public Frostfire(UltraPlayer owner, KillEffectType type, UltraCosmetics plugin) { super(owner, type, plugin); }

    @Override
    protected KillEffectExecution createExecution(KillEffectContext context, KillEffectScene scene) {
        return new CatalogExecution(context, scene, getType()) {
            @Override
            protected void frame(double t, int elapsed) {
                if (at(0)) { victim(); sound(Sound.IGNITE, 1, 0.4f, 0.7f); }
                if (t < 42) {
                    double rise = smooth(progress(t, 4, 34));
                    moveVictim(0, rise * 0.55, 0, context.death.yaw);
                    double radius = t < 30 ? 0.72 : 0.72 * (1 - smooth(progress(t, 30, 44)));
                    double height = 0.3 + 2.2 * smooth(progress(t, 0, 22));
                    helix(Particle.CYAN, t, radius, 0.1, height, 0);
                    helix(Particle.FLAME, t, radius, 0.1, height, Math.PI);
                    if (at(30)) { if (!lite) scene.status(subject, (byte) 2); sound(Sound.FIZZ, 1.5, 0.35f, 1.4f); }
                }
                if (at(42)) {
                    removeVictim();
                    climax(Sound.EXPLOSION, 1.5, 0.85f);
                    sound(Sound.FIZZ, 1.5, 0.6f, 0.65f);
                    burst(Particle.WHITE, 0, 1.4, 0, 0.45, 0);
                    burst(Particle.ICE, 0, 1.4, 0, 0.65, 0);
                }
                if (t >= 42 && t < 56) {
                    double u = progress(t, 42, 56);
                    double expansion = 1 - (1 - u) * (1 - u);
                    ring(Particle.CYAN, 0.3 + 1.85 * expansion, 1.35 + u * 0.2);
                    ring(Particle.FLAME, 0.3 + 1.85 * expansion, 1.35 - u * 0.2);
                    if (elapsed % 3 == 0) burst(Particle.CLOUD, 0, 1.4, 0, 0.25 + expansion * 0.85, 0);
                }
            }
        };
    }
}
