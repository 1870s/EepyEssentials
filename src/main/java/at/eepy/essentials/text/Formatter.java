package at.eepy.essentials.text;

import at.eepy.essentials.config.Settings;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared message formatting for all modules, so everything uses the same
 * palette, prefix and branding as EepyTab and EepyMOTD.
 * <p>
 * Immutable after construction and therefore safe to use from async events
 * (chat) as well as the main thread. Values passed as placeholders are inserted
 * as text or finished components and never parsed, so player input can not
 * inject formatting.
 */
public final class Formatter {

    private static final Map<String, TextColor> DEFAULT_PALETTE = defaultPalette();

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<String, TextColor> palette;
    private final TagResolver baseTags;
    private final Logger logger;

    public record Branding(String serverName, String ip, String website, String websiteUrl, String discord,
                           String discordUrl) {
        public static final Branding DEFAULT = new Branding("eepy", "mc.eepy.at", "eepy.at", "https://eepy.at",
                "dsc.gg/muezza", "https://dsc.gg/muezza");
    }

    public Formatter(Map<String, TextColor> palette, Branding branding, String prefix, Logger logger) {
        this.palette = Map.copyOf(palette);
        this.logger = logger;
        List<TagResolver> resolvers = new ArrayList<>();
        this.palette.forEach((name, color) -> resolvers.add(TagResolver.resolver(name, Tag.styling(color))));
        resolvers.add(Placeholder.unparsed("server_name", branding.serverName()));
        resolvers.add(Placeholder.unparsed("ip", branding.ip()));
        resolvers.add(Placeholder.unparsed("website", branding.website()));
        resolvers.add(Placeholder.unparsed("discord", branding.discord()));
        resolvers.add(Placeholder.component("website_link", link(branding.website(), branding.websiteUrl(), "secondary")));
        resolvers.add(Placeholder.component("discord_link", link(branding.discord(), branding.discordUrl(), "primary")));
        TagResolver styleAndBranding = TagResolver.resolver(resolvers);
        Component prefixComponent = miniMessage.deserialize(prefix, styleAndBranding);
        this.baseTags = TagResolver.resolver(styleAndBranding, Placeholder.component("prefix", prefixComponent));
    }

    /** Reads palette and branding from config.yml. */
    public static Formatter fromConfig(Settings config, String prefix, Logger logger) {
        Map<String, TextColor> palette = new LinkedHashMap<>(DEFAULT_PALETTE);
        Settings paletteSection = config.section("palette");
        for (String name : paletteSection.keys()) {
            TextColor color = paletteSection.color(name, null);
            if (color != null) {
                palette.put(name.toLowerCase(Locale.ROOT), color);
            }
        }
        Settings branding = config.section("branding");
        Branding values = new Branding(
                branding.string("server-name", Branding.DEFAULT.serverName()),
                branding.string("ip", Branding.DEFAULT.ip()),
                branding.string("website", Branding.DEFAULT.website()),
                branding.string("website-url", Branding.DEFAULT.websiteUrl()),
                branding.string("discord", Branding.DEFAULT.discord()),
                branding.string("discord-url", Branding.DEFAULT.discordUrl()));
        return new Formatter(palette, values, prefix, logger);
    }

    private Component link(String label, String url, String colorName) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            url = "https://" + url;
        }
        TextColor color = palette.getOrDefault(colorName, DEFAULT_PALETTE.get(colorName));
        return Component.text(label, color)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text("Open " + url, palette.get("muted"))));
    }

    /** Parses one MiniMessage text with palette, branding and the given extra tags. */
    public Component parse(String text, TagResolver... extra) {
        if (text.isEmpty()) {
            return Component.empty();
        }
        try {
            return miniMessage.deserialize(text, TagResolver.resolver(baseTags, TagResolver.resolver(extra)));
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Could not format message: " + text, e);
            return Component.text(miniMessage.stripTags(text));
        }
    }

    /** Parses lines and joins them with line breaks; empty list = empty component. */
    public Component parseLines(List<String> lines, TagResolver... extra) {
        List<Component> parsed = new ArrayList<>(lines.size());
        for (String line : lines) {
            parsed.add(parse(line, extra));
        }
        return Component.join(JoinConfiguration.newlines(), parsed);
    }

    /** Sends a text unless it is empty. */
    public void send(Audience audience, String text, TagResolver... extra) {
        if (!text.isEmpty()) {
            audience.sendMessage(parse(text, extra));
        }
    }

    /** Sends every line as its own message; an empty list sends nothing. */
    public void sendLines(Audience audience, List<String> lines, TagResolver... extra) {
        for (String line : lines) {
            audience.sendMessage(parse(line, extra));
        }
    }

    public void actionBar(Audience audience, String text, TagResolver... extra) {
        if (!text.isEmpty()) {
            audience.sendActionBar(parse(text, extra));
        }
    }

    public void title(Audience audience, String title, String subtitle, Title.Times times, TagResolver... extra) {
        if (!title.isEmpty() || !subtitle.isEmpty()) {
            audience.showTitle(Title.title(parse(title, extra), parse(subtitle, extra), times));
        }
    }

    public static Title.Times ticks(int fadeIn, int stay, int fadeOut) {
        return Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L),
                Duration.ofMillis(fadeOut * 50L));
    }

    public static void play(Audience audience, @Nullable SoundSpec sound) {
        if (sound != null) {
            sound.play(audience);
        }
    }

    public TextColor color(String name) {
        TextColor color = palette.get(name);
        return color != null ? color : DEFAULT_PALETTE.getOrDefault(name, TextColor.color(0xFFFFFF));
    }

    /** Tag resolver with palette, branding and links only, e.g. for the module tests. */
    public TagResolver baseTags() {
        return baseTags;
    }

    public static Component bold(Component component) {
        return component.decoration(TextDecoration.BOLD, true);
    }

    private static Map<String, TextColor> defaultPalette() {
        Map<String, TextColor> palette = new LinkedHashMap<>();
        palette.put("primary", TextColor.color(0xB98CFF));
        palette.put("secondary", TextColor.color(0x8DB6FF));
        palette.put("highlight", TextColor.color(0xF3EEFF));
        palette.put("text", TextColor.color(0xDCD3F5));
        palette.put("muted", TextColor.color(0x9C93BD));
        palette.put("subtle", TextColor.color(0x6F6890));
        palette.put("line", TextColor.color(0x4B4566));
        palette.put("good", TextColor.color(0x8EE8C0));
        palette.put("warn", TextColor.color(0xFFD48A));
        palette.put("bad", TextColor.color(0xFF8FA3));
        return Map.copyOf(palette);
    }
}
