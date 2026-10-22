package dev.copperbench.agent;

import com.google.gson.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Exact, versioned product templates. Never applied to user/assistant messages or loaded history. */
final class AgentCopy {
    private record Entry(String key, String zh, String en, List<String> parameters, Pattern pattern) { }
    private static final List<Entry> ENTRIES = load();
    private static List<Entry> load() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(AgentCopy.class.getResourceAsStream("messages.json")), StandardCharsets.UTF_8)) {
            List<Entry> result = new ArrayList<>();
            JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(item -> {
                JsonObject text = item.getValue().getAsJsonObject();
                String zh = text.get("zh").getAsString(), en = text.get("en").getAsString();
                Matcher variables = Pattern.compile("\\{([a-zA-Z]+)}").matcher(zh);
                List<String> names = new ArrayList<>(); StringBuilder regex = new StringBuilder("\\A"); int offset = 0;
                while (variables.find()) {
                    regex.append(Pattern.quote(zh.substring(offset, variables.start()))).append("(.*?)");
                    names.add(variables.group(1)); offset = variables.end();
                }
                regex.append(Pattern.quote(zh.substring(offset))).append("\\z");
                result.add(new Entry("agent." + item.getKey(), zh, en, names, Pattern.compile(regex.toString(), Pattern.DOTALL)));
            });
            return List.copyOf(result);
        } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
    static void put(JsonObject target, String field, String value) {
        target.addProperty(field, value);
        target.remove(field + "Text");
        if (value == null) return;
        for (Entry entry : ENTRIES) {
            Matcher match = entry.pattern().matcher(value);
            if (!match.matches()) continue;
            JsonObject args = new JsonObject();
            for (int i = 0; i < entry.parameters().size(); i++) args.addProperty(entry.parameters().get(i), match.group(i + 1));
            target.add(field + "Text", AgentJson.object("key", entry.key(), "zh", entry.zh(), "en", entry.en(), "args", args));
            return;
        }
    }

    static void retainValidTranslation(JsonObject message, String field) {
        JsonElement metadata = message.get(field + "Text");
        if (metadata == null) return;
        boolean valid = metadata.isJsonObject();
        JsonObject object = valid ? metadata.getAsJsonObject() : new JsonObject();
        for (String key : List.of("key", "zh", "en")) {
            JsonElement text = object.get(key);
            valid &= text != null && text.isJsonPrimitive() && text.getAsJsonPrimitive().isString()
                    && text.getAsString().length() <= 4096;
        }
        JsonElement args = object.get("args");
        valid &= args != null && args.isJsonObject();
        if (valid) valid = args.getAsJsonObject().size() <= 32 && args.getAsJsonObject().entrySet().stream()
                .allMatch(entry -> entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()
                        && entry.getValue().getAsString().length() <= 32768);
        // Old or damaged optional metadata falls back to its original text, without rewriting free text.
        if (!valid) message.remove(field + "Text");
    }
}
