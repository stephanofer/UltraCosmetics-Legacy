package be.isach.ultracosmetics.chat;

import java.util.ArrayList;
import java.util.List;

public final class PixelLineWrapper {
    private static final char COLOR_CHAR = '\u00A7';
    private final Minecraft18FontMetrics metrics;
    private final int maximumWidth;

    public PixelLineWrapper(Minecraft18FontMetrics metrics, int maximumWidth) {
        this.metrics = metrics;
        this.maximumWidth = maximumWidth;
    }

    public List<String> wrap(String legacyText) {
        List<Glyph> glyphs = glyphs(legacyText);
        List<String> result = new ArrayList<>();
        if (glyphs.isEmpty()) {
            result.add("");
            return result;
        }

        int start = 0;
        while (start < glyphs.size()) {
            int width = 0;
            int lastSpace = -1;
            int cursor = start;
            while (cursor < glyphs.size()) {
                Glyph glyph = glyphs.get(cursor);
                int proposed = width + metrics.glyphAdvance(glyph.character, glyph.bold);
                if (proposed > maximumWidth && cursor > start) break;
                width = proposed;
                if (glyph.character == ' ') lastSpace = cursor;
                cursor++;
                if (width > maximumWidth) break;
            }

            int end = cursor;
            int next = cursor;
            if (cursor < glyphs.size() && lastSpace >= start) {
                end = lastSpace;
                next = lastSpace + 1;
                while (next < glyphs.size() && glyphs.get(next).character == ' ') next++;
            }
            if (end == start) {
                end = Math.min(start + 1, glyphs.size());
                next = end;
            }
            result.add(serialize(glyphs, start, end));
            start = next;
        }
        return result;
    }

    public int width(String legacyText) {
        int width = 0;
        for (Glyph glyph : glyphs(legacyText)) {
            width += metrics.glyphAdvance(glyph.character, glyph.bold);
        }
        return width;
    }

    private List<Glyph> glyphs(String text) {
        List<Glyph> result = new ArrayList<>();
        String color = "";
        StringBuilder decorations = new StringBuilder();
        boolean bold = false;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == COLOR_CHAR && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++i));
                if (isColor(code)) {
                    color = "" + COLOR_CHAR + code;
                    decorations.setLength(0);
                    bold = false;
                } else if (code == 'r') {
                    color = "";
                    decorations.setLength(0);
                    bold = false;
                } else if (code >= 'k' && code <= 'o') {
                    String decoration = "" + COLOR_CHAR + code;
                    if (decorations.indexOf(decoration) < 0) decorations.append(decoration);
                    if (code == 'l') bold = true;
                }
                continue;
            }
            result.add(new Glyph(character, color + decorations, bold));
        }
        return result;
    }

    private boolean isColor(char code) {
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
    }

    private String serialize(List<Glyph> glyphs, int start, int end) {
        StringBuilder result = new StringBuilder((end - start) * 4);
        String style = null;
        for (int i = start; i < end; i++) {
            Glyph glyph = glyphs.get(i);
            if (!glyph.style.equals(style)) {
                result.append(COLOR_CHAR).append('r').append(glyph.style);
                style = glyph.style;
            }
            result.append(glyph.character);
        }
        return result.toString();
    }

    private static final class Glyph {
        private final char character;
        private final String style;
        private final boolean bold;

        private Glyph(char character, String style, boolean bold) {
            this.character = character;
            this.style = style;
            this.bold = bold;
        }
    }
}
