package us.drullk.aerolite.client;

import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.ShaftVariation;

/**
 * Maps the slider steps of the preset screen to setting values.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 */
@Component(dist = Dist.CLIENT)
public final class PresetSliders {

    public static final int SPACING_STEP = 16;
    public static final int SPACING_STEPS = (AeroliteSettings.MAX_SHAFT_SPACING - AeroliteSettings.MIN_SHAFT_SPACING) / SPACING_STEP;
    /** Steps of 0.05 from 0 to 1. */
    public static final int FRACTION_STEPS = 20;
    public static final int CLOUD_HEIGHT_STEP = 8;
    public static final int CLOUD_HEIGHT_STEPS = (AeroliteSettings.MAX_CLOUD_HEIGHT - AeroliteSettings.MIN_CLOUD_HEIGHT) / CLOUD_HEIGHT_STEP;
    /** Steps of 0.05 from the smallest to the largest maze bias. */
    public static final int BIAS_STEPS = Math.round((ShaftVariation.MAX_BIAS - ShaftVariation.MIN_BIAS) * FRACTION_STEPS);
    /** The bias step of a uniform maze. */
    public static final int UNIFORM_BIAS_STEP = Math.round((ShaftVariation.UNIFORM_BIAS - ShaftVariation.MIN_BIAS) * FRACTION_STEPS);

    public String percent(int step) {
        return Math.round(100F * step / FRACTION_STEPS) + "%";
    }

    public int spacing(int step) {
        return AeroliteSettings.MIN_SHAFT_SPACING + SPACING_STEP * step;
    }

    public int spacingStep(int spacing) {
        return Math.round((spacing - AeroliteSettings.MIN_SHAFT_SPACING) / (float) SPACING_STEP);
    }

    public int cloudHeight(int step) {
        return AeroliteSettings.MIN_CLOUD_HEIGHT + CLOUD_HEIGHT_STEP * step;
    }

    public int cloudHeightStep(int height) {
        return Math.round((height - AeroliteSettings.MIN_CLOUD_HEIGHT) / (float) CLOUD_HEIGHT_STEP);
    }

    public float bias(int step) {
        return (step + Math.round(ShaftVariation.MIN_BIAS * FRACTION_STEPS)) / (float) FRACTION_STEPS;
    }

    public int biasStep(float bias) {
        return Mth.clamp(Math.round((bias - ShaftVariation.MIN_BIAS) * FRACTION_STEPS), 0, BIAS_STEPS);
    }

    /** The percent of a bias step, from 5% to 95%. */
    public String biasPercent(int step) {
        return this.percent(step + Math.round(ShaftVariation.MIN_BIAS * FRACTION_STEPS));
    }

}
