package dev.example.surveypulse;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import dev.example.surveypulse.ScanResult.Point;

/** Executable assertions against pure scan policy; not an in-game validation claim. */
public final class ScannerContractTest {
    private static int passed;

    public static void main(String[] args) {
        Point origin = new Point(0, 64, 0);
        ScanResult empty = OreScanner.scan(p -> "stone", origin, 5);
        expect(empty.count() == 0 && empty.nearest().isEmpty(), "empty world");

        Map<Point, String> world = Map.of(
                new Point(1, 64, 0), "iron_ore",
                new Point(3, 64, 0), "deepslate_diamond_ore",
                new Point(5, 69, 5), "ancient_debris",
                new Point(8, 64, 0), "copper_ore",
                new Point(9, 64, 0), "gold_ore",
                new Point(0, 64, 1), "diamond_block");
        OreScanner.BlockReader reader = p -> world.getOrDefault(p, "stone");
        ScanResult small = OreScanner.scan(reader, origin, 5);
        expect(small.count() == 3, "inclusive cube and ore-name policy");
        expect(small.nearest().orElseThrow().equals(new Point(1, 64, 0)), "nearest ore");
        expect(OreScanner.scan(reader, origin, 8).count() == 4, "sneak radius includes 8 excludes 9");
        expect(OreScanner.scan(p -> null, origin, 5).count() == 0, "skip unavailable positions");
        AtomicInteger visits = new AtomicInteger();
        OreScanner.scan(p -> { visits.incrementAndGet(); return "stone"; }, origin, 8);
        expect(visits.get() == 17 * 17 * 17, "bounded scan volume");
        expect(small.message(5).contains("3 ore blocks; nearest offset 1, 0, 0"), "relative feedback");
        expect(empty.message(5).contains("no ore found"), "empty feedback");
        System.out.println("SCANNER_CONTRACT_PASSED=" + passed);
    }

    private static void expect(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        passed++;
        System.out.println("PASS " + name);
    }
}
