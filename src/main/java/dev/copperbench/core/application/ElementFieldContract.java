package dev.copperbench.core.application;

import com.google.gson.*;
import dev.copperbench.release.ElementCoverageCatalog;
import dev.copperbench.release.GeneratorElementCapabilityCatalog;
import net.mcreator.element.GeneratableElement;
import net.mcreator.element.ModElementTypeLoader;
import net.mcreator.element.types.interfaces.NonNullMappable;
import net.mcreator.generator.Generator;
import net.mcreator.minecraft.DataListLoader;
import java.lang.reflect.Modifier;
import java.util.*;

/** Versioned, side-effect-free creation discovery; available means all input shapes were projected. */
public final class ElementFieldContract {
    private ElementFieldContract() {}
    public static boolean supports(String type) { return Set.of("item", "recipe").contains(type); }
    public static boolean known(String type) {
        return ElementCoverageCatalog.isFirstParty(type) || ElementCoverageCatalog.BEDROCK_ADDON_NOT_APPLICABLE.contains(type);
    }

    public static JsonObject discover(String type, String generatorId) {
        if (!known(type)) throw new IllegalArgumentException("Unknown element type: " + type);
        if (generatorId == null) generatorId = "";
        var decision = GeneratorElementCapabilityCatalog.decision(generatorId, type);
        JsonObject result = new JsonObject();
        result.addProperty("elementType", type); result.addProperty("generatorId", generatorId);
        result.addProperty("contractVersion", "1"); result.addProperty("complete", false);
        result.addProperty("availability", decision.reasonCode().equals("GENERATOR_NOT_LOADED") ? "not_exposed" :
                decision.generatable() ? "not_exposed" : "unsupported");
        result.addProperty("reasonCode", decision.reasonCode());
        result.add("fields", new JsonArray());
        result.add("alternatives", new Gson().toJsonTree(List.of(Map.of("operation", "get_mod_element_editor",
                "requires", List.of("elementId"), "scope", "existing_element"))));
        if (!decision.generatable()) return result;
        if (!supports(type)) { result.addProperty("reasonCode", "FIELD_CONTRACT_NOT_EXPOSED"); return result; }
        try {
            var configuration = Generator.GENERATOR_CACHE.get(generatorId);
            var upstream = ModElementTypeLoader.getModElementType(type);
            var included = configuration.getSupportedDefinitionFields(upstream);
            var excluded = configuration.getUnsupportedDefinitionFields(upstream);
            GeneratableElement defaults = type.equals("item") ? StructuredElementDefaults.item(null, null, "") :
                    StructuredElementDefaults.recipe(null, null, "");
            JsonArray fields = new JsonArray();
            Set<String> covered = new HashSet<>();
            for (var field : upstream.getModElementStorageClass().getFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) continue;
                String name = field.getName(); covered.add(name);
                JsonObject descriptor = descriptor(name, FieldInputProjection.schema(field));
                Object value = field.get(defaults);
                descriptor.add("default", value == null ? JsonNull.INSTANCE : ElementFieldCodec.gson().toJsonTree(value, field.getGenericType()));
                descriptor.addProperty("defaultStage", "creation_before_generation_validation");
                descriptor.addProperty("requiredOnCreate", false);
                descriptor.addProperty("requiredByGenerator", field.isAnnotationPresent(javax.annotation.Nonnull.class));
                if (field.isAnnotationPresent(NonNullMappable.class))
                    descriptor.addProperty("generatorDefault", field.getAnnotation(NonNullMappable.class).value());
                var condition = FieldInputProjection.condition(field);
                if (condition != null) descriptor.add("requiredWhen", condition);
                boolean used = (included == null || included.contains(name)) && (excluded == null || !excluded.contains(name));
                descriptor.addProperty("generatorSupport", used ? "declared" : "excluded");
                if (name.equals("name")) {
                    descriptor.remove("default"); descriptor.addProperty("readOnlyAfterCreate", true);
                    descriptor.addProperty("defaultFrom", type.equals("item") ? "displayName" : "payload.name");
                }
                fields.add(descriptor);
            }
            for (String name : ElementMappingSupport.fields(type).stream().sorted().toList()) {
                if (covered.contains(name)) continue;
                JsonObject shape = name.equals("maxStackSize") ? FieldInputProjection.schema(net.mcreator.element.types.Item.class.getField("stackSize")) : FieldInputProjection.typed("string");
                JsonObject descriptor = descriptor(name, shape); descriptor.addProperty("requiredOnCreate", false);
                descriptor.addProperty("generatorSupport", Set.of("modelResource", "textureBase64").contains(name) ? "product_metadata_backend_dependent" : "product");
                if (name.equals("maxStackSize")) { descriptor.addProperty("aliasOf", "/stackSize"); descriptor.addProperty("default", 64); }
                else if (name.equals("displayName")) descriptor.addProperty("defaultFrom", "readable_payload_name");
                else descriptor.addProperty("default", "");
                fields.add(descriptor);
            }
            result.add("fields", fields);
            result.addProperty("availability", "available"); result.addProperty("complete", true);
            result.addProperty("reasonCode", "FIELD_CONTRACT_AVAILABLE");
            result.addProperty("compatibilityPath", "/fields");
            result.addProperty("aliasPolicy", "Top-level and /fields values must agree when both are provided; stackSize and maxStackSize must agree.");
            result.addProperty("unknownFields", "Rejected; stored unknown nested fields require review before rewriting.");
            result.addProperty("maxInputDepth", 64);
            JsonObject restrictions = new JsonObject();
            restrictions.add("includedFields", included == null ? JsonNull.INSTANCE : new Gson().toJsonTree(included));
            restrictions.add("excludedFields", excluded == null ? new JsonArray() : new Gson().toJsonTree(excluded));
            restrictions.addProperty("source", "active_generator_definition");
            restrictions.addProperty("scope", "Declared template coverage; excluded fields may persist but have no promised generated effect. References and contextual requirements are checked during generation.");
            result.add("generatorRestrictions", restrictions);
            result.add("minimalExample", example(type));
            result.add("createRequired", new Gson().toJsonTree(List.of("elementType", "name")));
            JsonObject identity = FieldInputProjection.typed("string");
            identity.addProperty("pattern", ElementMappingSupport.ELEMENT_NAME.pattern());
            result.add("createNameSchema", identity);
            result.addProperty("scope", "Complete creation/edit input metadata; inputSchema uses JSON Schema with reference and valueConstraints annotations for contextual rules. Generation and gameplay still require their own acceptance.");
        } catch (ReflectiveOperationException | IllegalArgumentException exception) {
            result.addProperty("availability", "not_exposed"); result.addProperty("complete", false);
            result.add("fields", new JsonArray()); result.addProperty("reasonCode", "FIELD_SHAPE_NOT_EXPOSED");
        }
        return result;
    }

    private static JsonObject descriptor(String name, JsonObject schema) {
        JsonObject result = new JsonObject(); result.addProperty("path", "/" + name);
        result.addProperty("compatibilityPath", "/fields/" + name); result.add("inputSchema", schema);
        return result;
    }

    private static JsonObject example(String type) {
        JsonObject payload = new JsonObject(), values = new JsonObject();
        payload.addProperty("elementType", type); payload.addProperty("name", type.equals("item") ? "discovery_item" : "discovery_recipe");
        if (type.equals("item")) {
            values.addProperty("displayName", "Discovery Item"); values.addProperty("stackSize", 16);
            values.addProperty("texture", "minecraft:barrier");
        } else {
            values.addProperty("recipeType", "Crafting"); values.addProperty("recipeShapeless", true);
            JsonArray slots = new JsonArray(); slots.add("Items.STICK");
            for (int i = 1; i < 9; i++) slots.add("");
            values.add("recipeSlots", slots); values.addProperty("recipeReturnStack", "Items.DIAMOND");
        }
        payload.add("initialValues", values); return payload;
    }

    /** Pages vanilla mapping values from the installed generator; never downloads or initializes a workspace. */
    public static JsonObject referenceOptions(String type, String generatorId, String mappingSource, String search, int offset, int limit) {
        JsonObject contract = discover(type, generatorId);
        Set<String> sources = new HashSet<>(); collectMappingSources(contract.get("fields"), sources);
        if (!sources.contains(mappingSource)) throw new IllegalArgumentException("Mapping source is not published by this element contract");
        if (offset < 0 || limit < 1 || limit > 200) throw new IllegalArgumentException("offset must be nonnegative and limit must be 1..200");
        var configuration = Generator.GENERATOR_CACHE.get(generatorId);
        var mapping = configuration.getMappingLoader().getMapping(mappingSource);
        Set<?> unsupported = mapping != null && mapping.get("_unsupported") instanceof Collection<?> values ? Set.copyOf(values) : Set.of();
        var entries = DataListLoader.loadDataList(mappingSource).stream()
                .filter(e -> mapping != null && mapping.containsKey(e.getName()) && !unsupported.contains(e.getName()))
                .filter(e -> e.getName().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparing(net.mcreator.minecraft.DataListEntry::getName)).toList();
        JsonObject result = new JsonObject(); result.addProperty("elementType", type); result.addProperty("generatorId", generatorId);
        result.addProperty("mappingSource", mappingSource); result.addProperty("scope", "vanilla_generator_mappings");
        result.addProperty("total", entries.size()); result.addProperty("offset", offset); result.addProperty("limit", limit);
        result.addProperty("truncated", (long)offset + limit < entries.size());
        JsonArray options = new JsonArray();
        entries.stream().skip(offset).limit(limit).forEach(e -> {
            JsonObject option = new JsonObject(); option.addProperty("value", e.getName()); option.addProperty("label", e.getReadableName());
            option.add("requiredApis", e.getRequiredAPIs() == null ? new JsonArray() : new Gson().toJsonTree(e.getRequiredAPIs())); options.add(option);
        });
        result.add("options", options); result.addProperty("workspaceOperation", "list_mod_elements");
        return result;
    }

    private static void collectMappingSources(JsonElement value, Set<String> result) {
        if (value.isJsonArray()) value.getAsJsonArray().forEach(v -> collectMappingSources(v, result));
        else if (value.isJsonObject()) {
            if (value.getAsJsonObject().has("mappingSource")) result.add(value.getAsJsonObject().get("mappingSource").getAsString());
            value.getAsJsonObject().entrySet().forEach(e -> collectMappingSources(e.getValue(), result));
        }
    }
}
