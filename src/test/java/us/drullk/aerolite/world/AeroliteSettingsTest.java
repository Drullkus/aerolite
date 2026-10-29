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
 */
class AeroliteSettingsTest {

    @Test
    void settingsRoundTrip() {
        AeroliteSettings settings = AeroliteSettings.DEFAULT.withDecayScale(64).withDecayThreshold(0.35F).withColumnChance(0.6F).withShaftSpacing(64, 96).withErosionThreshold(0.55F);
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

}
