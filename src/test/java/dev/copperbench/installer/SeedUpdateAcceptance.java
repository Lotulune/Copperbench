package dev.copperbench.installer;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/** Seeds an isolated installation using the fixture-compiled registration namespace. */
public final class SeedUpdateAcceptance {
    public static void main(String[] args) throws Exception {
        var plan = new InstallEngine.Plan(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), Path.of(args[3]),
                args[4], "en", "light", "user", false, false);
        var result = InstallerMain.execute(plan, Path.of(args[5]), false, new AtomicBoolean(), progress -> {});
        if (result.previousVersion() != null) throw new AssertionError("Fresh seed unexpectedly replaced an installation");
        System.out.println("Seeded isolated application " + result.target());
    }
}
