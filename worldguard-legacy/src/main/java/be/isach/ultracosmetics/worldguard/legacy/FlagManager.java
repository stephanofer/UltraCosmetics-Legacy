package be.isach.ultracosmetics.worldguard.legacy;

import be.isach.ultracosmetics.cosmetics.Category;
import be.isach.ultracosmetics.worldguard.IFlagManager;
import be.isach.ultracosmetics.worldguard.UCFlag;
import com.sk89q.worldguard.bukkit.RegionContainer;
import com.sk89q.worldguard.bukkit.RegionQuery;
import com.sk89q.worldguard.bukkit.WGBukkit;
import com.sk89q.worldguard.protection.flags.EnumFlag;
import com.sk89q.worldguard.protection.flags.SetFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.session.handler.Handler;
import org.bukkit.entity.Player;

import java.util.Set;

public class FlagManager implements IFlagManager {
    protected static final SetFlag<Category> CATEGORY_FLAG = new SetFlag<>("uc-blocked-categories", new EnumFlag<>(null, Category.class));

    @Override
    public void register() {
        FlagRegistry registry = WGBukkit.getPlugin().getFlagRegistry();
        for (UCFlag flag : UCFlag.values()) {
            registry.register(flag.getFlag());
        }
        registry.register(CATEGORY_FLAG);
    }

    @Override
    public void registerPhase2() {
        WGBukkit.getPlugin().getSessionManager().registerHandler(FACTORY, null);
    }

    @Override
    public boolean flagCheck(UCFlag flag, Player bukkitPlayer) {
        RegionContainer rc = WGBukkit.getPlugin().getRegionContainer();
        RegionQuery query = rc.createQuery();
        return query.testState(bukkitPlayer.getLocation(), bukkitPlayer, flag.getFlag());
    }

    @Override
    public Set<Category> categoryFlagCheck(Player bukkitPlayer) {
        RegionContainer rc = WGBukkit.getPlugin().getRegionContainer();
        RegionQuery query = rc.createQuery();
        return query.queryValue(bukkitPlayer.getLocation(), bukkitPlayer, CATEGORY_FLAG);
    }

    // from WorldGuard documentation:
    @Override
    public boolean flagCheckAt(UCFlag flag, Player player, org.bukkit.Location location) {
        return WGBukkit.getPlugin().getRegionContainer().createQuery().testState(location, player, flag.getFlag());
    }

    @Override
    public Set<Category> categoryFlagCheckAt(Player player, org.bukkit.Location location) {
        return WGBukkit.getPlugin().getRegionContainer().createQuery().queryValue(location, player, CATEGORY_FLAG);
    }

    // https://worldguard.enginehub.org/en/latest/developer/regions/custom-flags/
    private static final Factory FACTORY = new Factory();

    public static class Factory extends Handler.Factory<CosmeticFlagHandler> {
        @Override
        public CosmeticFlagHandler create(Session session) {
            return new CosmeticFlagHandler(session, UCFlag.COSMETICS.getFlag(), UCFlag.SHOWROOM.getFlag(), CATEGORY_FLAG);
        }
    }
}
