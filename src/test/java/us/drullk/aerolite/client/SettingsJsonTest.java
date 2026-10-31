package us.drullk.aerolite.client;

import org.junit.jupiter.api.Test;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.ShippedShaftData;
import us.drullk.aerolite.world.TestSettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the JSON export and import of the preset settings.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
class SettingsJsonTest {

    private final SettingsJson json = new SettingsJson();

    @Test
    void exportThenParseGivesEqualSettings() {
        for (AeroliteSettings settings : new AeroliteSettings[]{ShippedShaftData.lookup().defaultSettings(), TestSettings.SETTINGS}) {
            assertEquals(settings, this.json.parse(this.json.export(settings)).getOrThrow());
        }
    }

    @Test
    void exportIsOneLineWithoutIndentation() {
        String text = this.json.export(TestSettings.SETTINGS);
        assertFalse(text.contains("\n"));
        assertFalse(text.contains("\r"));
        assertFalse(text.contains(" "));
        assertFalse(text.contains("\t"));
    }

    @Test
    void badTextGivesAnError() {
        assertTrue(this.json.parse("{\"min_shaft_spacing\": \"bad\"}").isError());
        assertTrue(this.json.parse("{\"cloud_height\": \"bad\"}").isError());
        assertTrue(this.json.parse("{\"cloud_height\": 0, \"dimensions\": 5}").isError());
        assertTrue(this.json.parse("{\"min_shaft_spacing\": ").isError());
        assertTrue(this.json.parse("not json at all {").isError());
        assertTrue(this.json.parse("").isError());
    }

}
