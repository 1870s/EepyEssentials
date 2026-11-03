package at.eepy.essentials.modules.announcements;

import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Chooses the next announcement: in order, or at random but never the same one
 * twice in a row. Announcements outside their time window are skipped.
 */
final class AnnouncementPicker {

    enum Order { RANDOM, SEQUENTIAL }

    private final List<Announcement> announcements;
    private final Order order;
    private final Random random;
    private int nextIndex;
    private @Nullable String last;

    AnnouncementPicker(List<Announcement> announcements, Order order, Random random) {
        this.announcements = List.copyOf(announcements);
        this.order = order;
        this.random = random;
    }

    @Nullable Announcement next(LocalDateTime now) {
        List<Announcement> active = new ArrayList<>();
        for (Announcement announcement : announcements) {
            if (announcement.activeAt(now)) {
                active.add(announcement);
            }
        }
        if (active.isEmpty()) {
            return null;
        }
        Announcement chosen;
        if (order == Order.SEQUENTIAL) {
            chosen = null;
            for (int i = 0; i < announcements.size() && chosen == null; i++) {
                Announcement candidate = announcements.get((nextIndex + i) % announcements.size());
                if (active.contains(candidate)) {
                    chosen = candidate;
                    nextIndex = (announcements.indexOf(candidate) + 1) % announcements.size();
                }
            }
        } else {
            if (active.size() > 1 && last != null) {
                active.removeIf(announcement -> announcement.id().equals(last));
            }
            chosen = active.get(random.nextInt(active.size()));
        }
        last = chosen == null ? last : chosen.id();
        return chosen;
    }
}
