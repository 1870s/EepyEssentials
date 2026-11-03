package at.eepy.essentials.integrations;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

/**
 * Read-only access to LuckPerms group data. Only instantiated when LuckPerms is
 * enabled, so nothing else touches LuckPerms classes.
 * <p>
 * LuckPerms' cached data is safe to read from any thread, which lets the async
 * chat renderer use it directly.
 */
public final class LuckPermsHook {

    /** What a module needs to know about a player's groups. */
    public record GroupInfo(String primaryGroup, List<String> groupsByWeight, @Nullable String prefix,
                            @Nullable String suffix, int primaryWeight, @Nullable String primaryDisplayName) {
    }

    private final LuckPerms api;
    private final List<EventSubscription<?>> subscriptions = new ArrayList<>();

    public LuckPermsHook(Plugin plugin, Consumer<UUID> onUserChanged) {
        this.api = LuckPermsProvider.get();
        subscriptions.add(api.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class,
                event -> onUserChanged.accept(event.getUser().getUniqueId())));
    }

    /** {@code null} if LuckPerms has not loaded the user (e.g. not online). */
    public @Nullable GroupInfo info(UUID player) {
        User user = api.getUserManager().getUser(player);
        if (user == null) {
            return null;
        }
        List<Group> groups = new ArrayList<>(user.getInheritedGroups(user.getQueryOptions()));
        groups.sort(Comparator.comparingInt((Group group) -> group.getWeight().orElse(0)).reversed());
        CachedMetaData meta = user.getCachedData().getMetaData();
        Group primary = api.getGroupManager().getGroup(user.getPrimaryGroup());
        return new GroupInfo(
                user.getPrimaryGroup(),
                groups.stream().map(group -> group.getName().toLowerCase(Locale.ROOT)).toList(),
                meta.getPrefix(),
                meta.getSuffix(),
                primary == null ? 0 : primary.getWeight().orElse(0),
                primary == null ? null : primary.getDisplayName());
    }

    public String version() {
        return api.getPluginMetadata().getVersion();
    }

    public void close() {
        subscriptions.forEach(EventSubscription::close);
        subscriptions.clear();
    }
}
