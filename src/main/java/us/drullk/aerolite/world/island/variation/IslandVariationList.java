package us.drullk.aerolite.world.island.variation;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;

import java.util.List;
import java.util.Optional;

/**
 * An island variation list: weighted entries in pick sequence. Each entry names an island variation or another island variation list.
 * The entries hold resource keys, not holders. So an entry can name an entry of another pack.
 * A list holds at least one entry with a positive weight.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 *
 * @param entries the entries in pick sequence
 */
public record IslandVariationList(List<Entry> entries) {

    /** The largest weight of one entry. */
    public static final int MAX_WEIGHT = 16;

    /**
     * One entry: an island variation on the left or an island variation list on the right, and its weight.
     * The JSON object holds exactly one of {@code variation} and {@code list}.
     *
     * @param weight the pick weight from 0 to {@value #MAX_WEIGHT}; a weight of 0 never picks
     */
    public record Entry(Either<ResourceKey<IslandVariation>, ResourceKey<IslandVariationList>> target, int weight) {

        public static final Codec<Entry> CODEC = RecordCodecBuilder.<Raw>create(instance -> instance.group(
                ResourceKey.codec(IslandVariationRegistries.ISLAND_VARIATION).optionalFieldOf("variation").forGetter(Raw::variation),
                ResourceKey.codec(IslandVariationRegistries.ISLAND_VARIATION_LIST).optionalFieldOf("list").forGetter(Raw::list),
                Codec.intRange(0, MAX_WEIGHT).fieldOf("weight").forGetter(Raw::weight)
        ).apply(instance, Raw::new)).comapFlatMap(Raw::entry, Entry::raw);

        public static Entry variation(ResourceKey<IslandVariation> variation, int weight) {
            return new Entry(Either.left(variation), weight);
        }

        public static Entry list(ResourceKey<IslandVariationList> list, int weight) {
            return new Entry(Either.right(list), weight);
        }

        private Raw raw() {
            return new Raw(this.target.left(), this.target.right(), this.weight);
        }
    }

    /** The JSON fields of one entry before the check of its target. */
    private record Raw(Optional<ResourceKey<IslandVariation>> variation, Optional<ResourceKey<IslandVariationList>> list, int weight) {

        private DataResult<Entry> entry() {
            if (this.variation.isPresent() == this.list.isPresent()) {
                return DataResult.error(() -> "An island variation list entry holds exactly one of variation and list");
            }
            return DataResult.success(new Entry(this.variation.<Either<ResourceKey<IslandVariation>, ResourceKey<IslandVariationList>>>map(Either::left)
                    .orElseGet(() -> Either.right(this.list.orElseThrow())), this.weight));
        }
    }

    public static final Codec<IslandVariationList> CODEC = RecordCodecBuilder.<IslandVariationList>create(instance -> instance.group(
            Entry.CODEC.listOf().fieldOf("entries").forGetter(IslandVariationList::entries)
    ).apply(instance, IslandVariationList::new)).validate(list -> list.entries.stream().anyMatch(entry -> entry.weight > 0)
            ? DataResult.success(list)
            : DataResult.error(() -> "An island variation list holds no entry with a positive weight"));

    public IslandVariationList {
        entries = List.copyOf(entries);
    }

}
