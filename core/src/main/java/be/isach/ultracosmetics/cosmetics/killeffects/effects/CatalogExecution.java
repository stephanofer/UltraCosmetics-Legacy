package be.isach.ultracosmetics.cosmetics.killeffects.effects;

import be.isach.ultracosmetics.config.SettingsManager;
import be.isach.ultracosmetics.cosmetics.killeffects.KillEffectContext;
import be.isach.ultracosmetics.cosmetics.killeffects.render.KillEffectRenderer.Particle;
import be.isach.ultracosmetics.cosmetics.killeffects.render.KillEffectRenderer.Sound;
import be.isach.ultracosmetics.cosmetics.killeffects.runtime.AbstractKillEffectExecution;
import be.isach.ultracosmetics.cosmetics.killeffects.runtime.KillEffectScene;
import be.isach.ultracosmetics.cosmetics.killeffects.runtime.KillEffectSettings;
import be.isach.ultracosmetics.cosmetics.type.KillEffectType;

/** Bounded presentation primitives shared by the catalog, with no protocol or scheduling knowledge. */
abstract class CatalogExecution extends AbstractKillEffectExecution {
    private static final int POINTS = 24;
    private static final double[] X = new double[POINTS], Z = new double[POINTS];
    private static final double[] SX = new double[POINTS], SY = new double[POINTS], SZ = new double[POINTS];
    static {
        for (int i = 0; i < POINTS; i++) {
            double angle = i * Math.PI * 2 / POINTS;
            X[i] = Math.cos(angle);
            Z[i] = Math.sin(angle);
            // Equal-area latitude bands with a golden-angle azimuth avoid polar clusters.
            SY[i] = 1 - 2 * (i + 0.5) / POINTS;
            double radius = Math.sqrt(1 - SY[i] * SY[i]);
            SX[i] = radius * Math.cos(i * Math.PI * (3 - Math.sqrt(5)));
            SZ[i] = radius * Math.sin(i * Math.PI * (3 - Math.sqrt(5)));
        }
    }

    protected final KillEffectContext context;
    protected final KillEffectScene scene;
    protected final boolean lite;
    protected final double phase;
    protected int subject = -1;
    private final int duration;
    private double previous = -1;
    private double time;

    CatalogExecution(KillEffectContext context, KillEffectScene scene, KillEffectType type) {
        this(context, scene, type, 0);
    }

    CatalogExecution(KillEffectContext context, KillEffectScene scene, KillEffectType type, int extraTicks) {
        this(context, scene, context.lite ? 30 + extraTicks / 2 : KillEffectSettings.clamp(
                SettingsManager.getConfig().getInt(type.getConfigPath() + ".Duration", 64), 60, 80) + extraTicks);
    }

    private CatalogExecution(KillEffectContext context, KillEffectScene scene, int duration) {
        super(duration, scene::close);
        this.context = context;
        this.scene = scene;
        this.duration = duration;
        lite = context.lite;
        phase = new java.util.Random(context.seed).nextDouble() * Math.PI * 2;
    }

    @Override
    protected final void onTick(int elapsed) {
        time = elapsed * 60.0 / duration;
        frame(time, elapsed);
        previous = time;
    }

    protected abstract void frame(double t, int elapsed);

    protected final boolean at(int tick) { return previous < tick && time >= tick; }

    protected final void victim() {
        if (lite) subject = scene.spawnHead(0, 1.6, 0, context.death.yaw);
        else {
            subject = scene.spawnPlayer(context.anchor.y - context.death.y);
            scene.move(subject, 0, 0, 0, context.death.yaw, 0);
        }
    }

    protected final void moveVictim(double x, double y, double z, float yaw) {
        if (lite) scene.moveHead(subject, x, y + 1.6, z, yaw);
        else scene.move(subject, x, y, z, yaw, 0);
    }

    protected final void removeVictim() { scene.destroy(subject); subject = -1; }

    protected final void sound(Sound sound, double y, float volume, float pitch) {
        // Lite scenes retain one signature cue at their climax.
        if (!lite) scene.sound(sound, y, volume, pitch);
    }

    protected final void climax(Sound sound, double y, float pitch) {
        scene.sound(sound, y, lite ? 0.35f : 0.65f, pitch);
    }

    protected final void ring(Particle particle, double radius, double y) {
        int stride = lite ? 3 : 1;
        for (int i = 0; i < POINTS; i += stride) {
            scene.particle(particle, X[i] * radius, y, Z[i] * radius, i);
        }
    }

    protected final void burst(Particle particle, double x, double y, double z, double radius, double drop) {
        int stride = lite ? 3 : 1;
        for (int i = 0; i < POINTS; i += stride) {
            scene.particle(particle, x + SX[i] * radius, y + SY[i] * radius - drop,
                    z + SZ[i] * radius, i);
        }
    }

    protected final void helix(Particle particle, double t, double radius, double bottom, double height, double turn) {
        int count = lite ? 4 : 10;
        for (int i = 0; i < count; i++) {
            double u = i / (double) (count - 1);
            double angle = phase + turn + t * 0.22 + u * Math.PI * 2;
            scene.particle(particle, Math.cos(angle) * radius, bottom + height * u,
                    Math.sin(angle) * radius, i);
        }
    }

    protected static double progress(double t, double start, double end) {
        return Math.max(0, Math.min(1, (t - start) / (end - start)));
    }

    protected static double smooth(double u) { return u * u * (3 - 2 * u); }
}
