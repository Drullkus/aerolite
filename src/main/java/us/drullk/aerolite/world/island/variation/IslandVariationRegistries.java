package us.drullk.aerolite.world.island.variation;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import us.drullk.aerolite.Aerolite;

/**
 * The keys of the two island variation registries and of their shipped entries.
 * Each vanilla dimension ships one island variation list, one Basic variation and one Empty variation.
 * Their ids use the dimension names of the 0.5.0 entries: {@code overworld}, {@code nether} and {@code end}.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public final class IslandVariationRegistries {

    /** The island variations. An island variation list names them by id. */
    public static final ResourceKey<Registry<IslandVariation>> ISLAND_VARIATION = ResourceKey.createRegistryKey(id("island_variation"));
    /** The island variation lists. An island function names one by id. */
    public static final ResourceKey<Registry<IslandVariationList>> ISLAND_VARIATION_LIST = ResourceKey.createRegistryKey(id("island_variation_list"));

    private IslandVariationRegistries() {
    }

    /** The name of a dimension in the shipped ids: {@code overworld}, {@code nether}, {@code end}, else the path of the dimension id. */
    public static String dimensionName(ResourceKey<Level> dimension) {
        if (dimension.equals(Level.NETHER)) {
            return "nether";
        }
        if (dimension.equals(Level.END)) {
            return "end";
        }
        return dimension.location().getPath();
    }

    /** The shipped island variation list of a dimension, for example {@code aerolite:overworld_islands}. */
    public static ResourceKey<IslandVariationList> listOf(ResourceKey<Level> dimension) {
        return ResourceKey.create(ISLAND_VARIATION_LIST, id(dimensionName(dimension) + "_islands"));
    }

    /** The shipped Basic variation of a dimension, for example {@code aerolite:overworld_basic}. */
    public static ResourceKey<IslandVariation> basicOf(ResourceKey<Level> dimension) {
        return ResourceKey.create(ISLAND_VARIATION, id(dimensionName(dimension) + "_basic"));
    }

    /** The shipped Empty variation of a dimension, for example {@code aerolite:overworld_empty}. */
    public static ResourceKey<IslandVariation> emptyOf(ResourceKey<Level> dimension) {
        return ResourceKey.create(ISLAND_VARIATION, id(dimensionName(dimension) + "_empty"));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Aerolite.MODID, path);
    }

}
