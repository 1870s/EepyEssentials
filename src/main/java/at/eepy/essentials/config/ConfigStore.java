package at.eepy.essentials.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Loads the plugin's YAML files.
 * <p>
 * Missing files are created from the bundled defaults, existing files are never
 * overwritten. Every loaded file gets the bundled version as defaults, so keys
 * added in an update work without touching the user's file. When a file has a
 * YAML error the last good version is kept and the error is reported.
 */
public final class ConfigStore {

    public static final String MAIN = "config.yml";
    public static final String MESSAGES = "messages.yml";

    private final Plugin plugin;
    private final Map<String, YamlConfiguration> lastGood = new HashMap<>();

    public ConfigStore(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Thrown when a file can not be parsed; carries a short reason for chat. */
    public static final class LoadException extends Exception {
        private final String file;

        LoadException(String file, String reason, Throwable cause) {
            super(reason, cause);
            this.file = file;
        }

        public String file() {
            return file;
        }
    }

    /**
     * @param name path relative to the plugin folder, e.g. {@code modules/chat.yml}
     */
    public YamlConfiguration load(String name) throws LoadException {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            int newline = message.indexOf('\n');
            throw new LoadException(name, newline < 0 ? message : message.substring(0, newline), e);
        }
        yaml.setDefaults(bundled(name));
        lastGood.put(name, yaml);
        return yaml;
    }

    /** The last successfully loaded version, or the bundled defaults. */
    public YamlConfiguration lastGoodOrDefault(String name) {
        YamlConfiguration good = lastGood.get(name);
        return good != null ? good : bundled(name);
    }

    public YamlConfiguration bundled(String name) {
        try (InputStream in = Objects.requireNonNull(plugin.getResource(name), name + " missing in jar")) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read bundled " + name, e);
        }
    }

    /**
     * Changes one value in a file on disk and keeps the rest (including comments).
     * Used to persist /eepyessentials module enable|disable.
     */
    public void set(String name, String path, Object value) throws IOException, InvalidConfigurationException {
        File file = new File(plugin.getDataFolder(), name);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        yaml.set(path, value);
        yaml.save(file);
    }
}
