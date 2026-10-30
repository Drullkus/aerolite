package us.drullk.aerolite.client;

import net.neoforged.api.distmarker.Dist;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.AeroliteSettings;

/**
 * Maps the slider steps of the preset screen to setting values.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 */
@Component(dist = Dist.CLIENT)
public final class PresetSliders {

    public static final int SPACING_STEP = 16;
    public static final int SPACING_STEPS = (AeroliteSettings.MAX_SHAFT_SPACING - AeroliteSettings.MIN_SHAFT_SPACING) / SPACING_STEP;
    /** Steps of 0.05 from 0 to 1. */
    public static final int FRACTION_STEPS = 20;

    public String percent(int step) {
        return Math.round(100F * step / FRACTION_STEPS) + "%";
    }

    public int spacing(int step) {
        return AeroliteSettings.MIN_SHAFT_SPACING + SPACING_STEP * step;
    }

    public int spacingStep(int spacing) {
        return Math.round((spacing - AeroliteSettings.MIN_SHAFT_SPACING) / (float) SPACING_STEP);
    }

}
