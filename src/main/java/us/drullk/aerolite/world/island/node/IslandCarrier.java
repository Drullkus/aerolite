package us.drullk.aerolite.world.island.node;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * The carrier, the pass-through density function type {@code aerolite:island_carrier}. It computes its argument.
 * Its field {@code islands} holds the island function for the island biome source. {@link #mapAll} maps both fields.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public final class IslandCarrier implements DensityFunction {

    public static final MapCodec<IslandCarrier> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("islands").forGetter(IslandCarrier::islands),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(IslandCarrier::argument)
    ).apply(instance, IslandCarrier::new));
    public static final KeyDispatchDataCodec<IslandCarrier> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    private final DensityFunction islands;
    private final DensityFunction argument;

    public IslandCarrier(DensityFunction islands, DensityFunction argument) {
        this.islands = islands;
        this.argument = argument;
    }

    public DensityFunction islands() {
        return this.islands;
    }

    public DensityFunction argument() {
        return this.argument;
    }

    @Override
    public double compute(FunctionContext context) {
        return this.argument.compute(context);
    }

    @Override
    public void fillArray(double[] array, ContextProvider contextProvider) {
        this.argument.fillArray(array, contextProvider);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new IslandCarrier(this.islands.mapAll(visitor), this.argument.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        return this.argument.minValue();
    }

    @Override
    public double maxValue() {
        return this.argument.maxValue();
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

}
