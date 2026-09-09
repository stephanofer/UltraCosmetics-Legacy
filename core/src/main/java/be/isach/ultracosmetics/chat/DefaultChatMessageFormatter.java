package be.isach.ultracosmetics.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DefaultChatMessageFormatter implements ChatMessageFormatter {
    private static final Set<String> ALLOWED_TAGS = new HashSet<>(Arrays.asList(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white",
            "obfuscated", "obf", "bold", "b", "strikethrough", "st", "underlined", "u", "italic", "em", "i",
            "reset", "player", "world", "online", "max_players"));
    private final MiniMessage miniMessage;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
    private final Minecraft18FontMetrics metrics = new Minecraft18FontMetrics();
    private final PixelLineWrapper wrapper;
    private final int centerPixel;
    private final int maximumOutputLines;

    public DefaultChatMessageFormatter(int centerPixel, int maximumLineWidth, int maximumOutputLines) {
        this.centerPixel = centerPixel;
        this.maximumOutputLines = maximumOutputLines;
        this.wrapper = new PixelLineWrapper(metrics, maximumLineWidth);
        TagResolver.Builder tags = TagResolver.builder()
                .resolver(StandardTags.decorations())
                .resolver(StandardTags.reset());
        registerColors(tags);
        this.miniMessage = MiniMessage.builder().tags(tags.build()).build();
    }

    private void registerColors(TagResolver.Builder tags) {
        registerColor(tags, "black", NamedTextColor.BLACK);
        registerColor(tags, "dark_blue", NamedTextColor.DARK_BLUE);
        registerColor(tags, "dark_green", NamedTextColor.DARK_GREEN);
        registerColor(tags, "dark_aqua", NamedTextColor.DARK_AQUA);
        registerColor(tags, "dark_red", NamedTextColor.DARK_RED);
        registerColor(tags, "dark_purple", NamedTextColor.DARK_PURPLE);
        registerColor(tags, "gold", NamedTextColor.GOLD);
        registerColor(tags, "gray", NamedTextColor.GRAY);
        registerColor(tags, "dark_gray", NamedTextColor.DARK_GRAY);
        registerColor(tags, "blue", NamedTextColor.BLUE);
        registerColor(tags, "green", NamedTextColor.GREEN);
        registerColor(tags, "aqua", NamedTextColor.AQUA);
        registerColor(tags, "red", NamedTextColor.RED);
        registerColor(tags, "light_purple", NamedTextColor.LIGHT_PURPLE);
        registerColor(tags, "yellow", NamedTextColor.YELLOW);
        registerColor(tags, "white", NamedTextColor.WHITE);
    }

    private void registerColor(TagResolver.Builder tags, String name, NamedTextColor color) {
        tags.resolver(TagResolver.resolver(name, Tag.styling(color)));
    }

    @Override
    public List<Component> format(ChatMessageTemplate template, TagResolver... placeholders) {
        List<Component> result = new ArrayList<>();
        for (FormattedChatLine line : template.getLines()) {
            if (line.isBlank()) {
                result.add(Component.empty());
                enforceLineLimit(result);
                continue;
            }
            Component rendered = miniMessage.deserialize(line.getContent(), placeholders);
            for (String fragment : wrapper.wrap(legacy.serialize(rendered))) {
                if (line.isCentered()) fragment = center(fragment);
                result.add(legacy.deserialize(fragment));
                enforceLineLimit(result);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private void enforceLineLimit(List<Component> lines) {
        if (lines.size() > maximumOutputLines) {
            throw new IllegalArgumentException("Template produces more than " + maximumOutputLines + " chat lines");
        }
    }

    @Override
    public void validate(ChatMessageTemplate template) {
        for (FormattedChatLine line : template.getLines()) {
            validateTags(line.getContent());
        }
        format(template,
                Placeholder.unparsed("player", "Player"),
                Placeholder.unparsed("world", "world"),
                Placeholder.unparsed("online", "1"),
                Placeholder.unparsed("max_players", "20"));
    }

    private void validateTags(String input) {
        for (int start = 0; start < input.length(); start++) {
            if (input.charAt(start) != '<' || (start > 0 && input.charAt(start - 1) == '\\')) continue;
            int end = input.indexOf('>', start + 1);
            if (end < 0) return;
            String tag = input.substring(start + 1, end).trim().toLowerCase(Locale.ROOT);
            if (tag.startsWith("/")) tag = tag.substring(1);
            if (tag.startsWith("!")) tag = tag.substring(1);
            if (tag.indexOf(':') >= 0 || !ALLOWED_TAGS.contains(tag)) {
                throw new IllegalArgumentException("Unsupported Minecraft 1.8 formatting tag: <" + tag + ">");
            }
            start = end;
        }
    }

    private String center(String legacyText) {
        int paddingPixels = Math.max(0, centerPixel - wrapper.width(legacyText) / 2);
        int spaces = paddingPixels / metrics.spaceAdvance();
        StringBuilder result = new StringBuilder(legacyText.length() + spaces);
        for (int i = 0; i < spaces; i++) result.append(' ');
        return result.append(legacyText).toString();
    }

    @Override
    public int getMaximumOutputLines() {
        return maximumOutputLines;
    }
}
