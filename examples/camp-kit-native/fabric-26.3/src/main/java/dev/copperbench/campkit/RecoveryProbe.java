package dev.copperbench.campkit;

/** Native source edited through the SDK during the controlled failure/repair workflow. */
public final class RecoveryProbe {
    public static int bundles(int count) {
        if (count < 0) throw new IllegalArgumentException("count must be nonnegative");
        return count / 16 + (count % 16 == 0 ? 0 : 1);
    }
}
