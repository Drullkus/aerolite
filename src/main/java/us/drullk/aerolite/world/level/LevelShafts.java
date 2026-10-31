package us.drullk.aerolite.world.level;

import us.drullk.aerolite.world.DimensionSettings;
import us.drullk.aerolite.world.shaft.ShaftLayout;

import java.util.OptionalInt;

/**
 * The resolved shaft data of one server level.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 *
 * @param settings    the placement and the weighted variations of the dimension
 * @param layout      the shaft layout of the level
 * @param cloudHeight the cloud height that the server sends to the players of the level, if the level has one
 * @param source      where the settings come from
 */
public record LevelShafts(DimensionSettings settings, ShaftLayout layout, OptionalInt cloudHeight, Source source) {

    /** Where the shaft settings of a level come from. */
    public enum Source {
        /** The data map {@code aerolite:shaft_variations} has a value for the dimension. */
        DATA_MAP,
        /** The server claimed the settings of the world creation menu for a new world, and they list the dimension. */
        PENDING,
        /** The generator of the level carries settings for the dimension, and the data map has no value. */
        GENERATOR,
        /** Neither source has settings. The level gets no shafts. */
        NONE
    }

}
