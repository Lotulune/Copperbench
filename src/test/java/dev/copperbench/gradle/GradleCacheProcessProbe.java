package dev.copperbench.gradle;

import com.google.gson.JsonObject;
import dev.copperbench.generator.WorkspaceExecutionEnvironment;
import net.mcreator.io.UserFolderManager;

import java.nio.file.Path;

/** Separate-process probe of the real user-folder, backend and distribution-pool entry points. */
public final class GradleCacheProcessProbe {
    public static void main(String[] args) {
        JsonObject result = new JsonObject();
        result.addProperty("setupHome", UserFolderManager.getGradleHome().toPath().toString());
        result.add("backend", WorkspaceExecutionEnvironment.describe(Path.of(args[0]), Path.of("."), "fabric-1.21.1"));
        result.addProperty("externalRoots", GradleDistributionPool.extraSearchRoots().size());
        result.addProperty("seeded", GradleDistributionPool.seedPackagedDistributions());
        System.out.println("CACHE_PROBE=" + result);
    }
}
