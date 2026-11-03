package at.eepy.essentials;

import at.eepy.essentials.commands.EssentialsCommands;
import at.eepy.essentials.config.ConfigStore;
import at.eepy.essentials.config.Settings;
import at.eepy.essentials.core.Module;
import at.eepy.essentials.core.ModuleContext;
import at.eepy.essentials.core.ModuleManager;
import at.eepy.essentials.integrations.LuckPermsHook;
import at.eepy.essentials.integrations.PermissionChanges;
import at.eepy.essentials.modules.achievements.AchievementsModule;
import at.eepy.essentials.modules.announcements.AnnouncementsModule;
import at.eepy.essentials.modules.chat.ChatModule;
import at.eepy.essentials.modules.deaths.DeathsModule;
import at.eepy.essentials.modules.sleep.SleepModule;
import at.eepy.essentials.modules.welcome.WelcomeModule;
import at.eepy.essentials.text.Formatter;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * EepyEssentials entry point: loads the configuration files, owns the shared
 * services (formatter, LuckPerms hook) and lets the {@link ModuleManager}
 * start the modules that are enabled in config.yml.
 */
public final class EepyEssentials extends JavaPlugin {

    /** Plugins that handle the same things as a module, by module id. */
    private static final Map<String, List<String>> OVERLAPPING = Map.of(
            "chat", List.of("EssentialsChat", "LPC", "ChatControl", "ChatControlRed", "VentureChat", "CarbonChat",
                    "DeluxeChat", "ChatManager", "CMI"),
            "welcome", List.of("CMI", "JoinMessages", "SimpleJoinMessages", "AdvancedJoinMessages"),
            "sleep", List.of("Harbor", "BetterSleeping", "BetterSleeping4", "SimpleSleep", "SleepMost", "CMI"),
            "announcements", List.of("AnnouncerPlus", "AutoBroadcast", "AutoMessage", "ChatControlRed", "CMI"),
            "deaths", List.of("DeathMessagesPrime", "DeathMessages", "CMI"),
            "achievements", List.of("AdvancementAnnouncer", "CMI"));

    private ConfigStore store;
    private ModuleManager modules;
    private final PermissionChanges permissionChanges = new PermissionChanges();
    private @Nullable LuckPermsHook luckPerms;
    private Formatter formatter;
    private YamlConfiguration mainConfig;
    private Settings messages;
    private boolean useLuckPerms;
    private List<String> warnings = List.of();

    /** Outcome of a (re)load. */
    public record LoadResult(List<String> errors, List<String> warnings, long millis) {
    }

    @Override
    public void onEnable() {
        store = new ConfigStore(this);
        modules = new ModuleManager(getLogger(), List.of(new WelcomeModule(), new ChatModule(), new AchievementsModule(),
                new SleepModule(), new AnnouncementsModule(), new DeathsModule()));
        if (getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                luckPerms = new LuckPermsHook(this, permissionChanges::fire);
            } catch (LinkageError | IllegalStateException e) {
                getLogger().log(Level.WARNING, "LuckPerms is installed but its API could not be loaded", e);
            }
        }

        LoadResult result = loadAll();
        result.errors().forEach(error -> getLogger().severe(error + " (using the bundled defaults for this file)"));
        result.warnings().forEach(warning -> getLogger().warning(warning));

        EssentialsCommands commands = new EssentialsCommands(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> commands.register(event.registrar()));
        getServer().getScheduler().runTask(this, this::warnAboutOverlaps);
        getLogger().info("Enabled " + modules.enabledCount() + "/" + modules.ids().size() + " modules. LuckPerms: "
                + (luckPerms != null ? (useLuckPerms ? "hooked" : "installed, disabled in config") : "not found"));
    }

    @Override
    public void onDisable() {
        if (modules != null) {
            modules.stopAll();
        }
        if (luckPerms != null) {
            luckPerms.close();
            luckPerms = null;
        }
    }

    /** Reloads every file and restarts all modules. Broken files keep their previous version. */
    public LoadResult loadAll() {
        long started = System.nanoTime();
        List<String> errors = new ArrayList<>();
        List<String> collected = new ArrayList<>();
        mainConfig = loadOrKeep(ConfigStore.MAIN, errors);
        Settings main = new Settings(mainConfig, ConfigStore.MAIN, collected);
        messages = new Settings(loadOrKeep(ConfigStore.MESSAGES, errors), ConfigStore.MESSAGES, collected);
        formatter = Formatter.fromConfig(main, messages.string("prefix", ""), getLogger());
        useLuckPerms = main.section("integrations").bool("luckperms", true);

        Settings toggles = main.section("modules");
        Map<String, Boolean> wanted = new HashMap<>();
        Map<String, YamlConfiguration> files = new HashMap<>();
        for (String id : modules.ids()) {
            wanted.put(id, toggles.bool(id, true));
            files.put(id, loadOrKeep(moduleFile(id), errors));
        }
        modules.apply(wanted, module -> context(module, files.get(module.id()), collected));
        warnings = List.copyOf(collected);
        refreshCommands();
        return new LoadResult(List.copyOf(errors), warnings, (System.nanoTime() - started) / 1_000_000);
    }

    /**
     * Turns a module on or off, saves that in config.yml and applies it now.
     *
     * @return {@code null} on success, otherwise an error for the sender
     */
    public @Nullable String setModuleEnabled(Module module, boolean enabled) {
        try {
            store.set(ConfigStore.MAIN, "modules." + module.id(), enabled);
            mainConfig = store.load(ConfigStore.MAIN);
        } catch (IOException | InvalidConfigurationException | ConfigStore.LoadException e) {
            return "config.yml could not be updated: " + e.getMessage();
        }
        String error = null;
        if (enabled) {
            List<String> errors = new ArrayList<>();
            List<String> collected = new ArrayList<>(warnings);
            YamlConfiguration file = loadOrKeep(moduleFile(module.id()), errors);
            error = errors.isEmpty() ? null : errors.getFirst();
            String startError = modules.start(module, m -> context(m, file, collected));
            if (startError != null) {
                error = startError;
            }
            warnings = List.copyOf(collected);
        } else {
            modules.stop(module);
        }
        refreshCommands();
        return error;
    }

    private ModuleContext context(Module module, YamlConfiguration file, List<String> collected) {
        return new ModuleContext(this, formatter, new Settings(file, moduleFile(module.id()), collected),
                useLuckPerms ? luckPerms : null, permissionChanges);
    }

    private YamlConfiguration loadOrKeep(String name, List<String> errors) {
        try {
            return store.load(name);
        } catch (ConfigStore.LoadException e) {
            errors.add(name + ": " + e.getMessage());
            getLogger().log(Level.FINE, "YAML error in " + name, e);
            return store.lastGoodOrDefault(name);
        }
    }

    private static String moduleFile(String id) {
        return "modules/" + id + ".yml";
    }

    /** Commands of disabled modules are hidden; resend the command tree after changes. */
    private void refreshCommands() {
        getServer().getOnlinePlayers().forEach(Player::updateCommands);
    }

    public List<String> overlappingPlugins(String moduleId) {
        PluginManager pluginManager = getServer().getPluginManager();
        return OVERLAPPING.getOrDefault(moduleId, List.of()).stream()
                .map(pluginManager::getPlugin)
                .filter(Objects::nonNull)
                .filter(Plugin::isEnabled)
                .map(Plugin::getName)
                .toList();
    }

    private void warnAboutOverlaps() {
        if (!new Settings(mainConfig, ConfigStore.MAIN, new ArrayList<>()).section("compatibility")
                .bool("warn-about-overlapping-plugins", true)) {
            return;
        }
        for (Module module : modules.modules()) {
            if (!modules.isEnabled(module.id())) {
                continue;
            }
            for (String name : overlappingPlugins(module.id())) {
                getLogger().warning(name + " may also handle what " + module.title() + " does. Disable that feature "
                        + "in one of them (modules." + module.id() + " in config.yml) to avoid duplicate messages.");
            }
        }
    }

    // ── accessors for commands ────────────────────────────────────────────

    public ModuleManager modules() {
        return modules;
    }

    public Formatter formatter() {
        return formatter;
    }

    public Settings messages() {
        return messages;
    }

    public List<String> warnings() {
        return warnings;
    }

    public @Nullable LuckPermsHook luckPerms() {
        return luckPerms;
    }

    public boolean usesLuckPerms() {
        return useLuckPerms && luckPerms != null;
    }

    public <T extends Module> @Nullable T enabledModule(Class<T> type) {
        for (Module module : modules.modules()) {
            if (type.isInstance(module) && modules.isEnabled(module.id())) {
                return type.cast(module);
            }
        }
        return null;
    }
}
