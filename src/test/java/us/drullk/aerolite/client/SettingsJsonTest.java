package us.drullk.aerolite.client;

import net.minecraft.core.HolderLookup;
import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.ShippedShaftData;
import us.drullk.aerolite.world.TestSettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the JSON export and import of the shaft settings. Both go through registry ops of the shipped registries.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
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
