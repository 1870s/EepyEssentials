package at.eepy.essentials.modules.achievements;

import at.eepy.essentials.config.Settings;
import at.eepy.essentials.core.AbstractModule;
import at.eepy.essentials.text.Formatter;
import at.eepy.essentials.text.SoundSpec;
import io.papermc.paper.advancement.AdvancementDisplay;
import io.papermc.paper.advancement.AdvancementDisplay.Frame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * EepyAchievements: restyles the chat announcement of advancements.
 * <p>
 * It only replaces {@link PlayerAdvancementDoneEvent#message()}, so the
 * advancement itself, toasts and rewards are untouched and there is exactly
 * one message. If vanilla would not announce an advancement (message is
 * {@code null}: hidden display, recipes, gamerule announceAdvancements=false
 * or another plugin), nothing is announced here either.
 */
public final class AchievementsModule extends AbstractModule {

    record Personal(String title, String subtitle, String actionbar, @Nullable SoundSpec sound) {
    }

    private boolean publicMessages;
    private final Map<Frame, String> formats = new EnumMap<>(Frame.class);
    private final Map<Frame, TextColor> colors = new EnumMap<>(Frame.class);
    private final Map<Frame, Personal> personal = new EnumMap<>(Frame.class);
    private @Nullable SoundSpec challengeBroadcast;
    private final Set<String> excluded = new HashSet<>();
    private int announced;

    @Override
    public String id() {
        return "achievements";
    }

    @Override
    public String title() {
        return "EepyAchievements";
    }

    @Override
    public String description() {
        return "Styled advancement messages";
    }

    @Override
    protected void start() {
        Settings settings = context.settings();
        publicMessages = settings.bool("public", true);
        Settings formatSection = settings.section("formats");
        Settings colorSection = settings.section("colors");
        Settings personalSection = settings.section("personal");
        Map<Frame, TextColor> defaults = Map.of(Frame.TASK, TextColor.color(0xB98CFF),
                Frame.GOAL, TextColor.color(0x8DB6FF), Frame.CHALLENGE, TextColor.color(0xFFD48A));
        formats.clear();
        colors.clear();
        personal.clear();
        for (Frame frame : Frame.values()) {
            String key = frame.name().toLowerCase(Locale.ROOT);
            formats.put(frame, formatSection.string(key, "<highlight><player></highlight> <advancement>"));
            colors.put(frame, colorSection.color(key, defaults.get(frame)));
            Settings feedback = personalSection.section(key);
            personal.put(frame, new Personal(feedback.string("title", ""), feedback.string("subtitle", ""),
                    feedback.string("actionbar", ""), feedback.sound("sound")));
        }
        challengeBroadcast = settings.sound("challenge-broadcast-sound");
        excluded.clear();
        settings.stringList("excluded").forEach(key -> excluded.add(key.toLowerCase(Locale.ROOT)));
        listen();
    }

    // HIGH: after plugins that cancel/modify the message at normal priority.
    @EventHandler(priority = EventPriority.HIGH)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        AdvancementDisplay display = event.getAdvancement().getDisplay();
        if (display == null || event.message() == null) {
            return; // vanilla would not announce it either
        }
        Player player = event.getPlayer();
        Frame frame = display.frame();
        Formatter formatter = context.formatter();
        TagResolver tags = tags(player, display, frame);

        if (!publicMessages || excluded.contains(event.getAdvancement().getKey().asString())) {
            event.message(null);
        } else {
            event.message(formatter.parse(formats.get(frame), tags));
            announced++;
            if (frame == Frame.CHALLENGE && challengeBroadcast != null) {
                Bukkit.getOnlinePlayers().forEach(challengeBroadcast::play);
            }
        }

        Personal feedback = personal.get(frame);
        formatter.title(player, feedback.title(), feedback.subtitle(), Formatter.ticks(10, 50, 20), tags);
        formatter.actionBar(player, feedback.actionbar(), tags);
        Formatter.play(player, feedback.sound());
    }

    TagResolver tags(Player player, AdvancementDisplay display, Frame frame) {
        TextColor color = colors.get(frame);
        Component title = display.title().colorIfAbsent(color);
        Component description = display.description().colorIfAbsent(context.formatter().color("muted"));
        Component advancement = Component.text().color(color)
                .append(Component.text("["), title, Component.text("]"))
                .hoverEvent(HoverEvent.showText(Component.text().append(title, Component.newline(), description).build()))
                .build();
        return TagResolver.resolver(
                Placeholder.unparsed("player", player.getName()),
                Placeholder.component("advancement", advancement),
                Placeholder.component("advancement_plain", display.title()),
                Placeholder.component("description", description));
    }

    @Override
    public String status() {
        return "public " + (publicMessages ? "on" : "off") + " · " + excluded.size() + " excluded · "
                + announced + " announced since start";
    }
}
