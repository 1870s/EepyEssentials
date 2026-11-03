package at.eepy.essentials.config;

import at.eepy.essentials.TestFiles;
import at.eepy.essentials.text.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parses every MiniMessage text in the bundled files. Any tag that does not
 * exist (typo, missing placeholder) would be left in the output as text.
 */
class BundledFilesTest {

    /** Every placeholder some module provides; resolved to "x" here. */
    private static final Set<String> MODULE_TAGS = Set.of(
            "player", "displayname", "online", "max", "rank", "world", "rank_prefix", "rank_suffix", "name", "message",
            "target", "advancement", "advancement_plain", "description", "sleeping", "needed", "eligible",
            "percentage", "x", "y", "z", "world_name", "ms", "enabled", "total", "count", "file", "error", "module",
            "state", "modules");
    private static final Pattern LEFTOVER = Pattern.compile("<[a-zA-Z_#/!][^<>]*>");

    private final Formatter formatter = TestFiles.formatter();
    private final TagResolver moduleTags = TagResolver.resolver(
            MODULE_TAGS.stream().map(name -> Placeholder.unparsed(name, "x")).toList());

    @Test
    void everyMessageInEveryFileResolves() {
        List<String> files = new ArrayList<>(List.of("config.yml", "messages.yml"));
        TestFiles.MODULE_FILES.forEach(id -> files.add("modules/" + id + ".yml"));
        int checked = 0;
        for (String file : files) {
            YamlConfiguration yaml = TestFiles.yaml(file);
            for (Map.Entry<String, Object> entry : yaml.getValues(true).entrySet()) {
                for (String text : texts(entry.getValue())) {
                    if (!text.contains("<")) {
                        continue;
                    }
                    String plain = PlainTextComponentSerializer.plainText().serialize(formatter.parse(text, moduleTags));
                    assertFalse(LEFTOVER.matcher(plain).find(), file + " → " + entry.getKey() + ": " + plain);
                    checked++;
                }
            }
        }
        assertTrue(checked > 60, "checked " + checked + " texts");
    }

    @Test
    void everyModuleCanBeToggledInConfig() {
        Settings modules = TestFiles.settings("config.yml", new ArrayList<>()).section("modules");
        assertEquals(Set.copyOf(TestFiles.MODULE_FILES), modules.keys(), "every module can be toggled in config.yml");
    }

    private static List<String> texts(Object value) {
        if (value instanceof String text) {
            return List.of(text);
        }
        if (value instanceof List<?> list) {
            List<String> texts = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String text) {
                    texts.add(text);
                } else if (item instanceof Map<?, ?> map) {
                    map.values().forEach(inner -> texts.addAll(texts(inner)));
                }
            }
            return texts;
        }
        return List.of(); // sections are visited through getValues(true)
    }
}
