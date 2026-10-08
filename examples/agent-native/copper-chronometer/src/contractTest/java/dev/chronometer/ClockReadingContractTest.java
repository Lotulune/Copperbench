package dev.chronometer;

/** An independent pure-Java oracle for phase boundaries and time representation. */
public final class ClockReadingContractTest {
    private static int checks;
    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        long[] ticks = {0, 5_999, 6_000, 11_999, 12_000, 12_999, 13_000, 17_999,
                        18_000, 22_999, 23_000, 23_999, 24_000, -1, -24_000};
        String[] clocks = {"06:00", "11:59", "12:00", "17:59", "18:00", "18:59", "19:00",
                           "23:59", "00:00", "04:59", "05:00", "05:59", "06:00", "05:59", "06:00"};
        String[] phases = {"day","day","day","day","dusk","dusk","night","night",
                           "night","night","dawn","dawn","day","dawn","day"};
        for (int i = 0; i < ticks.length; i++) {
            ClockReading reading = ClockReading.at(ticks[i]);
            check(reading.format(false).equals(clocks[i]), "clock at " + ticks[i]);
            check(reading.phase().equals(phases[i]), "phase at " + ticks[i]);
            check(reading.ticksToNextPhase() > 0, "positive next boundary at " + ticks[i]);
        }
        check(ClockReading.at(0).format(true).equals("6:00 AM"), "morning 12-hour");
        check(ClockReading.at(6_000).format(true).equals("12:00 PM"), "noon 12-hour");
        check(ClockReading.at(18_000).format(true).equals("12:00 AM"), "midnight 12-hour");
        check(ClockReading.at(24_000).day() == 2, "second day");
        check(ClockReading.at(-1).day() == 0, "negative day floor");
        for (long large : new long[]{Long.MIN_VALUE, Long.MAX_VALUE}) {
            ClockReading value = ClockReading.at(large);
            check(value.tickOfDay() >= 0 && value.tickOfDay() < 24_000, "large tick normalization");
            check(value.minute() >= 0 && value.minute() < 60, "large minute range");
        }
        System.out.println("ClockReading contract: " + checks + " checks passed");
    }
}
