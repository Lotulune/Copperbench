package dev.copperbench.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Versioned business identities agreed before a single acceptance execution. */
public record RequiredCaseManifest(String id, String identityKind, int minimumTests, List<Identity> cases) {
    public static final String VERSION = "1";
    public static final String JUNIT = "junit_tuple";
    public static final String GAMETEST = "gametest_resource";
    private static final Set<String> FIELDS = Set.of("schemaVersion", "id", "identityKind", "minimumTests", "cases", "sha256");
    private static final List<Integer> NATIVE_CASES = List.of(1, 2, 3, 4, 11, 12);
    private static final List<Integer> SCOPED_CASES = java.util.stream.IntStream.rangeClosed(1, 13).boxed().toList();

    /** Original producer values, never display-truncated or joined with an ambiguous delimiter. */
    public record Identity(String className, String name) {
        public Identity {
            Objects.requireNonNull(className);
            Objects.requireNonNull(name);
            if (name.isBlank()) throw new IllegalArgumentException("Required testcase identity must be nonempty.");
        }
        JsonObject json() {
            JsonObject result = new JsonObject();
            result.addProperty("className", className);
            result.addProperty("name", name);
            return result;
        }
    }

    public RequiredCaseManifest {
        if (id == null || !id.matches("[a-z][a-z0-9_-]{0,127}"))
            throw new IllegalArgumentException("A stable, versioned required-case manifest ID is required.");
        if (!Set.of(JUNIT, GAMETEST).contains(identityKind))
            throw new IllegalArgumentException("Unknown required-case identity kind.");
        cases = List.copyOf(cases).stream().sorted(Comparator.comparing(Identity::className).thenComparing(Identity::name)).toList();
        if (cases.isEmpty() || cases.size() > 10_000 || minimumTests < cases.size() || minimumTests > 10_000)
            throw new IllegalArgumentException("The minimum must cover every required case and be at most 10000.");
        if (new HashSet<>(cases).size() != cases.size())
            throw new IllegalArgumentException("Required cases contain duplicate identities.");
        if (cases.stream().anyMatch(test -> test.name().length() > 4096 || test.className().length() > 4096
                || !StandardCharsets.UTF_8.newEncoder().canEncode(test.name()) || !StandardCharsets.UTF_8.newEncoder().canEncode(test.className())))
            throw new IllegalArgumentException("Required identities must be valid Unicode with at most 4096 characters per field.");
        if (cases.stream().anyMatch(test -> test.name().equals("minecraft:always_pass")))
            throw new IllegalArgumentException("Framework self-checks are not required business cases.");
        if (GAMETEST.equals(identityKind) && cases.stream().anyMatch(test -> !test.className().isEmpty()
                || !test.name().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")))
            throw new IllegalArgumentException("GameTest cases require full resource IDs and an empty className.");
        List<Integer> fixed = switch (id) {
            case "camp-kit-native-v1" -> NATIVE_CASES;
            case "camp-kit-scoped-v1" -> SCOPED_CASES;
            default -> null;
        };
        if (fixed != null && (!GAMETEST.equals(identityKind) || minimumTests != fixed.size() || !cases.equals(campCases(fixed))))
            throw new IllegalArgumentException("A published Camp Kit manifest cannot be redefined; use a new reviewed version.");
    }

    /** Returns the two fixed gates; loader and platform observations are bound separately to each run. */
    public static List<RequiredCaseManifest> catalog() {
        return List.of(new RequiredCaseManifest("camp-kit-native-v1", GAMETEST, 6, campCases(NATIVE_CASES)),
                new RequiredCaseManifest("camp-kit-scoped-v1", GAMETEST, 13, campCases(SCOPED_CASES)));
    }

    private static List<Identity> campCases(List<Integer> numbers) {
        return numbers.stream().map(number -> new Identity("", String.format(java.util.Locale.ROOT, "copperbench_camp_kit:t%02d", number))).toList();
    }

    /** Validates the supplied digest as well as the complete manifest; unknown fields fail closed. */
    public static RequiredCaseManifest parse(JsonObject value) {
        if (!value.keySet().equals(FIELDS) || !VERSION.equals(string(value, "schemaVersion")))
            throw new IllegalArgumentException("Unsupported or incomplete required-case manifest.");
        List<Identity> cases = new ArrayList<>();
        for (var entry : value.getAsJsonArray("cases")) {
            JsonObject test = entry.getAsJsonObject();
            if (!test.keySet().equals(Set.of("className", "name")))
                throw new IllegalArgumentException("A required case must contain only its raw className and name.");
            cases.add(new Identity(string(test, "className"), string(test, "name")));
        }
        if (!value.get("minimumTests").isJsonPrimitive() || !value.getAsJsonPrimitive("minimumTests").isNumber())
            throw new IllegalArgumentException("minimumTests must be an integer.");
        RequiredCaseManifest manifest = new RequiredCaseManifest(string(value, "id"), string(value, "identityKind"),
                value.get("minimumTests").getAsBigDecimal().intValueExact(), cases);
        if (!manifest.sha256().equals(string(value, "sha256")))
            throw new IllegalArgumentException("Required-case manifest SHA-256 does not match its identities.");
        return manifest;
    }

    private static String string(JsonObject value, String name) {
        if (!value.has(name) || !value.get(name).isJsonPrimitive() || !value.getAsJsonPrimitive(name).isString())
            throw new IllegalArgumentException("Required string: " + name);
        return value.get(name).getAsString();
    }

    /** SHA-256 of length-prefixed UTF-8 fields in sorted identity order; independent of JSON formatting. */
    public String sha256() {
        List<String> fields = new ArrayList<>(List.of(VERSION, id, identityKind, Integer.toString(minimumTests)));
        cases.forEach(test -> { fields.add(test.className()); fields.add(test.name()); });
        return digest(fields);
    }

    static String digest(List<String> fields) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                for (String field : fields) {
                    byte[] encoded = field.getBytes(StandardCharsets.UTF_8);
                    output.writeInt(encoded.length);
                    output.write(encoded);
                }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** Complete request/receipt projection for the separately negotiated acceptance extension. */
    public JsonObject projection() {
        JsonObject result = new JsonObject();
        result.addProperty("schemaVersion", VERSION);
        result.addProperty("id", id);
        result.addProperty("identityKind", identityKind);
        result.addProperty("minimumTests", minimumTests);
        JsonArray identities = new JsonArray();
        cases.forEach(test -> identities.add(test.json()));
        result.add("cases", identities);
        result.addProperty("sha256", sha256());
        return result;
    }

    Identity identity(String className, String name) {
        return new Identity(GAMETEST.equals(identityKind) ? "" : className, name);
    }
}
