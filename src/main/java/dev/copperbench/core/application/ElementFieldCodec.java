package dev.copperbench.core.application;

import com.google.gson.*;
import java.awt.Color;
import net.mcreator.element.parts.procedure.RetvalProcedure;
import net.mcreator.generator.mapping.MappableElement;
import net.mcreator.ui.minecraft.states.StateMap;

/** The shared field adapter set used by structured persistence and discovery defaults. */
public final class ElementFieldCodec {
    private ElementFieldCodec() {}
    private static final Gson GSON = create();
    public static Gson gson() { return GSON; }
    private static Gson create() {
        GsonBuilder builder = new GsonBuilder().disableHtmlEscaping().setStrictness(Strictness.LENIENT)
                .registerTypeAdapter(Color.class, (JsonDeserializer<Color>) (json, type, context) -> {
                    if (json == null || json.isJsonNull()) return null;
                    return new Color(json.isJsonObject() ? json.getAsJsonObject().get("value").getAsInt() : json.getAsInt(), true);
                });
        RetvalProcedure.GSON_ADAPTERS.forEach(builder::registerTypeAdapter);
        builder.registerTypeAdapter(StateMap.class, new StateMap.GSONAdapter());
        builder.registerTypeHierarchyAdapter(MappableElement.class, new MappableElement.GSONAdapter());
        return builder.create();
    }
}
