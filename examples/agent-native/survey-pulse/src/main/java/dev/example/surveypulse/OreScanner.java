package dev.example.surveypulse;

import java.util.Optional;
import dev.example.surveypulse.ScanResult.Point;

/** Pure scan policy. The Minecraft adapter supplies only already-loaded blocks. */
public final class OreScanner {
    private OreScanner() {}

    @FunctionalInterface
    public interface BlockReader {
        // Return the block registry path, or null for an unloaded position.
        String read(Point position);
    }

    public static ScanResult scan(BlockReader blocks, Point origin, int radius) {
        int count = 0;
        Point nearest = null;
        long bestDistance = Long.MAX_VALUE;
        // Same inclusive cube and registry-name heuristic as the previous prototype.
        for (int z = -radius; z <= radius; z++) {
            for (int y = -radius; y <= radius; y++) {
                for (int x = -radius; x <= radius; x++) {
                    Point position = new Point(origin.x() + x, origin.y() + y, origin.z() + z);
                    String id = blocks.read(position);
                    if (id == null || !(id.endsWith("_ore") || id.equals("ancient_debris"))) continue;
                    count++;
                    long distance = position.squaredDistance(origin);
                    if (distance < bestDistance) {
                        nearest = position;
                        bestDistance = distance;
                    }
                }
            }
        }
        return new ScanResult(count, Optional.ofNullable(nearest), origin);
    }
}

// EXTERNAL_IDE_EDIT_SENTINEL: preserve this external edit across metadata changes.
