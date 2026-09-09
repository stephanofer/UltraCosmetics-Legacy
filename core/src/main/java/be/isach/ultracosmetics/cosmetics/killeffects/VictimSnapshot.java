package be.isach.ultracosmetics.cosmetics.killeffects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

/** Captured while the victim is visible; the allowlist also protects offline API invocations. */
public final class VictimSnapshot {
    public static final class SkinProperty {
        public final String name, value, signature;

        public SkinProperty(String name, String value, String signature) {
            this.name = java.util.Objects.requireNonNull(name);
            this.value = java.util.Objects.requireNonNull(value);
            this.signature = signature;
        }
    }

    public final UUID uuid;
    public final String name;
    public final List<SkinProperty> skin;
    public final Set<UUID> visibleTo;
    public final String prefix;
    public final String suffix;
    public final boolean hasNameTag;

    public VictimSnapshot(UUID uuid, String name, List<SkinProperty> skin, Set<UUID> visibleTo) {
        this(uuid, name, skin, visibleTo, "", "", false);
    }

    public VictimSnapshot(UUID uuid, String name, List<SkinProperty> skin, Set<UUID> visibleTo,
                          String prefix, String suffix) {
        this(uuid, name, skin, visibleTo, prefix, suffix, true);
    }

    private VictimSnapshot(UUID uuid, String name, List<SkinProperty> skin, Set<UUID> visibleTo,
                           String prefix, String suffix, boolean hasNameTag) {
        this.uuid = java.util.Objects.requireNonNull(uuid);
        if (name == null || !name.matches("[A-Za-z0-9_]{1,16}")) throw new IllegalArgumentException("Invalid profile name");
        this.name = name;
        this.skin = Collections.unmodifiableList(new ArrayList<>(skin));
        this.visibleTo = Collections.unmodifiableSet(new HashSet<>(visibleTo));
        this.prefix = cutTo(prefix, 16);
        this.suffix = cutTo(suffix, 16);
        this.hasNameTag = hasNameTag;
    }

    public static String cutTo(String value, int limit) {
        if (value == null) return "";
        if (value.length() <= limit) return value;
        if (value.charAt(limit - 1) == '\u00a7') return value.substring(0, limit - 1);
        return value.substring(0, limit);
    }
}
