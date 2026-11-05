package us.drullk.aerolite.datagen.shaft;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import us.drullk.aerolite.world.ShaftPlacement;
import us.drullk.aerolite.world.ShaftRegistries;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.VariationList;

import java.util.List;
import java.util.Optional;

/**
 * The shipped shaft data of one dimension: its placement, its variation list and the variations that it owns.
 * Each implementation is a bean in this package. {@code AeroliteShaftEntries} collects them.
 *
 * <p>A list can name a variation of another dimension by the public key of that dimension class.
 * Only the owner registers a variation, so each variation has one JSON file.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-05_02-37_b76a8fd1-f370-4f0b-ae16-df6b75f51a08.md}.
 */
public interface DimensionShaftEntries {

    ResourceKey<Level> dimension();

    ShaftPlacement placement();

    /** Registers each variation that this dimension owns. */
    void variations(BootstrapContext<ShaftVariation> context);

    /** The variation list of the dimension. The sequence is the pick sequence. */
    VariationList variationList();

    /** The mod that holds the dimension. A game without that mod skips the list and the mod variations. */
    default Optional<String> requiredMod() {
        return Optional.empty();
    }

    /** The owned variations that name blocks of the required mod. */
    default List<ResourceKey<ShaftVariation>> modVariations() {
        return List.of();
    }

    static ResourceKey<ShaftVariation> variation(String name) {
        return ResourceKey.create(ShaftRegistries.SHAFT_VARIATION, ShaftRegistries.id(name));
    }

    static VariationList.Entry entry(ResourceKey<ShaftVariation> variation, int weight) {
        return new VariationList.Entry(variation, weight);
    }

    static VariationList list(VariationList.Entry... entries) {
        return new VariationList(List.of(entries));
    }

    /** The default state of a block that another mod registers. */
    static BlockState getBlockState(HolderGetter<Block> blocks, String namespace, String path) {
        return blocks.getOrThrow(ResourceKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(namespace, path))).value().defaultBlockState();
    }

}
