package us.drullk.aerolite.client;

import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import us.drullk.aerolite.world.ShaftPlacement;

import java.util.Optional;

/**
 * The build limits of one dimension, and the steps of the two pillar range sliders over them.
 * The bottom slider runs up to the smallest span below the top limit. The top slider starts the smallest span above the bottom limit.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-06_00-33_bb95aa4e-2e64-45f7-a528-d3415d48c0f3.md}.
 *
 * @param bottom the bottom build limit
 * @param top    the top build limit, exclusive
 */
public record YLimits(int bottom, int top) {

    /** The build limits of a dimension of the world being created, or empty when the world does not hold it. */
    public static Optional<YLimits> of(WorldCreationContext context, ResourceKey<Level> dimension) {
        LevelStem stem = PresetEdits.worldStems(context).get(Registries.levelToLevelStem(dimension));
        if (stem == null) {
            return Optional.empty();
        }
        DimensionType type = stem.type().value();
        return Optional.of(new YLimits(type.minY(), type.minY() + type.height()));
    }

    /** The step count of each slider. */
    public int steps() {
        return (this.top - this.bottom - ShaftPlacement.MIN_Y_SPAN) / ShaftPlacement.Y_STEP;
    }

    /** True when the build height is taller than the smallest span, so the sliders have steps. */
    public boolean hasRange() {
        return this.steps() > 0;
    }

    public int bottomAt(int step) {
        return this.bottom + ShaftPlacement.Y_STEP * step;
    }

    public int topAt(int step) {
        return this.bottom + ShaftPlacement.MIN_Y_SPAN + ShaftPlacement.Y_STEP * step;
    }

    /** The step of the range bottom, clamped to the slider. */
    public int bottomStep(ShaftPlacement placement) {
        return Mth.clamp(Math.floorDiv(placement.minY() - this.bottom, ShaftPlacement.Y_STEP), 0, this.steps());
    }

    /** The step of the range top, clamped to the slider. */
    public int topStep(ShaftPlacement placement) {
        return Mth.clamp(Math.floorDiv(placement.maxY() - this.bottom - ShaftPlacement.MIN_Y_SPAN, ShaftPlacement.Y_STEP), 0, this.steps());
    }

}
