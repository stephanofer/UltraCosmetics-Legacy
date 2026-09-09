package be.isach.ultracosmetics.cosmetics.type;

import be.isach.ultracosmetics.UltraCosmeticsData;
import be.isach.ultracosmetics.chat.ChatMessageFormatter;
import be.isach.ultracosmetics.chat.ChatMessageTemplate;
import be.isach.ultracosmetics.chat.DefaultChatMessageFormatter;
import be.isach.ultracosmetics.config.CustomConfiguration;
import be.isach.ultracosmetics.config.MessageManager;
import be.isach.ultracosmetics.config.SettingsManager;
import be.isach.ultracosmetics.cosmetics.Category;
import be.isach.ultracosmetics.cosmetics.joinmessages.JoinMessage;
import be.isach.ultracosmetics.util.SmartLogger.LogLevel;
import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XSound;
import org.bukkit.Sound;

public final class JoinMessageType extends CosmeticType<JoinMessage> {
    private static ChatMessageFormatter formatter;

    private final ChatMessageTemplate template;
    private final String defaultSound;
    private final int defaultPrice;
    private final int defaultWeight;
    private Sound sound;
    private float volume;
    private float pitch;
    private boolean valid;

    private JoinMessageType(String name, XMaterial material, String defaultSound, int defaultPrice, int defaultWeight) {
        super(Category.JOIN_MESSAGES, name, material, JoinMessage.class);
        this.defaultSound = defaultSound;
        this.defaultPrice = defaultPrice;
        this.defaultWeight = defaultWeight;
        ChatMessageTemplate compiled = null;
        try {
            compiled = ChatMessageTemplate.compile(MessageManager.getTemplateLines(getConfigPath() + ".Message"));
            formatter.validate(compiled);
            valid = true;
        } catch (RuntimeException exception) {
            UltraCosmeticsData.get().getPlugin().getSmartLogger().write(LogLevel.WARNING,
                    "Disabled Join Message " + getConfigPath() + ": " + exception.getMessage());
        }
        this.template = compiled;
    }

    public static void register() {
        int center = clamp("Chat-Formatting.Center-Pixel", 154, 0, 500);
        int width = clamp("Chat-Formatting.Maximum-Line-Width-Pixels", 300, 40, 1000);
        int lines = clamp("Chat-Formatting.Maximum-Output-Lines", 5, 1, 10);
        formatter = new DefaultChatMessageFormatter(center, width, lines);

        new JoinMessageType("Server", XMaterial.OAK_DOOR, "BLOCK_CHEST_OPEN", 250, 8);
        new JoinMessageType("Royal", XMaterial.GOLD_INGOT, "ENTITY_PLAYER_LEVELUP", 1500, 3);
        new JoinMessageType("Ender", XMaterial.ENDER_PEARL, "ENTITY_ENDERMAN_TELEPORT", 1500, 3);
        new JoinMessageType("Lightning", XMaterial.BLAZE_POWDER, "ENTITY_LIGHTNING_BOLT_THUNDER", 2500, 1);
        new JoinMessageType("Firework", XMaterial.FIREWORK_ROCKET, "ENTITY_FIREWORK_ROCKET_BLAST", 2500, 1);
        new JoinMessageType("Dragon", XMaterial.DRAGON_EGG, "ENTITY_ENDER_DRAGON_GROWL", 4000, 1);
        new JoinMessageType("Mystery", XMaterial.ENDER_EYE, "BLOCK_PORTAL_TRAVEL", 1500, 3);
        new JoinMessageType("Arcade", XMaterial.REDSTONE_LAMP, "ENTITY_EXPERIENCE_ORB_PICKUP", 2500, 1);
        new JoinMessageType("Game", XMaterial.COMPASS, "BLOCK_NOTE_BLOCK_HARP", 500, 6);
        new JoinMessageType("Roblox", XMaterial.REDSTONE, "BLOCK_NOTE_BLOCK_PLING", 750, 5);
        new JoinMessageType("Spawn", XMaterial.GRASS_BLOCK, "BLOCK_NOTE_BLOCK_HARP", 750, 5);
        new JoinMessageType("WildAppearance", XMaterial.SPAWNER, "BLOCK_NOTE_BLOCK_PLING", 1000, 4);
        new JoinMessageType("Landing", XMaterial.FEATHER, "ENTITY_BAT_TAKEOFF", 1500, 3);
        new JoinMessageType("BraceYourselves", XMaterial.IRON_CHESTPLATE, "BLOCK_ANVIL_LAND", 1500, 3);
        new JoinMessageType("PartyOver", XMaterial.JUKEBOX, "BLOCK_CHEST_CLOSE", 1500, 3);
        new JoinMessageType("Overpowered", XMaterial.NETHER_STAR, "ENTITY_WITHER_SPAWN", 2500, 1);
        new JoinMessageType("Achievement", XMaterial.BOOK, "ENTITY_PLAYER_LEVELUP", 2000, 2);
        new JoinMessageType("Hacker", XMaterial.COMMAND_BLOCK, "ENTITY_ENDERMAN_STARE", 2500, 1);
        new JoinMessageType("MoneyMan", XMaterial.EMERALD, "ENTITY_VILLAGER_YES", 1500, 3);
        new JoinMessageType("Minecrafter", XMaterial.DIAMOND_PICKAXE, "BLOCK_ANVIL_USE", 1500, 3);
        new JoinMessageType("SuperSaiyan", XMaterial.GOLDEN_APPLE, "ENTITY_GHAST_SHOOT", 2500, 1);
        new JoinMessageType("Impostor", XMaterial.RED_DYE, "ENTITY_CREEPER_PRIMED", 1500, 3);
        new JoinMessageType("Hide", XMaterial.SKELETON_SKULL, "ENTITY_GHAST_WARN", 1500, 3);
        new JoinMessageType("Uchiha", XMaterial.REDSTONE_TORCH, "ENTITY_ENDERMAN_SCREAM", 3000, 1);
        new JoinMessageType("PirateKing", XMaterial.COMPASS, "ENTITY_ITEM_PICKUP", 3000, 1);
        new JoinMessageType("Stardust", XMaterial.GLOWSTONE_DUST, "BLOCK_NOTE_BLOCK_PLING", 2500, 1);
        new JoinMessageType("Spotlight", XMaterial.NAME_TAG, "ENTITY_FIREWORK_ROCKET_TWINKLE", 2000, 2);
        new JoinMessageType("Speedrunner", XMaterial.CLOCK, "ENTITY_BAT_LOOP", 2000, 2);
    }

    private static int clamp(String path, int fallback, int minimum, int maximum) {
        int value = SettingsManager.getConfig().getInt(path, fallback);
        int clamped = Math.max(minimum, Math.min(maximum, value));
        if (value != clamped) {
            UltraCosmeticsData.get().getPlugin().getSmartLogger().write(LogLevel.WARNING,
                    path + " was clamped from " + value + " to " + clamped);
        }
        return clamped;
    }

    @Override
    public boolean isEnabled() {
        return valid && getCategory().isEnabled() && super.isEnabled();
    }

    @Override
    protected void setupConfig(CustomConfiguration config, String path) {
        config.addDefault(path + ".Purchase-Price", defaultPrice);
        config.addDefault(path + ".Treasure-Chest-Weight", defaultWeight);
        super.setupConfig(config, path);
        config.addDefault(path + ".Sound.Name", defaultSound);
        config.addDefault(path + ".Sound.Volume", 0.8);
        config.addDefault(path + ".Sound.Pitch", 1.0);
        resolveSound(config, path);
    }

    private void resolveSound(CustomConfiguration config, String path) {
        String configured = config.getString(path + ".Sound.Name", "").trim();
        if (configured.isEmpty()) return;
        XSound matched = XSound.matchXSound(configured).orElse(null);
        sound = matched == null ? null : matched.parseSound();
        if (sound == null && !configured.equals(defaultSound)) {
            configured = defaultSound;
            config.set(path + ".Sound.Name", configured);
            matched = XSound.matchXSound(configured).orElse(null);
            sound = matched == null ? null : matched.parseSound();
        }
        if (sound == null) {
            UltraCosmeticsData.get().getPlugin().getSmartLogger().write(LogLevel.WARNING,
                    "Invalid or unsupported Join Message sound at " + path + ".Sound.Name: " + configured);
            return;
        }
        volume = (float) clampDouble(config, path + ".Sound.Volume", 0.8, 0.0, 2.0);
        pitch = (float) clampDouble(config, path + ".Sound.Pitch", 1.0, 0.5, 2.0);
    }

    private double clampDouble(CustomConfiguration config, String path, double fallback, double minimum, double maximum) {
        double value = config.getDouble(path, fallback);
        if (Double.isNaN(value) || Double.isInfinite(value)) value = fallback;
        double clamped = Math.max(minimum, Math.min(maximum, value));
        if (Double.compare(config.getDouble(path, fallback), clamped) != 0) {
            UltraCosmeticsData.get().getPlugin().getSmartLogger().write(LogLevel.WARNING,
                    path + " was clamped to " + clamped);
        }
        return clamped;
    }

    public ChatMessageTemplate getTemplate() {
        return template;
    }

    public Sound getSound() {
        return sound;
    }

    public float getVolume() {
        return volume;
    }

    public float getPitch() {
        return pitch;
    }

    public static ChatMessageFormatter getFormatter() {
        return formatter;
    }
}
