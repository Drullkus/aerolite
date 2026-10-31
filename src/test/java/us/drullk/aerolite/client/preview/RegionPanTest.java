package us.drullk.aerolite.client.preview;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.ChunkPos;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the chunk diff of a region shift, the sprint timing of the movement keys and the ground drag math.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 */
class RegionPanTest {

    private static final int RADIUS = 3;
    private static final double EPSILON = 1.0E-6;

    private static LongSet region(ChunkPos center) {
        LongSet chunks = new LongOpenHashSet();
        for (ChunkPos pos : RegionChunks.missing(center, RADIUS, new LongOpenHashSet())) {
            chunks.add(pos.toLong());
        }
        return chunks;
    }

    private static Set<ChunkPos> dropped(LongSet present, ChunkPos center) {
        Set<ChunkPos> dropped = new HashSet<>();
        for (long key : present) {
            if (!RegionChunks.contains(center, RADIUS, ChunkPos.getX(key), ChunkPos.getZ(key))) {
                dropped.add(new ChunkPos(key));
            }
        }
        return dropped;
    }

    @Test
    void emptyRegionListsEveryChunkFromTheCenter() {
        ChunkPos center = new ChunkPos(-5, 7);
        List<ChunkPos> missing = RegionChunks.missing(center, RADIUS, new LongOpenHashSet());
        assertEquals((2 * RADIUS + 1) * (2 * RADIUS + 1), missing.size());
        assertEquals(center, missing.getFirst());
        assertEquals(missing.size(), new HashSet<>(missing).size());
    }

    @Test
    void moveOfZeroChangesNothing() {
        ChunkPos center = new ChunkPos(2, -3);
        LongSet present = region(center);
        assertTrue(RegionChunks.missing(center, RADIUS, present).isEmpty());
        assertTrue(dropped(present, center).isEmpty());
    }

    @Test
    void moveOfOneChunkAddsOneColumnAndDropsOne() {
        ChunkPos from = new ChunkPos(2, -3);
        ChunkPos to = new ChunkPos(3, -3);
        LongSet present = region(from);
        List<ChunkPos> added = RegionChunks.missing(to, RADIUS, present);
        Set<ChunkPos> expectedAdded = new HashSet<>();
        Set<ChunkPos> expectedDropped = new HashSet<>();
        for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            expectedAdded.add(new ChunkPos(to.x + RADIUS, to.z + dz));
            expectedDropped.add(new ChunkPos(from.x - RADIUS, from.z + dz));
        }
        assertEquals(expectedAdded, new HashSet<>(added));
        assertEquals(expectedDropped, dropped(present, to));
        // The added chunks come nearest first, so the middle of the new column leads.
        assertEquals(new ChunkPos(to.x + RADIUS, to.z), added.getFirst());
    }

    @Test
    void moveOfOneChunkAlongZAddsOneRow() {
        ChunkPos from = new ChunkPos(0, 0);
        ChunkPos to = new ChunkPos(0, -1);
        List<ChunkPos> added = RegionChunks.missing(to, RADIUS, region(from));
        assertEquals(2 * RADIUS + 1, added.size());
        for (ChunkPos pos : added) {
            assertEquals(to.z - RADIUS, pos.z);
        }
    }

    @Test
    void chunkOfNegativeBlockRoundsDown() {
        assertEquals(-1, RegionPan.chunk(-0.5));
        assertEquals(-1, RegionPan.chunk(-16));
        assertEquals(-2, RegionPan.chunk(-16.5));
        assertEquals(0, RegionPan.chunk(15.9));
    }

    @Test
    void secondPressWithinTheWindowSprints() {
        RegionPan pan = new RegionPan();
        pan.press(RegionPan.Key.FORWARD, 1000);
        assertFalse(pan.sprinting(false));
        pan.release(RegionPan.Key.FORWARD);
        pan.press(RegionPan.Key.FORWARD, 1000 + RegionPan.DOUBLE_PRESS_MILLIS);
        assertTrue(pan.sprinting(false));
        pan.release(RegionPan.Key.FORWARD);
        assertFalse(pan.sprinting(false));
    }

    @Test
    void secondPressAfterTheWindowWalks() {
        RegionPan pan = new RegionPan();
        pan.press(RegionPan.Key.LEFT, 1000);
        pan.release(RegionPan.Key.LEFT);
        pan.press(RegionPan.Key.LEFT, 1001 + RegionPan.DOUBLE_PRESS_MILLIS);
        assertFalse(pan.sprinting(false));
        assertTrue(pan.sprinting(true));
    }

    @Test
    void keyRepeatDoesNotSprint() {
        RegionPan pan = new RegionPan();
        pan.press(RegionPan.Key.RIGHT, 1000);
        pan.press(RegionPan.Key.RIGHT, 1050);
        assertFalse(pan.sprinting(false));
    }

    @Test
    void pressOfAnotherKeyDoesNotSprint() {
        RegionPan pan = new RegionPan();
        pan.press(RegionPan.Key.FORWARD, 1000);
        pan.release(RegionPan.Key.FORWARD);
        pan.press(RegionPan.Key.BACK, 1100);
        assertFalse(pan.sprinting(false));
    }

    /** Moves for one second in frames of a tenth of a second. */
    private static void moveOneSecond(RegionPan pan, float yaw, boolean ctrl) {
        for (int frame = 0; frame < 10; frame++) {
            pan.move(0.1, yaw, ctrl);
        }
    }

    @Test
    void forwardFollowsTheCameraYaw() {
        RegionPan pan = new RegionPan();
        pan.press(RegionPan.Key.FORWARD, 0);
        moveOneSecond(pan, 0, false);
        assertEquals(0, pan.x(), EPSILON);
        assertEquals(-RegionPan.WALK_SPEED, pan.z(), EPSILON);

        pan.set(0, 0);
        moveOneSecond(pan, 90, false);
        assertEquals(-RegionPan.WALK_SPEED, pan.x(), EPSILON);
        assertEquals(0, pan.z(), EPSILON);
    }

    @Test
    void rightIsEastAtYawZero() {
        RegionPan pan = new RegionPan();
        pan.press(RegionPan.Key.RIGHT, 0);
        moveOneSecond(pan, 0, true);
        assertEquals(RegionPan.SPRINT_SPEED, pan.x(), EPSILON);
        assertEquals(0, pan.z(), EPSILON);
    }

    @Test
    void diagonalMovesAtTheStraightSpeed() {
        RegionPan pan = new RegionPan();
        pan.press(RegionPan.Key.FORWARD, 0);
        pan.press(RegionPan.Key.LEFT, 0);
        moveOneSecond(pan, 30, false);
        assertEquals(RegionPan.WALK_SPEED, Math.hypot(pan.x(), pan.z()), EPSILON);
    }

    @Test
    void longFrameMovesAtMostTheFrameLimit() {
        RegionPan pan = new RegionPan();
        pan.press(RegionPan.Key.BACK, 0);
        pan.move(10, 0, false);
        assertEquals(RegionPan.WALK_SPEED * RegionPan.MAX_FRAME_SECONDS, pan.z(), EPSILON);
    }

    private static Matrix4f viewProjection(OrbitCamera camera) {
        return camera.projection(16 / 9F).mul(camera.view());
    }

    @Test
    void viewCenterHitsTheGroundAtTheOrigin() {
        OrbitCamera camera = new OrbitCamera();
        Vector3f hit = RegionPan.groundHit(viewProjection(camera).invert(), 0, 0);
        assertNotNull(hit);
        assertEquals(0, hit.x, 1.0E-2);
        assertEquals(0, hit.y, 1.0E-2);
        assertEquals(0, hit.z, 1.0E-2);
    }

    @Test
    void rayAboveTheHorizonMisses() {
        OrbitCamera camera = new OrbitCamera();
        camera.setPitch(10);
        assertNull(RegionPan.groundHit(viewProjection(camera).invert(), 0, 1));
    }

    @Test
    void dragKeepsTheGrabbedPointUnderTheCursor() {
        OrbitCamera camera = new OrbitCamera();
        camera.setPitch(60);
        Matrix4f viewProjection = viewProjection(camera);
        RegionPan pan = new RegionPan();
        pan.set(100, -40);
        Vector3f grabbed = RegionPan.groundHit(new Matrix4f(viewProjection).invert(), 0.2F, -0.1F);
        assertNotNull(grabbed);
        double worldX = pan.x() + grabbed.x;
        double worldZ = pan.z() + grabbed.z;
        pan.beginDrag(0.2F, -0.1F);
        assertTrue(pan.dragTo(viewProjection, -0.3F, 0.2F));
        Vector3f now = RegionPan.groundHit(new Matrix4f(viewProjection).invert(), -0.3F, 0.2F);
        assertNotNull(now);
        assertEquals(worldX, pan.x() + now.x, 1.0E-2);
        assertEquals(worldZ, pan.z() + now.z, 1.0E-2);
    }

    @Test
    void dragToTheSkyMovesNothing() {
        OrbitCamera camera = new OrbitCamera();
        camera.setPitch(10);
        RegionPan pan = new RegionPan();
        pan.beginDrag(0, 0);
        assertFalse(pan.dragTo(viewProjection(camera), 0, 1));
        assertEquals(0, pan.x());
        assertEquals(0, pan.z());
    }

}
