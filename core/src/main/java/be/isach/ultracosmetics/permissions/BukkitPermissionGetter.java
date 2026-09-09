package be.isach.ultracosmetics.permissions;

import be.isach.ultracosmetics.cosmetics.type.CosmeticType;
import be.isach.ultracosmetics.cosmetics.type.KillEffectType;

import org.bukkit.entity.Player;

public class BukkitPermissionGetter implements CosmeticPermissionGetter, RawPermissionGetter {

    @Override
    public boolean hasRawPermission(Player player, String permission) {
        return player.hasPermission(permission);
    }

    @Override
    public boolean hasPermission(Player player, CosmeticType<?> type) {
        if (type instanceof KillEffectType) {
            if (player.isPermissionSet(type.getPermission().getName())) {
                return hasRawPermission(player, type.getPermission().getName());
            }
            String legacy = ((KillEffectType) type).getLegacyName();
            if (legacy != null && (hasRawPermission(player, "ultracosmetics.deatheffects." + legacy.toLowerCase(java.util.Locale.ROOT))
                    || hasRawPermission(player, "ultracosmetics.killeffects." + legacy.toLowerCase(java.util.Locale.ROOT)))) return true;
        }
        return hasRawPermission(player, type.getPermission().getName());
    }

}
