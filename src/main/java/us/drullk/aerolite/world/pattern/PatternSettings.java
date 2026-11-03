package us.drullk.aerolite.world.pattern;

import com.mojang.serialization.Codec;
import us.drullk.aerolite.world.AeroliteCodecs;

import java.util.List;

/**
 * The surface pattern of a variation and its options. The key {@code type} of the JSON picks the record.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public sealed interface PatternSettings permits MazeSettings, BricksSettings, TilesSettings, ScalesSettings, ChaosSettings {

    /** Every field of each pattern type is written after the key {@code type}. A missing field takes its default. */
    Codec<PatternSettings> CODEC = AeroliteCodecs.sequenced(PatternType.CODEC.<PatternSettings>dispatchMap("type", PatternSettings::patternType, PatternType::codec), List.of("type")).codec();

    /** The pattern of a 0.3 variation: the uniform maze. */
    static PatternSettings standard() {
        return MazeSettings.DEFAULT;
    }

    PatternType patternType();

}
