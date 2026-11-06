package us.drullk.aerolite.client;

import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.api.distmarker.Dist;
import tamaized.beanification.Autowired;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.DimensionSettings;
import us.drullk.aerolite.world.PillarShape;
import us.drullk.aerolite.world.ShaftLookup;
import us.drullk.aerolite.world.ShaftPlacement;
import us.drullk.aerolite.world.ShaftRegistries;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.VariationList;
import us.drullk.aerolite.world.WeightedVariation;
import us.drullk.aerolite.world.pattern.BricksSettings;
import us.drullk.aerolite.world.pattern.ChaosSettings;
import us.drullk.aerolite.world.pattern.MazeSettings;
import us.drullk.aerolite.world.pattern.PatternSettings;
import us.drullk.aerolite.world.pattern.PatternType;
import us.drullk.aerolite.world.pattern.ScalesSettings;
import us.drullk.aerolite.world.pattern.SurfacePathSettings;
import us.drullk.aerolite.world.pattern.TilesSettings;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The edit rules of the shaft settings. The vanilla screens and the preview panel share them.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-06_00-00_10881e45-2016-40f4-83b8-2c27043cd0f4.md}.
 */
@Component(dist = Dist.CLIENT)
public final class PresetEdits {

    private static final String KEY = "createWorld.customize.aerolite.";

    /** The vanilla dimensions, in screen sequence. They come before every other dimension. */
    private static final List<ResourceKey<Level>> VANILLA_DIMENSIONS = List.of(Level.OVERWORLD, Level.NETHER, Level.END);

    private final PresetSliders sliders;
    /** The worldgen registries of the world being created. They hold the shaft data registries. */
    @Nullable
    private HolderLookup.Provider registries;
    @Nullable
    private ShaftLookup lookup;

    public PresetEdits(@Autowired PresetSliders sliders) {
        this.sliders = sliders;
    }

    /** Takes the worldgen registries of the world being created. Each screen of the flow calls this before it reads them. */
    public void useRegistries(HolderLookup.Provider registries) {
        if (registries != this.registries) {
            this.registries = registries;
            this.lookup = ShaftLookup.of(registries);
        }
    }

    /** The worldgen registries of the world being created. */
    public HolderLookup.Provider registries() {
        if (this.registries == null) {
            throw new IllegalStateException("No screen of the world creation flow has opened");
        }
        return this.registries;
    }

    /** The shaft data registries of the world being created. */
    public ShaftLookup lookup() {
        if (this.lookup == null) {
            throw new IllegalStateException("No screen of the world creation flow has opened");
        }
        return this.lookup;
    }

    /**
     * Every dimension of the world being created: the level stems of the data packs and the dimensions of the world type.
     * The Overworld, the Nether and the End come first, then the rest by id.
     */
    public List<ResourceKey<Level>> worldDimensions(WorldCreationContext context) {
        return this.sorted(worldStems(context).keySet().stream().map(Registries::levelStemToLevel).toList());
    }

    /**
     * The level stems of the world being created by key. A stem of the world type replaces a data pack stem of the same key.
     * The worldgen load context holds no level stem registry, so the data pack stems come from the context field.
     */
    public static Map<ResourceKey<LevelStem>, LevelStem> worldStems(WorldCreationContext context) {
        Map<ResourceKey<LevelStem>, LevelStem> stems = new LinkedHashMap<>();
        context.worldgenLoadContext().registry(Registries.LEVEL_STEM).ifPresent(registry -> putAll(stems, registry));
        putAll(stems, context.datapackDimensions());
        stems.putAll(context.selectedDimensions().dimensions());
        return stems;
    }

    private static void putAll(Map<ResourceKey<LevelStem>, LevelStem> stems, Registry<LevelStem> registry) {
        for (Map.Entry<ResourceKey<LevelStem>, LevelStem> entry : registry.entrySet()) {
            stems.put(entry.getKey(), entry.getValue());
        }
    }

    /** The ids of the variation lists of the world being created, by id. */
    public List<ResourceKey<VariationList>> variationLists() {
        return this.registries().lookupOrThrow(ShaftRegistries.VARIATION_LIST).listElementIds()
                .sorted(Comparator.comparing(ResourceKey::location))
                .toList();
    }

    /** The own variation list of a dimension, when the registry holds it. See {@link ShaftRegistries#listOf}. */
    public Optional<ResourceKey<VariationList>> ownList(ResourceKey<Level> dimension) {
        ResourceKey<VariationList> list = ShaftRegistries.listOf(dimension);
        return this.lookup().lists().get(list).isPresent() ? Optional.of(list) : Optional.empty();
    }

    /**
     * The name of a variation list choice, or "No prefab" for no list.
     * A list reads its name from {@code pillar_variations.<namespace>.<path>}. A list without a translation shows its full id.
     */
    public net.minecraft.network.chat.Component listName(Optional<ResourceKey<VariationList>> list) {
        return list.map(key -> net.minecraft.network.chat.Component.translatableWithFallback(
                        key.location().toLanguageKey("pillar_variations"), key.location().toString()))
                .orElseGet(() -> net.minecraft.network.chat.Component.translatable("createWorld.customize.aerolite.no_prefab"));
    }

    /** The tooltip of the list choice of the add row. With no dimension left to add, it names the word "Dimension". */
    public net.minecraft.network.chat.Component listTooltip(@Nullable ResourceKey<Level> dimension) {
        net.minecraft.network.chat.Component name = dimension == null
                ? net.minecraft.network.chat.Component.translatable("createWorld.customize.aerolite.prefab_dimension")
                : this.dimensionName(dimension);
        return net.minecraft.network.chat.Component.translatable("createWorld.customize.aerolite.prefab_tooltip", name);
    }

    /** The list choices of the add row: No prefab first, then every variation list by id. */
    public List<Optional<ResourceKey<VariationList>>> listChoices() {
        List<Optional<ResourceKey<VariationList>>> choices = new ArrayList<>();
        choices.add(Optional.empty());
        this.variationLists().forEach(list -> choices.add(Optional.of(list)));
        return List.copyOf(choices);
    }

    /**
     * Adds the defaults: every dimension of {@code dimensions} that has its own variation list and no settings, with that list.
     * A configured dimension and the cloud height stay.
     */
    public AeroliteSettings withDefaults(AeroliteSettings settings, Collection<ResourceKey<Level>> dimensions) {
        return this.lookup().withDefaults(settings, this.sorted(dimensions));
    }

    /** A minimum spacing above the maximum spacing pushes the maximum along. */
    public ShaftPlacement withMinSpacingStep(ShaftPlacement placement, int step) {
        int min = this.sliders.spacing(step);
        return placement.withShaftSpacing(min, Math.max(min, placement.maxShaftSpacing()));
    }

    /** A maximum spacing below the minimum spacing pushes the minimum along. */
    public ShaftPlacement withMaxSpacingStep(ShaftPlacement placement, int step) {
        int max = this.sliders.spacing(step);
        return placement.withShaftSpacing(Math.min(max, placement.minShaftSpacing()), max);
    }

    public int decayStep(ShaftVariation variation) {
        return this.sliders.fractionStep(variation.decayThreshold());
    }

    /** The erosion step runs from 1 to {@link PresetSliders#FRACTION_STEPS}. */
    public int erosionStep(ShaftVariation variation) {
        return this.sliders.fractionStep(variation.erosionThreshold());
    }

    /** The erosion threshold stays at least one step above the decay threshold. */
    public ShaftVariation withDecayStep(ShaftVariation variation, int step) {
        int decay = Math.min(step, PresetSliders.FRACTION_STEPS - 1);
        int erosion = Math.max(decay + 1, this.erosionStep(variation));
        return variation.withDecayThreshold(this.sliders.fraction(decay)).withErosionThreshold(this.sliders.fraction(erosion));
    }

    /** The decay threshold stays at least one step below the erosion threshold. */
    public ShaftVariation withErosionStep(ShaftVariation variation, int erosion) {
        int decay = Math.min(erosion - 1, this.decayStep(variation));
        return variation.withDecayThreshold(this.sliders.fraction(decay)).withErosionThreshold(this.sliders.fraction(erosion));
    }

    public List<ShaftVariation> variations(AeroliteSettings settings, ResourceKey<Level> dimension) {
        return settings.variations(dimension).stream().map(WeightedVariation::variation).toList();
    }

    public int weight(AeroliteSettings settings, ResourceKey<Level> dimension, int index) {
        return settings.variations(dimension).get(index).weight();
    }

    /** The dimensions of the settings map. The Overworld, the Nether and the End come first, then the rest by id. */
    public List<ResourceKey<Level>> configuredDimensions(AeroliteSettings settings) {
        return this.sorted(settings.dimensions().keySet());
    }

    /** The dimensions of {@code all} that the settings map does not hold, in the sequence of {@link #configuredDimensions}. */
    public List<ResourceKey<Level>> addableDimensions(AeroliteSettings settings, Collection<ResourceKey<Level>> all) {
        return this.sorted(all.stream().filter(dimension -> !settings.dimensions().containsKey(dimension)).toList());
    }

    /**
     * The name of a dimension. A dimension other than the three vanilla ones reads {@code dimension.<namespace>.<path>}.
     * Without that translation, it shows its id path.
     */
    public net.minecraft.network.chat.Component dimensionName(ResourceKey<Level> dimension) {
        return VANILLA_DIMENSIONS.contains(dimension)
                ? net.minecraft.network.chat.Component.translatable("createWorld.customize.aerolite.dimension." + dimension.location().getPath())
                : net.minecraft.network.chat.Component.translatableWithFallback(dimension.location().toLanguageKey("dimension"), dimension.location().getPath());
    }

    /**
     * Adds a dimension with the placement entry of the dimension and the weights of a variation list.
     * No list, or a list that the registry lacks, gives one stone variation with weight 1. A configured dimension stays as it is.
     */
    public AeroliteSettings withAddedDimension(AeroliteSettings settings, ResourceKey<Level> dimension, Optional<ResourceKey<VariationList>> list) {
        if (settings.dimensions().containsKey(dimension)) {
            return settings;
        }
        VariationList chosen = list.flatMap(key -> this.lookup().lists().get(key))
                .map(Holder::value)
                .orElse(ShaftLookup.STONE_LIST);
        return settings.withDimension(dimension, this.lookup().dimensionOrStone(dimension, chosen));
    }

    /** Removes a dimension and its settings. The dimension then gets no shafts. */
    public AeroliteSettings withoutDimension(AeroliteSettings settings, ResourceKey<Level> dimension) {
        return settings.withoutDimension(dimension);
    }

    private List<ResourceKey<Level>> sorted(Collection<ResourceKey<Level>> dimensions) {
        LinkedHashSet<ResourceKey<Level>> sorted = new LinkedHashSet<>();
        for (ResourceKey<Level> vanilla : VANILLA_DIMENSIONS) {
            if (dimensions.contains(vanilla)) {
                sorted.add(vanilla);
            }
        }
        dimensions.stream().sorted(Comparator.comparing(ResourceKey::location)).forEach(sorted::add);
        return List.copyOf(sorted);
    }

    /** A dimension keeps at least one variation with a positive weight. */
    public AeroliteSettings withWeight(AeroliteSettings settings, ResourceKey<Level> dimension, int index, int weight) {
        List<WeightedVariation> variations = settings.variations(dimension);
        boolean otherPositive = false;
        for (int other = 0; other < variations.size(); other++) {
            if (other != index && variations.get(other).weight() > 0) {
                otherPositive = true;
            }
        }
        int chosen = weight == 0 && !otherPositive ? 1 : weight;
        return settings.withWeight(dimension, index, chosen);
    }

    public boolean canRemove(AeroliteSettings settings, ResourceKey<Level> dimension) {
        return this.variations(settings, dimension).size() > 1;
    }

    /** Removes one variation. The first variation gets a weight of 1 when no positive weight stays. */
    public AeroliteSettings withoutVariation(AeroliteSettings settings, ResourceKey<Level> dimension, int index) {
        if (!this.canRemove(settings, dimension)) {
            return settings;
        }
        AeroliteSettings removed = settings.withoutVariation(dimension, index);
        List<WeightedVariation> rest = removed.variations(dimension);
        return rest.stream().anyMatch(other -> other.weight() > 0)
                ? removed
                : removed.withWeight(dimension, 0, 1);
    }

    /** Appends a copy of the first variation, or the stone variation with weight 1 when the dimension has none. */
    public AeroliteSettings withAddedVariation(AeroliteSettings settings, ResourceKey<Level> dimension) {
        List<WeightedVariation> current = settings.variations(dimension);
        return settings.withAddedVariation(dimension, current.isEmpty() ? this.stoneVariation() : current.getFirst());
    }

    private WeightedVariation stoneVariation() {
        return this.lookup().resolve(ShaftLookup.STONE_LIST).getFirst();
    }


    /** The pattern of a type with the defaults of that type. */
    public static PatternSettings defaultPattern(PatternType type) {
        return switch (type) {
            case MAZES -> MazeSettings.DEFAULT;
            case BRICKS -> BricksSettings.DEFAULT;
            case TILES -> TilesSettings.DEFAULT;
            case SCALES -> ScalesSettings.DEFAULT;
            case CHAOS -> ChaosSettings.DEFAULT;
        };
    }

    /** A new pattern type replaces the pattern with the defaults of that type. The same type keeps the pattern. */
    public ShaftVariation withPatternType(ShaftVariation variation, PatternType type) {
        return variation.pattern().patternType() == type ? variation : variation.withPattern(defaultPattern(type));
    }

    /** Edits the pattern when it is of the given class. Any other pattern stays. */
    public <T extends PatternSettings> ShaftVariation withPattern(ShaftVariation variation, Class<T> type, UnaryOperator<T> edit) {
        PatternSettings pattern = variation.pattern();
        return type.isInstance(pattern) ? variation.withPattern(edit.apply(type.cast(pattern))) : variation;
    }

    /** The loop and surface path options of the pattern. A maze has none. */
    public static Optional<SurfacePathSettings> paths(PatternSettings pattern) {
        return switch (pattern) {
            case MazeSettings maze -> Optional.empty();
            case BricksSettings bricks -> Optional.of(bricks.paths());
            case TilesSettings tiles -> Optional.of(tiles.paths());
            case ScalesSettings scales -> Optional.of(scales.paths());
            case ChaosSettings chaos -> Optional.of(chaos.paths());
        };
    }

    /** Edits the loop and surface path options. A maze stays. */
    public ShaftVariation withPaths(ShaftVariation variation, UnaryOperator<SurfacePathSettings> edit) {
        PatternSettings edited = switch (variation.pattern()) {
            case MazeSettings maze -> maze;
            case BricksSettings bricks -> bricks.withPaths(edit.apply(bricks.paths()));
            case TilesSettings tiles -> tiles.withPaths(edit.apply(tiles.paths()));
            case ScalesSettings scales -> scales.withPaths(edit.apply(scales.paths()));
            case ChaosSettings chaos -> chaos.withPaths(edit.apply(chaos.paths()));
        };
        return variation.withPattern(edited);
    }

    /** The loop share of a step from 0% to 100%. A step outside the slider clamps to it. */
    public SurfacePathSettings withLoopShareStep(SurfacePathSettings paths, int step) {
        return paths.withLoopShare(this.sliders.clampedFraction(step));
    }

    /** The path chance of a step from 0% to 100%. A step outside the slider clamps to it. */
    public SurfacePathSettings withPathChanceStep(SurfacePathSettings paths, int step) {
        return paths.withPathChance(this.sliders.clampedFraction(step));
    }

    /** The stagger choices of a tile size. Large tiles have no X stagger. */
    public static List<TilesSettings.Stagger> staggerChoices(TilesSettings.Size size) {
        return size == TilesSettings.Size.LARGE
                ? List.of(TilesSettings.Stagger.NONE, TilesSettings.Stagger.Y)
                : List.of(TilesSettings.Stagger.values());
    }

    /** False for the X stagger of encased tiles or large tiles. */
    public static boolean staggerAllowed(TilesSettings tiles, TilesSettings.Stagger stagger) {
        return stagger != TilesSettings.Stagger.X || tiles.size() != TilesSettings.Size.LARGE && !tiles.encased();
    }

    /** True for a maze type with the pattern strength slider. Uniform and Lines have none. */
    public static boolean showsStrength(MazeSettings.Type type) {
        return type != MazeSettings.Type.UNIFORM && type != MazeSettings.Type.LINES;
    }

    /** True when the variation shows the maze bias slider. A Lines maze has none. */
    public static boolean showsBias(ShaftVariation variation) {
        return !(variation.pattern() instanceof MazeSettings maze && maze.type() == MazeSettings.Type.LINES);
    }

    /** True when the variation shows the trusses block field. Only the shape Bridge has trusses. */
    public static boolean showsTrusses(ShaftVariation variation) {
        return variation.shape() == PillarShape.BRIDGE;
    }

    /** The name of a pattern type. It reads {@code createWorld.customize.aerolite.pattern.<id path>}. */
    public net.minecraft.network.chat.Component patternName(PatternType type) {
        return net.minecraft.network.chat.Component.translatable(KEY + "pattern." + ResourceLocation.parse(type.getSerializedName()).getPath());
    }

    /** The name of one entry of a combo. It reads {@code createWorld.customize.aerolite.<group>.<serialized name>}. */
    public net.minecraft.network.chat.Component choiceName(String group, StringRepresentable choice) {
        return net.minecraft.network.chat.Component.translatable(KEY + group + "." + choice.getSerializedName());
    }

}
