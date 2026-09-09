package be.isach.ultracosmetics.chat;

import java.util.Arrays;

public final class Minecraft18FontMetrics {
    private static final int[] WIDTHS = new int[256];

    static {
        Arrays.fill(WIDTHS, 4);
        set("ABCDEFGHJKLMNOPQRSTUVWXYZabcdefghjkmnopqrsuvwxyz0123456789#$%&*+-=_?/~", 5);
        set("ft(){}<>", 4);
        set("I[]\"", 3);
        set("`", 2);
        set("!il:;'|.,", 1);
        set("@", 6);
        set("\\^", 5);
        set(" ", 3);
        set("áéóúñÁÉÓÚÑüÜ", 5);
        set("íÍ¡", 1);
        set("¿", 5);
    }

    private static void set(String characters, int width) {
        for (int i = 0; i < characters.length(); i++) {
            WIDTHS[characters.charAt(i)] = width;
        }
    }

    public int glyphAdvance(char character, boolean bold) {
        int width = character < WIDTHS.length ? WIDTHS[character] : 4;
        return width + (bold && character != ' ' ? 1 : 0) + 1;
    }

    public int spaceAdvance() {
        return WIDTHS[' '] + 1;
    }
}
