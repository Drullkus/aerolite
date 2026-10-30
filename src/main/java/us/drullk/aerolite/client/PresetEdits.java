package us.drullk.aerolite.client;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import tamaized.beanification.Autowired;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.ShaftVariation;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The edit rules of the preset settings. The vanilla screen and the preview panel share them.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 */
@Component(dist = Dist.CLIENT)
public final class PresetEdits {

    /** The vanilla dimensions, in screen sequence. They come before every other dimension. */
    private static final List<ResourceKey<Level>> VANILLA_DIMENSIONS = List.of(Level.OVERWORLD, Level.NETHER, Level.END);

    /**
     * The three vanilla dimensions.
     *
     * @deprecated The preview still calls it. Use {@link #configuredDimensions} or {@link #addableDimensions}.
     */
    @Deprecated(forRemoval = true)
    public static final List<ResourceKey<Level>> DIMENSIONS = VANILLA_DIMENSIONS;

    private final PresetSliders sliders;

    public PresetEdits(@Autowired PresetSliders sliders) {
        this.sliders = sliders;
    }

    /** A minimum spacing above the maximum spacing pushes the maximum along. */
    public AeroliteSettings withMinSpacingStep(AeroliteSettings settings, int step) {
        int min = this.sliders.spacing(step);
        return settings.withShaftSpacing(min, Math.max(min, settings.maxShaftSpacing()));
    }

    /** A maximum spacing below the minimum spacing pushes the minimum along. */
    public AeroliteSettings withMaxSpacingStep(AeroliteSettings settings, int step) {
        int max = this.sliders.spacing(step);
        return settings.withShaftSpacing(Math.min(max, settings.minShaftSpacing()), max);
    }

    public int decayStep(AeroliteSettings settings) {
        return this.sliders.fractionStep(settings.decayThreshold());
    }

    /** The erosion step runs from 1 to {@link PresetSliders#FRACTION_STEPS}. */
    public int erosionStep(AeroliteSettings settings) {
        return this.sliders.fractionStep(settings.erosionThreshold());
    }

    /** The erosion threshold stays at least one step above the decay threshold. */
    public AeroliteSettings withDecayStep(AeroliteSettings settings, int step) {
        int decay = Math.min(step, PresetSliders.FRACTION_STEPS - 1);
        int erosion = Math.max(decay + 1, this.erosionStep(settings));
        return settings.withDecayThreshold(this.sliders.fraction(decay)).withErosionThreshold(this.sliders.fraction(erosion));
    }

    /** The decay threshold stays at least one step below the erosion threshold. */
    public AeroliteSettings withErosionStep(AeroliteSettings settings, int erosion) {
        int decay = Math.min(erosion - 1, this.decayStep(settings));
        return settings.withDecayThreshold(this.sliders.fraction(decay)).withErosionThreshold(this.sliders.fraction(erosion));
    }

    public List<ShaftVariation> variations(AeroliteSettings settings, ResourceKey<Level> dimension) {
        return settings.variations(dimension);
    }

    /** The dimensions of the variation map. The Overworld, the Nether and the End come first, then the rest by id. */
    public List<ResourceKey<Level>> configuredDimensions(AeroliteSettings settings) {
        return this.sorted(settings.variations().keySet());
    }

    /** The dimensions of {@code all} that the variation map does not hold, in the sequence of {@link #configuredDimensions}. */
    public List<ResourceKey<Level>> addableDimensions(AeroliteSettings settings, Collection<ResourceKey<Level>> all) {
        return this.sorted(all.stream().filter(dimension -> !settings.variations().containsKey(dimension)).toList());
    }

    /** The name of a dimension. A dimension other than the three vanilla ones shows its id path. */
    public net.minecraft.network.chat.Component dimensionName(ResourceKey<Level> dimension) {
        return VANILLA_DIMENSIONS.contains(dimension)
                ? net.minecraft.network.chat.Component.translatable("createWorld.customize.aerolite.dimension." + dimension.location().getPath())
                : net.minecraft.network.chat.Component.literal(dimension.location().getPath());
    }

    /** Adds a dimension with its default variation list. A configured dimension stays as it is. */
    public AeroliteSettings withAddedDimension(AeroliteSettings settings, ResourceKey<Level> dimension) {
        return settings.variations().containsKey(dimension) ? settings : settings.withDimension(dimension, ShaftVariation.defaultList(dimension));
    }

    /** Removes a dimension and its variations. The dimension then gets no shafts. */
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
        List<ShaftVariation> variations = this.variations(settings, dimension);
        boolean otherPositive = false;
        for (int other = 0; other < variations.size(); other++) {
            if (other != index && variations.get(other).weight() > 0) {
                otherPositive = true;
            }
        }
        int chosen = weight == 0 && !otherPositive ? 1 : weight;
        return settings.withVariation(dimension, index, variations.get(index).withWeight(chosen));
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
        List<ShaftVariation> rest = this.variations(removed, dimension);
        return rest.stream().anyMatch(other -> other.weight() > 0)
                ? removed
                : removed.withVariation(dimension, 0, rest.getFirst().withWeight(1));
    }

    /** Appends a copy of the first variation, or the default variation of the dimension. */
    public AeroliteSettings withAddedVariation(AeroliteSettings settings, ResourceKey<Level> dimension) {
        List<ShaftVariation> current = this.variations(settings, dimension);
        return settings.withAddedVariation(dimension, current.isEmpty() ? this.defaultVariation(dimension) : current.getFirst());
    }

    private ShaftVariation defaultVariation(ResourceKey<Level> dimension) {
        return ShaftVariation.defaultList(dimension).getFirst();
    }

}
