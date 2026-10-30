package us.drullk.aerolite.client.preview;

import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A camera that orbits the region center. A drag turns it and the scroll wheel moves it closer or farther.
 * The view gizmo of the panel reads the view matrix and sets the yaw and the pitch.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 */
public final class OrbitCamera {

    static final float FOV_DEGREES = 70;
    public static final float MAX_PITCH = 89;
    private static final float DEGREES_PER_UNIT = 0.5F;
    private static final float ZOOM_STEP = 0.9F;
    private static final float MIN_DISTANCE = 8;

    private float yaw = 45;
    private float pitch = 30;
    private float distance = 256;
    private float maxDistance = 1024;
    /** The radius of the sphere that holds the region. */
    private float extent = 128;

    /** Moves the camera so that the whole region fits the view. The angles stay. */
    void frame(float halfWidth, float halfHeight) {
        this.extent = (float) Math.sqrt(2 * halfWidth * halfWidth + halfHeight * halfHeight);
        this.distance = this.extent / Mth.sin(FOV_DEGREES / 2 * Mth.DEG_TO_RAD);
        this.maxDistance = 4 * this.distance;
    }

    void turn(double deltaX, double deltaY) {
        this.setYaw(this.yaw - (float) deltaX * DEGREES_PER_UNIT);
        this.setPitch(this.pitch + (float) deltaY * DEGREES_PER_UNIT);
    }

    void zoom(double scroll) {
        this.distance = Mth.clamp(this.distance * (float) Math.pow(ZOOM_STEP, scroll), MIN_DISTANCE, this.maxDistance);
    }

    /** The angle around the Y axis in degrees. At 0 the camera sits on the positive Z side of the center. */
    public float yaw() {
        return this.yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = Mth.wrapDegrees(yaw);
    }

    /** The angle above the horizon in degrees, from -{@link #MAX_PITCH} to {@link #MAX_PITCH}. */
    public float pitch() {
        return this.pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = Mth.clamp(pitch, -MAX_PITCH, MAX_PITCH);
    }

    /** The distance from the camera to the region center. */
    public float distance() {
        return this.distance;
    }

    /** The camera position relative to the region center. */
    Vector3f position() {
        float pitchRadians = this.pitch * Mth.DEG_TO_RAD;
        float yawRadians = this.yaw * Mth.DEG_TO_RAD;
        return new Vector3f(Mth.cos(pitchRadians) * Mth.sin(yawRadians), Mth.sin(pitchRadians), Mth.cos(pitchRadians) * Mth.cos(yawRadians))
                .mul(this.distance);
    }

    /** The view matrix. Its model space has the region center at the origin. */
    public Matrix4f view() {
        return new Matrix4f().setLookAt(this.position(), new Vector3f(), new Vector3f(0, 1, 0));
    }

    Matrix4f projection(float aspect) {
        float near = Math.max(0.5F, (this.distance - this.extent) * 0.5F);
        float far = this.distance + 2 * this.extent + 16;
        return new Matrix4f().setPerspective(FOV_DEGREES * Mth.DEG_TO_RAD, aspect, near, far);
    }

}
