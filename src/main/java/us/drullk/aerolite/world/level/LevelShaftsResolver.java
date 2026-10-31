package us.drullk.aerolite.world.level;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import tamaized.beanification.Autowired;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.AeroliteChunkGenerator;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.AeroliteWorldgen;
import us.drullk.aerolite.world.DimensionSettings;
import us.drullk.aerolite.world.ShaftDataMaps;
import us.drullk.aerolite.world.ShaftLookup;
import us.drullk.aerolite.world.VariationList;
import us.drullk.aerolite.world.shaft.ShaftLayout;
import us.drullk.aerolite.world.shaft.maze.ShaftMazes;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Resolves the shaft data of a server level from the data maps, or from the settings of an Aerolite generator.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
@Component
public final class LevelShaftsResolver {

    private final ShaftMazes mazes;
    private final DimensionShaftValues values;

    public LevelShaftsResolver(@Autowired ShaftMazes mazes, @Autowired DimensionShaftValues values) {
        this.mazes = mazes;
        this.values = values;
    }

    /** The shaft data of the level, from its dimension, its generator and the loaded data maps. */
    public LevelShafts resolve(ServerLevel level) {
        return this.resolve(level, level.dimension(), level.getChunkSource().getGenerator(), this.values.get(level.dimension()));
    }

    /**
     * The shaft data of a dimension of the level's world.
     * A data map value wins. Without one, the generator settings for the dimension apply, with their cloud height.
     *
     * @param value the data map value of the dimension, if one exists
     */
    public LevelShafts resolve(ServerLevel level, ResourceKey<Level> dimension, ChunkGenerator generator, Optional<VariationList> value) {
        ShaftLookup lookup = ShaftLookup.of(level.registryAccess());
        OptionalInt cloudHeight = this.cloudHeight(level);
        DimensionSettings settings = DimensionSettings.EMPTY;
        LevelShafts.Source source = LevelShafts.Source.NONE;
        if (value.isPresent()) {
            settings = lookup.dimension(dimension, value.get());
            source = LevelShafts.Source.DATA_MAP;
        } else if (generator instanceof AeroliteChunkGenerator aerolite) {
            AeroliteSettings generatorSettings = aerolite.aeroliteSettings(level.registryAccess());
            if (generatorSettings.dimensions().containsKey(dimension)) {
                settings = generatorSettings.dimension(dimension);
                source = LevelShafts.Source.GENERATOR;
                if (level.dimensionTypeRegistration().is(AeroliteWorldgen.OVERWORLD_TYPE)) {
                    cloudHeight = OptionalInt.of(generatorSettings.cloudHeight());
                }
            }
        }
        ShaftLayout layout = new ShaftLayout(this.mazes, level.getSeed(), dimension, level.getMinBuildHeight(), level.getMaxBuildHeight(), settings);
        return new LevelShafts(settings, layout, cloudHeight, source);
    }

    /** The value of the data map {@code aerolite:cloud_height} for the dimension type of the level. */
    private OptionalInt cloudHeight(ServerLevel level) {
        Optional<ResourceKey<DimensionType>> type = level.dimensionTypeRegistration().unwrapKey();
        if (type.isEmpty()) {
            return OptionalInt.empty();
        }
        Integer height = level.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE).getData(ShaftDataMaps.CLOUD_HEIGHT, type.get());
        return height == null ? OptionalInt.empty() : OptionalInt.of(height);
    }

}
