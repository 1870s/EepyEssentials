package at.eepy.essentials.modules.chat;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gentle spam protection: a short cooldown between messages and no identical
 * message twice in a row within a time window. Thread-safe, because chat
 * events are asynchronous.
 */
final class AntiSpam {

    enum Result { OK, TOO_FAST, REPEATED }

    private record Last(long time, String normalized) {
    }

    private final long cooldownMillis;
    private final long repeatMillis;
    private final Map<UUID, Last> last = new ConcurrentHashMap<>();

    AntiSpam(long cooldownMillis, long repeatMillis) {
        this.cooldownMillis = cooldownMillis;
        this.repeatMillis = repeatMillis;
    }

    Result check(UUID player, String message, long now) {
        String normalized = message.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        Result[] result = {Result.OK};
        last.compute(player, (uuid, previous) -> {
            if (previous != null) {
                if (now - previous.time() < cooldownMillis) {
                    result[0] = Result.TOO_FAST;
                    return previous; // a blocked message does not reset the timer
                }
                if (now - previous.time() < repeatMillis && previous.normalized().equals(normalized)) {
                    result[0] = Result.REPEATED;
                    return previous;
                }
            }
            return new Last(now, normalized);
        });
        return result[0];
    }

    void forget(UUID player) {
        last.remove(player);
    }
}
