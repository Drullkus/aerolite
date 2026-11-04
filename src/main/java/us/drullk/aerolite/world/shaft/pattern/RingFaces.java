package us.drullk.aerolite.world.shaft.pattern;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import us.drullk.aerolite.world.shaft.surface.RingShape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The faces of one ring: the runs of ring positions from one band corner to the next.
 *
 * <p>A face column counts from 0 at the corner where the face starts. The corner where the face ends is column 0 of the next face.
 * So a face of the Pillar has the columns 0 to 11, and its width is 12. The rules of the prototypes name the columns 0 to 12.
 * A ring without a corner has one face that starts at ring position 0 and spans the whole ring.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class RingFaces {

    private final int length;
    private final int[] starts;
    private final int[] widths;
    private final int[] faceOf;
    private final int[] columnOf;

    /**
     * @param length  the blocks around the ring
     * @param corners the corner ring positions, sorted
     */
    public RingFaces(int length, int[] corners) {
        this.length = length;
        this.starts = corners.length == 0 ? new int[] {0} : corners.clone();
        this.widths = new int[this.starts.length];
        this.faceOf = new int[length];
        this.columnOf = new int[length];
        for (int face = 0; face < this.starts.length; face++) {
            int start = this.starts[face];
            int end = face + 1 < this.starts.length ? this.starts[face + 1] : this.starts[0] + length;
            this.widths[face] = end - start;
            for (int column = 0; column < this.widths[face]; column++) {
                int index = Math.floorMod(start + column, length);
                this.faceOf[index] = face;
                this.columnOf[index] = column;
            }
        }
    }

    /** The faces of one ring of a ring description. */
    public static RingFaces of(RingShape ring, int r) {
        int[] corners = ring.bandCorners(r);
        Arrays.sort(corners);
        return new RingFaces(ring.length(r), corners);
    }

    public int length() {
        return this.length;
    }

    public int faces() {
        return this.starts.length;
    }

    /** The ring position of column 0 of the face. */
    public int start(int face) {
        return this.starts[face];
    }

    /** The columns of the face. */
    public int width(int face) {
        return this.widths[face];
    }

    /** The face of the ring position. */
    public int face(int index) {
        return this.faceOf[Math.floorMod(index, this.length)];
    }

    /** The face column of the ring position. */
    public int column(int index) {
        return this.columnOf[Math.floorMod(index, this.length)];
    }

    /** The width of the face of the ring position. */
    public int faceWidth(int index) {
        return this.widths[this.face(index)];
    }

    /** True when the ring position is the column of the face where {@code column mod period == residue}, and not a corner. */
    public boolean inner(int index, int period, int residue) {
        int column = this.column(index);
        return column > 0 && Math.floorMod(column, period) == residue;
    }

    /** The marks of every ring position whose face column passes {@link #inner}. */
    public boolean[] innerMarks(int period, int residue) {
        boolean[] marks = new boolean[this.length];
        for (int index = 0; index < this.length; index++) {
            marks[index] = this.inner(index, period, residue);
        }
        return marks;
    }

    /**
     * The maximal runs of unmarked ring positions, in ring sequence.
     * The first run holds ring position 0, or it is the first run after ring position 0 when that position is marked.
     * Each run lists wrapped ring positions in ring sequence. A ring without a mark is one run from position 0.
     */
    public static List<IntList> runs(boolean[] marks) {
        int length = marks.length;
        List<IntList> out = new ArrayList<>();
        int start = 0;
        if (!marks[0]) {
            int steps = 0;
            while (steps < length && !marks[Math.floorMod(start - 1, length)]) {
                start--;
                steps++;
            }
            if (steps == length) {
                IntList all = new IntArrayList(length);
                for (int index = 0; index < length; index++) {
                    all.add(index);
                }
                out.add(all);
                return out;
            }
        }
        IntList current = null;
        for (int step = 0; step < length; step++) {
            int index = Math.floorMod(start + step, length);
            if (marks[index]) {
                current = null;
                continue;
            }
            if (current == null) {
                current = new IntArrayList();
                out.add(current);
            }
            current.add(index);
        }
        return out;
    }

}
