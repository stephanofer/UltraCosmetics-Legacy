package be.isach.ultracosmetics.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ChatMessageTemplate {
    private static final String NEW_LINE = "{NL}";
    private static final String CENTER = "{CTR}";
    private static final String BLANK = "{BLANK}";

    private final List<FormattedChatLine> lines;

    private ChatMessageTemplate(List<FormattedChatLine> lines) {
        this.lines = Collections.unmodifiableList(lines);
    }

    public static ChatMessageTemplate compile(List<String> configuredLines) {
        if (configuredLines == null || configuredLines.isEmpty()) {
            throw new IllegalArgumentException("Template must contain at least one line");
        }
        List<FormattedChatLine> result = new ArrayList<>();
        for (String configuredLine : configuredLines) {
            String line = configuredLine == null ? "" : configuredLine;
            if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("Use {NL} or a YAML list instead of literal line breaks");
            }
            splitAndCompile(line, result);
        }
        return new ChatMessageTemplate(result);
    }

    private static void splitAndCompile(String input, List<FormattedChatLine> result) {
        int start = 0;
        for (int i = 0; i <= input.length() - NEW_LINE.length();) {
            if (input.regionMatches(true, i, NEW_LINE, 0, NEW_LINE.length())) {
                result.add(compileLine(input.substring(start, i)));
                i += NEW_LINE.length();
                start = i;
            } else {
                i++;
            }
        }
        result.add(compileLine(input.substring(start)));
    }

    private static FormattedChatLine compileLine(String input) {
        String trimmed = input.trim();
        if (trimmed.equalsIgnoreCase(BLANK) || trimmed.isEmpty()) {
            return new FormattedChatLine("", false, true);
        }
        boolean centered = startsWithIgnoreCase(trimmed, CENTER);
        String content = centered ? trimmed.substring(CENTER.length()) : input;
        return new FormattedChatLine(content, centered, false);
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.toUpperCase(Locale.ROOT).startsWith(prefix);
    }

    public List<FormattedChatLine> getLines() {
        return lines;
    }
}
