package at.eepy.essentials.core;

/**
 * One feature area of EepyEssentials (welcome, chat, ...).
 * <p>
 * A module is started with its own settings file and must leave no listeners,
 * tasks or other traces behind when it is stopped, so it can be switched at
 * runtime. Most modules extend {@link AbstractModule}, which tracks listeners
 * and tasks automatically.
 */
public interface Module {

    /** Short id used in config.yml and commands, e.g. {@code chat}. */
    String id();

    /** Display name, e.g. {@code EepyChat}. */
    String title();

    /** One-line description for /eepyessentials modules. */
    String description();

    void enable(ModuleContext context) throws Exception;

    void disable();

    /** Extra lines for /eepyessentials debug. */
    default String status() {
        return "";
    }
}
