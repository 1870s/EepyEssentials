package at.eepy.essentials.config;

import at.eepy.essentials.text.SoundSpec;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Typed, validating view on one YAML file or section.
 * <p>
 * Every getter has a default. Invalid values are reported as warnings with the
 * full path (e.g. {@code modules/chat.yml: anti-spam.cooldown-ms ...}) and the
 * default is used instead, so a typo never breaks a module.
 */
public final class Settings {

    private final ConfigurationSection section;
    private final String file;
    private final String path;
    private final List<String> warnings;

    public Settings(ConfigurationSection section, String file, List<String> warnings) {
        this(section, file, "", warnings);
    }

    private Settings(ConfigurationSection section, String file, String path, List<String> warnings) {
        this.section = section;
        this.file = file;
        this.path = path;
        this.warnings = warnings;
    }

    public Settings section(String key) {
        ConfigurationSection child = section.getConfigurationSection(key);
        if (child == null) {
            child = section.createSection(key); // empty: every getter returns its default
        }
        return new Settings(child, file, full(key), warnings);
    }

    public Set<String> keys() {
        return section.getKeys(false);
    }

    public boolean contains(String key) {
        return section.contains(key);
    }

    public String string(String key, String fallback) {
        String value = section.getString(key);
        return value != null ? value : fallback;
    }

    public @Nullable String optionalString(String key) {
        String value = section.getString(key);
        return value == null || value.isBlank() ? null : value;
    }

    public List<String> stringList(String key) {
        if (section.isString(key)) {
            return List.of(Objects.requireNonNull(section.getString(key)));
        }
        return List.copyOf(section.getStringList(key));
    }

    public List<Map<?, ?>> mapList(String key) {
        return List.copyOf(section.getMapList(key));
    }

    public boolean bool(String key, boolean fallback) {
        if (section.contains(key) && !section.isBoolean(key)) {
            warn(key, "must be true or false, using " + fallback);
            return fallback;
        }
        return section.getBoolean(key, fallback);
    }

    public int integer(String key, int fallback, int min, int max) {
        if (!section.contains(key)) {
            return fallback;
        }
        if (!section.isInt(key)) {
            warn(key, "must be a whole number, using " + fallback);
            return fallback;
        }
        int value = section.getInt(key);
        if (value < min || value > max) {
            warn(key, "must be between " + min + " and " + max + ", using " + fallback);
            return fallback;
        }
        return value;
    }

    public <E extends Enum<E>> E enumValue(String key, Class<E> type, E fallback) {
        String raw = section.getString(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            warn(key, "'" + raw + "' is not one of " + Arrays.toString(type.getEnumConstants()) + ", using " + fallback);
            return fallback;
        }
    }

    public <E extends Enum<E>> List<E> enumList(String key, Class<E> type, List<E> fallback) {
        if (!section.contains(key)) {
            return fallback;
        }
        List<E> values = new ArrayList<>();
        for (String raw : section.getStringList(key)) {
            try {
                values.add(Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                warn(key, "'" + raw + "' is not one of " + Arrays.toString(type.getEnumConstants()) + ", ignored");
            }
        }
        return List.copyOf(values);
    }

    public @Nullable TextColor color(String key, @Nullable TextColor fallback) {
        String raw = section.getString(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        TextColor color = parseColor(raw);
        if (color == null) {
            warn(key, "'" + raw + "' is not a colour (use #RRGGBB)");
            return fallback;
        }
        return color;
    }

    /** "" or missing = no sound. */
    public @Nullable SoundSpec sound(String key) {
        String raw = section.getString(key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return SoundSpec.parse(raw);
        } catch (IllegalArgumentException e) {
            warn(key, e.getMessage() + ", no sound is played");
            return null;
        }
    }

    public void warn(String key, String message) {
        warnings.add(file + ": " + full(key) + " " + message);
    }

    private String full(String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    public static @Nullable TextColor parseColor(String raw) {
        String value = raw.trim();
        return value.startsWith("#") ? TextColor.fromHexString(value) : NamedTextColor.NAMES.value(value.toLowerCase(Locale.ROOT));
    }
}
