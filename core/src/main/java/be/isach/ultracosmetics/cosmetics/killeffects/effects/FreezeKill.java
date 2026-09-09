package be.isach.ultracosmetics.cosmetics.killeffects.effects;

import be.isach.ultracosmetics.UltraCosmetics;
import be.isach.ultracosmetics.config.SettingsManager;
import be.isach.ultracosmetics.cosmetics.killeffects.KillEffect;
import be.isach.ultracosmetics.cosmetics.killeffects.KillEffectContext;
import be.isach.ultracosmetics.cosmetics.killeffects.render.KillEffectRenderer.Particle;
import be.isach.ultracosmetics.cosmetics.killeffects.runtime.AbstractKillEffectExecution;
import be.isach.ultracosmetics.cosmetics.killeffects.runtime.KillEffectExecution;
import be.isach.ultracosmetics.cosmetics.killeffects.runtime.KillEffectScene;
import be.isach.ultracosmetics.cosmetics.killeffects.runtime.KillEffectSettings;
import be.isach.ultracosmetics.cosmetics.type.KillEffectType;
import be.isach.ultracosmetics.player.UltraPlayer;
import java.util.Random;

public final class FreezeKill extends KillEffect {
    private static final double[] CIRCLE_X = new double[24], CIRCLE_Z = new double[24];
    static {
        for (int i = 0; i < 24; i++) {
            CIRCLE_X[i] = Math.cos(i * Math.PI / 12);
            CIRCLE_Z[i] = Math.sin(i * Math.PI / 12);
        }
    }

    public FreezeKill(UltraPlayer owner, KillEffectType type, UltraCosmetics plugin) { super(owner, type, plugin); }

    @Override
    protected KillEffectExecution createExecution(KillEffectContext context, KillEffectScene scene) {
        final int duration = context.lite ? 12 : KillEffectSettings.clamp(
                SettingsManager.getConfig().getInt(getType().getConfigPath() + ".Duration", 120), 120, 160);
        return new Execution(context, scene, duration);
    }

    private static final class Execution extends AbstractKillEffectExecution {
        private final KillEffectContext context;
        private final KillEffectScene scene;
        private final int duration;
        private final Random random;
        private int body = -1;
        private int[] lower, middle, upper, cap;
        private int previousTick = -1;

        private Execution(KillEffectContext context, KillEffectScene scene, int duration) {
            super(duration, scene::close);
            this.context = context;
            this.scene = scene;
            this.duration = duration;
            random = new Random(context.seed);
        }

        @Override
        protected void onTick(int elapsed) {
            int t = context.lite ? elapsed : FreezeTimeline.animationTick(elapsed, duration);
            if (elapsed == 0) {
                scene.sound(0.35f, 0.7f);
                if (!context.lite) {
                    lower = scene.spawnIce(0);
                    middle = scene.spawnIce(1);
                    upper = scene.spawnIce(2);
                    cap = scene.spawnIce(3);
                }
            }
            if (context.lite) {
                if (t < 7) ring(Particle.ICE, 0.7 - t * 0.07, t * 0.22, 12);
                return;
            }
            // Keep the replica after all ice entities for legacy transparency ordering.
            if (elapsed >= 1 && body == -1) body = scene.spawnPlayer(0.125);
            // Provisional 2-tick correction; the real-server spike must establish the lowest stable frequency.
            if (elapsed > 0 && elapsed % 2 == 0 && t < 56) {
                double vibration = t >= 43 ? Math.sin(t * 2.4) * 0.025 : 0;
                scene.stabilizeIce(lower, vibration);
                scene.stabilizeIce(middle, -vibration);
                scene.stabilizeIce(upper, vibration);
                scene.stabilizeIce(cap, -vibration);
            }
            if (t == previousTick && FreezeTimeline.phase(t) != FreezeTimeline.Phase.HOLD) return;
            previousTick = t;
            switch (FreezeTimeline.phase(t)) {
                case FREEZE:
                    ring(Particle.ICE, 0.55, t / 3.0, 12);
                    break;
                case FRACTURE:
                    ring(Particle.ICE, 0.54, random.nextDouble() * 1.8, 6 + (t - 43));
                    if (t == 43) scene.sound(0.18f, 1.8f);
                    break;
                case SHATTER:
                    if (t == 53) scene.sound(0.65f, 1.3f);
                    ring(Particle.ICE, 0.6 + (t - 53) * 0.28, 1 + (t - 53) * 0.08, 24);
                    if (t >= 53) scene.destroy(cap);
                    if (t >= 54) scene.destroy(upper);
                    if (t >= 55) scene.destroy(middle);
                    if (t >= 56) scene.destroy(lower);
                    if (t == 57) scene.status(body, (byte) 3);
                    break;
                case CLEANUP:
                    scene.destroy(body);
                    break;
                default: break;
            }
        }

        private void ring(Particle particle, double radius, double height, int count) {
            int capped = Math.min(24, count);
            for (int i = 0; i < capped; i++) {
                int index = i * 24 / capped;
                scene.particle(particle, CIRCLE_X[index] * radius, height, CIRCLE_Z[index] * radius, i);
            }
        }
    }
}
