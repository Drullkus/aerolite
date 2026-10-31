package us.drullk.aerolite.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import tamaized.beanification.Autowired;
import tamaized.beanification.Configurable;
import us.drullk.aerolite.world.shaft.ShaftLayout;
import us.drullk.aerolite.world.shaft.ShaftPainter;
import us.drullk.aerolite.world.shaft.maze.ShaftMazes;

import javax.annotation.Nullable;

/**
 * The noise-based generator of the preset. It carries the settings and builds the shafts in their own chunk step.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 */
@Configurable
public class AeroliteChunkGenerator extends NoiseBasedChunkGenerator {

    public static final MapCodec<AeroliteChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(NoiseBasedChunkGenerator::generatorSettings),
            AeroliteSettings.CODEC.optionalFieldOf("aerolite", AeroliteSettings.DEFAULT).forGetter(AeroliteChunkGenerator::aeroliteSettings)
    ).apply(instance, AeroliteChunkGenerator::new));

    private final AeroliteSettings aeroliteSettings;
    @Autowired
    private ShaftPainter painter;
    @Autowired
    private ShaftMazes mazes;
    @Nullable
    private volatile CachedLayout cachedLayout;

    private record CachedLayout(long seed, ResourceKey<Level> dimension, int minY, int maxY, ShaftLayout layout) {
    }

    public AeroliteChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings, AeroliteSettings aeroliteSettings) {
        super(biomeSource, settings);
        this.aeroliteSettings = aeroliteSettings;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    public AeroliteSettings aeroliteSettings() {
        return this.aeroliteSettings;
    }

    public AeroliteChunkGenerator withAeroliteSettings(AeroliteSettings settings) {
        return new AeroliteChunkGenerator(this.getBiomeSource(), this.generatorSettings(), settings);
    }

    /** Writes the shafts, columns and junctions of one chunk. A dimension without variations gets no shafts. */
    public void buildShafts(ServerLevel level, ChunkAccess chunk) {
        if (this.aeroliteSettings.variations(level.dimension()).isEmpty()) {
            return;
        }
        this.painter.paint(this.layout(level), chunk);
    }

    /** The shaft layout of the level. One generator serves one dimension, so the layout rarely changes. */
    public ShaftLayout layout(ServerLevel level) {
        long seed = level.getSeed();
        ResourceKey<Level> dimension = level.dimension();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        CachedLayout cached = this.cachedLayout;
        if (cached == null || cached.seed != seed || !cached.dimension.equals(dimension) || cached.minY != minY || cached.maxY != maxY) {
            cached = new CachedLayout(seed, dimension, minY, maxY, new ShaftLayout(this.mazes, seed, dimension, minY, maxY, this.aeroliteSettings.dimension(dimension)));
            this.cachedLayout = cached;
        }
        return cached.layout;
    }

}
