package at.eepy.essentials.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Pattern;

/**
 * Parses text from other plugins (e.g. LuckPerms prefixes), which may use
 * legacy codes ({@code &c}, {@code §c}, {@code &#RRGGBB}) or MiniMessage.
 */
public final class LegacyText {

    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR).hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.AMPERSAND_CHAR).hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static final Pattern AMPERSAND_CODE = Pattern.compile("&([0-9a-fk-orA-FK-OR]|#[0-9a-fA-F]{6})");
    private static final Pattern MINI_MESSAGE_TAG = Pattern.compile("<[#!/]?[a-zA-Z0-9_:#-]+>");

    private LegacyText() {
    }

    public static Component parse(String text) {
        if (text.isEmpty()) {
            return Component.empty();
        }
        if (text.indexOf(LegacyComponentSerializer.SECTION_CHAR) >= 0) {
            return SECTION.deserialize(text);
        }
        if (AMPERSAND_CODE.matcher(text).find()) {
            return AMPERSAND.deserialize(text);
        }
        if (MINI_MESSAGE_TAG.matcher(text).find()) {
            return MiniMessage.miniMessage().deserialize(text);
        }
        return Component.text(text);
    }
}
