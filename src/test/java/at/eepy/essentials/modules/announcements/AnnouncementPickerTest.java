package at.eepy.essentials.modules.announcements;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AnnouncementPickerTest {

    private static final LocalDateTime TUESDAY_NOON = LocalDateTime.parse("2026-11-03T12:00");

    private static Announcement plain(String id) {
        return new Announcement(id, List.of(id), "", "", "", null, Set.of(), Set.of(), null, null);
    }

    @Test
    void randomNeverRepeatsTheSameAnnouncementTwiceInARow() {
        AnnouncementPicker picker = new AnnouncementPicker(List.of(plain("a"), plain("b"), plain("c")),
                AnnouncementPicker.Order.RANDOM, new Random(42));
        String previous = null;
        for (int i = 0; i < 300; i++) {
            String current = picker.next(TUESDAY_NOON).id();
            assertNotEquals(previous, current);
            previous = current;
        }
    }

    @Test
    void sequentialGoesInOrderAndSkipsInactiveOnes() {
        Announcement weekendOnly = new Announcement("weekend", List.of("x"), "", "", "", null, Set.of(),
                Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), null, null);
        AnnouncementPicker picker = new AnnouncementPicker(List.of(plain("a"), weekendOnly, plain("c")),
                AnnouncementPicker.Order.SEQUENTIAL, new Random());
        List<String> shown = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            shown.add(picker.next(TUESDAY_NOON).id());
        }
        assertEquals(List.of("a", "c", "a", "c"), shown);
    }

    @Test
    void timeWindowsMayCrossMidnight() {
        Announcement night = new Announcement("night", List.of("x"), "", "", "", null, Set.of(), Set.of(),
                LocalTime.of(22, 0), LocalTime.of(2, 0));
        assertTrue(night.activeAt(LocalDateTime.parse("2026-11-03T23:30")));
        assertTrue(night.activeAt(LocalDateTime.parse("2026-11-04T01:30")));
        assertFalse(night.activeAt(LocalDateTime.parse("2026-11-04T12:00")));
        AnnouncementPicker picker = new AnnouncementPicker(List.of(night), AnnouncementPicker.Order.RANDOM, new Random());
        assertNull(picker.next(TUESDAY_NOON), "nothing to say at noon");
    }
}
