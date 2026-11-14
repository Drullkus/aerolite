package us.drullk.aerolite.world.island.variation;

import net.minecraft.resources.ResourceKey;

import javax.annotation.Nullable;
import java.util.OptionalDouble;

/**
 * The preview override of an island function: Solo and the pinned rolls. It changes no data, and the codec does not write it.
 * Solo of an island variation gives every cell of the row 0 that variation.
 * Solo of an island variation list makes every cell pick inside that list.
 * A pinned cell weight replaces the cell weight roll of every cell of the row 0. A pinned fill replaces the fill roll.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 *
 * @param soloVariation the island variation of every cell, or null
 * @param soloList the island variation list of every pick, or null
 */
public record PreviewOverride(@Nullable ResourceKey<IslandVariation> soloVariation, @Nullable ResourceKey<IslandVariationList> soloList,
                              OptionalDouble cellWeight, OptionalDouble fill) {

    /** No Solo and no pinned roll. */
    public static final PreviewOverride NONE = new PreviewOverride(null, null, OptionalDouble.empty(), OptionalDouble.empty());

    public PreviewOverride {
        if (soloVariation != null && soloList != null) {
            throw new IllegalArgumentException("A preview override holds one Solo at most");
        }
    }

    /** This override with Solo on the island variation. It drops a Solo on a list. */
    public PreviewOverride withSolo(ResourceKey<IslandVariation> variation) {
        return new PreviewOverride(variation, null, this.cellWeight, this.fill);
    }

    /** This override with Solo on the island variation list. It drops a Solo on a variation. */
    public PreviewOverride withSoloList(ResourceKey<IslandVariationList> list) {
        return new PreviewOverride(null, list, this.cellWeight, this.fill);
    }

    public PreviewOverride withoutSolo() {
        return new PreviewOverride(null, null, this.cellWeight, this.fill);
    }

    /** This override with the pinned cell weight, clamped to 0 and 1. An empty value removes the pin. */
    public PreviewOverride withCellWeight(OptionalDouble cellWeight) {
        return new PreviewOverride(this.soloVariation, this.soloList, clamp(cellWeight), this.fill);
    }

    /** This override with the pinned fill, clamped to 0 and 1. An empty value removes the pin. */
    public PreviewOverride withFill(OptionalDouble fill) {
        return new PreviewOverride(this.soloVariation, this.soloList, this.cellWeight, clamp(fill));
    }

    public boolean isNone() {
        return this.equals(NONE);
    }

    private static OptionalDouble clamp(OptionalDouble value) {
        return value.isPresent() ? OptionalDouble.of(Math.clamp(value.getAsDouble(), 0.0, 1.0)) : value;
    }

}
