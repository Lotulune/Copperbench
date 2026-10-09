package dev.chronometer;

import java.util.Locale;

/** Pure, deterministic reading of Minecraft's 24,000-tick world day.
 * Phase ranges are this instrument's explicit display convention, not sleep eligibility.
 */
public record ClockReading(long day, int tickOfDay, int hour, int minute,
                           String phase, int ticksToNextPhase) {
    public static ClockReading at(long dayTime) {
        int tick = (int) Math.floorMod(dayTime, 24_000L);
        long day = Math.floorDiv(dayTime, 24_000L) + 1;
        int hour = (tick / 1_000 + 6) % 24;
        int minute = (tick % 1_000) * 60 / 1_000;
        String phase;
        int next;
        if (tick < 12_000) { phase = "day"; next = 12_000; }
        else if (tick < 13_000) { phase = "dusk"; next = 13_000; }
        else if (tick < 23_000) { phase = "night"; next = 23_000; }
        else { phase = "dawn"; next = 24_000; }
        return new ClockReading(day, tick, hour, minute, phase, next - tick);
    }

    public String format(boolean twelveHour) {
        if (!twelveHour) return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
        int displayed = hour % 12 == 0 ? 12 : hour % 12;
        return String.format(Locale.ROOT, "%d:%02d %s", displayed, minute, hour < 12 ? "AM" : "PM");
    }
}
