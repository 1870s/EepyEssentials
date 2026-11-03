package at.eepy.essentials;

import at.eepy.essentials.config.Settings;
import at.eepy.essentials.text.Formatter;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/** Loads the bundled YAML files like the plugin does. */
public final class TestFiles {

    public static final List<String> MODULE_FILES = List.of("welcome", "chat", "achievements", "sleep", "announcements", "deaths");

    private TestFiles() {
    }

    public static YamlConfiguration yaml(String name) {
        try (InputStream in = Objects.requireNonNull(TestFiles.class.getResourceAsStream("/" + name), name)) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static Settings settings(String name, List<String> warnings) {
        return new Settings(yaml(name), name, warnings);
    }

    public static Formatter formatter() {
        Settings messages = settings("messages.yml", new ArrayList<>());
        return Formatter.fromConfig(settings("config.yml", new ArrayList<>()), messages.string("prefix", ""),
                Logger.getLogger("test"));
    }
}
