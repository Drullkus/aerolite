package us.drullk.aerolite.world.island;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.island.node.IslandCarrier;
import us.drullk.aerolite.world.island.node.IslandFunction;
import us.drullk.aerolite.world.island.variation.IslandPicks;
import us.drullk.aerolite.world.island.variation.IslandVariation;
import us.drullk.aerolite.world.island.variation.IslandVariationList;
import us.drullk.aerolite.world.island.variation.IslandVariationRegistries;
import us.drullk.aerolite.world.island.variation.PreviewOverride;
import us.drullk.aerolite.world.island.variation.VariationLookup;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * The island biome source, the biome source type {@code aerolite:island_biomes}. It gives each block the biome source of its variation.
 * The source searches the six climate functions of the sampler for a carrier, inside holders and markers.
 * The island function of the first carrier gives the cell of each lookup.
 * A cell of the row 0 gives the biome source of its variation. Any other block takes the row 0 cell with the least power rule value.
 * The biome source of the variation then answers the lookup with the same sampler.
 * A sampler without a carrier gives the fallback biome. So does a cell without a variation.
 * The fallback biome is the first possible biome, else the Plains.
 * The possible biomes are the possible biomes of every variation that the list reaches. The list resolves at its first use.
 * The source keeps the island function of the last sampler. It logs the first sampler without a carrier.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public final class IslandBiomeSource extends BiomeSource {

    /** The Plains holder of the registry ops, or empty for ops without the biome registry. */
    private static final MapCodec<Optional<Holder<Biome>>> PLAINS = ExtraCodecs.retrieveContext(ops -> DataResult.success(
            ops instanceof RegistryOps<?> registryOps
                    ? registryOps.getter(Registries.BIOME).flatMap(getter -> getter.get(Biomes.PLAINS)).map(holder -> (Holder<Biome>) holder)
                    : Optional.empty()));

    public static final MapCodec<IslandBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceKey.codec(IslandVariationRegistries.ISLAND_VARIATION_LIST).fieldOf("variations").forGetter(IslandBiomeSource::variations),
            VariationLookup.CODEC.forGetter(source -> source.lookup),
            PLAINS.forGetter(source -> source.plains)
    ).apply(instance, IslandBiomeSource::new));

    private final ResourceKey<IslandVariationList> variations;
    private final VariationLookup lookup;
    private final Optional<Holder<Biome>> plains;
    @Nullable
    private volatile IslandPicks picks;
    /** The last sampler and its island function. */
    private final AtomicReference<Found> last = new AtomicReference<>();
    private final AtomicBoolean logged = new AtomicBoolean();

    /** One sampler and its island function, or null without one. */
    private record Found(Climate.Sampler sampler, @Nullable IslandFunction islands) {
    }

    public IslandBiomeSource(ResourceKey<IslandVariationList> variations, VariationLookup lookup, Optional<Holder<Biome>> plains) {
        this.variations = variations;
        this.lookup = lookup;
        this.plains = plains;
    }

    /** The id of the island variation list. */
    public ResourceKey<IslandVariationList> variations() {
        return this.variations;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        List<Holder<Biome>> biomes = this.picks().variations().stream()
                .flatMap(variation -> variation.biomeSource().possibleBiomes().stream())
                .distinct()
                .toList();
        return biomes.isEmpty() ? this.plains.stream() : biomes.stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        IslandFunction islands = this.islands(sampler);
        if (islands == null) {
            return this.fallback();
        }
        IslandVariation variation = islands.biomeVariation(QuartPos.toBlock(x), QuartPos.toBlock(y), QuartPos.toBlock(z));
        if (variation == null) {
            return this.fallback();
        }
        return variation.biomeSource().getNoiseBiome(x, y, z, sampler);
    }

    /** The island function of the first carrier of the climate functions of the sampler, or null. */
    @Nullable
    public IslandFunction islands(Climate.Sampler sampler) {
        Found found = this.last.get();
        if (found == null || found.sampler != sampler) {
            found = new Found(sampler, find(sampler));
            this.last.set(found);
            if (found.islands == null && this.logged.compareAndSet(false, true)) {
                Aerolite.LOGGER.warn("The island biome source of {} finds no island carrier on a climate function, so it gives the fallback biome",
                        this.variations.location());
            }
        }
        return found.islands;
    }

    /** The island function of the first carrier of the six climate functions, inside holders and markers. */
    @Nullable
    public static IslandFunction find(Climate.Sampler sampler) {
        for (DensityFunction function : List.of(sampler.temperature(), sampler.humidity(), sampler.continentalness(), sampler.erosion(), sampler.depth(),
                sampler.weirdness())) {
            if (unwrap(function) instanceof IslandCarrier carrier && unwrap(carrier.islands()) instanceof IslandFunction islands) {
                return islands;
            }
        }
        return null;
    }

    private static DensityFunction unwrap(DensityFunction function) {
        while (true) {
            if (function instanceof DensityFunctions.HolderHolder holder && holder.function().isBound()) {
                function = holder.function().value();
            } else if (function instanceof DensityFunctions.MarkerOrMarked marker) {
                function = marker.wrapped();
            } else {
                return function;
            }
        }
    }

    private Holder<Biome> fallback() {
        return this.possibleBiomes().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("The island biome source of " + this.variations.location() + " has no possible biome"));
    }

    private IslandPicks picks() {
        IslandPicks found = this.picks;
        if (found == null) {
            found = IslandPicks.resolve(this.variations, this.lookup, PreviewOverride.NONE);
            this.picks = found;
        }
        return found;
    }

}
