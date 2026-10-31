package us.drullk.aerolite.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.ShippedShaftData;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the JSON export and import of one shaft variation.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
class VariationJsonTest {

    private static final List<ShaftVariation> VARIATIONS = List.of(
            ShippedShaftData.variation("stone"),
            ShippedShaftData.variation("tuff"),
            ShippedShaftData.variation("purpur"),
            ShippedShaftData.variation("copper").withDecayScale(256).withDecayThreshold(0.6F).withErosionThreshold(0.9F)
    );

    private final SettingsJson json = new SettingsJson();

    @Test
    void exportDropsTheWeight() {
        for (ShaftVariation variation : VARIATIONS) {
            String text = this.json.exportVariation(variation);
            JsonObject object = JsonParser.parseString(text).getAsJsonObject();
            assertFalse(object.has("weight"));
            assertTrue(object.has("interior"));
            assertTrue(object.has("wall"));
            assertTrue(object.has("path"));
            assertFalse(text.contains("\n"));
            assertFalse(text.contains(" "));
        }
    }

    @Test
    void importIgnoresAWeight() {
        ShaftVariation stone = ShippedShaftData.variation("stone");
        JsonObject object = JsonParser.parseString(this.json.exportVariation(stone)).getAsJsonObject();
        object.addProperty("weight", 12);
        assertEquals(stone, this.json.parseVariation(object.toString()).getOrThrow());
    }

    @Test
    void roundTripGivesTheSameVariation() {
        for (ShaftVariation variation : VARIATIONS) {
            assertEquals(variation, this.json.parseVariation(this.json.exportVariation(variation)).getOrThrow());
        }
    }

    @Test
    void badTextGivesAnError() {
        assertTrue(this.json.parseVariation("not json at all {").isError());
        assertTrue(this.json.parseVariation("").isError());
        assertTrue(this.json.parseVariation("[1, 2]").isError());
        assertTrue(this.json.parseVariation("{\"interior\": ").isError());
        assertTrue(this.json.parseVariation("{\"interior\": {\"Name\": \"minecraft:stone\"}}").isError());
        assertTrue(this.json.parseVariation("{\"decay_scale\": 100, \"interior\": {\"Name\": \"minecraft:stone\"},"
                + " \"wall\": {\"Name\": \"minecraft:stone\"}, \"path\": {\"Name\": \"minecraft:stone\"}}").isError());
    }

}
