package at.eepy.essentials.modules.welcome;

import at.eepy.essentials.config.Settings;
import at.eepy.essentials.core.AbstractModule;
import at.eepy.essentials.modules.Vanish;
import at.eepy.essentials.text.Formatter;
import at.eepy.essentials.text.SoundSpec;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * EepyWelcome: replaces the vanilla join/leave lines with styled ones and
 * greets the joining player privately (chat, title, action bar, sound).
 * <p>
 * The vanilla message is replaced on the same event, so there is never a
 * duplicate. Vanished players get no public message.
 */
public final class WelcomeModule extends AbstractModule {

    /** A permission-specific public message. */
    record GroupMessage(String permission, String message) {
    }

    /** Private greeting; empty texts are skipped. */
    record Personal(List<String> chat, String title, String subtitle, String actionbar, @Nullable SoundSpec sound) {
    }

    private boolean joinEnabled;
    private boolean joinReplace;
    private String firstJoin;
    private String returning;
    private List<GroupMessage> joinGroups;
    private boolean personalEnabled;
    private int personalDelay;
    private Personal personalFirst;
    private Personal personalReturning;
    private Title.Times titleTimes;
    private boolean leaveEnabled;
    private boolean leaveReplace;
    private String leaveMessage;
    private List<GroupMessage> leaveGroups;
    private boolean hideVanished;

    @Override
    public String id() {
        return "welcome";
    }

    @Override
    public String title() {
        return "EepyWelcome";
    }

    @Override
    public String description() {
        return "Join and leave messages, personal welcome";
    }

    @Override
    protected void start() {
        Settings settings = context.settings();
        Settings join = settings.section("join");
        Settings publicJoin = join.section("public");
        joinEnabled = publicJoin.bool("enabled", true);
        joinReplace = publicJoin.bool("replace-vanilla", true);
        firstJoin = publicJoin.string("first-join", "");
        returning = publicJoin.string("returning", "");
        joinGroups = groups(publicJoin, "groups");

        Settings personal = join.section("personal");
        personalEnabled = personal.bool("enabled", true);
        personalDelay = personal.integer("delay-ticks", 30, 0, 1200);
        personalFirst = personal(personal.section("first-join"));
        personalReturning = personal(personal.section("returning"));
        titleTimes = Formatter.ticks(personal.integer("title-fade-in", 10, 0, 200),
                personal.integer("title-stay", 50, 0, 400), personal.integer("title-fade-out", 20, 0, 200));

        Settings leave = settings.section("leave");
        leaveEnabled = leave.bool("enabled", true);
        leaveReplace = leave.bool("replace-vanilla", true);
        leaveMessage = leave.string("message", "");
        leaveGroups = groups(leave, "groups");
        hideVanished = settings.bool("hide-vanished", true);
        listen();
    }

    private static Personal personal(Settings section) {
        return new Personal(section.stringList("chat"), section.string("title", ""), section.string("subtitle", ""),
                section.string("actionbar", ""), section.sound("sound"));
    }

    private static List<GroupMessage> groups(Settings section, String key) {
        List<GroupMessage> groups = new ArrayList<>();
        int index = 0;
        for (Map<?, ?> entry : section.mapList(key)) {
            Object permission = entry.get("permission");
            Object message = entry.get("message");
            if (permission == null || message == null) {
                section.warn(key + "[" + index + "]", "needs 'permission' and 'message', ignored");
            } else {
                groups.add(new GroupMessage(permission.toString(), message.toString()));
            }
            index++;
        }
        return List.copyOf(groups);
    }

    // HIGH: after most plugins set their own join message, before vanish plugins (HIGHEST) hide it.
    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean first = !player.hasPlayedBefore();
        TagResolver tags = tags(player);
        boolean hidden = hideVanished && Vanish.isVanished(player);

        if (!joinEnabled || hidden) {
            event.joinMessage(null);
        } else if (joinReplace) {
            String template = first ? firstJoin : groupMessage(player, joinGroups, returning);
            event.joinMessage(template.isEmpty() ? null : context.formatter().parse(template, tags));
        }

        if (personalEnabled) {
            Personal greeting = first ? personalFirst : personalReturning;
            later(() -> {
                if (player.isOnline()) {
                    greet(player, greeting);
                }
            }, personalDelay);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!leaveEnabled || (hideVanished && Vanish.isVanished(player))) {
            event.quitMessage(null);
        } else if (leaveReplace) {
            String template = groupMessage(player, leaveGroups, leaveMessage);
            event.quitMessage(template.isEmpty() ? null : context.formatter().parse(template, tags(player)));
        }
    }

    private void greet(Player player, Personal greeting) {
        Formatter formatter = context.formatter();
        TagResolver tags = tags(player);
        formatter.sendLines(player, greeting.chat(), tags);
        formatter.title(player, greeting.title(), greeting.subtitle(), titleTimes, tags);
        formatter.actionBar(player, greeting.actionbar(), tags);
        Formatter.play(player, greeting.sound());
    }

    private static String groupMessage(Player player, List<GroupMessage> groups, String fallback) {
        for (GroupMessage group : groups) {
            if (player.hasPermission(group.permission())) {
                return group.message();
            }
        }
        return fallback;
    }

    private TagResolver tags(Player player) {
        return TagResolver.resolver(
                Placeholder.unparsed("player", player.getName()),
                Placeholder.component("displayname", player.displayName()),
                Placeholder.unparsed("online", Integer.toString(visibleOnline(player))),
                Placeholder.unparsed("max", Integer.toString(player.getServer().getMaxPlayers())));
    }

    /** Online count without vanished players (except the player themself). */
    private int visibleOnline(Player self) {
        int count = 0;
        for (Player player : self.getServer().getOnlinePlayers()) {
            if (player.equals(self) || !Vanish.isVanished(player)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public String status() {
        return "public join " + (joinEnabled ? (joinReplace ? "styled" : "vanilla") : "off")
                + " · personal " + (personalEnabled ? "on" : "off")
                + " · leave " + (leaveEnabled ? (leaveReplace ? "styled" : "vanilla") : "off");
    }
}
