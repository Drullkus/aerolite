package us.drullk.aerolite.world.shaft.pattern.bricks;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.util.RandomSource;
import us.drullk.aerolite.world.pattern.BricksSettings;
import us.drullk.aerolite.world.shaft.binding.PatternContext;
import us.drullk.aerolite.world.shaft.pattern.BandPattern;
import us.drullk.aerolite.world.shaft.pattern.PatternCache;
import us.drullk.aerolite.world.shaft.pattern.PatternRegion;
import us.drullk.aerolite.world.shaft.pattern.RingFaces;
import us.drullk.aerolite.world.shaft.surface.path.Band;
import us.drullk.aerolite.world.shaft.surface.path.BandKind;

import java.util.ArrayList;
import java.util.List;

/**
 * The bricks pattern. Bed joints are grooves around the ring. Head joints are grooves along the axis inside one course.
 *
 * <p>The rules follow {@code prototypes/patterns/README.md}. A rule on the face columns 0 to 12 applies to each face between two corners.
 * A face of another width keeps the same period from its first column. Head joints never sit on a corner.
 * <ul>
 * <li>Small: 4 courses of 3 rows per chunk. A phase 0 face has head joints where the face column is a multiple of 6.
 * A phase 1 face has them 3 columns further. The phase alternates per course and per face.
 * The pillar random picks the face pair that starts on phase 0.</li>
 * <li>Jumbled: 3 courses per chunk of 4, 4 and 5 rows in a random sequence. Each face has two head joints at even columns
 * from 2 to the width minus 2, at least 4 apart. No head joint shares a ring position with one in the course above or below.
 * A face falls back to one head joint when no free pair exists.</li>
 * <li>Large: 2 courses of 7 rows per chunk. Course 0 has its head joints at face column 2, course 1 at face column 10, on every face.</li>
 * </ul>
 * The last course of a chunk draws its jumbled joints first from a random that needs no other chunk.
 * The first course then avoids the last course of the chunk before.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class BricksPattern extends BandPattern {

    private static final int SMALL_PERIOD = 6;
    private static final int LARGE_PERIOD = 12;
    private static final int LARGE_FIRST = 2;
    private static final int LARGE_SECOND = 10;
    private static final int JUMBLED_EDGE = 2;
    private static final int JUMBLED_GAP = 4;

    private final BricksSettings settings;
    private final BricksSettings.Size size;
    private final int flip;

    /**
     * One course of a chunk.
     *
     * @param top    the first row of the course along the axis
     * @param height the rows of the course; the bed joint follows it
     * @param joints the ring positions of the head joints
     */
    private record Course(int top, int height, IntList joints) {
    }

    /** A pattern with a cache of its own. */
    public BricksPattern(PatternContext context, BricksSettings settings) {
        this(context, settings, new PatternCache());
    }

    public BricksPattern(PatternContext context, BricksSettings settings, PatternCache cache) {
        super(context, settings.paths(), cache);
        this.settings = settings;
        this.size = settings.size();
        this.flip = this.pillarBit(SALT_FLIP) ? 1 : 0;
    }

    public BricksSettings.Size size() {
        return this.size;
    }

    @Override
    protected Object options() {
        return List.of(BricksPattern.class, this.settings);
    }

    /** The rows of each course of the chunk. */
    private int[] heights(int ring, int chunk) {
        return switch (this.size) {
            case SMALL -> new int[] {3, 3, 3, 3};
            case LARGE -> new int[] {7, 7};
            case JUMBLED -> {
                int[] heights = {4, 4, 4};
                heights[this.random(SALT_HEIGHTS, chunk, ring).nextInt(3)] = 5;
                yield heights;
            }
        };
    }

    /** The courses of the chunk with their head joints. */
    private List<Course> courses(int ring, int chunk) {
        int[] heights = this.heights(ring, chunk);
        List<IntList> joints = switch (this.size) {
            case SMALL -> {
                List<IntList> out = new ArrayList<>();
                for (int i = 0; i < heights.length; i++) {
                    out.add(this.smallJoints(ring, heights.length * chunk + i));
                }
                yield out;
            }
            case LARGE -> {
                List<IntList> out = new ArrayList<>();
                for (int i = 0; i < heights.length; i++) {
                    int residue = Math.floorMod(heights.length * chunk + i, 2) == 0 ? LARGE_FIRST : LARGE_SECOND;
                    out.add(this.marked(this.faces(ring).innerMarks(LARGE_PERIOD, residue)));
                }
                yield out;
            }
            case JUMBLED -> this.jumbledJoints(ring, chunk, heights.length);
        };
        List<Course> out = new ArrayList<>();
        int top = BandPattern.CHUNK * chunk;
        for (int i = 0; i < heights.length; i++) {
            out.add(new Course(top, heights[i], joints.get(i)));
            top += heights[i] + 1;
        }
        return out;
    }

    private IntList marked(boolean[] marks) {
        IntList out = new IntArrayList();
        for (int index = 0; index < marks.length; index++) {
            if (marks[index]) {
                out.add(index);
            }
        }
        return out;
    }

    /** The head joints of small course {@code k}. */
    private IntList smallJoints(int ring, int k) {
        RingFaces faces = this.faces(ring);
        IntList out = new IntArrayList();
        for (int index = 0; index < faces.length(); index++) {
            int phase = Math.floorMod(k + faces.face(index) + this.flip, 2);
            if (faces.inner(index, SMALL_PERIOD, phase == 0 ? 0 : SMALL_PERIOD / 2)) {
                out.add(index);
            }
        }
        return out;
    }

    /** The head joints of the last jumbled course of the chunk. Its random needs no other chunk. */
    private IntList jumbledLast(int ring, int chunk) {
        return this.drawJumbled(ring, this.random(SALT_JOINTS_LAST, chunk, ring), new IntOpenHashSet());
    }

    private List<IntList> jumbledJoints(int ring, int chunk, int courses) {
        List<IntList> out = new ArrayList<>();
        IntList last = this.jumbledLast(ring, chunk);
        IntList previous = this.jumbledLast(ring, chunk - 1);
        RandomSource random = this.random(SALT_JOINTS, chunk, ring);
        for (int i = 0; i < courses - 1; i++) {
            IntSet avoid = new IntOpenHashSet(previous);
            if (i == courses - 2) {
                avoid.addAll(last);
            }
            IntList joints = this.drawJumbled(ring, random, avoid);
            out.add(joints);
            previous = joints;
        }
        out.add(last);
        return out;
    }

    /** Two head joints per face at even columns at least 4 apart, none in {@code avoid}, or one when no pair is free. */
    private IntList drawJumbled(int ring, RandomSource random, IntSet avoid) {
        RingFaces faces = this.faces(ring);
        IntList out = new IntArrayList();
        for (int face = 0; face < faces.faces(); face++) {
            int start = faces.start(face);
            IntList free = new IntArrayList();
            for (int column = JUMBLED_EDGE; column <= faces.width(face) - JUMBLED_EDGE; column += 2) {
                int index = Math.floorMod(start + column, faces.length());
                if (!avoid.contains(index)) {
                    free.add(index);
                }
            }
            List<int[]> pairs = new ArrayList<>();
            for (int a = 0; a < free.size(); a++) {
                for (int b = a + 1; b < free.size(); b++) {
                    if (faces.column(free.getInt(b)) - faces.column(free.getInt(a)) >= JUMBLED_GAP) {
                        pairs.add(new int[] {free.getInt(a), free.getInt(b)});
                    }
                }
            }
            if (!pairs.isEmpty()) {
                int[] pair = pairs.get(random.nextInt(pairs.size()));
                out.add(pair[0]);
                out.add(pair[1]);
            } else if (!free.isEmpty()) {
                out.add(free.getInt(random.nextInt(free.size())));
            }
        }
        return out;
    }

    /** The node rows of a course, as offsets from its top. */
    private int[] nodeRows(int height) {
        return switch (this.size) {
            case SMALL -> new int[] {1};
            case LARGE -> new int[] {1, 3, 5};
            case JUMBLED -> height == 4 ? new int[] {1, 2} : new int[] {1, 3};
        };
    }

    /** True when the ring position is a groove node of a bed joint. */
    private boolean bedNode(RingFaces faces, int index) {
        return this.size == BricksSettings.Size.SMALL ? faces.column(index) % 3 == 0 : faces.column(index) % 2 == 0;
    }

    /** The bricks of a course: the runs of ring positions between its head joints. */
    private List<IntList> bricks(int ring, IntList joints) {
        boolean[] marks = new boolean[this.faces(ring).length()];
        for (int joint : joints) {
            marks[joint] = true;
        }
        return RingFaces.runs(marks);
    }

    /** The ring positions of the face nodes of one brick. */
    private IntList facePositions(RingFaces faces, IntList brick) {
        IntList out = new IntArrayList();
        if (this.size == BricksSettings.Size.SMALL) {
            for (int offset = 1; offset < brick.size() - 1; offset += 2) {
                out.add(brick.getInt(offset));
            }
        } else {
            for (int index : brick) {
                if (faces.column(index) % 2 == 0) {
                    out.add(index);
                }
            }
        }
        return out;
    }

    @Override
    protected void layout(Band band, int ring) {
        RingFaces faces = this.faces(ring);
        for (int chunk = Math.floorDiv(band.axis0(), CHUNK); chunk * CHUNK < band.axisEnd(); chunk++) {
            for (Course course : this.courses(ring, chunk)) {
                int bed = course.top() + course.height();
                if (band.insideAxis(bed)) {
                    for (int index = 0; index < band.ringLength(); index++) {
                        band.set(index, bed, BandKind.GROOVE);
                        if (this.bedNode(faces, index)) {
                            band.grooveNodes().add(band.pos(index, bed));
                        }
                    }
                }
                int[] rows = this.nodeRows(course.height());
                for (int joint : course.joints()) {
                    for (int along = course.top(); along < bed; along++) {
                        if (band.insideAxis(along)) {
                            band.set(joint, along, BandKind.GROOVE);
                        }
                    }
                    for (int row : rows) {
                        if (band.insideAxis(course.top() + row)) {
                            band.grooveNodes().add(band.pos(joint, course.top() + row));
                        }
                    }
                }
                for (IntList brick : this.bricks(ring, course.joints())) {
                    for (int index : this.facePositions(faces, brick)) {
                        for (int row : rows) {
                            if (band.insideAxis(course.top() + row)) {
                                band.faceNodes().add(band.pos(index, course.top() + row));
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    protected List<PatternRegion> regions(int ring, int chunk) {
        List<Course> courses = this.courses(ring, chunk);
        List<PatternRegion> out = new ArrayList<>();
        for (int i = 0; i < courses.size(); i++) {
            Course course = courses.get(i);
            for (IntList brick : this.bricks(ring, course.joints())) {
                LongSet blocks = new LongOpenHashSet();
                for (int index : brick) {
                    for (int along = course.top(); along < course.top() + course.height(); along++) {
                        blocks.add(this.pos(ring, index, along));
                    }
                }
                out.add(new PatternRegion(blocks, i == courses.size() - 1));
            }
        }
        return out;
    }

}
