package be.isach.ultracosmetics.cosmetics.killeffects;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class KillEffectContext {
    public final UUID killer;
    public final String killerName;
    public final VictimSnapshot victim;
    public final KillEffectPosition death, anchor;
    public final Map<UUID, Boolean> audience;
    public final long seed, initialTick;
    public final boolean preview, airborne, lite;

    public KillEffectContext(UUID killer, String killerName, VictimSnapshot victim, KillEffectPosition death,
                             KillEffectPosition anchor, Map<UUID, Boolean> audience, long seed, long initialTick,
                             boolean preview, boolean airborne, boolean lite) {
        this.killer = killer;
        this.killerName = killerName;
        this.victim = victim;
        this.death = death;
        this.anchor = anchor;
        this.audience = Collections.unmodifiableMap(new LinkedHashMap<>(audience));
        this.seed = seed;
        this.initialTick = initialTick;
        this.preview = preview;
        this.airborne = airborne;
        this.lite = lite;
    }
}
