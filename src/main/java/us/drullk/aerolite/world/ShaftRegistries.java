package us.drullk.aerolite.world;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import us.drullk.aerolite.Aerolite;

import java.util.Map;

/**
 * The keys of the shaft data registries and their shipped entries.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
public final class ShaftRegistries {

    /** The shaft variations. A variation list and the data map refer to them by id. */
    public static final ResourceKey<Registry<ShaftVariation>> SHAFT_VARIATION = ResourceKey.createRegistryKey(id("shaft_variation"));
    /** The shaft placements. A dimension takes the entry under its own id. */
    public static final ResourceKey<Registry<ShaftPlacement>> SHAFT_PLACEMENT = ResourceKey.createRegistryKey(id("shaft_placement"));
    /** The variation lists that the menu copies from. */
    public static final ResourceKey<Registry<VariationList>> VARIATION_LIST = ResourceKey.createRegistryKey(id("variation_list"));

    /** The placement of a dimension without its own placement entry. */
    public static final ResourceKey<ShaftPlacement> DEFAULT_PLACEMENT = ResourceKey.create(SHAFT_PLACEMENT, id("default"));
    /** The variation of a dimension other than the three vanilla dimensions, with weight 1. */
    public static final ResourceKey<ShaftVariation> STONE = ResourceKey.create(SHAFT_VARIATION, id("stone"));

    public static final ResourceKey<VariationList> OVERWORLD_LIST = ResourceKey.create(VARIATION_LIST, id("overworld"));
    public static final ResourceKey<VariationList> NETHER_LIST = ResourceKey.create(VARIATION_LIST, id("nether"));
    public static final ResourceKey<VariationList> END_LIST = ResourceKey.create(VARIATION_LIST, id("end"));

    /** The variation list of each vanilla dimension. */
    public static final Map<ResourceKey<Level>, ResourceKey<VariationList>> DIMENSION_LISTS = Map.of(
            Level.OVERWORLD, OVERWORLD_LIST,
            Level.NETHER, NETHER_LIST,
            Level.END, END_LIST
    );

    private ShaftRegistries() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Aerolite.MODID, path);
    }

    /** The placement entry under the id of a dimension. */
    public static ResourceKey<ShaftPlacement> placementOf(ResourceKey<Level> dimension) {
        return ResourceKey.create(SHAFT_PLACEMENT, dimension.location());
    }

}
