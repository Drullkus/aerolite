package us.drullk.aerolite.client;

import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import tamaized.beanification.Component;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.ShaftPlacement;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.island.IslandSettings;
import us.drullk.aerolite.world.island.VoronoiIslands;
import us.drullk.aerolite.world.pattern.ChaosSettings;

/**
 * Maps the slider steps of the preset screen to setting values.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 */
@Component(dist = Dist.CLIENT)
public final class PresetSliders {

    public static final int SPACING_STEP = 16;
    public static final int SPACING_STEPS = (ShaftPlacement.MAX_SHAFT_SPACING - ShaftPlacement.MIN_SHAFT_SPACING) / SPACING_STEP;
    /** Steps of 0.05 from 0 to 1. */
    public static final int FRACTION_STEPS = 20;
    public static final int CLOUD_HEIGHT_STEP = 8;
    public static final int CLOUD_HEIGHT_STEPS = (AeroliteSettings.MAX_CLOUD_HEIGHT - AeroliteSettings.MIN_CLOUD_HEIGHT) / CLOUD_HEIGHT_STEP;
    public static final int DISTORTION_STEP = 2;
    public static final int DISTORTION_STEPS = (VoronoiIslands.MAX_DISTORTION - VoronoiIslands.MIN_DISTORTION) / DISTORTION_STEP;
    public static final int CELL_SIZE_STEPS = (IslandSettings.MAX_CELL_SIZE - IslandSettings.MIN_CELL_SIZE) / IslandSettings.CELL_SIZE_STEP;
    public static final int CELL_HEIGHT_STEPS = (IslandSettings.MAX_CELL_HEIGHT - IslandSettings.MIN_CELL_HEIGHT) / IslandSettings.CELL_HEIGHT_STEP;
    /** The island bottom slider spans the build height of every dimension. */
    public static final int ISLAND_BOTTOM_STEPS = (ShaftPlacement.HIGHEST_Y - ShaftPlacement.LOWEST_Y) / IslandSettings.LAYER_STEP;
    public static final int MIN_DISTORTION_SCALE_EXPONENT = Mth.log2(VoronoiIslands.MIN_DISTORTION_SCALE);
    public static final int DISTORTION_SCALE_STEPS = Mth.log2(VoronoiIslands.MAX_DISTORTION_SCALE) - MIN_DISTORTION_SCALE_EXPONENT;
    public static final int DISTORTION_OCTAVE_STEPS = VoronoiIslands.MAX_DISTORTION_OCTAVES - VoronoiIslands.MIN_DISTORTION_OCTAVES;
    /** Steps of 0.05 from the smallest to the largest maze bias. */
    public static final int BIAS_STEPS = Math.round((ShaftVariation.MAX_BIAS - ShaftVariation.MIN_BIAS) * FRACTION_STEPS);
    /** The bias step of a uniform maze. */
    public static final int UNIFORM_BIAS_STEP = Math.round((ShaftVariation.UNIFORM_BIAS - ShaftVariation.MIN_BIAS) * FRACTION_STEPS);
    public static final int MIN_SCALE_EXPONENT = Mth.log2(ShaftVariation.MIN_DECAY_SCALE);
    public static final int MAX_SCALE_EXPONENT = Mth.log2(ShaftVariation.MAX_DECAY_SCALE);
    /** One step per power of two from the smallest to the largest decay scale. */
    public static final int DECAY_SCALE_STEPS = MAX_SCALE_EXPONENT - MIN_SCALE_EXPONENT;
    /** The erosion slider skips the 0% step, so its steps run from 1 to {@link #FRACTION_STEPS}. */
    public static final int EROSION_STEPS = FRACTION_STEPS - 1;
    /** One step per block from the smallest to the largest chaos node spacing. */
    public static final int CHAOS_SPACING_STEPS = ChaosSettings.MAX_SPACING - ChaosSettings.MIN_SPACING;
    /** One step per block from the smallest to the largest chaos corner radius. */
    public static final int CORNER_RADIUS_STEPS = ChaosSettings.MAX_CORNER_RADIUS - ChaosSettings.MIN_CORNER_RADIUS;

    public int fractionStep(float fraction) {
        return Math.round(fraction * FRACTION_STEPS);
    }

    public float fraction(int step) {
        return step / (float) FRACTION_STEPS;
    }

    /** The fraction step of a value from 0 to 1, clamped to the slider. */
    public int clampedFractionStep(float fraction) {
        return Mth.clamp(this.fractionStep(fraction), 0, FRACTION_STEPS);
    }

    /** The fraction of a step, clamped to the slider. */
    public float clampedFraction(int step) {
        return this.fraction(Mth.clamp(step, 0, FRACTION_STEPS));
    }

    public int chaosSpacing(int step) {
        return ChaosSettings.MIN_SPACING + Mth.clamp(step, 0, CHAOS_SPACING_STEPS);
    }

    public int chaosSpacingStep(int spacing) {
        return Mth.clamp(spacing - ChaosSettings.MIN_SPACING, 0, CHAOS_SPACING_STEPS);
    }

    public int cornerRadius(int step) {
        return ChaosSettings.MIN_CORNER_RADIUS + Mth.clamp(step, 0, CORNER_RADIUS_STEPS);
    }

    public int cornerRadiusStep(int radius) {
        return Mth.clamp(radius - ChaosSettings.MIN_CORNER_RADIUS, 0, CORNER_RADIUS_STEPS);
    }

    public int decayScale(int step) {
        return 1 << (MIN_SCALE_EXPONENT + step);
    }

    public int decayScaleStep(int scale) {
        return Mth.log2(scale) - MIN_SCALE_EXPONENT;
    }

    public String percent(int step) {
        return Math.round(100F * step / FRACTION_STEPS) + "%";
    }

    public int spacing(int step) {
        return ShaftPlacement.MIN_SHAFT_SPACING + SPACING_STEP * step;
    }

    public int spacingStep(int spacing) {
        return Math.round((spacing - ShaftPlacement.MIN_SHAFT_SPACING) / (float) SPACING_STEP);
    }

    public int cloudHeight(int step) {
        return AeroliteSettings.MIN_CLOUD_HEIGHT + CLOUD_HEIGHT_STEP * step;
    }

    public int cloudHeightStep(int height) {
        return Math.round((height - AeroliteSettings.MIN_CLOUD_HEIGHT) / (float) CLOUD_HEIGHT_STEP);
    }

    /** The cell boundary distortion of a slider step, in blocks. */
    public int distortion(int step) {
        return VoronoiIslands.MIN_DISTORTION + DISTORTION_STEP * step;
    }

    public int distortionStep(double distortion) {
        return (int) Math.round((distortion - VoronoiIslands.MIN_DISTORTION) / DISTORTION_STEP);
    }

    public int cellSize(int step) {
        return IslandSettings.MIN_CELL_SIZE + IslandSettings.CELL_SIZE_STEP * step;
    }

    public int cellSizeStep(int cellSize) {
        return Math.round((cellSize - IslandSettings.MIN_CELL_SIZE) / (float) IslandSettings.CELL_SIZE_STEP);
    }

    public int cellHeight(int step) {
        return IslandSettings.MIN_CELL_HEIGHT + IslandSettings.CELL_HEIGHT_STEP * step;
    }

    public int cellHeightStep(int cellHeight) {
        return Math.round((cellHeight - IslandSettings.MIN_CELL_HEIGHT) / (float) IslandSettings.CELL_HEIGHT_STEP);
    }

    public int islandBottom(int step) {
        return ShaftPlacement.LOWEST_Y + IslandSettings.LAYER_STEP * step;
    }

    public int islandBottomStep(int layerMinY) {
        return Math.round((layerMinY - ShaftPlacement.LOWEST_Y) / (float) IslandSettings.LAYER_STEP);
    }

    /** The distortion scale steps through the powers of two. */
    public int distortionScale(int step) {
        return 1 << (MIN_DISTORTION_SCALE_EXPONENT + step);
    }

    public int distortionScaleStep(int scale) {
        return Mth.log2(scale) - MIN_DISTORTION_SCALE_EXPONENT;
    }

    public int distortionOctaves(int step) {
        return VoronoiIslands.MIN_DISTORTION_OCTAVES + step;
    }

    public int distortionOctavesStep(int octaves) {
        return octaves - VoronoiIslands.MIN_DISTORTION_OCTAVES;
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

    /** The bias label of a step, with the ringwise, uniform or lengthwise suffix. */
    public net.minecraft.network.chat.Component biasLabel(int step) {
        String suffix = step < UNIFORM_BIAS_STEP ? "ringwise" : step == UNIFORM_BIAS_STEP ? "uniform" : "lengthwise";
        return net.minecraft.network.chat.Component.translatable("createWorld.customize.aerolite.maze_bias." + suffix, this.biasPercent(step));
    }

}
