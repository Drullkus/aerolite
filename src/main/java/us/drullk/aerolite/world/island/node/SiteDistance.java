package us.drullk.aerolite.world.island.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * The island node {@code aerolite:site_distance}: the distance of the block from the site, in blocks.
 * The plane {@code xyz} measures in three axes. The plane {@code xz} ignores Y.
 * With a context other than an {@link IslandContext}, the node gives 0.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public record SiteDistance(Plane plane) implements DensityFunction.SimpleFunction {

    public static final MapCodec<SiteDistance> MAP_CODEC = Plane.CODEC.fieldOf("plane").xmap(SiteDistance::new, SiteDistance::plane);
    public static final KeyDispatchDataCodec<SiteDistance> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    /** The axes of the distance. */
    public enum Plane implements StringRepresentable {
        XYZ("xyz"),
        XZ("xz");

        public static final Codec<Plane> CODEC = StringRepresentable.fromEnum(Plane::values);

        private final String name;

        Plane(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    @Override
    public double compute(DensityFunction.FunctionContext context) {
        if (!(context instanceof IslandContext island)) {
            return 0;
        }
        double dx = island.siteOffset(Direction.Axis.X);
        double dz = island.siteOffset(Direction.Axis.Z);
        double dy = this.plane == Plane.XYZ ? island.siteOffset(Direction.Axis.Y) : 0;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public double minValue() {
        return 0;
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
