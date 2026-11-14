package us.drullk.aerolite.world.island.variation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * A range of a minimum and a maximum inside 0 to 1. The JSON form is a pair of numbers.
 * The constructor sorts the pair and clamps each value to 0 and 1.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public record UnitRange(double min, double max) {

    public static final Codec<UnitRange> CODEC = Codec.DOUBLE.listOf().comapFlatMap(
            pair -> pair.size() == 2
                    ? DataResult.success(new UnitRange(pair.get(0), pair.get(1)))
                    : DataResult.error(() -> "A range is a pair of two numbers, not " + pair.size()),
            range -> List.of(range.min, range.max));

    public UnitRange {
        double low = Mth.clamp(Math.min(min, max), 0.0, 1.0);
        double high = Mth.clamp(Math.max(min, max), 0.0, 1.0);
        min = low;
        max = high;
    }

    /** The value at the fraction of the way from the minimum to the maximum. */
    public double at(double fraction) {
        return this.min + (this.max - this.min) * fraction;
    }

}
