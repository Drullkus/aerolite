package us.drullk.aerolite.devtools.providers;

import java.util.ArrayList;
import java.util.List;

/**
 * The sample variation of one prototype screen: the slider steps and one block field per provider type.
 * The sliders change only these steps. They reach no pillar settings.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class VariationSample {

    public final List<ProviderSample> fields = new ArrayList<>();
    public int columnChanceStep = 10;
    public int minSpacingStep = 4;
    public int maxSpacingStep = 12;
    public int weight = 1;
    public int biasStep = 9;
    public int decayScaleStep = 1;
    public int decayStep = 7;
    public int erosionStep = 10;

    /** The default sample: one field per provider type, in the sequence of {@link ProviderType}. */
    public static VariationSample defaults() {
        VariationSample sample = new VariationSample();
        for (ProviderType type : ProviderType.values()) {
            sample.fields.add(new ProviderSample(type));
        }
        return sample;
    }

    public VariationSample copy() {
        VariationSample copy = new VariationSample();
        this.fields.forEach(field -> copy.fields.add(field.copy()));
        copy.columnChanceStep = this.columnChanceStep;
        copy.minSpacingStep = this.minSpacingStep;
        copy.maxSpacingStep = this.maxSpacingStep;
        copy.weight = this.weight;
        copy.biasStep = this.biasStep;
        copy.decayScaleStep = this.decayScaleStep;
        copy.decayStep = this.decayStep;
        copy.erosionStep = this.erosionStep;
        return copy;
    }

    /** The label of a field, such as "Block 3". */
    public static String label(int index) {
        return "Block " + (index + 1);
    }

}
