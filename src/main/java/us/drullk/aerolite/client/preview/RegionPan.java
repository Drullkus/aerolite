package us.drullk.aerolite.client.preview;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import javax.annotation.Nullable;

/**
 * The region center of the preview and the input that moves it.
 * The movement keys move the center along the camera yaw on the ground plane. A middle drag grabs the ground.
 * The center is a continuous world XZ position in blocks.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 */
public final class RegionPan {

    /** The key pan speed in blocks per second. */
    public static final double WALK_SPEED = 64;
    /** The key pan speed in blocks per second while Ctrl is held or a double press sprints. */
    public static final double SPRINT_SPEED = 256;
    /** The longest time in milliseconds between two presses of one key that starts a sprint. */
    public static final long DOUBLE_PRESS_MILLIS = 300;
    /** The longest frame time in seconds that one key move covers. */
    public static final double MAX_FRAME_SECONDS = 0.25;

    /** A movement key. The directions are relative to the camera yaw. */
    public enum Key {
        FORWARD,
        BACK,
        LEFT,
        RIGHT
    }

    private static final int KEYS = Key.values().length;

    private double x;
    private double z;
    private final boolean[] held = new boolean[KEYS];
    private final boolean[] sprint = new boolean[KEYS];
    private final long[] lastPress = new long[KEYS];
    private final boolean[] pressedBefore = new boolean[KEYS];
    private boolean dragging;
    private float dragStartX;
    private float dragStartY;
    private double dragCenterX;
    private double dragCenterZ;

    public RegionPan() {
    }

    /** The center X in blocks. */
    public double x() {
        return this.x;
    }

    /** The center Z in blocks. */
    public double z() {
        return this.z;
    }

    public void set(double x, double z) {
        this.x = x;
        this.z = z;
        this.dragging = false;
    }

    /** The coordinate of the chunk that holds a block coordinate. */
    public static int chunk(double block) {
        return Math.floorDiv((int) Math.floor(block), 16);
    }

    public int chunkX() {
        return chunk(this.x);
    }

    public int chunkZ() {
        return chunk(this.z);
    }

    /** Holds a key. A key repeat of a held key changes nothing. A second press within the double press time sprints. */
    public void press(Key key, long millis) {
        int index = key.ordinal();
        if (this.held[index]) {
            return;
        }
        this.held[index] = true;
        this.sprint[index] = this.pressedBefore[index] && millis - this.lastPress[index] <= DOUBLE_PRESS_MILLIS;
        this.lastPress[index] = millis;
        this.pressedBefore[index] = true;
    }

    /** Lets go of a key. Its sprint ends. */
    public void release(Key key) {
        this.held[key.ordinal()] = false;
        this.sprint[key.ordinal()] = false;
    }

    /** Lets go of every key and ends the drag. */
    public void releaseAll() {
        for (Key key : Key.values()) {
            this.release(key);
        }
        this.dragging = false;
    }

    public boolean held(Key key) {
        return this.held[key.ordinal()];
    }

    /** True when a held key sprints or {@code ctrl} is held. */
    public boolean sprinting(boolean ctrl) {
        for (int index = 0; index < KEYS; index++) {
            if (this.held[index] && (ctrl || this.sprint[index])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Moves the center by the held keys for one frame. A diagonal moves at the same speed as a straight move.
     *
     * @param seconds the real frame time
     * @param yaw     the camera yaw in degrees; at 0 the camera looks toward negative Z
     */
    public void move(double seconds, float yaw, boolean ctrl) {
        int forward = (this.held(Key.FORWARD) ? 1 : 0) - (this.held(Key.BACK) ? 1 : 0);
        int right = (this.held(Key.RIGHT) ? 1 : 0) - (this.held(Key.LEFT) ? 1 : 0);
        if (forward == 0 && right == 0) {
            return;
        }
        double radians = Math.toRadians(yaw);
        double forwardX = -Math.sin(radians);
        double forwardZ = -Math.cos(radians);
        double moveX = forward * forwardX - right * forwardZ;
        double moveZ = forward * forwardZ + right * forwardX;
        double length = Math.sqrt(moveX * moveX + moveZ * moveZ);
        double distance = (this.sprinting(ctrl) ? SPRINT_SPEED : WALK_SPEED) * Math.clamp(seconds, 0, MAX_FRAME_SECONDS);
        this.x += moveX / length * distance;
        this.z += moveZ / length * distance;
    }

    /** Starts a ground drag at a cursor position in normalized device coordinates. */
    public void beginDrag(float ndcX, float ndcY) {
        this.dragging = true;
        this.dragStartX = ndcX;
        this.dragStartY = ndcY;
        this.dragCenterX = this.x;
        this.dragCenterZ = this.z;
    }

    public boolean dragging() {
        return this.dragging;
    }

    public void endDrag() {
        this.dragging = false;
    }

    /**
     * Moves the center so that the ground point under the drag start follows the cursor.
     * A ray that misses the ground plane or hits it behind the camera moves nothing.
     *
     * @param viewProjection the projection times the view, with the region center at the model space origin
     * @return true when the center moved
     */
    public boolean dragTo(Matrix4f viewProjection, float ndcX, float ndcY) {
        if (!this.dragging) {
            return false;
        }
        Matrix4f inverse = new Matrix4f(viewProjection).invert();
        Vector3f start = groundHit(inverse, this.dragStartX, this.dragStartY);
        Vector3f now = groundHit(inverse, ndcX, ndcY);
        if (start == null || now == null) {
            return false;
        }
        this.x = this.dragCenterX + start.x - now.x;
        this.z = this.dragCenterZ + start.z - now.z;
        return true;
    }

    /**
     * The point where the view ray through a cursor position meets the model space plane at Y 0.
     * Null when the ray runs parallel to the plane or meets it behind the near plane.
     *
     * @param inverseViewProjection the inverse of the projection times the view
     */
    @Nullable
    public static Vector3f groundHit(Matrix4f inverseViewProjection, float ndcX, float ndcY) {
        Vector3f near = unproject(inverseViewProjection, ndcX, ndcY, -1);
        Vector3f far = unproject(inverseViewProjection, ndcX, ndcY, 1);
        if (near == null || far == null) {
            return null;
        }
        Vector3f direction = new Vector3f(far).sub(near);
        if (Math.abs(direction.y) < 1.0E-6F) {
            return null;
        }
        float t = -near.y / direction.y;
        if (t < 0) {
            return null;
        }
        return direction.mul(t).add(near);
    }

    @Nullable
    private static Vector3f unproject(Matrix4f inverseViewProjection, float ndcX, float ndcY, float ndcZ) {
        Vector4f point = inverseViewProjection.transform(new Vector4f(ndcX, ndcY, ndcZ, 1));
        if (Math.abs(point.w) < 1.0E-12F) {
            return null;
        }
        return new Vector3f(point.x / point.w, point.y / point.w, point.z / point.w);
    }

}
