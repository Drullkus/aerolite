package us.drullk.aerolite.client;

import us.drullk.aerolite.world.TestSettings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.ShippedShaftData;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the JSON export and import of one shaft variation.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-02_09-29_550f132f-0def-4354-8b7f-e97e27655707.md}.
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
            assertTrue(object.has("junction"));
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
    void exportWritesTheJunctionBlockAfterTheInteriorBlock() {
        JsonObject object = JsonParser.parseString(this.json.exportVariation(ShippedShaftData.variation("stone"))).getAsJsonObject();
        assertEquals(Set.of("bias", "decay_scale", "decay_threshold", "erosion_threshold", "interior", "junction", "wall", "path"), object.keySet());
        List<String> blocks = object.keySet().stream().filter(key -> List.of("interior", "junction", "wall", "path").contains(key)).toList();
        assertEquals(List.of("interior", "junction", "wall", "path"), blocks);
    }

    @Test
    void importKeepsAJunctionBlock() {
        ShaftVariation chiseled = ShippedShaftData.variation("stone").withJunction(Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        String text = this.json.exportVariation(chiseled);
        assertTrue(text.contains("minecraft:chiseled_stone_bricks"));
        assertEquals(chiseled, this.json.parseVariation(text).getOrThrow());
    }

    @Test
    void importWithoutAJunctionBlockTakesTheInteriorBlock() {
        ShaftVariation copper = ShippedShaftData.variation("copper").withJunction(Blocks.CHISELED_COPPER.defaultBlockState());
        JsonObject object = JsonParser.parseString(this.json.exportVariation(copper)).getAsJsonObject();
        object.remove("junction");
        ShaftVariation parsed = this.json.parseVariation(object.toString()).getOrThrow();
        assertEquals(TestSettings.state(copper.interior()), TestSettings.state(parsed.junction()));
        assertEquals(copper.withJunction(copper.interior()), parsed);
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
