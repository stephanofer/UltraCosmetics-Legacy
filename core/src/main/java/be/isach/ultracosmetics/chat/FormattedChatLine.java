package be.isach.ultracosmetics.chat;

public final class FormattedChatLine {
    private final String content;
    private final boolean centered;
    private final boolean blank;

    public FormattedChatLine(String content, boolean centered, boolean blank) {
        this.content = content;
        this.centered = centered;
        this.blank = blank;
    }

    public String getContent() {
        return content;
    }

    public boolean isCentered() {
        return centered;
    }

    public boolean isBlank() {
        return blank;
    }
}
