package at.eepy.essentials.modules.sleep;

/**
 * Sleep progress of one world, calculated like vanilla's SleepStatus:
 * {@code needed = max(1, ceil(eligible * percentage / 100))}. With a percentage
 * above 100 the night can not be skipped by sleeping, so {@code needed} is
 * larger than {@code eligible}.
 *
 * @param eligible players that count (not spectators, not "sleeping ignored")
 */
public record SleepCount(int sleeping, int eligible, int percentage) {

    public int needed() {
        return Math.max(1, (int) Math.ceil(eligible * percentage / 100.0));
    }

    /** 0..1 for the boss bar. */
    public float progress() {
        return Math.min(1f, (float) sleeping / needed());
    }

    public boolean enough() {
        return sleeping >= needed();
    }
}
