package at.eepy.essentials.modules;

import at.eepy.essentials.modules.sleep.SleepCount;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModuleLogicTest {

    /** Same numbers as vanilla's SleepStatus. */
    @Test
    void sleepCountMatchesVanilla() {
        assertEquals(4, new SleepCount(0, 4, 100).needed());
        assertEquals(2, new SleepCount(0, 4, 50).needed());
        assertEquals(1, new SleepCount(0, 4, 25).needed());
        assertEquals(1, new SleepCount(0, 4, 0).needed(), "at least one sleeper");
        assertEquals(2, new SleepCount(0, 3, 50).needed(), "rounded up");
        assertEquals(1, new SleepCount(0, 0, 100).needed());
        assertEquals(5, new SleepCount(4, 4, 101).needed(), "above 100% the night can not be skipped");
        assertFalse(new SleepCount(4, 4, 101).enough());
        assertTrue(new SleepCount(2, 4, 50).enough());
        assertEquals(0.5f, new SleepCount(1, 4, 50).progress());
        assertEquals(1f, new SleepCount(3, 4, 50).progress(), "capped at full");
    }
}
