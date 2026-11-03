package us.drullk.aerolite.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.PillarShape;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.ShippedShaftData;
import us.drullk.aerolite.world.TestSettings;
import us.drullk.aerolite.world.pattern.SurfacePathSettings;
import us.drullk.aerolite.world.pattern.TilesSettings;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the JSON export and import of the shaft settings. Both go through registry ops of the shipped registries.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-02_09-29_550f132f-0def-4354-8b7f-e97e27655707.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
class SettingsJsonTest {

    private static final HolderLookup.Provider REGISTRIES = ShippedShaftData.registries();

    private final SettingsJson json = new SettingsJson();

    @Test
    void exportThenParseGivesEqualSettings() {
        for (AeroliteSettings settings : new AeroliteSettings[]{ShippedShaftData.lookup().defaultSettings(), TestSettings.SETTINGS}) {
            assertEquals(settings, this.json.parse(this.json.export(settings, REGISTRIES), REGISTRIES).getOrThrow());
        }
    }

    @Test
    void exportKeepsAJunctionBlock() {
        ShaftVariation chiseled = TestSettings.STONE.withJunction(Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        AeroliteSettings settings = TestSettings.SETTINGS.withVariation(Level.OVERWORLD, 0, chiseled);
        String text = this.json.export(settings, REGISTRIES);
        assertTrue(text.contains("\"junction\":{\"state\":{\"Name\":\"minecraft:chiseled_stone_bricks\"},\"type\":\"minecraft:simple_state_provider\"}"), text);
        assertEquals(settings, this.json.parse(text, REGISTRIES).getOrThrow());
    }

    @Test
    void settingsWithoutJunctionBlocksTakeTheInteriorBlocks() {
        JsonObject json = JsonParser.parseString(this.json.export(TestSettings.SETTINGS, REGISTRIES)).getAsJsonObject();
        assertTrue(removeFields(json, Set.of("junction")) > 0);
        assertEquals(TestSettings.SETTINGS, this.json.parse(json.toString(), REGISTRIES).getOrThrow());
    }

    @Test
    void settingsOf03LoadWithTheDefaults() {
        JsonObject json = JsonParser.parseString(this.json.export(TestSettings.SETTINGS, REGISTRIES)).getAsJsonObject();
        assertEquals(4 * 4, removeFields(json, Set.of("pattern", "shape", "surface", "trusses")));
        assertEquals(TestSettings.SETTINGS, this.json.parse(json.toString(), REGISTRIES).getOrThrow());
    }

    @Test
    void exportKeepsThePatternTheShapeAndTheNewBlocks() {
        ShaftVariation tiles = TestSettings.STONE
                .withPattern(TilesSettings.DEFAULT.withSize(TilesSettings.Size.SMALL).withStagger(TilesSettings.Stagger.X)
                        .withPaths(SurfacePathSettings.DEFAULT.withSurfacePaths(true).withCutting(true)))
                .withShape(PillarShape.BRIDGE)
                .withSurface(Blocks.DIRT_PATH.defaultBlockState())
                .withTrusses(Blocks.CHAIN.defaultBlockState());
        AeroliteSettings settings = TestSettings.SETTINGS.withVariation(Level.OVERWORLD, 0, tiles);
        String text = this.json.export(settings, REGISTRIES);
        assertTrue(text.contains("\"type\":\"aerolite:tiles\""), text);
        assertTrue(text.contains("\"stagger\":\"x\""), text);
        assertTrue(text.contains("\"shape\":\"bridge\""), text);
        assertEquals(settings, this.json.parse(text, REGISTRIES).getOrThrow());
    }

    /** Removes every field with one of the names from the JSON tree. Returns the count of removed fields. */
    private static int removeFields(JsonElement element, Set<String> names) {
        int removed = 0;
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (String name : names) {
                if (object.remove(name) != null) {
                    removed++;
                }
            }
            for (String key : object.keySet()) {
                removed += removeFields(object.get(key), names);
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                removed += removeFields(child, names);
            }
        }
        return removed;
    }

    @Test
    void exportIsOneLineWithoutIndentation() {
        String text = this.json.export(TestSettings.SETTINGS, REGISTRIES);
        assertFalse(text.contains("\n"));
        assertFalse(text.contains("\r"));
        assertFalse(text.contains(" "));
        assertFalse(text.contains("\t"));
    }

    @Test
    void badTextGivesAnError() {
        for (String text : new String[]{"{\"min_shaft_spacing\": \"bad\"}", "{\"cloud_height\": \"bad\"}", "{\"cloud_height\": 0, \"dimensions\": 5}",
                "{\"min_shaft_spacing\": ", "not json at all {", ""}) {
            assertTrue(this.json.parse(text, REGISTRIES).isError(), text);
        }
    }

    @Test
    void settingsWithoutDimensionsReadTheShippedLists() {
        AeroliteSettings parsed = this.json.parse("{\"min_shaft_spacing\": 64, \"max_shaft_spacing\": 96}", REGISTRIES).getOrThrow();
        assertEquals(ShippedShaftData.lookup().defaultSettings().dimensions().keySet(), parsed.dimensions().keySet());
        parsed.dimensions().values().forEach(dimension -> assertEquals(64, dimension.placement().minShaftSpacing()));
    }

}
