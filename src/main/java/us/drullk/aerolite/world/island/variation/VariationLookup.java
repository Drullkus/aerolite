package us.drullk.aerolite.world.island.variation;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;

import java.util.Map;
import java.util.Optional;

/**
 * Finds island variations and island variation lists by key. An island function and an island biome source resolve their list through it.
 * The codec takes the lookup from the registry ops of the decode and writes nothing. Ops without the registries give a lookup that finds nothing.
 * During the registry load a found holder has no value yet. A lookup of it then throws {@link Unbound}.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public interface VariationLookup {

    /** The lookup that finds nothing. */
    VariationLookup NONE = new VariationLookup() {

        @Override
        public Optional<IslandVariation> variation(ResourceKey<IslandVariation> key) {
            return Optional.empty();
        }

        @Override
        public Optional<IslandVariationList> list(ResourceKey<IslandVariationList> key) {
            return Optional.empty();
        }
    };

    /** The lookup of the registry ops of the decode. */
    MapCodec<VariationLookup> CODEC = ExtraCodecs.retrieveContext(ops -> DataResult.success(ops instanceof RegistryOps<?> registryOps ? of(registryOps) : NONE));

    /** The island variation of the key. Empty for a key without an entry. */
    Optional<IslandVariation> variation(ResourceKey<IslandVariation> key);

    /** The island variation list of the key. Empty for a key without an entry. */
    Optional<IslandVariationList> list(ResourceKey<IslandVariationList> key);

    /** The lookup over the getters of the registry ops. A registry that the ops do not hold finds nothing. */
    static VariationLookup of(RegistryOps<?> ops) {
        Optional<HolderGetter<IslandVariation>> variations = ops.getter(IslandVariationRegistries.ISLAND_VARIATION);
        Optional<HolderGetter<IslandVariationList>> lists = ops.getter(IslandVariationRegistries.ISLAND_VARIATION_LIST);
        return new VariationLookup() {

            @Override
            public Optional<IslandVariation> variation(ResourceKey<IslandVariation> key) {
                return variations.flatMap(getter -> value(getter, key));
            }

            @Override
            public Optional<IslandVariationList> list(ResourceKey<IslandVariationList> key) {
                return lists.flatMap(getter -> value(getter, key));
            }
        };
    }

    /** The lookup over two maps. Tests use it. */
    static VariationLookup of(Map<ResourceKey<IslandVariation>, IslandVariation> variations, Map<ResourceKey<IslandVariationList>, IslandVariationList> lists) {
        Map<ResourceKey<IslandVariation>, IslandVariation> variationCopy = Map.copyOf(variations);
        Map<ResourceKey<IslandVariationList>, IslandVariationList> listCopy = Map.copyOf(lists);
        return new VariationLookup() {

            @Override
            public Optional<IslandVariation> variation(ResourceKey<IslandVariation> key) {
                return Optional.ofNullable(variationCopy.get(key));
            }

            @Override
            public Optional<IslandVariationList> list(ResourceKey<IslandVariationList> key) {
                return Optional.ofNullable(listCopy.get(key));
            }
        };
    }

    /**
     * The value of the holder of the key. The registration getter of a frozen registry throws for a missing key, so that key finds nothing.
     * A holder without a value throws {@link Unbound}.
     */
    private static <T> Optional<T> value(HolderGetter<T> getter, ResourceKey<T> key) {
        Optional<? extends Holder<T>> holder;
        try {
            holder = getter.get(key);
        } catch (IllegalStateException missing) {
            return Optional.empty();
        }
        if (holder.isEmpty()) {
            return Optional.empty();
        }
        if (!holder.get().isBound()) {
            throw new Unbound(key);
        }
        return Optional.of(holder.get().value());
    }

    /** A lookup during the registry load, before the holder of the key has its value. */
    final class Unbound extends IllegalStateException {

        public Unbound(ResourceKey<?> key) {
            super("The entry " + key.location() + " of " + key.registry() + " has no value yet");
        }
    }

}
