package at.eepy.essentials.commands;

import at.eepy.essentials.EepyEssentials;
import at.eepy.essentials.core.Module;
import at.eepy.essentials.core.ModuleManager;
import at.eepy.essentials.modules.announcements.AnnouncementsModule;
import at.eepy.essentials.modules.chat.ChatModule;
import at.eepy.essentials.modules.deaths.DeathsModule;
import at.eepy.essentials.text.Formatter;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * All commands, built with Paper's Brigadier API. Commands of disabled modules
 * are hidden from players (the plugin resends the command tree when a module
 * is switched), and every command still checks its module when executed.
 */
public final class EssentialsCommands {

    private static final String ADMIN = "eepyessentials.admin";

    private final EepyEssentials plugin;

    public EssentialsCommands(EepyEssentials plugin) {
        this.plugin = plugin;
    }

    public void register(Commands registrar) {
        SuggestionProvider<CommandSourceStack> moduleIds = (ctx, builder) -> {
            String typed = builder.getRemainingLowerCase();
            plugin.modules().ids().stream().filter(id -> id.startsWith(typed)).forEach(builder::suggest);
            return builder.buildFuture();
        };

        registrar.register(Commands.literal("eepyessentials")
                .executes(this::info)
                .then(Commands.literal("help").executes(this::help))
                .then(Commands.literal("reload").requires(permission("eepyessentials.reload")).executes(this::reload))
                .then(Commands.literal("modules").requires(permission("eepyessentials.modules")).executes(this::modules))
                .then(Commands.literal("module").requires(permission("eepyessentials.modules"))
                        .then(Commands.literal("enable").then(Commands.argument("module", StringArgumentType.word())
                                .suggests(moduleIds).executes(ctx -> toggle(ctx, true))))
                        .then(Commands.literal("disable").then(Commands.argument("module", StringArgumentType.word())
                                .suggests(moduleIds).executes(ctx -> toggle(ctx, false)))))
                .then(Commands.literal("debug").requires(permission("eepyessentials.debug")).executes(this::debug))
                .build(), "EepyEssentials administration", List.of("eessentials"));

        registrar.register(Commands.literal("lastdeath")
                .requires(source -> moduleOn("deaths"))
                .executes(ctx -> lastDeath(ctx, null))
                .then(Commands.argument("player", ArgumentTypes.player())
                        .requires(permission("eepyessentials.death.location.others"))
                        .executes(ctx -> lastDeath(ctx, ctx.getArgument("player", PlayerSelectorArgumentResolver.class)
                                .resolve(ctx.getSource()).getFirst())))
                .build(), "Shows where you died last", List.of());

        registrar.register(Commands.literal("eepyannounce")
                .requires(permission("eepyessentials.announce").and(source -> moduleOn("announcements")))
                .then(Commands.argument("message", StringArgumentType.greedyString()).executes(this::announce))
                .build(), "Sends a server announcement", List.of("announce"));

        registrar.register(Commands.literal("eepychat")
                .requires(permission("eepyessentials.chat.clear").and(source -> moduleOn("chat")))
                .then(Commands.literal("clear").executes(this::clearChat))
                .build(), "Chat tools", List.of());

        Predicate<CommandSourceStack> pmAllowed = source -> {
            ChatModule chat = plugin.enabledModule(ChatModule.class);
            return chat != null && chat.privateMessagesEnabled() && source.getSender().hasPermission("eepyessentials.chat.pm");
        };
        registrar.register(Commands.literal("pm").requires(pmAllowed)
                .then(Commands.argument("player", ArgumentTypes.player())
                        .then(Commands.argument("message", StringArgumentType.greedyString()).executes(this::privateMessage)))
                .build(), "Sends a private message", List.of("dm"));
        registrar.register(Commands.literal("reply").requires(pmAllowed)
                .then(Commands.argument("message", StringArgumentType.greedyString()).executes(this::reply))
                .build(), "Replies to your last private message", List.of("r"));
    }

    // ── /eepyessentials ───────────────────────────────────────────────────

    private int info(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Formatter formatter = plugin.formatter();
        formatter.send(sender, "<prefix><text>EepyEssentials <primary><version></primary> <subtle>· quality of life for <server_name></subtle></text>",
                Placeholder.unparsed("version", plugin.getPluginMeta().getVersion()));
        formatter.send(sender, "<muted>Modules</muted> <text><enabled>/<total> active</text>  <line>·</line>  <muted>Website</muted> <website_link>  <line>·</line>  <muted>Discord</muted> <discord_link>",
                Placeholder.unparsed("enabled", Long.toString(plugin.modules().enabledCount())),
                Placeholder.unparsed("total", Integer.toString(plugin.modules().ids().size())));
        formatter.send(sender, "<subtle>Type</subtle> <text>/eepyessentials help</text> <subtle>for all commands.</subtle>");
        return Command.SINGLE_SUCCESS;
    }

    private int help(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        plugin.formatter().send(sender, "<prefix><highlight>Commands</highlight>");
        helpLine(sender, null, "/eepyessentials", "Plugin information");
        helpLine(sender, "eepyessentials.reload", "/eepyessentials reload", "Reload all configuration files");
        helpLine(sender, "eepyessentials.modules", "/eepyessentials modules", "Show all modules");
        helpLine(sender, "eepyessentials.modules", "/eepyessentials module enable|disable <name>", "Switch a module");
        helpLine(sender, "eepyessentials.debug", "/eepyessentials debug", "Technical information");
        if (moduleOn("deaths")) {
            helpLine(sender, null, "/lastdeath", "Where you died last");
        }
        if (moduleOn("announcements")) {
            helpLine(sender, "eepyessentials.announce", "/eepyannounce <message>", "Send an announcement");
        }
        if (moduleOn("chat")) {
            helpLine(sender, "eepyessentials.chat.clear", "/eepychat clear", "Clear the chat");
            ChatModule chat = plugin.enabledModule(ChatModule.class);
            if (chat != null && chat.privateMessagesEnabled()) {
                helpLine(sender, "eepyessentials.chat.pm", "/pm <player> <message>", "Private message");
                helpLine(sender, "eepyessentials.chat.pm", "/reply <message>", "Reply to a private message");
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        EepyEssentials.LoadResult result = plugin.loadAll();
        for (String error : result.errors()) {
            int colon = error.indexOf(": ");
            message(sender, "reload-failed",
                    Placeholder.unparsed("file", colon < 0 ? "a file" : error.substring(0, colon)),
                    Placeholder.unparsed("error", colon < 0 ? error : error.substring(colon + 2)));
            plugin.getLogger().warning(error);
        }
        message(sender, "reload-success",
                Placeholder.unparsed("ms", Long.toString(result.millis())),
                Placeholder.unparsed("enabled", Long.toString(plugin.modules().enabledCount())),
                Placeholder.unparsed("total", Integer.toString(plugin.modules().ids().size())));
        if (!result.warnings().isEmpty()) {
            message(sender, "reload-warnings", Placeholder.unparsed("count", Integer.toString(result.warnings().size())));
            result.warnings().forEach(warning -> plugin.getLogger().warning(warning));
            if (sender instanceof Player) {
                result.warnings().forEach(warning -> plugin.formatter().send(sender, "<subtle>• <warning></subtle>",
                        Placeholder.unparsed("warning", warning)));
            }
        }
        failedModules(sender);
        return Command.SINGLE_SUCCESS;
    }

    private int modules(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Formatter formatter = plugin.formatter();
        formatter.send(sender, "<prefix><highlight>Modules</highlight>");
        for (Module module : plugin.modules().modules()) {
            ModuleManager.Status status = plugin.modules().status(module.id());
            String state = switch (status.state()) {
                case ENABLED -> "<good>●</good>";
                case DISABLED -> "<subtle>○</subtle>";
                case FAILED -> "<bad>✖</bad>";
            };
            formatter.send(sender, state + " <text><title></text> <subtle>(<id>) · <description></subtle>",
                    Placeholder.unparsed("title", module.title()),
                    Placeholder.unparsed("id", module.id()),
                    Placeholder.unparsed("description", status.error() != null ? "failed: " + status.error() : module.description()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int toggle(CommandContext<CommandSourceStack> ctx, boolean enable) {
        CommandSender sender = ctx.getSource().getSender();
        String id = StringArgumentType.getString(ctx, "module").toLowerCase(Locale.ROOT);
        Module module = plugin.modules().get(id);
        if (module == null) {
            message(sender, "unknown-module", Placeholder.unparsed("module", id),
                    Placeholder.unparsed("modules", String.join(", ", plugin.modules().ids())));
            return 0;
        }
        if (plugin.modules().isEnabled(id) == enable) {
            message(sender, "module-already", Placeholder.unparsed("module", module.title()),
                    Placeholder.unparsed("state", enable ? "enabled" : "disabled"));
            return 0;
        }
        String error = plugin.setModuleEnabled(module, enable);
        if (error != null) {
            message(sender, "module-failed", Placeholder.unparsed("module", module.title()), Placeholder.unparsed("error", error));
            return 0;
        }
        message(sender, enable ? "module-enabled" : "module-disabled-now", Placeholder.unparsed("module", module.title()));
        return Command.SINGLE_SUCCESS;
    }

    private int debug(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Formatter formatter = plugin.formatter();
        formatter.send(sender, "<prefix><highlight>Debug</highlight>");
        row(sender, "Plugin", "EepyEssentials " + plugin.getPluginMeta().getVersion() + " · " + Bukkit.getName() + " "
                + Bukkit.getVersion() + " · Java " + Runtime.version());
        row(sender, "LuckPerms", plugin.luckPerms() == null ? "not installed"
                : plugin.luckPerms().version() + (plugin.usesLuckPerms() ? " (used)" : " (disabled in config.yml)"));
        for (Module module : plugin.modules().modules()) {
            ModuleManager.Status status = plugin.modules().status(module.id());
            String details = switch (status.state()) {
                case ENABLED -> module.status();
                case DISABLED -> "disabled";
                case FAILED -> "FAILED: " + status.error();
            };
            List<String> overlaps = plugin.overlappingPlugins(module.id());
            row(sender, module.id(), details + (overlaps.isEmpty() ? "" : " · overlaps with " + String.join(", ", overlaps)));
        }
        row(sender, "Config", plugin.warnings().size() + " warning(s)");
        return Command.SINGLE_SUCCESS;
    }

    // ── module commands ───────────────────────────────────────────────────

    private int lastDeath(CommandContext<CommandSourceStack> ctx, @Nullable Player target) {
        CommandSender sender = ctx.getSource().getSender();
        DeathsModule deaths = plugin.enabledModule(DeathsModule.class);
        if (deaths == null) {
            message(sender, "module-disabled", Placeholder.unparsed("module", "EepyDeaths"));
            return 0;
        }
        if (target == null) {
            if (!(sender instanceof Player player)) {
                message(sender, "player-only");
                return 0;
            }
            if (!player.hasPermission("eepyessentials.death.location")) {
                message(sender, "no-permission");
                return 0;
            }
            target = player;
        }
        deaths.showLastDeath(sender, target);
        return Command.SINGLE_SUCCESS;
    }

    private int announce(CommandContext<CommandSourceStack> ctx) {
        AnnouncementsModule announcements = plugin.enabledModule(AnnouncementsModule.class);
        if (announcements == null) {
            message(ctx.getSource().getSender(), "module-disabled", Placeholder.unparsed("module", "EepyAnnouncements"));
            return 0;
        }
        announcements.announceManual(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "message"));
        return Command.SINGLE_SUCCESS;
    }

    private int clearChat(CommandContext<CommandSourceStack> ctx) {
        ChatModule chat = plugin.enabledModule(ChatModule.class);
        if (chat == null) {
            message(ctx.getSource().getSender(), "module-disabled", Placeholder.unparsed("module", "EepyChat"));
            return 0;
        }
        chat.clear(ctx.getSource().getSender());
        return Command.SINGLE_SUCCESS;
    }

    private int privateMessage(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ChatModule chat = plugin.enabledModule(ChatModule.class);
        if (chat == null || !(ctx.getSource().getSender() instanceof Player from)) {
            message(ctx.getSource().getSender(), "player-only");
            return 0;
        }
        Player to = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();
        chat.privateMessage(from, to, StringArgumentType.getString(ctx, "message"));
        return Command.SINGLE_SUCCESS;
    }

    private int reply(CommandContext<CommandSourceStack> ctx) {
        ChatModule chat = plugin.enabledModule(ChatModule.class);
        if (chat == null || !(ctx.getSource().getSender() instanceof Player from)) {
            message(ctx.getSource().getSender(), "player-only");
            return 0;
        }
        chat.reply(from, StringArgumentType.getString(ctx, "message"));
        return Command.SINGLE_SUCCESS;
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private void failedModules(CommandSender sender) {
        for (Module module : plugin.modules().modules()) {
            ModuleManager.Status status = plugin.modules().status(module.id());
            if (status.state() == ModuleManager.State.FAILED) {
                message(sender, "module-failed", Placeholder.unparsed("module", module.title()),
                        Placeholder.unparsed("error", String.valueOf(status.error())));
            }
        }
    }

    private boolean moduleOn(String id) {
        return plugin.modules().isEnabled(id);
    }

    private void message(CommandSender sender, String key, TagResolver... tags) {
        plugin.formatter().send(sender, plugin.messages().string(key, ""), tags);
    }

    private void helpLine(CommandSender sender, @Nullable String permission, String usage, String description) {
        if (permission == null || allowed(sender, permission)) {
            plugin.formatter().send(sender, "<primary><usage></primary> <line>›</line> <text><description></text>",
                    Placeholder.unparsed("usage", usage), Placeholder.unparsed("description", description));
        }
    }

    private void row(CommandSender sender, String label, String value) {
        plugin.formatter().send(sender, "<muted><label></muted> <line>›</line> <text><value></text>",
                Placeholder.unparsed("label", String.format(Locale.ROOT, "%-13s", label)),
                Placeholder.unparsed("value", value));
    }

    private static Predicate<CommandSourceStack> permission(String node) {
        return source -> allowed(source.getSender(), node);
    }

    private static boolean allowed(CommandSender sender, String node) {
        return sender.hasPermission(node) || sender.hasPermission(ADMIN);
    }
}
