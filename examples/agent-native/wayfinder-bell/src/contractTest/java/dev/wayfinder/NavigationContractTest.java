package dev.wayfinder;

public final class NavigationContractTest {
    public static void main(String[] args) {
        check("north", 0, -10); check("northeast", 10, -10);
        check("east", 10, 0); check("southeast", 10, 10);
        check("south", 0, 10); check("southwest", -10, 10);
        check("west", -10, 0); check("northwest", -10, -10);
        check("here", 0, 0);
        var triangle = Navigation.between(-10, 64, -10, -7, 72, -6);
        require(triangle.horizontalDistance() == 5, "3-4-5 distance at negative coordinates");
        require(triangle.heightDifference() == 8, "positive height difference");
        require(Navigation.between(0, 80, 0, 0, 64, 0).heightDifference() == -16, "negative height difference");
        var border = Navigation.between(-30_000_000, 64, 0, 30_000_000, 64, 0);
        require(border.horizontalDistance() == 60_000_000, "world-border distance without integer overflow");
        System.out.println("WAYFINDER_GEOMETRY_VERIFIED checks=13");
    }

    private static void check(String direction, int dx, int dz) {
        require(Navigation.between(0, 64, 0, dx, 64, dz).direction().equals(direction), direction);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
