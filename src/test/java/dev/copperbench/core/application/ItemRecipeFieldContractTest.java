package dev.copperbench.core.application;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.mcreator.element.types.Item;
import net.mcreator.element.types.Recipe;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ItemRecipeFieldContractTest {
    @Test void itemStackBoundsMatchTheExistingWriteValidatorWithoutCoercion() throws Exception {
        JsonObject stack = field("item", "stackSize");
        assertEquals("integer", stack.get("type").getAsString());
        assertEquals(1, stack.get("min").getAsInt());
        assertEquals(99, stack.get("max").getAsInt());
        assertFalse(stack.get("nullable").getAsBoolean());
        for (String accepted : List.of("1", "99")) assertAccepted(Item.class, "stackSize", accepted);
        for (String rejected : List.of("0", "100", "1.0000000000000001"))
            assertIssue(Item.class, "stackSize", rejected, "FIELD_VALUE_OUT_OF_RANGE", "/stackSize");
        assertIssue(Item.class, "stackSize", "\"1\"", "FIELD_TYPE_INVALID", "/stackSize");
        assertIssue(Item.class, "immuneToFire", "\"true\"", "FIELD_TYPE_INVALID", "/immuneToFire");
    }

    @Test void itemAndRecipeOptionsAreActualEnforcedValuesIncludingSpaces() throws Exception {
        JsonObject rarity = field("item", "rarity");
        assertEquals(JsonParser.parseString("[\"COMMON\",\"UNCOMMON\",\"RARE\",\"EPIC\"]"), rarity.get("options"));
        assertAccepted(Item.class, "rarity", "\"UNCOMMON\"");
        assertIssue(Item.class, "rarity", "\"common\"", "FIELD_ENUM_INVALID", "/rarity");
        JsonObject type = field("recipe", "recipeType");
        assertEquals(JsonParser.parseString("""
                ["Crafting","Smelting","Brewing","Blasting","Smoking","Stone cutting","Campfire cooking","Smithing"]
                """), type.get("options"));
        assertAccepted(Recipe.class, "recipeType", "\"Stone cutting\"");
        assertIssue(Recipe.class, "recipeType", "\"stonecutting\"", "FIELD_ENUM_INVALID", "/recipeType");
        assertEquals(JsonParser.parseString("[\"mod\",\"minecraft\"]"), field("recipe", "namespace").get("options"));
        // The current input validator enforces LimitedOptions, including this upstream allowCustom field.
        assertIssue(Recipe.class, "namespace", "\"another_mod\"", "FIELD_ENUM_INVALID", "/namespace");
    }

    @Test void recipeNumericRangesAreNotGuessedFromUiStepsOrDefaults() throws Exception {
        JsonObject amount = field("recipe", "recipeRetstackSize");
        assertEquals(1, amount.get("min").getAsInt());
        assertEquals(99, amount.get("max").getAsInt());
        assertAccepted(Recipe.class, "recipeRetstackSize", "99");
        assertIssue(Recipe.class, "recipeRetstackSize", "100", "FIELD_VALUE_OUT_OF_RANGE", "/recipeRetstackSize");
        assertEquals(1_000_000, field("recipe", "cookingTime").get("max").getAsInt());
        assertIssue(Recipe.class, "cookingTime", "1000001", "FIELD_VALUE_OUT_OF_RANGE", "/cookingTime");
        assertEquals("number", field("recipe", "xpReward").get("type").getAsString());
        assertAccepted(Recipe.class, "xpReward", "0.05");
        assertIssue(Recipe.class, "xpReward", "256.01", "FIELD_VALUE_OUT_OF_RANGE", "/xpReward");
        assertFalse(field("item", "attackSpeed").has("default"));
        assertFalse(field("recipe", "xpReward").has("step"));
    }

    @Test void referenceAndCollectionShapesMatchValidationAndNeverPromiseResolution() throws Exception {
        JsonObject output = field("recipe", "recipeReturnStack");
        assertEquals(JsonParser.parseString("[\"string\",\"object\"]"), output.get("type"));
        assertEquals("string", output.getAsJsonObject("properties").getAsJsonObject("value").get("type").getAsString());
        assertEquals(JsonParser.parseString("[\"value\"]"), output.get("required"));
        assertFalse(output.get("additionalProperties").getAsBoolean());
        assertAccepted(Recipe.class, "recipeReturnStack", "\"Items.COMPASS\"");
        assertAccepted(Recipe.class, "recipeReturnStack", "{\"value\":\"Items.COMPASS\"}");
        assertIssue(Recipe.class, "recipeReturnStack", "42", "FIELD_TYPE_INVALID", "/recipeReturnStack");
        assertIssue(Recipe.class, "recipeReturnStack", "{\"value\":true}", "FIELD_TYPE_INVALID", "/recipeReturnStack/value");
        assertIssue(Recipe.class, "recipeReturnStack", "{\"value\":\"Items.COMPASS\",\"typo\":true}",
                "FIELD_UNSUPPORTED", "/recipeReturnStack/typo");
        JsonObject slots = field("recipe", "recipeSlots");
        assertEquals("array", slots.get("type").getAsString());
        assertFalse(slots.getAsJsonObject("items").get("nullable").getAsBoolean());
        assertAccepted(Recipe.class, "recipeSlots", "[\"Items.COPPER_INGOT\",{\"value\":\"Items.CLOCK\"}]");
        assertIssue(Recipe.class, "recipeSlots", "[null]", "FIELD_TYPE_INVALID", "/recipeSlots/0");
        assertEquals("array", field("item", "repairItems").get("type").getAsString());
        assertIssue(Item.class, "repairItems", "[42]", "FIELD_TYPE_INVALID", "/repairItems/0");
    }

    @Test void partialProjectionDoesNotInventWriteCapabilitiesOrShareMutableResults() {
        for (String type : List.of("item", "recipe")) {
            JsonObject contract = ItemRecipeFieldContract.capabilities(type);
            assertEquals("partial", contract.get("coverage").getAsString());
            assertEquals(type, contract.get("elementType").getAsString());
            assertTrue(contract.get("scope").getAsString().contains("Not a complete element schema"));
            for (var raw : contract.getAsJsonArray("fields")) {
                JsonObject field = raw.getAsJsonObject();
                String name = field.get("name").getAsString();
                assertTrue(ElementMappingSupport.fields(type).contains(name), name);
                assertEquals("/" + name, field.get("path").getAsString());
                assertEquals("/fields/" + name, field.get("compatibilityPath").getAsString());
                assertNotEquals("name", name);
                assertNotEquals("maxStackSize", name);
                if (field.get("type").isJsonPrimitive() && "integer".equals(field.get("type").getAsString())) {
                    assertTrue(field.has("min") && field.has("max"), name);
                    assertTrue(field.get("min").getAsDouble() >= Integer.MIN_VALUE, name);
                    assertTrue(field.get("max").getAsDouble() <= Integer.MAX_VALUE, name);
                }
            }
        }
        field("item", "stackSize").addProperty("max", 1000);
        assertEquals(99, field("item", "stackSize").get("max").getAsInt());
        assertThrows(IllegalArgumentException.class, () -> ItemRecipeFieldContract.capabilities("block"));
    }

    private static JsonObject field(String type, String name) {
        for (var raw : ItemRecipeFieldContract.capabilities(type).getAsJsonArray("fields"))
            if (name.equals(raw.getAsJsonObject().get("name").getAsString())) return raw.getAsJsonObject();
        throw new AssertionError("Missing advertised field: " + type + "." + name);
    }

    private static void assertAccepted(Class<?> storage, String name, String value) throws Exception {
        assertNull(GenericFieldInputContract.validate(storage.getField(name), JsonParser.parseString(value), "/" + name), value);
    }

    private static void assertIssue(Class<?> storage, String name, String value, String code, String path) throws Exception {
        var issue = GenericFieldInputContract.validate(storage.getField(name), JsonParser.parseString(value), "/" + name);
        assertNotNull(issue, value);
        assertEquals(code, issue.code(), value);
        assertEquals(path, issue.path(), value);
    }
}
