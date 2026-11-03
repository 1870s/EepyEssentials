package at.eepy.essentials.modules.announcements;

import org.jspecify.annotations.Nullable;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * One configured announcement.
 *
 * @param days empty = every day
 * @param from {@code null} = all day; if {@code to} is before {@code from} the window runs past midnight
 */
record Announcement(String id, List<String> lines, String actionbar, String title, String subtitle,
                    @Nullable String permission, Set<String> worlds, Set<DayOfWeek> days,
                    @Nullable LocalTime from, @Nullable LocalTime to) {

    boolean activeAt(LocalDateTime now) {
        if (!days.isEmpty() && !days.contains(now.getDayOfWeek())) {
            return false;
        }
        if (from == null || to == null) {
            return true;
        }
        LocalTime time = now.toLocalTime();
        return to.isBefore(from)
                ? !time.isBefore(from) || time.isBefore(to)
                : !time.isBefore(from) && time.isBefore(to);
    }
}
