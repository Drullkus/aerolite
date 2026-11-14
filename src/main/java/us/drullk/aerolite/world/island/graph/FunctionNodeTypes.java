package us.drullk.aerolite.world.island.graph;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.dimension.DimensionType;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.AeroliteWorldgen;
import us.drullk.aerolite.world.island.VoronoiIslands;
import us.drullk.aerolite.world.island.node.CellRandom;
import us.drullk.aerolite.world.island.node.IslandValue;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The function node types: every density function type of Minecraft 1.21.1 and the Aerolite types.
 * The Aerolite types are {@code aerolite:voronoi_islands}, the island function, the carrier and the nine island nodes.
 * Each field list follows the codec of its type: {@code DensityFunctions}, {@code BlendedNoise}, {@link VoronoiIslands} and the island node package.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public final class FunctionNodeTypes {

    /** The bounds of {@code BlendedNoise.SCALE_RANGE}. */
    public static final double MIN_BLENDED_SCALE = 0.001;
    public static final double MAX_BLENDED_SCALE = 1000.0;
    /** The bounds of the smear scale multiplier of {@code BlendedNoise}. */
    public static final double MIN_SMEAR = 1.0;
    public static final double MAX_SMEAR = 8.0;
    /** The Y bounds of {@code DensityFunctions.YClampedGradient}. */
    public static final int MIN_GRADIENT_Y = DimensionType.MIN_Y * 2;
    public static final int MAX_GRADIENT_Y = DimensionType.MAX_Y * 2;

    public static final ResourceLocation CONSTANT = ResourceLocation.withDefaultNamespace("constant");

    private static final Map<ResourceLocation, FunctionNodeType> TYPES = build();

    private FunctionNodeTypes() {
    }

    @Nullable
    public static FunctionNodeType get(ResourceLocation id) {
        return TYPES.get(id);
    }

    /** The types in the sequence of the vanilla bootstrap, then the Aerolite types. */
    public static Collection<FunctionNodeType> all() {
        return Collections.unmodifiableCollection(TYPES.values());
    }

    public static FunctionNodeType constant() {
        return TYPES.get(CONSTANT);
    }

    private static Map<ResourceLocation, FunctionNodeType> build() {
        Map<ResourceLocation, FunctionNodeType> types = new LinkedHashMap<>();
        vanilla(types, "blend_alpha");
        vanilla(types, "blend_offset");
        vanilla(types, "beardifier");
        vanilla(types, "old_blended_noise",
                FieldSpec.number("xz_scale", MIN_BLENDED_SCALE, MAX_BLENDED_SCALE),
                FieldSpec.number("y_scale", MIN_BLENDED_SCALE, MAX_BLENDED_SCALE),
                FieldSpec.number("xz_factor", MIN_BLENDED_SCALE, MAX_BLENDED_SCALE),
                FieldSpec.number("y_factor", MIN_BLENDED_SCALE, MAX_BLENDED_SCALE),
                FieldSpec.number("smear_scale_multiplier", MIN_SMEAR, MAX_SMEAR));
        for (String marker : List.of("interpolated", "flat_cache", "cache_2d", "cache_once", "cache_all_in_cell")) {
            vanilla(types, marker, FieldSpec.function("argument"));
        }
        vanilla(types, "noise", FieldSpec.noise("noise"), FieldSpec.number("xz_scale"), FieldSpec.number("y_scale"));
        vanilla(types, "end_islands");
        vanilla(types, "weird_scaled_sampler", FieldSpec.function("input"), FieldSpec.noise("noise"),
                FieldSpec.choice("rarity_value_mapper", List.of("type_1", "type_2")));
        vanilla(types, "shifted_noise", FieldSpec.function("shift_x"), FieldSpec.function("shift_y"), FieldSpec.function("shift_z"),
                FieldSpec.number("xz_scale"), FieldSpec.number("y_scale"), FieldSpec.noise("noise"));
        vanilla(types, "range_choice", FieldSpec.function("input"), FieldSpec.noiseValue("min_inclusive"), FieldSpec.noiseValue("max_exclusive"),
                FieldSpec.function("when_in_range"), FieldSpec.function("when_out_of_range"));
        vanilla(types, "shift_a", FieldSpec.noise("argument"));
        vanilla(types, "shift_b", FieldSpec.noise("argument"));
        vanilla(types, "shift", FieldSpec.noise("argument"));
        vanilla(types, "blend_density", FieldSpec.function("argument"));
        vanilla(types, "clamp", FieldSpec.directFunction("input"), FieldSpec.noiseValue("min"), FieldSpec.noiseValue("max"));
        for (String mapped : List.of("abs", "square", "cube", "half_negative", "quarter_negative", "squeeze")) {
            vanilla(types, mapped, FieldSpec.function("argument"));
        }
        for (String twoArguments : List.of("add", "mul", "min", "max")) {
            vanilla(types, twoArguments, FieldSpec.function("argument1"), FieldSpec.function("argument2"));
        }
        vanilla(types, "spline", FieldSpec.spline("spline"));
        vanilla(types, "constant", FieldSpec.noiseValue("argument"));
        vanilla(types, "y_clamped_gradient", FieldSpec.integer("from_y", MIN_GRADIENT_Y, MAX_GRADIENT_Y), FieldSpec.integer("to_y", MIN_GRADIENT_Y, MAX_GRADIENT_Y),
                FieldSpec.noiseValue("from_value"), FieldSpec.noiseValue("to_value"));
        add(types, AeroliteWorldgen.VORONOI_ISLANDS_TYPE,
                FieldSpec.noise("noise"),
                FieldSpec.integer("cell_size", VoronoiIslands.MIN_CELL, VoronoiIslands.MAX_CELL),
                FieldSpec.integer("cell_height", VoronoiIslands.MIN_CELL, VoronoiIslands.MAX_CELL),
                FieldSpec.integer("layer_min_y"),
                FieldSpec.number("jitter", 0.0, 1.0),
                FieldSpec.number("fill_min", 0.0, 1.0),
                FieldSpec.number("fill_max", 0.0, 1.0),
                FieldSpec.number("empty_chance", 0.0, 1.0),
                FieldSpec.number("distortion", VoronoiIslands.MIN_DISTORTION, VoronoiIslands.MAX_DISTORTION),
                FieldSpec.integer("distortion_scale", VoronoiIslands.MIN_DISTORTION_SCALE, VoronoiIslands.MAX_DISTORTION_SCALE),
                FieldSpec.integer("distortion_octaves", VoronoiIslands.MIN_DISTORTION_OCTAVES, VoronoiIslands.MAX_DISTORTION_OCTAVES));
        add(types, AeroliteWorldgen.ISLAND_VARIATIONS_TYPE,
                FieldSpec.noise("noise"),
                FieldSpec.integer("cell_size", VoronoiIslands.MIN_CELL, VoronoiIslands.MAX_CELL),
                FieldSpec.integer("cell_height", VoronoiIslands.MIN_CELL, VoronoiIslands.MAX_CELL),
                FieldSpec.integer("layer_min_y"),
                FieldSpec.number("jitter", 0.0, 1.0),
                FieldSpec.variationList("variations"));
        add(types, AeroliteWorldgen.ISLAND_CARRIER_TYPE, FieldSpec.function("islands"), FieldSpec.function("argument"));
        for (IslandValue.Kind kind : IslandValue.Kind.values()) {
            add(types, ResourceLocation.fromNamespaceAndPath(Aerolite.MODID, kind.path()));
        }
        add(types, AeroliteWorldgen.SITE_OFFSET_TYPE, FieldSpec.choice("axis", List.of("x", "y", "z")));
        add(types, AeroliteWorldgen.SITE_DISTANCE_TYPE, FieldSpec.choice("plane", List.of("xyz", "xz")));
        add(types, AeroliteWorldgen.CELL_RANDOM_TYPE,
                FieldSpec.number("min", -CellRandom.VALUE_LIMIT, CellRandom.VALUE_LIMIT),
                FieldSpec.number("max", -CellRandom.VALUE_LIMIT, CellRandom.VALUE_LIMIT),
                FieldSpec.integer("channel", 0, Integer.MAX_VALUE));
        return Collections.unmodifiableMap(types);
    }

    private static void vanilla(Map<ResourceLocation, FunctionNodeType> types, String path, FieldSpec... fields) {
        add(types, ResourceLocation.withDefaultNamespace(path), fields);
    }

    private static void add(Map<ResourceLocation, FunctionNodeType> types, ResourceLocation id, FieldSpec... fields) {
        types.put(id, new FunctionNodeType(id, List.of(fields)));
    }

}
