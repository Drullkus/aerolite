package us.drullk.aerolite.client.preview;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import us.drullk.aerolite.Aerolite;

import java.util.Optional;

/**
 * The biome lookup of one preview scene. A biome source that fails without a running server gives the fallback biome.
 * The fallback is the first possible biome of the source, else the Plains.
 * After the first failure, every lookup gives the fallback, so the source throws only once.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
public final class PreviewBiomes {

    private final BiomeSource source;
    private final Climate.Sampler sampler;
    private final Registry<Biome> biomes;
    private volatile boolean failed;

    public PreviewBiomes(BiomeSource source, Climate.Sampler sampler, Registry<Biome> biomes) {
        this.source = source;
        this.sampler = sampler;
        this.biomes = biomes;
    }

    /** The biome at a quart position. */
    public Holder<Biome> at(int quartX, int quartY, int quartZ) {
        if (!this.failed) {
            try {
                return this.source.getNoiseBiome(quartX, quartY, quartZ, this.sampler);
            } catch (RuntimeException exception) {
                this.failed = true;
                Aerolite.LOGGER.warn("The biome source {} fails in the preview, so the preview shows its fallback biome", this.source, exception);
            }
        }
        return this.fallback();
    }

    private Holder<Biome> fallback() {
        Optional<Holder<Biome>> first;
        try {
            first = this.source.possibleBiomes().stream().findFirst();
        } catch (RuntimeException exception) {
            first = Optional.empty();
        }
        return first.orElseGet(() -> this.biomes.getHolderOrThrow(Biomes.PLAINS));
    }

}
