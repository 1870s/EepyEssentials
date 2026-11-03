package at.eepy.essentials.modules.sleep;

import at.eepy.essentials.config.Settings;
import at.eepy.essentials.core.AbstractModule;
import at.eepy.essentials.text.Formatter;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ClockTimeSkipEvent;
import org.bukkit.event.world.TimeSkipEvent;

import java.util.*;

/**
 * EepySleep: shows how many players are sleeping and how many are needed.
 * <p>
 * Skipping the night stays completely vanilla (game rule
 * {@code playersSleepingPercentage}); this module only displays the progress
 * and, if explicitly configured, sets that game rule. Display updates are
 * event driven (bed enter/leave, quit, world/game mode change), no polling.
 */
public final class SleepModule extends AbstractModule {

    enum Display { BOSSBAR, ACTIONBAR, CHAT }

    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Set<UUID> pendingWorlds = new HashSet<>();
    /** Game rule values before this module changed them, restored on stop. */
    private final Map<UUID, Integer> originalPercentage = new HashMap<>();
    private Set<Display> displays;
    private String bossbarText;
    private BossBar.Color bossbarColor;
    private BossBar.Overlay bossbarStyle;
    private String actionbarText;
    private String chatText;
    private String bedEnter;
    private String bedLeave;
    private String nightSkipped;
    private Set<String> disabledWorlds;

    @Override
    public String id() {
        return "sleep";
    }

    @Override
    public String title() {
        return "EepySleep";
    }

    @Override
    public String description() {
        return "Sleep progress display (vanilla night skipping)";
    }

    @Override
    protected void start() {
        Settings settings = context.settings();
        displays = EnumSet.noneOf(Display.class);
        displays.addAll(settings.enumList("display", Display.class, List.of(Display.BOSSBAR)));
        Settings bossbar = settings.section("bossbar");
        bossbarText = bossbar.string("text", "<sleeping>/<needed> players sleeping");
        bossbarColor = bossbar.enumValue("color", BossBar.Color.class, BossBar.Color.PURPLE);
        bossbarStyle = bossbarOverlay(bossbar.string("style", "PROGRESS"), bossbar);
        actionbarText = settings.string("actionbar", "");
        chatText = settings.string("chat", "");
        Settings messages = settings.section("messages");
        bedEnter = messages.string("bed-enter", "");
        bedLeave = messages.string("bed-leave", "");
        nightSkipped = messages.string("night-skipped", "");
        disabledWorlds = new HashSet<>(settings.stringList("disabled-worlds"));
        applyGameRules(settings.section("percentage"));
        listen();
    }

    @Override
    protected void stop() {
        bossBars.forEach((world, bar) -> Bukkit.getOnlinePlayers().forEach(player -> player.hideBossBar(bar)));
        bossBars.clear();
        pendingWorlds.clear();
        originalPercentage.forEach((uuid, value) -> {
            World world = Bukkit.getWorld(uuid);
            if (world != null) {
                world.setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, value);
            }
        });
        originalPercentage.clear();
    }

    /** Optional gameplay change, only when configured (default -1 = untouched). */
    private void applyGameRules(Settings percentage) {
        int fallback = percentage.integer("default", -1, -1, 1000);
        Settings perWorld = percentage.section("worlds");
        for (World world : Bukkit.getWorlds()) {
            int value = perWorld.contains(world.getName())
                    ? perWorld.integer(world.getName(), fallback, -1, 1000) : fallback;
            if (value < 0) {
                continue;
            }
            Integer current = world.getGameRuleValue(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
            if (current != null && current != value) {
                originalPercentage.put(world.getUID(), current);
                world.setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, value);
                context.plugin().getLogger().info("EepySleep: playersSleepingPercentage in " + world.getName()
                        + " set to " + value + "% (was " + current + "%)");
            }
        }
    }

    public static SleepCount count(World world) {
        int sleeping = 0;
        int eligible = 0;
        for (Player player : world.getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || player.isSleepingIgnored()) {
                continue;
            }
            eligible++;
            if (player.isSleeping()) {
                sleeping++;
            }
        }
        Integer percentage = world.getGameRuleValue(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
        return new SleepCount(sleeping, eligible, percentage == null ? 100 : percentage);
    }

    // ── events ────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();
        // The player is only marked as sleeping after the event, and only if entering the bed
        // worked (night, no monsters nearby). So check and count on the next tick.
        later(() -> {
            if (player.isOnline() && player.isSleeping()) {
                World world = player.getWorld();
                if (enabledIn(world)) {
                    sendToWorld(world, bedEnter, tags(player.getName(), count(world), world));
                }
                update(world);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBedLeave(PlayerBedLeaveEvent event) {
        World world = event.getPlayer().getWorld();
        if (enabledIn(world) && !bedLeave.isEmpty() && world.getTime() > 12_000) {
            later(() -> sendToWorld(world, bedLeave, tags(event.getPlayer().getName(), count(world), world)), 1L);
        }
        scheduleUpdate(world);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        bossBars.values().forEach(event.getPlayer()::hideBossBar);
        scheduleUpdate(event.getPlayer().getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        BossBar old = bossBars.get(event.getFrom().getUID());
        if (old != null) {
            event.getPlayer().hideBossBar(old);
        }
        scheduleUpdate(event.getFrom());
        scheduleUpdate(event.getPlayer().getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        scheduleUpdate(event.getPlayer().getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTimeSkip(TimeSkipEvent event) {
        if (event.getSkipReason() != ClockTimeSkipEvent.SkipReason.NIGHT_SKIP) {
            return;
        }
        World world = event.getWorld();
        hide(world);
        if (enabledIn(world)) {
            sendToWorld(world, nightSkipped, tags("", count(world), world));
        }
    }

    // ── display ───────────────────────────────────────────────────────────

    private void scheduleUpdate(World world) {
        if (pendingWorlds.add(world.getUID())) {
            later(() -> {
                pendingWorlds.remove(world.getUID());
                update(world);
            }, 1L);
        }
    }

    private void update(World world) {
        SleepCount count = count(world);
        if (!enabledIn(world) || count.sleeping() == 0) {
            hide(world);
            return;
        }
        TagResolver tags = tags("", count, world);
        Formatter formatter = context.formatter();
        if (displays.contains(Display.BOSSBAR)) {
            BossBar bar = bossBars.computeIfAbsent(world.getUID(),
                    uuid -> BossBar.bossBar(formatter.parse(bossbarText, tags), 0f, bossbarColor, bossbarStyle));
            bar.name(formatter.parse(bossbarText, tags));
            bar.progress(count.progress());
            world.getPlayers().forEach(player -> player.showBossBar(bar));
        }
        if (displays.contains(Display.ACTIONBAR)) {
            world.getPlayers().forEach(player -> formatter.actionBar(player, actionbarText, tags));
        }
        if (displays.contains(Display.CHAT)) {
            sendToWorld(world, chatText, tags);
        }
    }

    private void hide(World world) {
        BossBar bar = bossBars.remove(world.getUID());
        if (bar != null) {
            Bukkit.getOnlinePlayers().forEach(player -> player.hideBossBar(bar));
        }
    }

    private void sendToWorld(World world, String text, TagResolver tags) {
        if (!text.isEmpty()) {
            world.sendMessage(context.formatter().parse(text, tags));
        }
    }

    private boolean enabledIn(World world) {
        return !disabledWorlds.contains(world.getName());
    }

    static TagResolver tags(String player, SleepCount count, World world) {
        return TagResolver.resolver(
                Placeholder.unparsed("player", player),
                Placeholder.unparsed("sleeping", Integer.toString(count.sleeping())),
                Placeholder.unparsed("needed", Integer.toString(count.needed())),
                Placeholder.unparsed("eligible", Integer.toString(count.eligible())),
                Placeholder.unparsed("percentage", Integer.toString(count.percentage())),
                Placeholder.unparsed("world", world.getName()));
    }

    private static BossBar.Overlay bossbarOverlay(String raw, Settings settings) {
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "PROGRESS" -> BossBar.Overlay.PROGRESS;
            case "NOTCHED_6" -> BossBar.Overlay.NOTCHED_6;
            case "NOTCHED_10" -> BossBar.Overlay.NOTCHED_10;
            case "NOTCHED_12" -> BossBar.Overlay.NOTCHED_12;
            case "NOTCHED_20" -> BossBar.Overlay.NOTCHED_20;
            default -> {
                settings.warn("style", "'" + raw + "' is not a boss bar style, using PROGRESS");
                yield BossBar.Overlay.PROGRESS;
            }
        };
    }

    @Override
    public String status() {
        StringBuilder worlds = new StringBuilder();
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) {
                continue;
            }
            SleepCount count = count(world);
            worlds.append(world.getName()).append(' ').append(count.sleeping()).append('/').append(count.needed())
                    .append(" (").append(count.percentage()).append("%) ");
        }
        return "display " + displays + " · " + worlds.toString().trim()
                + (originalPercentage.isEmpty() ? " · game rule untouched" : " · game rule set by config");
    }
}
