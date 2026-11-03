package at.eepy.essentials.modules.chat;

import at.eepy.essentials.config.Settings;
import at.eepy.essentials.core.AbstractModule;
import at.eepy.essentials.text.Formatter;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * EepyChat: rank-based chat design, anti-spam, chat clear and optional private
 * messages.
 * <p>
 * Thread-safety: chat events are asynchronous. Everything they need about a
 * player (rank, permissions, world) is computed on the main thread and kept in
 * {@link #profiles}; the async renderer only reads those snapshots.
 * <p>
 * Chat signing: the message content ({@code event.message()}) is never changed.
 * Only Paper's renderer is replaced, which decides how the line looks.
 */
public final class ChatModule extends AbstractModule {

    /** Main-thread snapshot of what the async chat code needs. */
    record Profile(ChatRanks.Rank rank, boolean canFormat, boolean bypassSpam, String world) {
    }

    enum NameClick { SUGGEST_MESSAGE, NONE }

    private static final Pattern URL = Pattern.compile(
            "(?i)\\b(?:https?://)?(?:[a-z0-9-]+\\.)+[a-z]{2,24}(?:/[^\\s]*)?");
    private static final MiniMessage PLAYER_FORMAT = MiniMessage.builder()
            .tags(TagResolver.resolver(StandardTags.color(), StandardTags.decorations(), StandardTags.gradient(),
                    StandardTags.reset()))
            .build();

    private final Map<UUID, Profile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> replyTargets = new ConcurrentHashMap<>();
    private ChatRanks ranks;
    private String format;
    private List<String> nameHover;
    private NameClick nameClick;
    private boolean clickableLinks;
    private String formatPermission;
    private @Nullable AntiSpam antiSpam;
    private String bypassPermission;
    private String tooFast;
    private String repeated;
    private int clearLines;
    private String clearBypass;
    private String clearMessage;
    private boolean privateMessages;
    private String pmToSender;
    private String pmToTarget;
    private String pmNoReply;
    private @Nullable Runnable unsubscribe;

    @Override
    public String id() {
        return "chat";
    }

    @Override
    public String title() {
        return "EepyChat";
    }

    @Override
    public String description() {
        return "Chat design with ranks, anti-spam, chat clear, private messages";
    }

    @Override
    protected void start() {
        Settings settings = context.settings();
        format = settings.string("format", "<rank_prefix><name><rank_suffix> <line>›</line> <message>");
        Settings name = settings.section("name");
        nameHover = name.stringList("hover");
        nameClick = name.enumValue("click", NameClick.class, NameClick.SUGGEST_MESSAGE);
        clickableLinks = settings.bool("clickable-links", true);
        formatPermission = settings.string("format-permission", "eepyessentials.chat.format");
        ranks = new ChatRanks(settings.section("ranks"), context.formatter());
        ranks.registerPermissions(Bukkit.getPluginManager());

        Settings spam = settings.section("anti-spam");
        antiSpam = spam.bool("enabled", true)
                ? new AntiSpam(spam.integer("cooldown-ms", 1000, 0, 60_000), spam.integer("repeat-seconds", 15, 0, 3600) * 1000L)
                : null;
        bypassPermission = spam.string("bypass-permission", "eepyessentials.chat.bypass");
        tooFast = spam.string("too-fast", "");
        repeated = spam.string("repeated", "");

        Settings clear = settings.section("clear");
        clearLines = clear.integer("lines", 100, 1, 500);
        clearBypass = clear.string("bypass-permission", "eepyessentials.chat.clear.bypass");
        clearMessage = clear.string("message", "");

        Settings pm = settings.section("private-messages");
        privateMessages = pm.bool("enabled", false);
        pmToSender = pm.string("to-sender", "");
        pmToTarget = pm.string("to-target", "");
        pmNoReply = pm.string("no-reply", "");

        Bukkit.getOnlinePlayers().forEach(this::refresh);
        listen();
        listen(AsyncChatEvent.class, settings.enumValue("listener-priority", EventPriority.class, EventPriority.NORMAL),
                true, this::onChat);
        // LuckPerms changes arrive on another thread; refresh on the main thread.
        unsubscribe = context.permissionChanges().subscribe(uuid -> Bukkit.getScheduler().runTask(context.plugin(), () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                refresh(player);
            }
        }));
        // Plain permission plugins have no change events; refresh once a minute.
        repeat(() -> Bukkit.getOnlinePlayers().forEach(this::refresh), 1200L, 1200L);
    }

    @Override
    protected void stop() {
        if (unsubscribe != null) {
            unsubscribe.run();
            unsubscribe = null;
        }
        if (ranks != null) {
            ranks.unregisterPermissions(Bukkit.getPluginManager());
        }
        profiles.clear();
        replyTargets.clear();
    }

    // ── profiles (main thread) ────────────────────────────────────────────

    private void refresh(Player player) {
        profiles.put(player.getUniqueId(), new Profile(
                ranks.resolve(player, context.luckPerms()),
                player.hasPermission(formatPermission),
                player.hasPermission(bypassPermission),
                player.getWorld().getName()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        profiles.remove(uuid);
        replyTargets.remove(uuid);
        replyTargets.values().removeIf(uuid::equals);
        if (antiSpam != null) {
            antiSpam.forget(uuid);
        }
    }

    // ── chat (async) ──────────────────────────────────────────────────────

    private void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Profile profile = profile(player);
        if (antiSpam != null && !profile.bypassSpam()) {
            String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
            AntiSpam.Result result = antiSpam.check(player.getUniqueId(), plain, System.currentTimeMillis());
            if (result != AntiSpam.Result.OK) {
                event.setCancelled(true);
                context.formatter().send(player, result == AntiSpam.Result.TOO_FAST ? tooFast : repeated);
                return;
            }
        }
        event.renderer(ChatRenderer.viewerUnaware((source, displayName, message) -> render(source, profile, message)));
    }

    Component render(Player source, Profile profile, Component message) {
        ChatRanks.Rank rank = profile.rank();
        Formatter formatter = context.formatter();
        TagResolver playerTags = TagResolver.resolver(
                Placeholder.unparsed("player", source.getName()),
                Placeholder.unparsed("rank", rank.displayName()),
                Placeholder.unparsed("world", profile.world()));

        Component name = Component.text(source.getName(), rank.nameColor() != null ? rank.nameColor() : formatter.color("highlight"));
        if (!nameHover.isEmpty()) {
            name = name.hoverEvent(HoverEvent.showText(formatter.parseLines(nameHover, playerTags)));
        }
        if (nameClick == NameClick.SUGGEST_MESSAGE) {
            name = name.clickEvent(ClickEvent.suggestCommand("/msg " + source.getName() + " "));
        }

        Component body = profile.canFormat()
                ? PLAYER_FORMAT.deserialize(PlainTextComponentSerializer.plainText().serialize(message))
                : message;
        if (clickableLinks) {
            body = linkify(body);
        }
        TextColor messageColor = rank.messageColor() != null ? rank.messageColor() : formatter.color("text");
        body = body.colorIfAbsent(messageColor);

        return formatter.parse(format, playerTags,
                Placeholder.component("rank_prefix", rank.prefix()),
                Placeholder.component("rank_suffix", rank.suffix()),
                Placeholder.component("name", name),
                Placeholder.component("message", body));
    }

    private Component linkify(Component message) {
        return message.replaceText(TextReplacementConfig.builder().match(URL).replacement((match, builder) -> {
            String url = match.group();
            String target = url.regionMatches(true, 0, "http", 0, 4) ? url : "https://" + url;
            return builder.clickEvent(ClickEvent.openUrl(target))
                    .hoverEvent(HoverEvent.showText(Component.text("Open " + target, context.formatter().color("muted"))))
                    .color(context.formatter().color("secondary"));
        }).build());
    }

    private Profile profile(Player player) {
        Profile profile = profiles.get(player.getUniqueId());
        // Only right after a reload; the next refresh fills in the real rank.
        return profile != null ? profile : new Profile(ranks.defaultRank(), false, false, "");
    }

    // ── commands ──────────────────────────────────────────────────────────

    public void clear(CommandSender sender) {
        Component empty = Component.empty();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission(clearBypass)) {
                for (int i = 0; i < clearLines; i++) {
                    player.sendMessage(empty);
                }
            }
        }
        Bukkit.getServer().sendMessage(context.formatter().parse(clearMessage,
                Placeholder.unparsed("player", sender.getName())));
    }

    public boolean privateMessagesEnabled() {
        return privateMessages;
    }

    public void privateMessage(Player from, Player to, String message) {
        TagResolver tags = TagResolver.resolver(
                Placeholder.unparsed("player", from.getName()),
                Placeholder.unparsed("target", to.getName()),
                Placeholder.unparsed("message", message));
        context.formatter().send(from, pmToSender, tags);
        context.formatter().send(to, pmToTarget, tags);
        replyTargets.put(to.getUniqueId(), from.getUniqueId());
        replyTargets.put(from.getUniqueId(), to.getUniqueId());
    }

    public void reply(Player from, String message) {
        UUID target = replyTargets.get(from.getUniqueId());
        Player to = target == null ? null : Bukkit.getPlayer(target);
        if (to == null) {
            context.formatter().send(from, pmNoReply);
            return;
        }
        privateMessage(from, to, message);
    }

    @Override
    public String status() {
        return ranks.size() + " ranks · " + profiles.size() + " cached profiles · anti-spam "
                + (antiSpam != null ? "on" : "off") + " · private messages " + (privateMessages ? "on" : "off")
                + " · LuckPerms " + (context.luckPerms() != null ? "used" : "not used");
    }
}
