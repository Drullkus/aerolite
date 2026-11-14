package us.drullk.aerolite.world.island.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * The island node {@code aerolite:cell_random}: one seeded value of the cell between {@code min} and {@code max}.
 * Each channel has its own salt, so each channel is independent.
 * With a context other than an {@link IslandContext}, the node gives 0.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public record CellRandom(double min, double max, int channel) implements DensityFunction.SimpleFunction {

    /** The range of {@code DensityFunctions.NOISE_VALUE_CODEC}. */
    public static final double VALUE_LIMIT = 1000000.0;

    public static final MapCodec<CellRandom> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.doubleRange(-VALUE_LIMIT, VALUE_LIMIT).fieldOf("min").forGetter(CellRandom::min),
            Codec.doubleRange(-VALUE_LIMIT, VALUE_LIMIT).fieldOf("max").forGetter(CellRandom::max),
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("channel").forGetter(CellRandom::channel)
    ).apply(instance, CellRandom::new));
    public static final KeyDispatchDataCodec<CellRandom> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    @Override
    public double compute(DensityFunction.FunctionContext context) {
        return context instanceof IslandContext island ? island.random(this.min, this.max, this.channel) : 0;
    }

    /** The least of the two bounds and 0, the value outside an island function. */
    @Override
    public double minValue() {
        return Math.min(0, Math.min(this.min, this.max));
    }

    /** The largest of the two bounds and 0, the value outside an island function. */
    @Override
    public double maxValue() {
        return Math.max(0, Math.max(this.min, this.max));
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

}
