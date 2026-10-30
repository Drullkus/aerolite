package us.drullk.aerolite.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the codec of the preset settings.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 */
class AeroliteSettingsTest {

    @Test
    void settingsRoundTrip() {
        AeroliteSettings settings = AeroliteSettings.DEFAULT.withDecayScale(64).withDecayThreshold(0.35F).withColumnChance(0.6F).withShaftSpacing(64, 96).withErosionThreshold(0.55F).withCloudHeight(200);
        JsonElement json = AeroliteSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow();
        assertEquals(settings, AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void missingFieldsTakeTheDefaults() {
        assertEquals(AeroliteSettings.DEFAULT, AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow());
    }

    @Test
    void decayScaleIsAPowerOfTwo() {
        assertTrue(AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"decay_scale\": 48}")).isError());
        assertTrue(AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"decay_scale\": 2048}")).isError());
    }

    @Test
    void erosionStaysAboveTheDecayThresholdOfAnOldSave() {
        AeroliteSettings settings = AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"decay_threshold\": 0.9}")).getOrThrow();
        assertEquals(0.95F, settings.erosionThreshold(), 1e-6);
    }

    @Test
    void minimumSpacingDoesNotExceedMaximumSpacing() {
        assertTrue(AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"min_shaft_spacing\": 512, \"max_shaft_spacing\": 256}")).isError());
    }

    @Test
    void cloudHeightDefaultsToMinus32() {
        assertEquals(-32, AeroliteSettings.DEFAULT.cloudHeight());
    }

    @Test
    void cloudHeightRoundTrip() {
        AeroliteSettings settings = AeroliteSettings.DEFAULT.withCloudHeight(136);
        JsonElement json = AeroliteSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow();
        assertEquals(136, json.getAsJsonObject().get("cloud_height").getAsInt());
        assertEquals(settings, AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void cloudHeightStaysInsideTheBuildHeight() {
        assertEquals(-64, AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"cloud_height\": -64}")).getOrThrow().cloudHeight());
        assertEquals(320, AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"cloud_height\": 320}")).getOrThrow().cloudHeight());
        assertTrue(AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"cloud_height\": -65}")).isError());
        assertTrue(AeroliteSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"cloud_height\": 321}")).isError());
    }

}
