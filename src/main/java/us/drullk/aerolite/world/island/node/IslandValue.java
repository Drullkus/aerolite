package us.drullk.aerolite.world.island.node;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.function.ToDoubleFunction;

/**
 * An island node without fields: one value of the cell of the block. Each kind is its own density function type.
 * With a context other than an {@link IslandContext}, the node gives 0.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public final class IslandValue implements DensityFunction.SimpleFunction {

    /** The kinds of the island nodes without fields. A kind without a fixed range has no bound. */
    public enum Kind {
        /** The rolled fill minus the gauge at the distorted position, times the cell radius. */
        ISLAND_BODY("island_body", Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, IslandContext::islandBody),
        /** The gauge of the cell at the block. */
        CELL_GAUGE("cell_gauge", Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, IslandContext::gauge),
        /** The distance from the block to the nearest wall, in blocks. */
        WALL_DISTANCE("wall_distance", Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, IslandContext::wallDistance),
        /** The distance from the site to the nearest wall, in blocks. */
        CELL_RADIUS("cell_radius", Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, IslandContext::cellRadius),
        /** The rolled fill. */
        CELL_FILL("cell_fill", 0.0, 1.0, IslandContext::fill),
        /** The rolled cell weight. */
        CELL_WEIGHT("cell_weight", 0.0, 1.0, IslandContext::cellWeight);

        private final String path;
        private final double min;
        private final double max;
        private final ToDoubleFunction<IslandContext> value;
        private final IslandValue function;
        private final KeyDispatchDataCodec<IslandValue> codec;

        Kind(String path, double min, double max, ToDoubleFunction<IslandContext> value) {
            this.path = path;
            this.min = min;
            this.max = max;
            this.value = value;
            this.function = new IslandValue(this);
            this.codec = KeyDispatchDataCodec.of(MapCodec.unit(this.function));
        }

        /** The path of the type id in the namespace {@code aerolite}. */
        public String path() {
            return this.path;
        }

        /** The one function of this kind. */
        public IslandValue function() {
            return this.function;
        }

        public KeyDispatchDataCodec<IslandValue> codec() {
            return this.codec;
        }
    }

    private final Kind kind;

    private IslandValue(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() {
        return this.kind;
    }

    @Override
    public double compute(DensityFunction.FunctionContext context) {
        return context instanceof IslandContext island ? this.kind.value.applyAsDouble(island) : 0;
    }

    @Override
    public double minValue() {
        return this.kind.min;
    }

    @Override
    public double maxValue() {
        return this.kind.max;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return this.kind.codec;
    }

    @Override
    public String toString() {
        return "IslandValue[" + this.kind.path + "]";
    }

}
