package us.drullk.aerolite.world.island.node;

import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.phys.Vec3;
import us.drullk.aerolite.world.island.VoronoiLattice;
import us.drullk.aerolite.world.island.variation.CellRolls;
import us.drullk.aerolite.world.island.variation.IslandVariation;

/**
 * The context that the island function gives the density function of a variation. It holds the block position, the cell,
 * the variation and the rolls. The island nodes read it. The blender comes from the context of the island function.
 * The cell is a cell of the row 0 that is not lost.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public final class IslandContext implements DensityFunction.FunctionContext {

    private final DensityFunction.FunctionContext outer;
    private final VoronoiLattice lattice;
    private final VoronoiLattice.Cell cell;
    private final IslandVariation variation;
    private final Distortion distortion;
    private final CellRolls rolls;
    private final double fill;

    public IslandContext(DensityFunction.FunctionContext outer, VoronoiLattice lattice, VoronoiLattice.Cell cell, IslandVariation variation, Distortion distortion,
                         CellRolls rolls, double fill) {
        this.outer = outer;
        this.lattice = lattice;
        this.cell = cell;
        this.variation = variation;
        this.distortion = distortion;
        this.rolls = rolls;
        this.fill = fill;
    }

    @Override
    public int blockX() {
        return this.outer.blockX();
    }

    @Override
    public int blockY() {
        return this.outer.blockY();
    }

    @Override
    public int blockZ() {
        return this.outer.blockZ();
    }

    @Override
    public Blender getBlender() {
        return this.outer.getBlender();
    }

    public VoronoiLattice lattice() {
        return this.lattice;
    }

    public VoronoiLattice.Cell cell() {
        return this.cell;
    }

    public IslandVariation variation() {
        return this.variation;
    }

    /** The rolled fill of the cell, or the pinned fill. */
    public double fill() {
        return this.fill;
    }

    /** The rolled cell weight of the cell, or the pinned cell weight. */
    public double cellWeight() {
        return this.lattice.cellWeight(this.cell);
    }

    /** The gauge of the cell at the block. */
    public double gauge() {
        return this.lattice.evaluate(this.cell, this.blockX(), this.blockY(), this.blockZ()).value();
    }

    /** The distance from the block to the nearest wall of the cell, in blocks. */
    public double wallDistance() {
        return this.lattice.values(this.cell, this.blockX(), this.blockY(), this.blockZ()).wallDistance();
    }

    /** The distance from the site to the nearest wall of the cell, in blocks. */
    public double cellRadius() {
        return this.lattice.cellRadius(this.cell);
    }

    /** The offset of the block from the site along the axis, in blocks. */
    public double siteOffset(Direction.Axis axis) {
        return switch (axis) {
            case X -> this.blockX() - this.cell.siteX();
            case Y -> this.blockY() - this.cell.siteY();
            case Z -> this.blockZ() - this.cell.siteZ();
        };
    }

    /** The island body: the fill minus the gauge at the distorted position, times the cell radius. */
    public double islandBody() {
        Vec3 shifted = this.distortion.warp(this.blockX(), this.blockY(), this.blockZ());
        VoronoiLattice.Gauge gauge = this.lattice.evaluate(this.cell, shifted.x, shifted.y, shifted.z);
        return (this.fill - gauge.value()) * gauge.halfSpacing();
    }

    /** One seeded value of the cell between the two bounds. Each channel is independent. */
    public double random(double min, double max, int channel) {
        return min + (max - min) * this.rolls.random(channel, this.cell.ix(), this.cell.iy(), this.cell.iz());
    }

}
