package us.drullk.aerolite.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A map of variation id to pick weight. The JSON sequence of the map sets the pick sequence.
 * It is the entry type of {@code aerolite:variation_list} and the value type of the data map {@code aerolite:shaft_variations}.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 *
 * @param entries the variations and their weights, in pick sequence
 */
public record VariationList(List<Entry> entries) {

    /**
     * @param variation the id of the variation in {@code aerolite:shaft_variation}
     * @param weight    the pick weight; a weight of 0 never picks
     */
    public record Entry(ResourceKey<ShaftVariation> variation, int weight) {
    }

    public VariationList {
        entries = List.copyOf(entries);
    }

    /** A list holds at least one variation with a positive weight. */
    public static final Codec<VariationList> CODEC = Codec.unboundedMap(ResourceKey.codec(ShaftRegistries.SHAFT_VARIATION), ExtraCodecs.NON_NEGATIVE_INT)
            .xmap(VariationList::fromMap, VariationList::toMap)
            .validate(list -> list.entries.stream().anyMatch(entry -> entry.weight > 0)
                    ? DataResult.success(list)
                    : DataResult.error(() -> "A variation list holds no variation with a positive weight"));

    private static VariationList fromMap(Map<ResourceKey<ShaftVariation>, Integer> map) {
        List<Entry> entries = new ArrayList<>(map.size());
        map.forEach((variation, weight) -> entries.add(new Entry(variation, weight)));
        return new VariationList(entries);
    }

    private Map<ResourceKey<ShaftVariation>, Integer> toMap() {
        Map<ResourceKey<ShaftVariation>, Integer> map = new LinkedHashMap<>();
        this.entries.forEach(entry -> map.put(entry.variation, entry.weight));
        return map;
    }

}
