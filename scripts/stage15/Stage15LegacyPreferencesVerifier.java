import com.google.gson.Gson;
import net.mcreator.preferences.PreferencesManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Run with a new -Duser.home fixture and XDG_CONFIG_HOME pointing inside it, using the installed JAR. */
public class Stage15LegacyPreferencesVerifier {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        if (!mode.equals("modern") && !mode.equals("old")) throw new IllegalArgumentException("Expected modern or old");
        Path home = Path.of(System.getProperty("user.home")).toAbsolutePath();
        Path config = Path.of(System.getenv("XDG_CONFIG_HOME")).toAbsolutePath().resolve("copperbench");
        if (!config.startsWith(home) || Files.exists(config)) throw new IllegalStateException("Expected a fresh isolated fixture");
        Path legacy = Files.createDirectories(home.resolve(".copperbench"));
        String filename = mode.equals("modern") ? "userpreferences" : "preferences";
        String seed = mode.equals("modern") ? "{\"core\":{\"ui\":{\"backgroundSource\":\"None\"}}}"
                : "{\"ui\":{\"backgroundSource\":\"None\"}}";
        Files.writeString(legacy.resolve(filename), seed);
        PreferencesManager.init();
        if (!PreferencesManager.PREFERENCES.ui.backgroundSource.get().equals("None"))
            throw new IllegalStateException("Legacy value was not loaded by PreferencesManager.init");
        if (!Files.readString(legacy.resolve(filename)).equals(seed)) throw new IllegalStateException("Legacy file was altered");
        if (!Files.isRegularFile(config.resolve("userpreferences"))) throw new IllegalStateException("Active preferences missing");
        PreferencesManager.PREFERENCES.ui.backgroundSource.set("Custom");
        PreferencesManager.savePreferences();
        if (!Files.readString(config.resolve("userpreferences")).contains("Custom"))
            throw new IllegalStateException("New writes did not use the XDG target");
        System.out.println(new Gson().toJson(Map.of("status", "passed", "mode", mode,
                "legacyValueLoaded", true, "legacySourcePreserved", true, "newWritesUseXdg", true)));
    }
}
