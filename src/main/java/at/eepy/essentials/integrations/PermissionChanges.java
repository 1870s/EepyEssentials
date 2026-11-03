package at.eepy.essentials.integrations;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Tells modules when a player's groups or prefixes changed (fed by LuckPerms
 * events, which arrive on LuckPerms threads). Listeners must hand work to the
 * main thread themselves if they need the Bukkit API.
 */
public final class PermissionChanges {

    private final List<Consumer<UUID>> listeners = new CopyOnWriteArrayList<>();

    public Runnable subscribe(Consumer<UUID> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public void fire(UUID player) {
        listeners.forEach(listener -> listener.accept(player));
    }
}
