package us.drullk.aerolite.client.preview.imgui;

import com.mojang.logging.LogUtils;
import foundry.imgui.api.ImGuiMCEvents;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.extension.imnodes.ImNodes;
import imgui.extension.imnodes.ImNodesContext;
import imgui.extension.imnodes.flag.ImNodesMiniMapLocation;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiFocusedFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The node canvas window over the game view of the islands preview, left of the panel.
 * It draws a fixed set of placeholder nodes with imnodes. It evaluates nothing and saves nothing.
 * The canvas owns the imnodes context. imgui-mc creates no imnodes context.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
final class ImGuiNodeCanvas {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float MARGIN = 8;
    /** The share of the game view height that the window takes when it appears. */
    private static final float HEIGHT_SHARE = 0.45F;
    private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoDocking
            | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
    private static final float MINI_MAP_SIZE = 0.2F;

    private record Pin(int id, String label) {
    }

    private record Node(int id, String title, List<Pin> inputs, List<Pin> outputs, float x, float y) {
    }

    private record Link(int id, int start, int end) {
    }

    private final List<Node> nodes = new ArrayList<>();
    private final List<Link> links = new ArrayList<>();
    private final ImInt start = new ImInt();
    private final ImInt end = new ImInt();
    private final ImInt destroyed = new ImInt();
    private int nextId = 1;
    /** True until the first frame places the nodes on the grid. */
    private boolean place = true;
    @Nullable
    private ImNodesContext context;

    ImGuiNodeCanvas() {
        Node noise = this.node("noise", List.of(), List.of("out"), 0, 0);
        Node constant = this.node("constant", List.of(), List.of("out"), 0, 120);
        Node add = this.node("add", List.of("argument1", "argument2"), List.of("out"), 180, 40);
        Node mul = this.node("mul", List.of("argument1", "argument2"), List.of("out"), 360, 60);
        Node interpolated = this.node("interpolated", List.of("argument"), List.of("out"), 540, 60);
        Node finalDensity = this.node("final density", List.of("argument"), List.of(), 720, 60);
        this.link(noise.outputs().getFirst(), add.inputs().get(0));
        this.link(constant.outputs().getFirst(), add.inputs().get(1));
        this.link(add.outputs().getFirst(), mul.inputs().get(0));
        this.link(mul.outputs().getFirst(), interpolated.inputs().getFirst());
        this.link(interpolated.outputs().getFirst(), finalDensity.inputs().getFirst());
    }

    private Node node(String title, List<String> inputs, List<String> outputs, float x, float y) {
        int id = this.nextId++;
        List<Pin> in = inputs.stream().map(label -> new Pin(this.nextId++, label)).toList();
        List<Pin> out = outputs.stream().map(label -> new Pin(this.nextId++, label)).toList();
        Node node = new Node(id, title, in, out, x, y);
        this.nodes.add(node);
        return node;
    }

    private void link(Pin start, Pin end) {
        this.links.add(new Link(this.nextId++, start.id(), end.id()));
    }

    /** Creates the imnodes context after imgui-mc loads ImGui. */
    void register() {
        // The post load event fires while the new imgui-mc context is current.
        ImGuiMCEvents.INSTANCE.imGuiLoadPost(() -> this.createContext("ImGui post load event"));
    }

    private void createContext(String source) {
        if (this.context != null) {
            return;
        }
        this.context = ImNodes.createContext();
        ImNodes.setImGuiContext(ImGui.getCurrentContext());
        LOGGER.info("The imnodes context of the node canvas exists, created on the {}", source);
    }

    /** Draws the window once per ImGui frame, inside the font bracket of the panel. */
    void draw(float panelWidth) {
        if (this.context == null) {
            this.createContext("first draw");
        }
        ImNodes.setCurrentContext(this.context);
        ImNodes.setImGuiContext(ImGui.getCurrentContext());

        // With multiple viewports, ImGui positions are desktop coordinates. The main viewport is the game window.
        ImGuiViewport viewport = ImGui.getMainViewport();
        float gameWidth = viewport.getSizeX() - panelWidth;
        float height = viewport.getSizeY() * HEIGHT_SHARE;
        ImGui.setNextWindowViewport(viewport.getID());
        ImGui.setNextWindowPos(viewport.getPosX() + MARGIN, viewport.getPosY() + viewport.getSizeY() - height - MARGIN, ImGuiCond.Appearing);
        ImGui.setNextWindowSize(gameWidth - 2 * MARGIN, height, ImGuiCond.Appearing);
        boolean open = ImGui.begin("Node canvas##aerolite_node_canvas", WINDOW_FLAGS);
        try {
            if (open) {
                this.drawEditor();
                this.applyEdits();
            }
        } finally {
            ImGui.end();
        }
    }

    private void drawEditor() {
        ImNodes.beginNodeEditor();
        try {
            if (this.place) {
                this.place = false;
                for (Node node : this.nodes) {
                    ImNodes.setNodeGridSpacePos(node.id(), node.x(), node.y());
                }
            }
            for (Node node : this.nodes) {
                this.drawNode(node);
            }
            for (Link link : this.links) {
                ImNodes.link(link.id(), link.start(), link.end());
            }
            ImNodes.miniMap(MINI_MAP_SIZE, ImNodesMiniMapLocation.BottomRight);
        } finally {
            ImNodes.endNodeEditor();
        }
    }

    private void drawNode(Node node) {
        ImNodes.beginNode(node.id());
        try {
            ImNodes.beginNodeTitleBar();
            try {
                ImGui.textUnformatted(node.title());
            } finally {
                ImNodes.endNodeTitleBar();
            }
            for (Pin pin : node.inputs()) {
                ImNodes.beginInputAttribute(pin.id());
                try {
                    ImGui.textUnformatted(pin.label());
                } finally {
                    ImNodes.endInputAttribute();
                }
            }
            for (Pin pin : node.outputs()) {
                ImNodes.beginOutputAttribute(pin.id());
                try {
                    ImGui.textUnformatted(pin.label());
                } finally {
                    ImNodes.endOutputAttribute();
                }
            }
        } finally {
            ImNodes.endNode();
        }
    }

    /** imnodes reports new and removed links after the editor ends. An input holds one link. */
    private void applyEdits() {
        if (ImNodes.isLinkCreated(this.start, this.end)) {
            int input = this.end.get();
            this.links.removeIf(link -> link.end() == input);
            this.links.add(new Link(this.nextId++, this.start.get(), input));
        }
        if (ImNodes.isLinkDestroyed(this.destroyed)) {
            int id = this.destroyed.get();
            this.links.removeIf(link -> link.id() == id);
        }
        int selected = ImNodes.numSelectedLinks();
        if (selected > 0 && ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows) && ImGui.isKeyPressed(ImGuiKey.Delete, false)) {
            int[] ids = new int[selected];
            ImNodes.getSelectedLinks(ids);
            ImNodes.clearLinkSelection();
            for (int id : ids) {
                this.links.removeIf(link -> link.id() == id);
            }
        }
    }

}
