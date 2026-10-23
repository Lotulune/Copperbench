package dev.copperbench.assets;

import com.google.gson.*;
import java.util.*;

/** Geometry for the standard static Java model templates, usable before a client has been downloaded. */
final class PreviewModels {
    static final List<String> DIRECTIONS = List.of("down", "up", "north", "south", "west", "east");
    private PreviewModels() {}

    static JsonObject template(String id) {
        if (!id.startsWith("minecraft:")) return null;
        String name = id.substring(10);
        JsonObject model = new JsonObject();
        JsonObject textures = new JsonObject(); model.add("textures", textures);
        JsonArray elements = new JsonArray(); model.add("elements", elements);
        switch (name) {
            case "builtin/generated", "item/generated", "item/handheld", "item/handheld_rod" -> {
                model.addProperty("previewGenerated", true); return model;
            }
            case "block/block" -> { return model; }
            case "block/cube" -> elements.add(box(0, 0, 0, 16, 16, 16));
            case "block/cube_all", "block/leaves" -> {
                DIRECTIONS.forEach(face -> textures.addProperty(face, "#all"));
                elements.add(box(0, 0, 0, 16, 16, 16));
            }
            case "block/cube_column", "block/cube_column_horizontal" -> {
                DIRECTIONS.forEach(face -> textures.addProperty(face, Set.of("up", "down").contains(face) ? "#end" : "#side"));
                elements.add(box(0, 0, 0, 16, 16, 16));
                if (name.endsWith("horizontal")) model.addProperty("previewRotationX", 90);
            }
            case "block/cube_bottom_top", "block/orientable", "block/orientable_with_bottom", "block/slab", "block/slab_top", "block/stairs" -> {
                DIRECTIONS.forEach(face -> textures.addProperty(face, face.equals("down") ? "#bottom" : face.equals("up") ? "#top"
                        : face.equals("north") && name.contains("orientable") ? "#front" : "#side"));
                if (name.equals("block/orientable")) textures.addProperty("bottom", "#top");
                elements.add(box(0, name.equals("block/slab_top") ? 8 : 0, 0, 16,
                        Set.of("block/slab", "block/stairs").contains(name) ? 8 : 16, 16));
                if (name.equals("block/stairs")) elements.add(box(8, 8, 0, 16, 16, 16));
            }
            case "block/cross", "block/tinted_cross" -> {
                for (int angle : new int[] {-45, 45}) {
                    JsonObject element = box(.8, 0, 8, 15.2, 16, 8);
                    JsonObject faces = new JsonObject();
                    for (String face : List.of("north", "south")) { JsonObject f = new JsonObject(); f.addProperty("texture", "#cross"); f.add("uv", array(0, 0, 16, 16)); faces.add(face, f); }
                    element.add("faces", faces);
                    JsonObject rotation = new JsonObject(); rotation.add("origin", array(8, 8, 8)); rotation.addProperty("axis", "y"); rotation.addProperty("angle", angle);
                    element.add("rotation", rotation); elements.add(element);
                }
            }
            case "block/crop" -> {
                for (int p : new int[] {4, 12}) for (boolean x : new boolean[] {true, false}) {
                    JsonObject element = x ? box(p, 0, 0, p, 16, 16) : box(0, 0, p, 16, 16, p);
                    JsonObject faces = new JsonObject();
                    for (String face : x ? List.of("east", "west") : List.of("north", "south")) { JsonObject f = new JsonObject(); f.addProperty("texture", "#crop"); f.add("uv", array(0, 0, 16, 16)); faces.add(face, f); }
                    element.add("faces", faces); elements.add(element);
                }
            }
            default -> { return null; }
        }
        return model;
    }

    static JsonObject box(double x, double y, double z, double x2, double y2, double z2) {
        JsonObject result = new JsonObject(); result.add("from", array(x, y, z)); result.add("to", array(x2, y2, z2));
        JsonObject faces = new JsonObject();
        for (String face : DIRECTIONS) { JsonObject f = new JsonObject(); f.addProperty("texture", "#" + face); faces.add(face, f); }
        result.add("faces", faces); return result;
    }
    static JsonArray array(double... values) { JsonArray result = new JsonArray(); for (double n : values) result.add(n); return result; }
}
