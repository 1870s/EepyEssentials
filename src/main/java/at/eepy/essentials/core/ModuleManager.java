package at.eepy.essentials.core;

import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Starts and stops modules and remembers their state.
 * <p>
 * Each module is started in isolation: if one throws while starting, it is
 * stopped again (removing whatever it registered so far), marked as failed and
 * the others continue normally.
 */
public final class ModuleManager {

    public enum State { ENABLED, DISABLED, FAILED }

    public record Status(State state, @Nullable String error) {
    }

    private final Map<String, Module> modules = new LinkedHashMap<>();
    private final Map<String, Status> states = new HashMap<>();
    private final Logger logger;

    public ModuleManager(Logger logger, List<Module> modules) {
        this.logger = logger;
        for (Module module : modules) {
            this.modules.put(module.id(), module);
            states.put(module.id(), new Status(State.DISABLED, null));
        }
    }

    public Collection<Module> modules() {
        return Collections.unmodifiableCollection(modules.values());
    }

    public Set<String> ids() {
        return Collections.unmodifiableSet(modules.keySet());
    }

    public @Nullable Module get(String id) {
        return modules.get(id.toLowerCase(Locale.ROOT));
    }

    public Status status(String id) {
        return states.getOrDefault(id, new Status(State.DISABLED, null));
    }

    public boolean isEnabled(String id) {
        return status(id).state() == State.ENABLED;
    }

    public long enabledCount() {
        return states.values().stream().filter(s -> s.state() == State.ENABLED).count();
    }

    /**
     * (Re)starts every module that should run and stops the others.
     *
     * @param wanted   module id → enabled in config.yml
     * @param contexts builds the context for a module (may throw if its file is broken)
     */
    public void apply(Map<String, Boolean> wanted, Function<Module, ModuleContext> contexts) {
        for (Module module : modules.values()) {
            stop(module);
            if (wanted.getOrDefault(module.id(), false)) {
                start(module, contexts);
            }
        }
    }

    /** @return {@code null} on success, otherwise the error */
    public @Nullable String start(Module module, Function<Module, ModuleContext> contexts) {
        stop(module);
        try {
            module.enable(contexts.apply(module));
            states.put(module.id(), new Status(State.ENABLED, null));
            return null;
        } catch (Exception | LinkageError e) {
            logger.log(Level.SEVERE, module.title() + " could not be enabled; the other modules keep running", e);
            try {
                module.disable();
            } catch (RuntimeException cleanup) {
                logger.log(Level.WARNING, module.title() + " failed to clean up", cleanup);
            }
            String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            states.put(module.id(), new Status(State.FAILED, error));
            return error;
        }
    }

    public void stop(Module module) {
        if (status(module.id()).state() == State.DISABLED) {
            return;
        }
        try {
            module.disable();
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, module.title() + " failed to stop cleanly", e);
        }
        states.put(module.id(), new Status(State.DISABLED, null));
    }

    public void stopAll() {
        List<Module> reversed = new ArrayList<>(modules.values());
        Collections.reverse(reversed);
        reversed.forEach(this::stop);
    }
}
