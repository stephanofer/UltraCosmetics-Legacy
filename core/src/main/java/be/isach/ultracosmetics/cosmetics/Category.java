package be.isach.ultracosmetics.cosmetics;

import be.isach.ultracosmetics.UltraCosmeticsData;
import be.isach.ultracosmetics.config.MessageManager;
import be.isach.ultracosmetics.config.SettingsManager;
import be.isach.ultracosmetics.cosmetics.killeffects.compatibility.KillEffectMigration;
import be.isach.ultracosmetics.cosmetics.suits.ArmorSlot;
import be.isach.ultracosmetics.cosmetics.type.CosmeticType;
import be.isach.ultracosmetics.util.ItemFactory;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Cosmetic category enum.
 *
 * @author iSach
 * @since 06-20-2016
 */
public enum Category {

    PETS("Pets", "petname", "pets", "pe", true, () -> UltraCosmeticsData.get().getServerVersion().isMobChipAvailable()),
    GADGETS("Gadgets", "gadgetname", "gadgets", "g", true),
    EFFECTS("Particle-Effects", "effectname", "particleeffects", "ef", true),
    MOUNTS("Mounts", "mountname", "mounts", "mou", true),
    MORPHS("Morphs", "morphname", "morphs", "mor", true, () -> Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")),
    HATS("Hats", "hatname", "hats", "h", true),
    SUITS_HELMET(ArmorSlot.HELMET),
    SUITS_CHESTPLATE(ArmorSlot.CHESTPLATE),
    SUITS_LEGGINGS(ArmorSlot.LEGGINGS),
    SUITS_BOOTS(ArmorSlot.BOOTS),
    EMOTES("Emotes", "emotename", "emotes", "e", true),
    PROJECTILE_EFFECTS("Projectile-Effects", "projectile-effectname", "projectileeffects", "p", false),
    KILL_EFFECTS("Kill-Effects", "kill-effectname", "killeffects", "k", false,
            () -> UltraCosmeticsData.get().getPlugin().getKillEffectManager() != null),
    // Retained only so WorldGuard can deserialize historical enum flags. Never exposed as a product.
    @Deprecated
    DEATH_EFFECTS("Death-Effects", "death-effectname", "deatheffects", "d", false, () -> false),
    ;

    // Avoids counting suit categories multiple times since they share settings
    public static int enabledSize() {
        int size = SUITS_HELMET.isEnabled() ? 1 : 0;
        for (Category cat : enabled()) {
            if (cat.isSuits()) continue;
            size += 1;
        }
        return size;
    }

    public static List<Category> enabled() {
        return Arrays.stream(values()).filter(Category::isEnabled).collect(Collectors.toList());
    }

    public static Category fromString(String name) {
        if (name == null) return null;
        if (name.equalsIgnoreCase("DEATH_EFFECTS") || name.equalsIgnoreCase("Death-Effects")) return KILL_EFFECTS;
        String lowerName = name.toLowerCase();
        for (Category cat : values()) {
            if (lowerName.startsWith(cat.prefix)) {
                return cat == DEATH_EFFECTS ? KILL_EFFECTS : cat;
            }
        }
        return null;
    }

    public String getStorageId() {
        switch (this) {
            case PETS: return "pets";
            case GADGETS: return "gadgets";
            case EFFECTS: return "effects";
            case MOUNTS: return "mounts";
            case MORPHS: return "morphs";
            case HATS: return "hats";
            case SUITS_HELMET: return "suits_helmet";
            case SUITS_CHESTPLATE: return "suits_chestplate";
            case SUITS_LEGGINGS: return "suits_leggings";
            case SUITS_BOOTS: return "suits_boots";
            case EMOTES: return "emotes";
            case PROJECTILE_EFFECTS: return "projectile_effects";
            case KILL_EFFECTS:
            case DEATH_EFFECTS: return "death_effects";
            default: throw new IllegalStateException("Missing storage identifier for " + this);
        }
    }

    public static Category fromStorage(String name) {
        String canonical = KillEffectMigration.category(name);
        for (Category category : values()) {
            if (category != DEATH_EFFECTS && KillEffectMigration.category(category.getStorageId()).equals(canonical)) return category;
        }
        return null;
    }

    public static void forEachCosmetic(Consumer<CosmeticType<?>> func) {
        for (Category cat : values()) {
            for (CosmeticType<?> type : cat.getValues()) {
                func.accept(type);
            }
        }
    }

    public static Category suitsFromSlot(ArmorSlot slot) {
        return Category.valueOf("SUITS_" + slot.name());
    }

    /**
     * The config path name.
     */
    private final String configPath;

    private final String chatPlaceholder;
    private final String permission;
    private final String prefix;
    private final boolean clearOnDeath;
    private final BooleanSupplier enableCondition;

    private Category(String configPath, String chatPlaceholder, String permission, String prefix, boolean clearOnDeath, BooleanSupplier enableCondition) {
        this.configPath = configPath;
        this.chatPlaceholder = chatPlaceholder;
        this.permission = permission;
        this.prefix = prefix;
        this.enableCondition = enableCondition;
        this.clearOnDeath = clearOnDeath;
    }

    private Category(String configPath, String chatPlaceholder, String permission, String prefix, boolean clearOnDeath) {
        this(configPath, chatPlaceholder, permission, prefix, clearOnDeath, () -> true);
    }

    private Category(ArmorSlot slot) {
        this("Suits", "suitname", "suits", "suits_" + slot.toString().toLowerCase().charAt(0), true);
    }

    /**
     * Gets the ItemStack in Main Menu.
     *
     * @return The ItemStack in Main Menu.
     */
    public ItemStack getItemStack() {
        ItemStack is;
        if (this == EMOTES) {
            is = ItemFactory.createSkull("5059d59eb4e59c31eecf9ece2f9cf3934e45c0ec476fc86bfaef8ea913ea710", "");
        } else {
            is = ItemFactory.getItemStackFromConfig("Categories." + configPath + ".Main-Menu-Item");
        }
        ItemMeta itemMeta = is.getItemMeta();
        itemMeta.setDisplayName(MessageManager.getLegacyMessage("Menu." + configPath + ".Button.Name"));
        is.setItemMeta(itemMeta);
        return is;
    }

    /**
     * Checks if the category is enabled.
     *
     * @return {@code true} if enabled, otherwise {@code false}.
     */
    public boolean isEnabled() {
        return enableCondition.getAsBoolean() && SettingsManager.getConfig().getBoolean("Categories-Enabled." + configPath);
    }

    /**
     * Checks if the category should have a back arrow in its menu.
     *
     * @return {@code true} if has arrow, otherwise {@code false}
     */
    public boolean hasGoBackArrow() {
        return !(!UltraCosmeticsData.get().areTreasureChestsEnabled() && enabledSize() == 1)
                && SettingsManager.getConfig().getBoolean("Categories." + configPath + ".Go-Back-Arrow");
    }

    /**
     * @return Config Path.
     */
    public String getConfigPath() {
        return configPath;
    }

    public String getMessagesName() {
        if (isSuits()) return "Suits";
        // Like configPath but value is different for Category.EFFECTS
        return name().charAt(0) + name().substring(1).toLowerCase().replace("_", "-");
    }

    public Component getActivateTooltip() {
        return MessageManager.getMessage("Menu." + configPath + ".Button.Tooltip-Equip");
    }

    public Component getDeactivateTooltip() {
        return MessageManager.getMessage("Menu." + configPath + ".Button.Tooltip-Unequip");
    }

    public String getChatPlaceholder() {
        return chatPlaceholder;
    }

    public String getPermission() {
        return "ultracosmetics." + permission;
    }

    public List<? extends CosmeticType<?>> getEnabled() {
        return CosmeticType.enabledOf(this);
    }

    public List<? extends CosmeticType<?>> getValues() {
        return CosmeticType.valuesOf(this);
    }

    public CosmeticType<?> valueOfType(String name) {
        if (name == null) return null;
        return CosmeticType.valueOf(this, name);
    }

    public boolean isSuits() {
        return name().startsWith("SUITS_");
    }

    public boolean isClearOnDeath() {
        return clearOnDeath;
    }
}
