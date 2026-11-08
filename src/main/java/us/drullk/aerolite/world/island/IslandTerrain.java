package us.drullk.aerolite.world.island;

import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import us.drullk.aerolite.mixin.NoiseChunkAccessor;

/**
 * The terrain of one chunk from noise settings without a level: the noise fill of the vanilla noise generator, then its surface.
 * The preview uses it. It has no structure manager, so the terrain adaptation of structures is absent.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 */
public final class IslandTerrain {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final NoiseGeneratorSettings settings;
    private final RandomState randomState;
    private final Registry<Biome> biomes;
    private final BiomeManager biomeManager;
    private final Aquifer.FluidPicker fluidPicker;
    private final NoiseBasedChunkGenerator generator;

    /**
     * @param source the biome source of the dimension; the surface rules read the biomes through {@code biomeLookup}
     * @param biomeLookup the biome at a quart position
     */
    public IslandTerrain(NoiseGeneratorSettings settings, RandomState randomState, BiomeSource source, BiomeManager.NoiseBiomeSource biomeLookup,
                         Registry<Biome> biomes, long seed) {
        this.settings = settings;
        this.randomState = randomState;
        this.biomes = biomes;
        this.biomeManager = new BiomeManager(biomeLookup, BiomeManager.obfuscateSeed(seed));
        this.fluidPicker = fluidPicker(settings);
        this.generator = new NoiseBasedChunkGenerator(source, Holder.direct(settings));
    }

    /** Fills the chunk with the terrain and builds its surface. */
    public void generate(ChunkAccess chunk) {
        this.fill(chunk);
        this.surface(chunk);
    }

    private static Aquifer.FluidPicker fluidPicker(NoiseGeneratorSettings settings) {
        // [VANILLACOPY] NoiseBasedChunkGenerator#createFluidPicker
        Aquifer.FluidStatus lava = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
        int seaLevel = settings.seaLevel();
        Aquifer.FluidStatus sea = new Aquifer.FluidStatus(seaLevel, settings.defaultFluid());
        return (x, y, z) -> y < Math.min(-54, seaLevel) ? lava : sea;
    }

    /** The noise chunk of the chunk. A chunk keeps its noise chunk, so the fill and the surface share one. */
    private NoiseChunk noiseChunk(ChunkAccess chunk) {
        return chunk.getOrCreateNoiseChunk(access -> NoiseChunk.forChunk(access, this.randomState,
                new Beardifier(ObjectLists.<Beardifier.Rigid>emptyList().iterator(), ObjectLists.<JigsawJunction>emptyList().iterator()),
                this.settings, this.fluidPicker, Blender.empty()));
    }

    /** Writes the terrain blocks of the chunk. */
    public void fill(ChunkAccess chunk) {
        // [VANILLACOPY] NoiseBasedChunkGenerator#fillFromNoise, the cell range; the fill runs on the calling thread
        NoiseSettings noiseSettings = this.settings.noiseSettings().clampToHeightAccessor(chunk.getHeightAccessorForGeneration());
        int minY = noiseSettings.minY();
        int minCellY = Mth.floorDiv(minY, noiseSettings.getCellHeight());
        int cellCountY = Mth.floorDiv(noiseSettings.height(), noiseSettings.getCellHeight());
        if (cellCountY <= 0) {
            return;
        }
        // [VANILLACOPY] NoiseBasedChunkGenerator#doFill, without the debug surface and the fluid update schedule
        NoiseChunk noiseChunk = this.noiseChunk(chunk);
        NoiseChunkAccessor accessor = (NoiseChunkAccessor) noiseChunk;
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        ChunkPos pos = chunk.getPos();
        int minBlockX = pos.getMinBlockX();
        int minBlockZ = pos.getMinBlockZ();
        noiseChunk.initializeForFirstCellX();
        int cellWidth = accessor.aerolite$cellWidth();
        int cellHeight = accessor.aerolite$cellHeight();
        int cellsX = 16 / cellWidth;
        int cellsZ = 16 / cellWidth;
        for (int cellX = 0; cellX < cellsX; cellX++) {
            noiseChunk.advanceCellX(cellX);
            for (int cellZ = 0; cellZ < cellsZ; cellZ++) {
                int sectionIndex = chunk.getSectionsCount() - 1;
                LevelChunkSection section = chunk.getSection(sectionIndex);
                for (int cellY = cellCountY - 1; cellY >= 0; cellY--) {
                    noiseChunk.selectCellYZ(cellY, cellZ);
                    for (int inCellY = cellHeight - 1; inCellY >= 0; inCellY--) {
                        int y = (minCellY + cellY) * cellHeight + inCellY;
                        int localY = y & 15;
                        int index = chunk.getSectionIndex(y);
                        if (sectionIndex != index) {
                            sectionIndex = index;
                            section = chunk.getSection(index);
                        }
                        noiseChunk.updateForY(y, inCellY / (double) cellHeight);
                        for (int inCellX = 0; inCellX < cellWidth; inCellX++) {
                            int x = minBlockX + cellX * cellWidth + inCellX;
                            int localX = x & 15;
                            noiseChunk.updateForX(x, inCellX / (double) cellWidth);
                            for (int inCellZ = 0; inCellZ < cellWidth; inCellZ++) {
                                int z = minBlockZ + cellZ * cellWidth + inCellZ;
                                int localZ = z & 15;
                                noiseChunk.updateForZ(z, inCellZ / (double) cellWidth);
                                BlockState state = accessor.aerolite$getInterpolatedState();
                                if (state == null) {
                                    state = this.settings.defaultBlock();
                                }
                                if (state != AIR) {
                                    section.setBlockState(localX, localY, localZ, state, false);
                                    oceanFloor.update(localX, y, localZ, state);
                                    worldSurface.update(localX, y, localZ, state);
                                }
                            }
                        }
                    }
                }
            }
            noiseChunk.swapSlices();
        }
        noiseChunk.stopInterpolation();
    }

    /** Builds the surface of the filled chunk with the surface rules of the settings. */
    public void surface(ChunkAccess chunk) {
        // [VANILLACOPY] NoiseBasedChunkGenerator#buildSurface, with the biome manager of this terrain
        LevelHeightAccessor height = chunk.getHeightAccessorForGeneration();
        WorldGenerationContext context = new WorldGenerationContext(this.generator, height);
        this.randomState.surfaceSystem().buildSurface(this.randomState, this.biomeManager, this.biomes, this.settings.useLegacyRandomSource(),
                context, chunk, this.noiseChunk(chunk), this.settings.surfaceRule());
    }

}
