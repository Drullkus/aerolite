package us.drullk.aerolite.client;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import tamaized.beanification.Autowired;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.ShaftVariation;

import java.util.List;

/**
 * The edit rules of the preset settings. The vanilla screen and the preview panel share them.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 */
@Component(dist = Dist.CLIENT)
public final class PresetEdits {

    /** The dimensions that the variation sections edit, in screen sequence. */
    public static final List<ResourceKey<Level>> DIMENSIONS = List.of(Level.OVERWORLD, Level.NETHER, Level.END);

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
        return settings.variations().getOrDefault(dimension, List.of());
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
        List<ShaftVariation> defaults = AeroliteSettings.DEFAULT.variations().get(dimension);
        return defaults == null || defaults.isEmpty() ? ShaftVariation.STONE : defaults.getFirst();
    }

}
