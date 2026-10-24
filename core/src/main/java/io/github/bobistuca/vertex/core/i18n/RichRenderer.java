package io.github.bobistuca.vertex.core.i18n;

import io.github.bobistuca.vertex.core.config.Settings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;

/**
 * Parses configurable MiniMessage templates with Adventure and flattens the result into {@link Rich}.
 * Only color, decoration, reset and newline tags are allowed: templates cannot produce click events or other
 * interactive components. Player-controlled values are inserted as unparsed (escaped) text.
 */
public final class RichRenderer {
    private final MiniMessage miniMessage;
    private final TagResolver palette;

    public RichRenderer(Settings.Appearance appearance) {
        this.miniMessage = MiniMessage.builder()
                .tags(TagResolver.builder()
                        .resolver(StandardTags.color())
                        .resolver(StandardTags.decorations())
                        .resolver(StandardTags.reset())
                        .resolver(StandardTags.newline())
                        .build())
                .strict(false)
                .build();
        this.palette = TagResolver.builder()
                .resolver(Placeholder.styling("primary", color(appearance.primary())))
                .resolver(Placeholder.styling("secondary", color(appearance.secondary())))
                .resolver(Placeholder.styling("text", color(appearance.text())))
                .build();
    }

    private static TextColor color(String hex) {
        TextColor parsed = TextColor.fromHexString(hex);
        return parsed == null ? TextColor.color(0xFFFFFF) : parsed;
    }

    /**
     * @param template     MiniMessage template from a locale file
     * @param placeholders values inserted as escaped text; keys are tag names used as {@code <key>}
     */
    public Rich render(String template, Map<String, String> placeholders) {
        TagResolver.Builder resolvers = TagResolver.builder().resolver(palette);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolvers.resolver(Placeholder.unparsed(entry.getKey(), entry.getValue()));
        }
        Component component = miniMessage.deserialize(template, resolvers.build());
        List<RichSpan> spans = new ArrayList<>();
        flatten(component, Style.empty(), spans);
        return new Rich(spans);
    }

    private static void flatten(Component component, Style inherited, List<RichSpan> out) {
        Style effective = component.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            TextColor color = effective.color();
            out.add(new RichSpan(text.content(), color == null ? -1 : color.value(),
                    effective.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE,
                    effective.decoration(TextDecoration.ITALIC) == TextDecoration.State.TRUE,
                    effective.decoration(TextDecoration.UNDERLINED) == TextDecoration.State.TRUE, null));
        }
        for (Component child : component.children()) {
            flatten(child, effective, out);
        }
    }
}
