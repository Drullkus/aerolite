package us.drullk.aerolite.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

import java.util.Optional;

/**
 * The noise-based generator of the preset. It carries the settings of the world creation menu.
 * The shafts step does not read the generator. A level reads its settings only when the data map has no value for it.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
public class AeroliteChunkGenerator extends NoiseBasedChunkGenerator {

    /** A generator without the field {@code aerolite} takes the default settings. */
    public static final MapCodec<AeroliteChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(NoiseBasedChunkGenerator::generatorSettings),
            AeroliteSettings.CODEC.optionalFieldOf("aerolite").forGetter(AeroliteChunkGenerator::aeroliteSettings)
    ).apply(instance, AeroliteChunkGenerator::new));

    private final Optional<AeroliteSettings> aeroliteSettings;

    public AeroliteChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings, Optional<AeroliteSettings> aeroliteSettings) {
        super(biomeSource, settings);
        this.aeroliteSettings = aeroliteSettings;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /** The settings of the generator. Empty means the default settings. */
    public Optional<AeroliteSettings> aeroliteSettings() {
        return this.aeroliteSettings;
    }

    /** The settings of the generator, or the default settings from the shipped registry entries. */
    public AeroliteSettings aeroliteSettings(HolderLookup.Provider registries) {
        return this.aeroliteSettings.orElseGet(() -> ShaftLookup.of(registries).defaultSettings());
    }

    public AeroliteChunkGenerator withAeroliteSettings(AeroliteSettings settings) {
        return new AeroliteChunkGenerator(this.getBiomeSource(), this.generatorSettings(), Optional.of(settings));
    }

}
