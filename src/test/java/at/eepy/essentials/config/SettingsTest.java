package at.eepy.essentials.config;

import at.eepy.essentials.text.SoundSpec;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {

    @Test
    void invalidValuesFallBackAndAreReportedWithTheirPath() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("anti-spam.cooldown-ms", "fast");
        yaml.set("interval-minutes", 1);
        yaml.set("listener-priority", "SOMETIMES");
        yaml.set("color", "blurple");
        yaml.set("sound", "not a valid sound at all");
        yaml.set("public", "yes please");
        List<String> warnings = new ArrayList<>();
        Settings settings = new Settings(yaml, "modules/chat.yml", warnings);

        assertEquals(1000, settings.section("anti-spam").integer("cooldown-ms", 1000, 0, 60_000));
        assertEquals(15, settings.integer("interval-minutes", 15, 3, 1440));
        assertEquals(EventPriority.NORMAL, settings.enumValue("listener-priority", EventPriority.class, EventPriority.NORMAL));
        assertNull(settings.color("color", null));
        assertNull(settings.sound("sound"));
        assertTrue(settings.bool("public", true));
        assertEquals("fallback", settings.string("missing", "fallback"));

        assertEquals(6, warnings.size(), warnings.toString());
        assertTrue(warnings.getFirst().startsWith("modules/chat.yml: anti-spam.cooldown-ms"), warnings.getFirst());
    }

    @Test
    void validValuesAreRead() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("color", "#B98CFF");
        yaml.set("named", "light_purple");
        yaml.set("sound", "minecraft:block.amethyst_block.chime 0.8 1.2");
        yaml.set("display", List.of("bossbar", "chat", "nonsense"));
        List<String> warnings = new ArrayList<>();
        Settings settings = new Settings(yaml, "test.yml", warnings);

        assertEquals(TextColor.color(0xB98CFF), settings.color("color", null));
        assertNotNull(settings.color("named", null));
        assertEquals(new SoundSpec(Key.key("minecraft:block.amethyst_block.chime"), 0.8f, 1.2f), settings.sound("sound"));
        assertEquals(List.of(Mode.BOSSBAR, Mode.CHAT), settings.enumList("display", Mode.class, List.of()));
        assertEquals(1, warnings.size(), "only 'nonsense' is reported: " + warnings);
    }

    enum Mode { BOSSBAR, CHAT }
}
