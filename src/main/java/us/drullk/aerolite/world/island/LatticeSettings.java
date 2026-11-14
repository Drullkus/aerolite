package us.drullk.aerolite.world.island;

/**
 * The lattice settings of one dimension: the cell size, the cell height, the island bottom and the jitter.
 * The lattice sliders of the Islands screen and of the preview panel edit them.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
public record LatticeSettings(int cellSize, int cellHeight, int layerMinY, double jitter) {

    /** The shipped lattice settings of each dimension. They are the values of 0.5.0. */
    public static final LatticeSettings DEFAULT = new LatticeSettings(IslandSettings.DEFAULT.cellSize(), IslandSettings.DEFAULT.cellHeight(),
            IslandSettings.DEFAULT.layerMinY(), IslandSettings.DEFAULT.jitter());

    public LatticeSettings withCellSize(int cellSize) {
        return new LatticeSettings(cellSize, this.cellHeight, this.layerMinY, this.jitter);
    }

    public LatticeSettings withCellHeight(int cellHeight) {
        return new LatticeSettings(this.cellSize, cellHeight, this.layerMinY, this.jitter);
    }

    public LatticeSettings withLayerMinY(int layerMinY) {
        return new LatticeSettings(this.cellSize, this.cellHeight, layerMinY, this.jitter);
    }

    public LatticeSettings withJitter(double jitter) {
        return new LatticeSettings(this.cellSize, this.cellHeight, this.layerMinY, jitter);
    }

}
