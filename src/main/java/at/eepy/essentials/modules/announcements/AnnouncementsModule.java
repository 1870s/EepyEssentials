package at.eepy.essentials.modules.announcements;

import at.eepy.essentials.config.Settings;
import at.eepy.essentials.core.AbstractModule;
import at.eepy.essentials.text.Formatter;
import at.eepy.essentials.text.SoundSpec;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * EepyAnnouncements: calm automatic announcements plus /eepyannounce.
 * <p>
 * One task runs every {@code interval-minutes}, starting only after
 * {@code initial-delay-minutes}, so a restart never causes a burst of messages.
 * While fewer than {@code min-players} are online the turn is skipped without
 * advancing the rotation.
 */
public final class AnnouncementsModule extends AbstractModule {

    private AnnouncementPicker picker;
    private int minPlayers;
    private @Nullable SoundSpec sound;
    private List<String> manualFormat;
    private @Nullable SoundSpec manualSound;
    private ZoneId zone;
    private int count;
    private long intervalMinutes;
    private @Nullable String lastSent;

    @Override
    public String id() {
        return "announcements";
    }

    @Override
    public String title() {
        return "EepyAnnouncements";
    }

    @Override
    public String description() {
        return "Automatic and manual server announcements";
    }

    @Override
    protected void start() {
        Settings settings = context.settings();
        intervalMinutes = settings.integer("interval-minutes", 15, 3, 24 * 60);
        int initialDelay = settings.integer("initial-delay-minutes", 10, 1, 24 * 60);
        minPlayers = settings.integer("min-players", 1, 1, 10_000);
        sound = settings.sound("sound");
        manualFormat = settings.stringList("manual");
        manualSound = settings.sound("manual-sound");
        String zoneId = settings.string("timezone", "Europe/Vienna");
        try {
            zone = ZoneId.of(zoneId);
        } catch (DateTimeException e) {
            settings.warn("timezone", "'" + zoneId + "' is not a time zone, using the server's");
            zone = ZoneId.systemDefault();
        }
        List<Announcement> announcements = parse(settings.section("announcements"));
        picker = new AnnouncementPicker(announcements,
                settings.enumValue("order", AnnouncementPicker.Order.class, AnnouncementPicker.Order.RANDOM), new Random());
        if (!announcements.isEmpty()) {
            repeat(this::announceNext, initialDelay * 1200L, intervalMinutes * 1200L);
        }
    }

    private static List<Announcement> parse(Settings section) {
        List<Announcement> announcements = new ArrayList<>();
        for (String id : section.keys()) {
            Settings entry = section.section(id);
            if (!entry.bool("enabled", true)) {
                continue;
            }
            List<String> lines = entry.stringList("lines");
            String actionbar = entry.string("actionbar", "");
            String title = entry.string("title", "");
            if (lines.isEmpty() && actionbar.isEmpty() && title.isEmpty()) {
                entry.warn("lines", "is empty, announcement ignored");
                continue;
            }
            Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
            days.addAll(entry.enumList("days", DayOfWeek.class, List.of()));
            LocalTime from = time(entry, "from");
            LocalTime to = time(entry, "to");
            if ((from == null) != (to == null)) {
                entry.warn("from", "needs both 'from' and 'to', time window ignored");
                from = null;
                to = null;
            }
            announcements.add(new Announcement(id, lines, actionbar, title, entry.string("subtitle", ""),
                    entry.optionalString("permission"), Set.copyOf(entry.stringList("worlds")), Set.copyOf(days), from, to));
        }
        return announcements;
    }

    private static @Nullable LocalTime time(Settings entry, String key) {
        String raw = entry.optionalString(key);
        if (raw == null) {
            return null;
        }
        try {
            return LocalTime.parse(raw.trim());
        } catch (DateTimeParseException e) {
            entry.warn(key, "'" + raw + "' is not a time (use HH:mm)");
            return null;
        }
    }

    private void announceNext() {
        if (Bukkit.getOnlinePlayers().size() < minPlayers) {
            return;
        }
        Announcement announcement = picker.next(LocalDateTime.now(zone));
        if (announcement == null) {
            return;
        }
        Formatter formatter = context.formatter();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (announcement.permission() != null && !player.hasPermission(announcement.permission())) {
                continue;
            }
            if (!announcement.worlds().isEmpty() && !announcement.worlds().contains(player.getWorld().getName())) {
                continue;
            }
            formatter.sendLines(player, announcement.lines());
            formatter.actionBar(player, announcement.actionbar());
            formatter.title(player, announcement.title(), announcement.subtitle(), Formatter.ticks(10, 60, 20));
            Formatter.play(player, sound);
        }
        count++;
        lastSent = announcement.id();
    }

    /** /eepyannounce: the message may use MiniMessage, only staff can send it. */
    public void announceManual(CommandSender sender, String message) {
        TagResolver tags = TagResolver.resolver(
                Placeholder.component("message", context.formatter().parse(message)),
                Placeholder.unparsed("player", sender.getName()));
        Formatter formatter = context.formatter();
        for (Player player : Bukkit.getOnlinePlayers()) {
            formatter.sendLines(player, manualFormat, tags);
            Formatter.play(player, manualSound);
        }
        formatter.sendLines(Bukkit.getConsoleSender(), manualFormat, tags);
    }

    @Override
    public String status() {
        return "every " + intervalMinutes + " min · min players " + minPlayers + " · " + count + " sent"
                + (lastSent != null ? " (last: " + lastSent + ")" : "") + " · " + activeTasks() + " task";
    }
}
