package us.drullk.aerolite.world.shaft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Maps shaft coordinates to world coordinates.
 * A shaft coordinate is a block along the shaft axis plus a first and a second offset from the shaft center.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 *
 * @param center1 the world coordinate of the first offset origin
 * @param center2 the world coordinate of the second offset origin
 */
public record ShaftFrame(Kind kind, int center1, int center2) {

    public enum Kind {
        /** Along X. The first offset is Y, the second offset is Z. */
        ALONG_X,
        /** Along Z. The first offset is Y, the second offset is X. */
        ALONG_Z,
        /** Along Y. The first offset is X, the second offset is Z. */
        COLUMN
    }

    public BlockPos at(int along, int first, int second) {
        return switch (this.kind) {
            case ALONG_X -> new BlockPos(along, this.center1 + first, this.center2 + second);
            case ALONG_Z -> new BlockPos(this.center2 + second, this.center1 + first, along);
            case COLUMN -> new BlockPos(this.center1 + first, along, this.center2 + second);
        };
    }

    /** The direction of a positive second offset times {@code sign}. */
    public Direction secondDirection(int sign) {
        return switch (this.kind) {
            case ALONG_X, COLUMN -> sign > 0 ? Direction.SOUTH : Direction.NORTH;
            case ALONG_Z -> sign > 0 ? Direction.EAST : Direction.WEST;
        };
    }

}
