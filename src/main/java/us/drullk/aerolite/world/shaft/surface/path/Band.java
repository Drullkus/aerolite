package us.drullk.aerolite.world.shaft.surface.path;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * The unwrapped outer layer of one pillar over an axis range: a grid of ring position by axis position.
 *
 * <p>The contract of a band:
 * <ul>
 * <li>The ring has the length of the constructor: 48 for a pillar, 64 for an I-beam, 28 for a bridge plate. The ring wraps.</li>
 * <li>The axis covers {@code [axis0, axis0 + length)} in global axis positions. The axis does not wrap.</li>
 * <li>A position is a {@link BandPos} with a wrapped ring position. {@link #pos} wraps any ring position.</li>
 * <li>Each block has one {@link BandKind}. A new band is all wall.</li>
 * <li>A corner is a ring position from the constructor. An axis step on a corner never links.</li>
 * <li>The band holds two node sets: groove nodes and face nodes. A node on a corner is a corner node.</li>
 * <li>{@link #crop} copies an axis range with the node sets cut to that range.</li>
 * <li>A band is mutable and not thread safe. A pattern fills the kinds and the node sets of its band.</li>
 * </ul>
 *
 * <p>Each pattern paints into a band, and the surface path library reads the same band.
 * The surface model of the shapes adopts this type as the band of every pattern.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class Band {

    /** The ring length of a pillar. */
    public static final int PILLAR_RING = 48;

    private static final int[] PILLAR_CORNERS = {0, 12, 24, 36};

    private final int ringLength;
    private final int axis0;
    private final int length;
    private final int[] corners;
    private final boolean[] cornerRing;
    private final byte[] kinds;
    private final LongSet grooveNodes = new LongOpenHashSet();
    private final LongSet faceNodes = new LongOpenHashSet();

    /**
     * An all-wall band.
     *
     * @param ringLength the blocks around the ring
     * @param axis0 the first axis position
     * @param length the blocks along the axis
     * @param corners the corner ring positions, each in {@code [0, ringLength)}
     */
    public Band(int ringLength, int axis0, int length, int... corners) {
        if (ringLength < 1 || length < 0 || axis0 == Integer.MIN_VALUE) {
            throw new IllegalArgumentException("Bad band size: ring " + ringLength + ", axis " + axis0 + ", length " + length);
        }
        this.ringLength = ringLength;
        this.axis0 = axis0;
        this.length = length;
        this.cornerRing = new boolean[ringLength];
        for (int corner : corners) {
            if (corner < 0 || corner >= ringLength) {
                throw new IllegalArgumentException("Corner " + corner + " is outside the ring of " + ringLength);
            }
            this.cornerRing[corner] = true;
        }
        this.corners = Arrays.stream(corners).distinct().sorted().toArray();
        this.kinds = new byte[ringLength * length];
    }

    /** An all-wall pillar band: ring 48, corners at 0, 12, 24 and 36. */
    public static Band pillar(int axis0, int length) {
        return new Band(PILLAR_RING, axis0, length, PILLAR_CORNERS);
    }

    public int ringLength() {
        return this.ringLength;
    }

    public int axis0() {
        return this.axis0;
    }

    public int length() {
        return this.length;
    }

    /** The first axis position after the band. */
    public int axisEnd() {
        return this.axis0 + this.length;
    }

    /** The corner ring positions, sorted. */
    public int[] corners() {
        return this.corners.clone();
    }

    /** The position with the ring position wrapped. */
    public long pos(int ring, int axis) {
        return BandPos.pack(Math.floorMod(ring, this.ringLength), axis);
    }

    /** True when the axis position lies in the band. */
    public boolean insideAxis(int axis) {
        return axis >= this.axis0 && axis < this.axis0 + this.length;
    }

    /** True when the position lies in the band. */
    public boolean inside(long pos) {
        return pos != BandPos.NONE && this.insideAxis(BandPos.axis(pos));
    }

    public boolean isCornerRing(int ring) {
        return this.cornerRing[Math.floorMod(ring, this.ringLength)];
    }

    /** True when the position sits on a corner ring position. */
    public boolean isCorner(long pos) {
        return this.cornerRing[BandPos.ring(pos)];
    }

    private int index(long pos) {
        int axis = BandPos.axis(pos);
        if (!this.insideAxis(axis)) {
            throw new IndexOutOfBoundsException("Position " + BandPos.toString(pos) + " is outside the band");
        }
        return (axis - this.axis0) * this.ringLength + BandPos.ring(pos);
    }

    public BandKind get(long pos) {
        return BandKind.byId(this.kinds[this.index(pos)]);
    }

    public BandKind get(int ring, int axis) {
        return this.get(this.pos(ring, axis));
    }

    public void set(long pos, BandKind kind) {
        this.kinds[this.index(pos)] = (byte) kind.ordinal();
    }

    public void set(int ring, int axis, BandKind kind) {
        this.set(this.pos(ring, axis), kind);
    }

    /** True when the position lies in the band and holds a groove or a surface path. */
    public boolean isPath(long pos) {
        return this.inside(pos) && this.get(pos).isPath();
    }

    /** The neighbor in the direction, with the ring wrapped, or {@link BandPos#NONE} outside the axis range. */
    public long step(long pos, BandDirection direction) {
        int axis = BandPos.axis(pos) + direction.axis();
        if (!this.insideAxis(axis)) {
            return BandPos.NONE;
        }
        return BandPos.pack(Math.floorMod(BandPos.ring(pos) + direction.ring(), this.ringLength), axis);
    }

    /** The 4-neighbors inside the band, in the sequence of {@link BandDirection}. */
    public LongList neighbors(long pos) {
        LongList out = new LongArrayList(4);
        for (BandDirection direction : BandDirection.values()) {
            long next = this.step(pos, direction);
            if (next != BandPos.NONE) {
                out.add(next);
            }
        }
        return out;
    }

    /** True when the neighbor blocks link: an axis step on a corner never links. */
    public boolean linked(long a, long b) {
        return BandPos.axis(a) == BandPos.axis(b) || !this.isCorner(a);
    }

    /** The direction of the step from one block to its neighbor, or null when the blocks are no neighbors. */
    @Nullable
    public BandDirection direction(long from, long to) {
        for (BandDirection direction : BandDirection.values()) {
            if (this.step(from, direction) == to) {
                return direction;
            }
        }
        return null;
    }

    /** The groove nodes. The set is live. */
    public LongSet grooveNodes() {
        return this.grooveNodes;
    }

    /** The face nodes. The set is live. */
    public LongSet faceNodes() {
        return this.faceNodes;
    }

    /** Every position of the kind, sorted. */
    public LongList blocksOf(BandKind kind) {
        LongList out = new LongArrayList();
        byte id = (byte) kind.ordinal();
        for (int i = 0; i < this.kinds.length; i++) {
            if (this.kinds[i] == id) {
                out.add(BandPos.pack(i % this.ringLength, this.axis0 + i / this.ringLength));
            }
        }
        return out;
    }

    /** A copy of the band over {@code [axis0, axis0 + length)}, with the node sets cut to that range. The range must lie in the band. */
    public Band crop(int axis0, int length) {
        if (axis0 < this.axis0 || axis0 + length > this.axisEnd()) {
            throw new IndexOutOfBoundsException("Crop [" + axis0 + ", " + (axis0 + length) + ") is outside the band");
        }
        Band out = new Band(this.ringLength, axis0, length, this.corners);
        System.arraycopy(this.kinds, (axis0 - this.axis0) * this.ringLength, out.kinds, 0, out.kinds.length);
        this.grooveNodes.forEach(pos -> {
            if (out.inside(pos)) {
                out.grooveNodes.add(pos);
            }
        });
        this.faceNodes.forEach(pos -> {
            if (out.inside(pos)) {
                out.faceNodes.add(pos);
            }
        });
        return out;
    }

    /** A full copy of the band. */
    public Band copy() {
        return this.crop(this.axis0, this.length);
    }

    /** True when the other band has the same ring, axis range and block kinds. The node sets do not count. */
    public boolean sameKinds(Band other) {
        return this.ringLength == other.ringLength && this.axis0 == other.axis0 && this.length == other.length
            && Arrays.equals(this.kinds, other.kinds);
    }

}
