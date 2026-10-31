package us.drullk.aerolite.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.neoforge.registries.datamaps.DataMapType;

/**
 * The data map types of Aerolite. Neither data map syncs to the client.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
public final class ShaftDataMaps {

    /** The variations of a dimension, keyed by level stem id. A dimension without a value gets no shafts. */
    public static final DataMapType<LevelStem, VariationList> SHAFT_VARIATIONS = DataMapType
            .builder(ShaftRegistries.id("shaft_variations"), Registries.LEVEL_STEM, VariationList.CODEC)
            .build();

    /** The cloud height of a dimension type. */
    public static final DataMapType<DimensionType, Integer> CLOUD_HEIGHT = DataMapType
            .builder(ShaftRegistries.id("cloud_height"), Registries.DIMENSION_TYPE, AeroliteSettings.CLOUD_HEIGHT_CODEC)
            .build();

    private ShaftDataMaps() {
    }

}
