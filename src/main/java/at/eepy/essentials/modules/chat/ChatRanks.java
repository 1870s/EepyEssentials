package at.eepy.essentials.modules.chat;

import at.eepy.essentials.config.Settings;
import at.eepy.essentials.integrations.LuckPermsHook;
import at.eepy.essentials.text.Formatter;
import at.eepy.essentials.text.LegacyText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Chat ranks from chat.yml, matched to LuckPerms groups or (without LuckPerms)
 * to {@code eepyessentials.rank.<id>} permissions.
 * <p>
 * Only one prefix source is used per player, so a rank prefix is never shown
 * twice (see {@link PrefixSource}). The player's own name is always used, not a
 * display name other plugins may already have decorated.
 */
final class ChatRanks {

    enum PrefixSource { CONFIG, LUCKPERMS, AUTO }

    /** A rank ready for rendering. */
    record Rank(String id, String displayName, Component prefix, Component suffix, @Nullable TextColor nameColor,
                @Nullable TextColor messageColor, int priority) {
    }

    private record Definition(String id, String permission, List<String> groups, Rank rank) {
    }

    private final List<Definition> definitions = new ArrayList<>();
    private final Map<String, Definition> byGroup = new HashMap<>();
    private final Definition fallback;
    private final PrefixSource prefixSource;
    private final List<Permission> registered = new ArrayList<>();

    ChatRanks(Settings settings, Formatter formatter) {
        prefixSource = settings.enumValue("prefix-source", PrefixSource.class, PrefixSource.AUTO);
        Settings list = settings.section("list");
        for (String id : list.keys()) {
            Settings rank = list.section(id);
            List<String> groups = rank.stringList("luckperms-groups").stream()
                    .map(group -> group.toLowerCase(Locale.ROOT)).toList();
            Definition definition = new Definition(id, rank.string("permission", "eepyessentials.rank." + id),
                    groups.isEmpty() ? List.of(id) : groups,
                    new Rank(id, rank.string("display-name", id),
                            formatter.parse(rank.string("prefix", "")),
                            formatter.parse(rank.string("suffix", "")),
                            rank.color("name-color", null),
                            rank.color("message-color", null),
                            rank.integer("priority", 0, Integer.MIN_VALUE, Integer.MAX_VALUE)));
            definitions.add(definition);
            definition.groups().forEach(group -> byGroup.putIfAbsent(group, definition));
        }
        definitions.sort(Comparator.comparingInt((Definition d) -> d.rank().priority()).reversed());
        String defaultId = settings.string("default-rank", "member");
        Definition found = definitions.stream().filter(d -> d.id().equals(defaultId)).findFirst().orElse(null);
        if (found == null) {
            if (!definitions.isEmpty()) {
                settings.warn("default-rank", "'" + defaultId + "' is not in ranks.list, using the lowest rank");
                found = definitions.getLast();
            } else {
                found = new Definition("member", "eepyessentials.rank.member", List.of("default"),
                        new Rank("member", "Member", Component.empty(), Component.empty(), null, null, 0));
            }
        }
        fallback = found;
    }

    Rank defaultRank() {
        return fallback.rank();
    }

    /** Main thread (permission checks). */
    Rank resolve(Player player, @Nullable LuckPermsHook luckPerms) {
        if (luckPerms != null) {
            LuckPermsHook.GroupInfo info = luckPerms.info(player.getUniqueId());
            if (info != null) {
                return fromLuckPerms(info);
            }
        }
        for (Definition definition : definitions) {
            if (player.hasPermission(definition.permission())) {
                return definition.rank();
            }
        }
        return fallback.rank();
    }

    Rank fromLuckPerms(LuckPermsHook.GroupInfo info) {
        Definition definition = byGroup.get(info.primaryGroup().toLowerCase(Locale.ROOT));
        if (definition == null) {
            for (String group : info.groupsByWeight()) {
                definition = byGroup.get(group);
                if (definition != null) {
                    break;
                }
            }
        }
        if (definition != null) {
            Rank rank = definition.rank();
            if (prefixSource == PrefixSource.LUCKPERMS) {
                return new Rank(rank.id(), rank.displayName(), affix(info.prefix()), affix(info.suffix()),
                        rank.nameColor(), rank.messageColor(), rank.priority());
            }
            return rank;
        }
        if (prefixSource == PrefixSource.CONFIG || (info.prefix() == null && info.suffix() == null)) {
            return fallback.rank();
        }
        Rank base = fallback.rank();
        String name = info.primaryDisplayName() != null ? info.primaryDisplayName() : capitalize(info.primaryGroup());
        return new Rank("luckperms:" + info.primaryGroup(), name, affix(info.prefix()), affix(info.suffix()),
                base.nameColor(), base.messageColor(), info.primaryWeight());
    }

    void registerPermissions(PluginManager pluginManager) {
        for (Definition definition : definitions) {
            if (pluginManager.getPermission(definition.permission()) == null) {
                Permission permission = new Permission(definition.permission(),
                        "Gives the chat rank '" + definition.id() + "'", PermissionDefault.FALSE);
                pluginManager.addPermission(permission);
                registered.add(permission);
            }
        }
    }

    void unregisterPermissions(PluginManager pluginManager) {
        registered.forEach(pluginManager::removePermission);
        registered.clear();
    }

    int size() {
        return definitions.size();
    }

    private static Component affix(@Nullable String raw) {
        return raw == null ? Component.empty() : LegacyText.parse(raw);
    }

    private static String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
