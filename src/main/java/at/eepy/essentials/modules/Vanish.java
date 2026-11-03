package at.eepy.essentials.modules;

import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

/** Detects players hidden by vanish plugins. */
public final class Vanish {

    private Vanish() {
    }

    /**
     * The {@code vanished} metadata is the de facto standard set by vanish plugins
     * (SuperVanish, PremiumVanish, EssentialsX, CMI). The metadata API is deprecated
     * in Paper but those plugins have no replacement, so it is read here on purpose.
     */
    @SuppressWarnings("deprecation")
    public static boolean isVanished(Player player) {
        for (MetadataValue value : player.getMetadata("vanished")) {
            if (value.asBoolean()) {
                return true;
            }
        }
        return false;
    }
}
