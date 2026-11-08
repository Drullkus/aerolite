package us.drullk.aerolite.world.island;

import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * The island settings of a world: the fields of the island density function without its noise.
 * The Islands screen and the preview panel edit them. The generators of the world store them.
 * The constants hold the slider bounds and the shipped values.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 */
public record IslandSettings(int cellSize, int cellHeight, int layerMinY, double jitter, double fillMin, double fillMax, double emptyChance,
                             double distortion, int distortionScale, int distortionOctaves) {

    /** The slider bounds of the cell size along X and Z, in blocks. */
    public static final int MIN_CELL_SIZE = 64;
    public static final int MAX_CELL_SIZE = 1024;
    public static final int CELL_SIZE_STEP = 32;
    /** The slider bounds of the cell height, in blocks. */
    public static final int MIN_CELL_HEIGHT = 32;
    public static final int MAX_CELL_HEIGHT = 512;
    public static final int CELL_HEIGHT_STEP = 16;
    /** The slider step of the island bottom, in blocks. The slider spans the build height of every dimension. */
    public static final int LAYER_STEP = 16;
    /** The shipped values of the island density function of each dimension. */
    public static final IslandSettings DEFAULT = new IslandSettings(256, 128, 0, 0.8, 0.3, 0.9, 0.1, 12.0, 64, 3);

    /** The settings of an island density function. */
    public static IslandSettings of(VoronoiIslands islands) {
        return new IslandSettings(islands.cellSize(), islands.cellHeight(), islands.layerMinY(), islands.jitter(), islands.fillMin(), islands.fillMax(),
                islands.emptyChance(), islands.distortion(), islands.distortionScale(), islands.distortionOctaves());
    }

    /** An island density function with these settings over the noise. */
    public VoronoiIslands function(DensityFunction.NoiseHolder noise) {
        return new VoronoiIslands(noise, this.cellSize, this.cellHeight, this.layerMinY, this.jitter, this.fillMin, this.fillMax, this.emptyChance,
                this.distortion, this.distortionScale, this.distortionOctaves);
    }

    /** The island density function with these settings and the noise of {@code islands}. */
    public VoronoiIslands apply(VoronoiIslands islands) {
        return this.function(islands.noise());
    }

    public IslandSettings withCellSize(int cellSize) {
        return new IslandSettings(cellSize, this.cellHeight, this.layerMinY, this.jitter, this.fillMin, this.fillMax, this.emptyChance, this.distortion, this.distortionScale, this.distortionOctaves);
    }

    public IslandSettings withCellHeight(int cellHeight) {
        return new IslandSettings(this.cellSize, cellHeight, this.layerMinY, this.jitter, this.fillMin, this.fillMax, this.emptyChance, this.distortion, this.distortionScale, this.distortionOctaves);
    }

    public IslandSettings withLayerMinY(int layerMinY) {
        return new IslandSettings(this.cellSize, this.cellHeight, layerMinY, this.jitter, this.fillMin, this.fillMax, this.emptyChance, this.distortion, this.distortionScale, this.distortionOctaves);
    }

    public IslandSettings withJitter(double jitter) {
        return new IslandSettings(this.cellSize, this.cellHeight, this.layerMinY, jitter, this.fillMin, this.fillMax, this.emptyChance, this.distortion, this.distortionScale, this.distortionOctaves);
    }

    /** A fill minimum above the fill maximum pushes the maximum along. */
    public IslandSettings withFillMin(double fillMin) {
        return new IslandSettings(this.cellSize, this.cellHeight, this.layerMinY, this.jitter, fillMin, Math.max(fillMin, this.fillMax), this.emptyChance, this.distortion, this.distortionScale, this.distortionOctaves);
    }

    /** A fill maximum below the fill minimum pushes the minimum along. */
    public IslandSettings withFillMax(double fillMax) {
        return new IslandSettings(this.cellSize, this.cellHeight, this.layerMinY, this.jitter, Math.min(this.fillMin, fillMax), fillMax, this.emptyChance, this.distortion, this.distortionScale, this.distortionOctaves);
    }

    public IslandSettings withEmptyChance(double emptyChance) {
        return new IslandSettings(this.cellSize, this.cellHeight, this.layerMinY, this.jitter, this.fillMin, this.fillMax, emptyChance, this.distortion, this.distortionScale, this.distortionOctaves);
    }

    public IslandSettings withDistortion(double distortion) {
        return new IslandSettings(this.cellSize, this.cellHeight, this.layerMinY, this.jitter, this.fillMin, this.fillMax, this.emptyChance, distortion, this.distortionScale, this.distortionOctaves);
    }

    public IslandSettings withDistortionScale(int distortionScale) {
        return new IslandSettings(this.cellSize, this.cellHeight, this.layerMinY, this.jitter, this.fillMin, this.fillMax, this.emptyChance, this.distortion, distortionScale, this.distortionOctaves);
    }

    public IslandSettings withDistortionOctaves(int distortionOctaves) {
        return new IslandSettings(this.cellSize, this.cellHeight, this.layerMinY, this.jitter, this.fillMin, this.fillMax, this.emptyChance, this.distortion, this.distortionScale, distortionOctaves);
    }

}
