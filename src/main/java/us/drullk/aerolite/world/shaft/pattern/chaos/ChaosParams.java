package us.drullk.aerolite.world.shaft.pattern.chaos;

import net.minecraft.util.Mth;
import us.drullk.aerolite.world.pattern.ChaosSettings;
import us.drullk.aerolite.world.pattern.SurfacePathSettings;
import us.drullk.aerolite.world.shaft.surface.path.LoopRationing;

/**
 * The chaos options of one ring, after the clamps and the jitter cap.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 *
 * @param cells      the cell kind
 * @param spacing    the node spacing in blocks, from 6 to 12
 * @param jitter     the offset range of each point after the cap of its spacing
 * @param radius     the corner radius, 0 for none
 * @param surface    true places surface paths
 * @param cut        true carves the surface paths as grooves
 * @param share      the loop share, from 0 to 1
 * @param chance     the chance of a surface path on one face
 * @param ringLength the blocks around the ring
 */
public record ChaosParams(ChaosSettings.Cells cells, int spacing, double jitter, int radius, boolean surface, boolean cut, double share,
                          double chance, int ringLength) {

    /** The blocks of one chunk along the axis. */
    public static final int CHUNK = LoopRationing.SEGMENT_LENGTH;
    /** The merge width of the label rules: a face without a square of this side merges. */
    public static final int MERGE_WIDTH = 2;
    /** The tree segments above a connector whose joins the connector sees. */
    public static final int LOOK = LoopRationing.DEFAULT_LOOK;
    /** The chunks below a window range whose faces can take a surface path. */
    public static final int PATH_BELOW = 4;
    /** The chunks above a window range whose faces can take a surface path. */
    public static final int PATH_ABOVE = 2 * LOOK + 4;

    /** The options of the settings on a ring of the given length. */
    public static ChaosParams of(ChaosSettings settings, int ringLength) {
        int spacing = Mth.clamp(settings.spacing(), ChaosSettings.MIN_SPACING, ChaosSettings.MAX_SPACING);
        double jitter = Math.min(Mth.clamp(settings.jitter(), ChaosSettings.MIN_JITTER, ChaosSettings.MAX_JITTER), jitterCap(spacing));
        int radius = Mth.clamp(settings.cornerRadius(), ChaosSettings.MIN_CORNER_RADIUS, ChaosSettings.MAX_CORNER_RADIUS);
        SurfacePathSettings paths = settings.paths();
        double share = paths.allowLoops() ? Mth.clamp(paths.loopShare(), 0, 1) : 0;
        return new ChaosParams(settings.cells(), spacing, jitter, radius, paths.surfacePaths(), paths.surfacePaths() && paths.cutting(), share,
                Mth.clamp(paths.pathChance(), 0, 1), ringLength);
    }

    /** The largest jitter at which every stone of the spacing stays 3 blocks across. */
    public static double jitterCap(int spacing) {
        return switch (spacing) {
            case 6 -> 0.25;
            case 7 -> 0.6;
            case 8 -> 0.7;
            case 9, 10 -> 0.85;
            default -> 1;
        };
    }

    /** The cells around the ring: the ring length over the spacing, rounded half up. */
    public int cellsAround() {
        return Math.max(1, (int) Math.floor((double) this.ringLength / this.spacing + 0.5));
    }

    /** The width of one cell around the ring. */
    public double cellWidth() {
        return (double) this.ringLength / this.cellsAround();
    }

    /** The side of the center ring of a Delaunay stone. */
    public int ringSide() {
        return 4 + (this.spacing >= 8 ? 1 : 0) + (this.spacing >= 12 ? 1 : 0);
    }

    /** The chunks of margin on each side of a window, so any window gives the same chunk. */
    public int margin() {
        return LoopRationing.margin(LOOK) + Math.ceilDiv(3 * this.spacing, CHUNK) + Math.ceilDiv(this.radius, CHUNK);
    }

}
