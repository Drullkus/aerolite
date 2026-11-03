package us.drullk.aerolite.client;

import us.drullk.aerolite.world.TestSettings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.PillarShape;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.ShippedShaftData;
import us.drullk.aerolite.world.pattern.ChaosSettings;
import us.drullk.aerolite.world.pattern.MazeSettings;
import us.drullk.aerolite.world.pattern.PatternSettings;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the JSON export and import of one shaft variation.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-02_09-29_550f132f-0def-4354-8b7f-e97e27655707.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class VariationJsonTest {

    private static final List<ShaftVariation> VARIATIONS = List.of(
            ShippedShaftData.variation("stone"),
            ShippedShaftData.variation("tuff"),
            ShippedShaftData.variation("purpur"),
            ShippedShaftData.variation("copper").withDecayScale(256).withDecayThreshold(0.6F).withErosionThreshold(0.9F),
            ShippedShaftData.variation("mossy").withPattern(MazeSettings.DEFAULT.withType(MazeSettings.Type.RIBS).withStrength(0.6F).withSeam(MazeSettings.Seam.PILLAR))
                    .withShape(PillarShape.BRIDGE).withSurface(Blocks.DIRT_PATH.defaultBlockState()).withTrusses(Blocks.CHAIN.defaultBlockState())
    );

    private static final List<String> KEYS = List.of("bias", "decay_scale", "decay_threshold", "erosion_threshold", "pattern", "shape",
            "interior", "junction", "wall", "path", "surface", "trusses");
    private static final String STONE_BLOCKS = "\"interior\":{\"Name\":\"minecraft:stone\"},\"wall\":{\"Name\":\"minecraft:stone\"},\"path\":{\"Name\":\"minecraft:stone\"}";

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
    void exportWritesEveryFieldInSequence() {
        for (ShaftVariation variation : VARIATIONS) {
            JsonObject object = JsonParser.parseString(this.json.exportVariation(variation)).getAsJsonObject();
            assertEquals(KEYS, List.copyOf(object.keySet()));
        }
    }

    @Test
    void variationOf03LoadsWithTheDefaults() {
        String text = "{\"bias\":0.7,\"decay_scale\":64,\"decay_threshold\":0.35,\"erosion_threshold\":0.5,"
                + "\"interior\":{\"type\":\"minecraft:simple_state_provider\",\"state\":{\"Name\":\"minecraft:tuff\"}},"
                + "\"junction\":{\"type\":\"minecraft:simple_state_provider\",\"state\":{\"Name\":\"minecraft:tuff\"}},"
                + "\"wall\":{\"type\":\"minecraft:simple_state_provider\",\"state\":{\"Name\":\"minecraft:tuff_bricks\"}},"
                + "\"path\":{\"type\":\"minecraft:simple_state_provider\",\"state\":{\"Name\":\"minecraft:polished_tuff\"}}}";
        ShaftVariation parsed = this.json.parseVariation(text).getOrThrow();
        assertEquals(MazeSettings.DEFAULT, parsed.pattern());
        assertEquals(PatternSettings.standard(), parsed.pattern());
        assertEquals(PillarShape.PILLAR, parsed.shape());
        assertEquals(Blocks.POLISHED_TUFF.defaultBlockState(), TestSettings.state(parsed.surface()));
        assertEquals(Blocks.TUFF.defaultBlockState(), TestSettings.state(parsed.trusses()));
        assertEquals(ShippedShaftData.variation("tuff"), parsed);
    }

    @Test
    void variationOf03WithBareStatesLoadsWithTheDefaults() {
        ShaftVariation parsed = this.json.parseVariation("{\"interior\":{\"Name\":\"minecraft:stone\"},"
                + "\"wall\":{\"Name\":\"minecraft:stone_bricks\"},\"path\":{\"Name\":\"minecraft:cobblestone\"}}").getOrThrow();
        assertEquals(ShippedShaftData.variation("stone"), parsed);
    }

    @Test
    void importWithoutASurfaceBlockTakesThePathBlock() {
        ShaftVariation copper = ShippedShaftData.variation("copper").withSurface(Blocks.DIRT_PATH.defaultBlockState());
        JsonObject object = JsonParser.parseString(this.json.exportVariation(copper)).getAsJsonObject();
        assertTrue(object.toString().contains("minecraft:dirt_path"));
        object.remove("surface");
        ShaftVariation parsed = this.json.parseVariation(object.toString()).getOrThrow();
        assertEquals(TestSettings.state(copper.path()), TestSettings.state(parsed.surface()));
        assertEquals(copper.withSurface(copper.path()), parsed);
    }

    @Test
    void importWithoutATrussesBlockTakesTheInteriorBlock() {
        ShaftVariation bridge = ShippedShaftData.variation("stone").withShape(PillarShape.BRIDGE).withTrusses(Blocks.CHAIN.defaultBlockState());
        JsonObject object = JsonParser.parseString(this.json.exportVariation(bridge)).getAsJsonObject();
        assertTrue(object.toString().contains("minecraft:chain"));
        object.remove("trusses");
        ShaftVariation parsed = this.json.parseVariation(object.toString()).getOrThrow();
        assertEquals(TestSettings.state(bridge.interior()), TestSettings.state(parsed.trusses()));
        assertEquals(bridge.withTrusses(bridge.interior()), parsed);
    }

    @Test
    void shapeRoundTrips() {
        for (PillarShape shape : PillarShape.values()) {
            ShaftVariation variation = ShippedShaftData.variation("stone").withShape(shape);
            String text = this.json.exportVariation(variation);
            assertTrue(text.contains("\"shape\":\"" + shape.getSerializedName() + "\""), text);
            assertEquals(variation, this.json.parseVariation(text).getOrThrow());
        }
        assertTrue(this.json.parseVariation("{\"shape\":\"tube\"," + STONE_BLOCKS + "}").isError());
    }

    @Test
    void patternRoundTripsInAVariation() {
        ShaftVariation variation = ShippedShaftData.variation("stone")
                .withPattern(ChaosSettings.DEFAULT.withCells(ChaosSettings.Cells.DELAUNAY).withSpacing(9).withJitter(0.85F))
                .withShape(PillarShape.I_BEAM)
                .withSurface(Blocks.GRAVEL.defaultBlockState())
                .withTrusses(Blocks.IRON_BARS.defaultBlockState());
        ShaftVariation parsed = this.json.parseVariation(this.json.exportVariation(variation)).getOrThrow();
        assertEquals(variation, parsed);
        assertEquals(variation.hashCode(), parsed.hashCode());
        assertFalse(variation.equals(variation.withPattern(PatternSettings.standard())));
        assertFalse(variation.equals(variation.withShape(PillarShape.PILLAR)));
        assertFalse(variation.equals(variation.withSurface(variation.path())));
        assertFalse(variation.equals(variation.withTrusses(variation.interior())));
    }

    @Test
    void unknownPatternTypeGivesAnError() {
        assertTrue(this.json.parseVariation("{\"pattern\":{\"type\":\"aerolite:waves\"}," + STONE_BLOCKS + "}").isError());
        assertTrue(this.json.parseVariation("{\"pattern\":{\"maze\":\"ribs\"}," + STONE_BLOCKS + "}").isError());
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
