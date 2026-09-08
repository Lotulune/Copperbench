package dev.example.surveypulse;

import java.util.Optional;

public record ScanResult(int count, Optional<Point> nearest, Point origin) {
    public record Point(int x, int y, int z) {
        public long squaredDistance(Point other) {
            long dx = (long) x - other.x;
            long dy = (long) y - other.y;
            long dz = (long) z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    public String message(int radius) {
        return nearest.map(target -> "Survey Pulse: " + count + " ore blocks; nearest offset "
                + (target.x() - origin.x()) + ", " + (target.y() - origin.y()) + ", "
                + (target.z() - origin.z()) + " (sneak for radius 8)")
                .orElse("Survey Pulse [" + radius + "]: no ore found.");
    }
}
