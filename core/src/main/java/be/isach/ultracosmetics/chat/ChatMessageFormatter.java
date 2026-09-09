package be.isach.ultracosmetics.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.List;

public interface ChatMessageFormatter {
    List<Component> format(ChatMessageTemplate template, TagResolver... placeholders);

    void validate(ChatMessageTemplate template);

    int getMaximumOutputLines();
}
