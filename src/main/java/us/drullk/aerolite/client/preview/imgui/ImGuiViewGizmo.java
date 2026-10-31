package us.drullk.aerolite.client.preview.imgui;

import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.extension.imguizmo.ImGuizmo;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import us.drullk.aerolite.client.preview.OrbitCamera;

import java.util.Arrays;

/**
 * The view gizmo in the top right corner of the game view, left of the panel. A drag on it turns the orbit camera.
 * A click on a face turns the camera to look at that face.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_1143d92a-d4be-41d5-9df1-3b5c5d54fb8d.md}.
 */
final class ImGuiViewGizmo {

    /** The edge length of the gizmo in ImGui units. */
    static final float SIZE = 112;
    static final float MARGIN = 8;
    private static final int BACKGROUND = 0x30000000;
    private static final float MIN_HORIZONTAL = 1.0E-4F;
    /** ImGuizmo finds the mouse over the gizmo only when no ImGui window is hovered. */
    private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoSavedSettings
            | ImGuiWindowFlags.NoBackground | ImGuiWindowFlags.NoFocusOnAppearing | ImGuiWindowFlags.NoNav
            | ImGuiWindowFlags.NoDocking | ImGuiWindowFlags.NoScrollWithMouse | ImGuiWindowFlags.NoInputs;

    private final float[] view = new float[16];
    private final float[] drawn = new float[16];
    private final Matrix4f matrix = new Matrix4f();

    /** Draws the gizmo once per ImGui frame. It writes the yaw and the pitch back when the gizmo turns the view. */
    void draw(OrbitCamera camera, float panelWidth) {
        ImGuizmo.beginFrame();
        // With multiple viewports, ImGui positions are desktop coordinates. The main viewport is the game window.
        ImGuiViewport viewport = ImGui.getMainViewport();
        float left = viewport.getPosX() + viewport.getSizeX() - panelWidth - SIZE - MARGIN;
        float top = viewport.getPosY() + MARGIN;
        ImGui.setNextWindowViewport(viewport.getID());
        ImGui.setNextWindowPos(left, top);
        ImGui.setNextWindowSize(SIZE, SIZE);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0);
        boolean open = ImGui.begin("##aerolite_view_gizmo", WINDOW_FLAGS);
        ImGui.popStyleVar(2);
        try {
            if (open) {
                ImGuizmo.setDrawList();
                camera.view().get(this.view);
                System.arraycopy(this.view, 0, this.drawn, 0, this.view.length);
                ImGuizmo.viewManipulate(this.view, camera.distance(), left, top, SIZE, SIZE, BACKGROUND);
                if (!Arrays.equals(this.view, this.drawn)) {
                    this.apply(camera);
                }
            }
        } finally {
            ImGui.end();
        }
    }

    /** The third row of a view matrix points from the orbit center to the camera. */
    private void apply(OrbitCamera camera) {
        this.matrix.set(this.view);
        float x = this.matrix.m02();
        float y = this.matrix.m12();
        float z = this.matrix.m22();
        float length = Mth.sqrt(x * x + y * y + z * z);
        if (length <= 0) {
            return;
        }
        camera.setPitch((float) Math.toDegrees(Math.asin(Mth.clamp(y / length, -1, 1))));
        if (Mth.sqrt(x * x + z * z) > MIN_HORIZONTAL * length) {
            camera.setYaw((float) Math.toDegrees(Mth.atan2(x, z)));
        }
    }

}
