package at.eepy.essentials.core;

import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Base class that owns a module's listeners and tasks and removes all of them
 * on {@link #disable()}. Task bodies are wrapped so an exception is logged
 * once instead of killing the task or spamming the console.
 */
public abstract class AbstractModule implements Module, Listener {

    private final List<Listener> listeners = new ArrayList<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    protected ModuleContext context;
    private boolean taskErrorReported;

    @Override
    public final void enable(ModuleContext context) throws Exception {
        this.context = context;
        taskErrorReported = false;
        start();
    }

    @Override
    public final void disable() {
        try {
            stop();
        } finally {
            listeners.forEach(HandlerList::unregisterAll);
            listeners.clear();
            tasks.forEach(BukkitTask::cancel);
            tasks.clear();
        }
    }

    /** Reads settings and registers listeners/tasks. */
    protected abstract void start() throws Exception;

    /** Releases anything not covered by listener/task tracking (boss bars, game rules, ...). */
    protected void stop() {
    }

    /** Registers this module's {@code @EventHandler} methods. */
    protected void listen() {
        listen(this);
    }

    protected void listen(Listener listener) {
        context.plugin().getServer().getPluginManager().registerEvents(listener, context.plugin());
        listeners.add(listener);
    }

    /** Registers one handler with a priority chosen at runtime (e.g. from config). */
    protected <E extends Event> void listen(Class<E> type, EventPriority priority, boolean ignoreCancelled,
                                            Consumer<E> handler) {
        Listener holder = new Listener() {
        };
        context.plugin().getServer().getPluginManager().registerEvent(type, holder, priority,
                (listener, event) -> {
                    if (type.isInstance(event)) {
                        handler.accept(type.cast(event));
                    }
                }, context.plugin(), ignoreCancelled);
        listeners.add(holder);
    }

    protected BukkitTask later(Runnable task, long delayTicks) {
        BukkitTask scheduled = context.plugin().getServer().getScheduler().runTaskLater(context.plugin(), safe(task), delayTicks);
        tasks.removeIf(BukkitTask::isCancelled);
        tasks.add(scheduled);
        return scheduled;
    }

    protected BukkitTask repeat(Runnable task, long delayTicks, long periodTicks) {
        BukkitTask scheduled = context.plugin().getServer().getScheduler()
                .runTaskTimer(context.plugin(), safe(task), delayTicks, periodTicks);
        tasks.add(scheduled);
        return scheduled;
    }

    protected int activeTasks() {
        tasks.removeIf(task -> task.isCancelled() || !context.plugin().getServer().getScheduler().isQueued(task.getTaskId()));
        return tasks.size();
    }

    protected int listenerCount() {
        return listeners.size();
    }

    private Runnable safe(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                if (!taskErrorReported) {
                    taskErrorReported = true;
                    context.plugin().getLogger().log(Level.SEVERE, title() + " task failed", e);
                }
            }
        };
    }
}
