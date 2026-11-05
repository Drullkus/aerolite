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
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md}.
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

    public static final ResourceKey<VariationList> OVERWORLD_LIST = ResourceKey.create(VARIATION_LIST, id("overworld_pillars"));
    public static final ResourceKey<VariationList> NETHER_LIST = ResourceKey.create(VARIATION_LIST, id("nether_pillars"));
    public static final ResourceKey<VariationList> END_LIST = ResourceKey.create(VARIATION_LIST, id("end_pillars"));

    public static final ResourceKey<VariationList> AETHER_LIST = ResourceKey.create(VARIATION_LIST, id("aether_pillars"));
    public static final ResourceKey<VariationList> BUMBLEZONE_LIST = ResourceKey.create(VARIATION_LIST, id("bumblezone_pillars"));
    public static final ResourceKey<VariationList> GAIA_LIST = ResourceKey.create(VARIATION_LIST, id("gaia_pillars"));
    public static final ResourceKey<VariationList> TWILIGHT_LIST = ResourceKey.create(VARIATION_LIST, id("twilight_pillars"));
    public static final ResourceKey<VariationList> UNDERGARDEN_LIST = ResourceKey.create(VARIATION_LIST, id("undergarden_pillars"));
    public static final ResourceKey<VariationList> VOID_LIST = ResourceKey.create(VARIATION_LIST, id("void_pillars"));

    /** The variation list of each known dimension. */
    public static final Map<ResourceKey<Level>, ResourceKey<VariationList>> DIMENSION_LISTS = Map.of(
            Level.OVERWORLD, OVERWORLD_LIST,
            Level.NETHER, NETHER_LIST,
            Level.END, END_LIST,
            ModdedDimensions.AETHER, AETHER_LIST,
            ModdedDimensions.BUMBLEZONE, BUMBLEZONE_LIST,
            ModdedDimensions.GAIA, GAIA_LIST,
            ModdedDimensions.TWILIGHT_FOREST, TWILIGHT_LIST,
            ModdedDimensions.UNDERGARDEN, UNDERGARDEN_LIST,
            ModdedDimensions.VOID, VOID_LIST
    );

    private ShaftRegistries() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Aerolite.MODID, path);
    }

    /**
     * The id of the own variation list of a dimension.
     * A vanilla dimension uses {@code aerolite:overworld_pillars}, {@code aerolite:nether_pillars} or {@code aerolite:end_pillars}.
     * Any other dimension uses {@code aerolite:<dimension namespace>/<dimension path>_pillars}.
     */
    public static ResourceKey<VariationList> listOf(ResourceKey<Level> dimension) {
        ResourceKey<VariationList> vanilla = DIMENSION_LISTS.get(dimension);
        if (vanilla != null) {
            return vanilla;
        }
        ResourceLocation location = dimension.location();
        return ResourceKey.create(VARIATION_LIST, id(location.getNamespace() + "/" + location.getPath() + "_pillars"));
    }

    /** The placement entry under the id of a dimension. */
    public static ResourceKey<ShaftPlacement> placementOf(ResourceKey<Level> dimension) {
        return ResourceKey.create(SHAFT_PLACEMENT, dimension.location());
    }

}
