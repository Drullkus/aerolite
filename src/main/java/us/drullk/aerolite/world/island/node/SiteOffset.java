package us.drullk.aerolite.world.island.node;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * The island node {@code aerolite:site_offset}: the offset of the block from the site along the axis, in blocks.
 * With a context other than an {@link IslandContext}, the node gives 0.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public record SiteOffset(Direction.Axis axis) implements DensityFunction.SimpleFunction {

    public static final MapCodec<SiteOffset> MAP_CODEC = Direction.Axis.CODEC.fieldOf("axis").xmap(SiteOffset::new, SiteOffset::axis);
    public static final KeyDispatchDataCodec<SiteOffset> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    @Override
    public double compute(DensityFunction.FunctionContext context) {
        return context instanceof IslandContext island ? island.siteOffset(this.axis) : 0;
    }

    @Override
    public double minValue() {
        return Double.NEGATIVE_INFINITY;
    }

    @Override
    public double maxValue() {
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

}
