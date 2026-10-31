package us.drullk.aerolite.client;

import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.api.distmarker.Dist;
import tamaized.beanification.Autowired;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.DimensionSettings;
import us.drullk.aerolite.world.ShaftLookup;
import us.drullk.aerolite.world.ShaftPlacement;
import us.drullk.aerolite.world.ShaftRegistries;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.VariationList;
import us.drullk.aerolite.world.WeightedVariation;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The edit rules of the shaft settings. The vanilla screens and the preview panel share them.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_1143d92a-d4be-41d5-9df1-3b5c5d54fb8d.md}.
 */
@Component(dist = Dist.CLIENT)
public final class PresetEdits {

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

    /** The name of a variation list choice: its id path, or "Blank" for no list. */
    public net.minecraft.network.chat.Component listName(Optional<ResourceKey<VariationList>> list) {
        return list.<net.minecraft.network.chat.Component>map(key -> net.minecraft.network.chat.Component.literal(key.location().getPath()))
                .orElseGet(() -> net.minecraft.network.chat.Component.translatable("createWorld.customize.aerolite.blank_list"));
    }

    /** The list choices of the add row: Blank first, then every variation list by id. */
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
        AeroliteSettings edited = settings;
        for (ResourceKey<Level> dimension : this.sorted(dimensions)) {
            Optional<ResourceKey<VariationList>> list = this.ownList(dimension);
            if (list.isPresent()) {
                edited = this.withAddedDimension(edited, dimension, list);
            }
        }
        return edited;
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

    /** The name of a dimension. A dimension other than the three vanilla ones shows its id path. */
    public net.minecraft.network.chat.Component dimensionName(ResourceKey<Level> dimension) {
        return VANILLA_DIMENSIONS.contains(dimension)
                ? net.minecraft.network.chat.Component.translatable("createWorld.customize.aerolite.dimension." + dimension.location().getPath())
                : net.minecraft.network.chat.Component.literal(dimension.location().getPath());
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
                .orElseGet(PresetEdits::blankList);
        DimensionSettings added = this.lookup().dimension(dimension, chosen);
        if (added.variations().isEmpty()) {
            added = this.lookup().dimension(dimension, blankList());
        }
        return settings.withDimension(dimension, added);
    }

    private static VariationList blankList() {
        return new VariationList(List.of(new VariationList.Entry(ShaftRegistries.STONE, 1)));
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
        return this.lookup().resolve(blankList()).getFirst();
    }

}
