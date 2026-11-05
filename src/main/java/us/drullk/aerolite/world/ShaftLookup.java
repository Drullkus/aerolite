package us.drullk.aerolite.world;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import us.drullk.aerolite.Aerolite;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the shaft data registries. It resolves variation lists and placements, and builds the default settings from the shipped entries.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 *
 * @param variations the registry {@code aerolite:shaft_variation}
 * @param lists      the registry {@code aerolite:variation_list}
 * @param placements the registry {@code aerolite:shaft_placement}
 */
public record ShaftLookup(HolderGetter<ShaftVariation> variations, HolderGetter<VariationList> lists, HolderGetter<ShaftPlacement> placements) {

    public static ShaftLookup of(HolderLookup.Provider registries) {
        return new ShaftLookup(
                registries.lookupOrThrow(ShaftRegistries.SHAFT_VARIATION),
                registries.lookupOrThrow(ShaftRegistries.VARIATION_LIST),
                registries.lookupOrThrow(ShaftRegistries.SHAFT_PLACEMENT)
        );
    }

    /** The registries of registry ops. Other ops give empty. */
    public static Optional<ShaftLookup> of(DynamicOps<?> ops) {
        if (!(ops instanceof RegistryOps<?> registryOps)) {
            return Optional.empty();
        }
        Optional<HolderGetter<ShaftVariation>> variations = registryOps.getter(ShaftRegistries.SHAFT_VARIATION);
        Optional<HolderGetter<VariationList>> lists = registryOps.getter(ShaftRegistries.VARIATION_LIST);
        Optional<HolderGetter<ShaftPlacement>> placements = registryOps.getter(ShaftRegistries.SHAFT_PLACEMENT);
        if (variations.isEmpty() || lists.isEmpty() || placements.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ShaftLookup(variations.get(), lists.get(), placements.get()));
    }

    /** The placement entry under the dimension id, else the entry {@code aerolite:default}, else the built-in default. */
    public ShaftPlacement placement(ResourceKey<Level> dimension) {
        return this.placements.get(ShaftRegistries.placementOf(dimension))
                .or(() -> this.placements.get(ShaftRegistries.DEFAULT_PLACEMENT))
                .map(Holder::value)
                .orElse(ShaftPlacement.DEFAULT);
    }

    /** The variations of a list in its sequence. An id without a registry entry is skipped. */
    public List<WeightedVariation> resolve(VariationList list) {
        List<WeightedVariation> resolved = new ArrayList<>(list.entries().size());
        for (VariationList.Entry entry : list.entries()) {
            Optional<Holder.Reference<ShaftVariation>> variation = this.variations.get(entry.variation());
            if (variation.isPresent()) {
                resolved.add(new WeightedVariation(entry.weight(), variation.get().value()));
            } else {
                Aerolite.LOGGER.warn("Unknown shaft variation {} in a variation list", entry.variation().location());
            }
        }
        return resolved;
    }

    /** The placement of the dimension and the variations of the list. */
    public DimensionSettings dimension(ResourceKey<Level> dimension, VariationList list) {
        return new DimensionSettings(this.placement(dimension), this.resolve(list));
    }

    /** The default settings of a dimension. A dimension other than the recognized ones gets stone with weight 1. */
    public DimensionSettings defaults(ResourceKey<Level> dimension) {
        ResourceKey<VariationList> listKey = ShaftRegistries.DIMENSION_LISTS.get(dimension);
        if (listKey != null) {
            Optional<Holder.Reference<VariationList>> list = this.lists.get(listKey);
            if (list.isPresent()) {
                return this.dimension(dimension, list.get().value());
            }
        }
        VariationList stone = new VariationList(List.of(new VariationList.Entry(ShaftRegistries.STONE, 1)));
        return this.dimension(dimension, stone);
    }

    /** The default settings of the preset: the three vanilla dimensions and the default cloud height. */
    public AeroliteSettings defaultSettings() {
        Map<ResourceKey<Level>, DimensionSettings> dimensions = new LinkedHashMap<>();
        for (ResourceKey<Level> dimension : ShaftRegistries.DIMENSION_LISTS.keySet()) {
            dimensions.put(dimension, this.defaults(dimension));
        }
        return new AeroliteSettings(AeroliteSettings.DEFAULT_CLOUD_HEIGHT, ImmutableMap.copyOf(dimensions));
    }

}
