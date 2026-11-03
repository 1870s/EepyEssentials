package at.eepy.essentials.text;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

import java.util.Locale;

/**
 * A sound from config, written as {@code "namespace:sound volume pitch"},
 * e.g. {@code "minecraft:block.amethyst_block.chime 0.8 1.2"}.
 * Played with {@link Sound.Emitter#self()} so only the receiver hears it.
 */
public record SoundSpec(Key key, float volume, float pitch) {

    public static SoundSpec parse(String raw) {
        String[] parts = raw.trim().split("\\s+");
        if (parts.length > 3) {
            throw new IllegalArgumentException("'" + raw + "' must be \"sound [volume] [pitch]\"");
        }
        Key key;
        try {
            key = Key.key(parts[0].toLowerCase(Locale.ROOT));
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("'" + parts[0] + "' is not a sound name");
        }
        float volume = parts.length > 1 ? number(parts[1], raw, 0f, 10f) : 1f;
        float pitch = parts.length > 2 ? number(parts[2], raw, 0.5f, 2f) : 1f;
        return new SoundSpec(key, volume, pitch);
    }

    private static float number(String value, String raw, float min, float max) {
        try {
            float parsed = Float.parseFloat(value);
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException("'" + raw + "': " + value + " must be between " + min + " and " + max);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + raw + "': " + value + " is not a number");
        }
    }

    public void play(Audience audience) {
        audience.playSound(Sound.sound(key, Sound.Source.MASTER, volume, pitch), Sound.Emitter.self());
    }
}
