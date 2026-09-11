package dev.wayfinder;

/** Pure navigation geometry; Minecraft coordinates have south at positive Z. */
public final class Navigation {
    private static final String[] DIRECTIONS = {
        "north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"
    };

    private Navigation() {}

    public record Reading(String direction, double horizontalDistance, int heightDifference) {}

    public static Reading between(int x, int y, int z, int targetX, int targetY, int targetZ) {
        double dx = (double) targetX - x;
        double dz = (double) targetZ - z;
        double distance = Math.hypot(dx, dz);
        String direction = distance == 0 ? "here" : DIRECTIONS[
            Math.floorMod((int) Math.floor((Math.toDegrees(Math.atan2(dx, -dz)) + 22.5) / 45), 8)
        ];
        return new Reading(direction, distance, targetY - y);
    }
}
