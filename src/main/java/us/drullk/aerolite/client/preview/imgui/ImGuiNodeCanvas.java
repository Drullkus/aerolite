package us.drullk.aerolite.client.preview.imgui;

import com.mojang.logging.LogUtils;
import foundry.imgui.api.ImGuiMCEvents;
import imgui.ImFont;
import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.ImGuiViewport;
import imgui.ImVec4;
import imgui.extension.imnodes.ImNodes;
import imgui.extension.imnodes.ImNodesContext;
import imgui.extension.imnodes.ImNodesEditorContext;
import imgui.extension.imnodes.ImNodesStyle;
import imgui.extension.imnodes.flag.ImNodesCol;
import imgui.extension.imnodes.flag.ImNodesMiniMapLocation;
import imgui.extension.imnodes.flag.ImNodesStyleVar;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiFocusedFlags;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImDouble;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import us.drullk.aerolite.client.AeroliteClientConfig;
import us.drullk.aerolite.client.preview.CanvasZoom;
import us.drullk.aerolite.client.preview.FieldsWidth;
import us.drullk.aerolite.client.preview.IslandEditor;
import us.drullk.aerolite.client.preview.PreviewScreen;
import us.drullk.aerolite.world.island.graph.FieldKind;
import us.drullk.aerolite.world.island.graph.FieldSpec;
import us.drullk.aerolite.world.island.graph.FieldValue;
import us.drullk.aerolite.world.island.graph.FunctionGraph;
import us.drullk.aerolite.world.island.graph.FunctionNode;
import us.drullk.aerolite.world.island.graph.FunctionNodeDefaults;
import us.drullk.aerolite.world.island.graph.FunctionNodeGraph;
import us.drullk.aerolite.world.island.graph.FunctionNodeType;
import us.drullk.aerolite.world.island.graph.FunctionNodeTypes;
import us.drullk.aerolite.world.island.graph.GraphLayout;
import us.drullk.aerolite.world.island.graph.IslandGraphs;
import us.drullk.aerolite.world.island.graph.RouterGraph;
import us.drullk.aerolite.world.island.graph.Sidecar;
import us.drullk.aerolite.world.island.graph.SplineValue;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The node canvas window over the islands preview. It edits the graphs of the island editor with imnodes.
 * The window shows only while the editor is open. The title bar cross closes it.
 * Each open puts the window over the whole game window, panel and footer included, inside a margin. The window then moves and resizes freely.
 * The router graph view holds one router box with one input pin per router field. The function graph view holds one output box.
 * An input pin of a box without a link shows its constant or the switch Dimension default.
 * Each function node shows its type path, its input pins and its output pin. A reference node also shows its entry id and the button Open.
 * The field editor right of the canvas edits the selected function node. The problems of that function node follow in red.
 * The problems that name no function node follow, then the notice that the terrain keeps the last good build.
 * Each function node with a problem has a red title bar.
 * A splitter between the canvas and the field editor sets the field editor width. The release writes it to the client config.
 * The scroll wheel zooms the canvas around the cursor. imnodes has no zoom, so the canvas scales the positions, the imnodes style, the item spacing and the font.
 * The stored positions stay unscaled, so the sidecar holds no zoom.
 * A right click on empty canvas opens the palette. Delete removes the selected links and function nodes.
 * The canvas owns the imnodes context. imgui-mc creates no imnodes context.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_18-19_754b3009-7e46-47f9-99e4-01f82356bea1.md}.
 */
final class ImGuiNodeCanvas {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** The gap between the opened window and each edge of the game window, in ImGui units. */
    private static final float MARGIN = 32;
    /** The width of the splitter between the canvas and the field editor. */
    private static final float SPLITTER_WIDTH = 6;
    private static final float SPLITTER_LINE = 2;
    private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoDocking
            | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
    /** The wheel over the canvas zooms, so the canvas region never scrolls. */
    private static final int CANVAS_FLAGS = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
    private static final float MINI_MAP_SIZE = 0.2F;
    /** The attribute ids of one function node. Slot 0 is the output pin, slot 1 and up the input pins. */
    private static final int SLOTS = 1024;
    /** The imnodes node id of the router box or the output box. */
    private static final int BOX_NODE = 0;
    private static final float CONSTANT_WIDTH = 110;
    /** The share of the field editor width that the label column takes at most. */
    private static final float LABEL_SHARE = 0.5F;
    private static final float PALETTE_WIDTH = 260;
    private static final int PALETTE_ROWS = 12;
    private static final int TEXT_CAPACITY = 256;
    /** The end mark of a clipped type id. */
    private static final String ELLIPSIS = "...";
    private static final String PALETTE = "##aerolite_palette";
    private static final float[] ERROR_COLOR = {1, 0.4F, 0.4F, 1};
    private static final String NOTICE = "The terrain keeps the last good build";
    private static final float INVALID_RED = 0.55F;

    /** One imnodes editor context per graph of the drawn screen. */
    private final Map<IslandGraphs.GraphId, ImNodesEditorContext> editorContexts = new HashMap<>();
    /** The function nodes whose canvas position imnodes holds, per graph. The box has the key {@link GraphLayout#BOX}. */
    private final Map<IslandGraphs.GraphId, Set<Integer>> placed = new HashMap<>();
    /** The zoom of each graph view. A graph view without an entry has zoom 1. */
    private final Map<IslandGraphs.GraphId, Float> zooms = new HashMap<>();
    /** The zoom at which imnodes took the positions of {@link #placed}, per graph. */
    private final Map<IslandGraphs.GraphId, Float> placedZooms = new HashMap<>();
    private final ImInt start = new ImInt();
    private final ImInt end = new ImInt();
    private final ImInt hovered = new ImInt();
    private final ImString filter = new ImString(TEXT_CAPACITY);
    private final ImString reference = new ImString(TEXT_CAPACITY);
    /** The work that changes the shown graph or the view. It runs after the node editor ends. */
    private final List<Runnable> deferred = new ArrayList<>();
    @Nullable
    private PreviewScreen drawnScreen;
    /** The canvas point of the open palette. */
    @Nullable
    private Sidecar.Position paletteAt;
    private boolean referenceOpen;
    @Nullable
    private ImNodesContext context;
    /** The zoom of the graph that draws now. */
    private float drawZoom = 1;
    /** The field editor width, from the client config, or from the cursor during a splitter drag. */
    private float fieldsWidth = AeroliteClientConfig.DEFAULT_FIELDS_WIDTH;
    private boolean fieldsDragging;
    /** True when the last drawn frame showed the window. A frame after a closed frame puts the window at the margin. */
    private boolean shown;
    private final ImBoolean windowOpen = new ImBoolean(true);
    /** The problems of the frame that draws now. */
    private IslandEditor.ShownProblems drawProblems = IslandEditor.ShownProblems.NONE;

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

    private static int nodeId(int functionNode) {
        return functionNode + 1;
    }

    private static int outputAttribute(int functionNode) {
        return nodeId(functionNode) * SLOTS;
    }

    private static int inputAttribute(int functionNode, int pinIndex) {
        return nodeId(functionNode) * SLOTS + 1 + pinIndex;
    }

    private static int boxAttribute(int outputIndex) {
        return 1 + outputIndex;
    }

    private static boolean isOutputAttribute(int attribute) {
        return attribute >= SLOTS && attribute % SLOTS == 0;
    }

    /**
     * Draws the window once per ImGui frame, inside the font bracket of the panel.
     *
     * @param font     the panel font
     * @param fontSize the size of the panel font at zoom 1
     */
    void draw(PreviewScreen screen, IslandEditor editor, ImFont font, float fontSize) {
        if (this.context == null) {
            this.createContext("first draw");
        }
        ImNodes.setCurrentContext(this.context);
        ImNodes.setImGuiContext(ImGui.getCurrentContext());
        if (screen != this.drawnScreen) {
            // A new screen holds new graph copies, so each function node takes its stored position again.
            this.drawnScreen = screen;
            this.placed.clear();
            this.zooms.clear();
            this.placedZooms.clear();
            this.paletteAt = null;
            this.fieldsDragging = false;
            this.shown = false;
            // The editor contexts of a closed screen live until the next screen draws.
            this.editorContexts.values().forEach(ImNodes::editorContextFree);
            this.editorContexts.clear();
        }

        if (!editor.isOpen()) {
            this.shown = false;
            this.paletteAt = null;
            this.fieldsDragging = false;
            return;
        }
        // With multiple viewports, ImGui positions are desktop coordinates. The main viewport is the full game window, panel included.
        ImGuiViewport viewport = ImGui.getMainViewport();
        float width = Math.max(1, viewport.getSizeX() - 2 * MARGIN);
        float height = Math.max(1, viewport.getSizeY() - 2 * MARGIN);
        ImGui.setNextWindowViewport(viewport.getID());
        if (!this.shown) {
            ImGui.setNextWindowPos(viewport.getPosX() + MARGIN, viewport.getPosY() + MARGIN, ImGuiCond.Always);
            ImGui.setNextWindowSize(width, height, ImGuiCond.Always);
            ImGui.setNextWindowFocus();
            this.shown = true;
        }
        // A narrower window shows the field editor below its least width.
        float leastWidth = FieldsWidth.leastContentWidth() + 2 * ImGui.getStyle().getWindowPaddingX();
        ImGui.setNextWindowSizeConstraints(Math.min(leastWidth, width), 0, Float.MAX_VALUE, Float.MAX_VALUE);
        this.windowOpen.set(true);
        this.drawProblems = editor.shownProblems();
        boolean expanded = ImGui.begin("Island editor##aerolite_node_canvas", this.windowOpen, WINDOW_FLAGS);
        try {
            if (expanded) {
                this.drawWindow(screen, editor, font, fontSize);
            }
        } finally {
            ImGui.end();
        }
        if (!this.windowOpen.get()) {
            this.deferred.add(editor::close);
        }
        List<Runnable> work = List.copyOf(this.deferred);
        this.deferred.clear();
        work.forEach(Runnable::run);
    }

    private void drawWindow(PreviewScreen screen, IslandEditor editor, ImFont font, float fontSize) {
        ResourceLocation entry = editor.openEntry();
        if (entry != null) {
            ImGui.alignTextToFramePadding();
            ImGui.textUnformatted(entry.toString());
            ImGui.sameLine();
            if (ImGui.button("Back##back")) {
                this.deferred.add(editor::back);
            }
        } else {
            ResourceKey<Level> dimension = editor.dimension();
            ImGui.alignTextToFramePadding();
            ImGui.textUnformatted("Router graph of " + (dimension == null ? "no dimension" : dimension.location().toString()));
        }
        if (editor.openError() != null) {
            errorText(editor.openError());
        }
        FunctionNodeGraph graph = editor.graph();
        IslandGraphs.GraphId graphId = editor.graphId();
        if (graph == null || graphId == null) {
            ImGui.textWrapped("The previewed dimension has no router graph.");
            return;
        }
        float available = ImGui.getContentRegionAvailX();
        float height = ImGui.getContentRegionAvailY();
        float right = ImGui.getCursorScreenPosX() + available;
        if (!this.fieldsDragging) {
            this.fieldsWidth = screen.tools().config().fieldsWidth();
        }
        float fieldsWidth = FieldsWidth.shown(this.fieldsWidth, available);
        boolean canvas = ImGui.beginChild("##canvas", Math.max(1, available - fieldsWidth - SPLITTER_WIDTH), 0, ImGuiChildFlags.None, CANVAS_FLAGS);
        try {
            if (canvas) {
                this.drawCanvas(editor, graph, graphId, font, fontSize);
            }
        } finally {
            ImGui.endChild();
        }
        ImGui.sameLine(0, 0);
        this.drawSplitter(screen, right, height, available);
        ImGui.sameLine(0, 0);
        boolean fields = ImGui.beginChild("##fields", 0, 0, ImGuiChildFlags.Borders);
        try {
            if (fields) {
                ImGuiViewport viewport = ImGui.getMainViewport();
                editor.setFieldsView(new IslandEditor.Area(ImGui.getWindowPosX() - viewport.getPosX(), ImGui.getWindowPosY() - viewport.getPosY(),
                        ImGui.getWindowWidth(), ImGui.getWindowHeight()), ImGui.getScrollY(), ImGui.getScrollMaxY());
                this.drawFields(editor, graph);
            }
        } finally {
            ImGui.endChild();
        }
    }

    /** An invisible button between the canvas and the field editor. A drag moves it. The release saves the width. */
    private void drawSplitter(PreviewScreen screen, float right, float height, float available) {
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        ImGui.invisibleButton("##fields_splitter", SPLITTER_WIDTH, Math.max(1, height));
        boolean hovered = ImGui.isItemHovered();
        boolean active = ImGui.isItemActive();
        if (hovered || active) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
        }
        if (active) {
            this.fieldsDragging = true;
            this.fieldsWidth = FieldsWidth.dragged(right, ImGui.getIO().getMousePosX(), SPLITTER_WIDTH, available);
        }
        ImVec4 color = ImGui.getStyle().getColor(hovered || active ? ImGuiCol.SeparatorActive : ImGuiCol.Separator);
        float lineLeft = left + (SPLITTER_WIDTH - SPLITTER_LINE) / 2;
        ImGui.getWindowDrawList().addRectFilled(lineLeft, top, lineLeft + SPLITTER_LINE, top + height,
                ImGui.colorConvertFloat4ToU32(color.x, color.y, color.z, color.w));
        if (ImGui.isItemDeactivated()) {
            this.fieldsDragging = false;
            screen.tools().config().setFieldsWidth(Math.round(this.fieldsWidth));
        }
    }

    private ImNodesEditorContext editorContext(IslandGraphs.GraphId graphId) {
        return this.editorContexts.computeIfAbsent(graphId, ignored -> ImNodes.editorContextCreate());
    }

    private void drawCanvas(IslandEditor editor, FunctionNodeGraph graph, IslandGraphs.GraphId graphId, ImFont font, float fontSize) {
        Map<Integer, Sidecar.Position> positions = editor.graphs().positions(graphId);
        if (!positions.containsKey(GraphLayout.BOX) || !positions.keySet().containsAll(graph.functionNodes().keySet())) {
            GraphLayout.layout(graph).forEach(positions::putIfAbsent);
        }
        ImNodes.editorContextSet(this.editorContext(graphId));
        float originX = ImGui.getCursorScreenPosX();
        float originY = ImGui.getCursorScreenPosY();
        ImGuiViewport viewport = ImGui.getMainViewport();
        editor.setCanvasArea(new IslandEditor.Area(originX - viewport.getPosX(), originY - viewport.getPosY(),
                ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY()));
        float zoom = this.zooms.getOrDefault(graphId, 1F);
        Float zoomRequest = editor.takeZoomRequest();
        if (zoomRequest != null) {
            // A requested zoom keeps the canvas point at the canvas origin in place.
            float requested = CanvasZoom.clamp(zoomRequest);
            ImNodes.editorContextResetPanning(CanvasZoom.panAround(ImNodes.editorContextGetPanningX(), 0, zoom, requested),
                    CanvasZoom.panAround(ImNodes.editorContextGetPanningY(), 0, zoom, requested));
            zoom = requested;
            this.zooms.put(graphId, zoom);
        }
        Sidecar.Position pan = editor.takePanRequest();
        if (pan != null) {
            ImNodes.editorContextResetPanning(pan.x(), pan.y());
        }
        Set<Integer> placedIds = this.placed.computeIfAbsent(graphId, ignored -> new HashSet<>());
        Float placedZoom = this.placedZooms.put(graphId, zoom);
        if (placedZoom != null && placedZoom != zoom) {
            // imnodes holds grid positions of the old zoom, so each function node takes its position again.
            placedIds.clear();
        }
        Set<Integer> placedBefore = Set.copyOf(placedIds);
        this.drawZoom = zoom;
        this.drawEditor(editor, graph, positions, placedIds, font, fontSize * zoom);
        // imnodes reads its hover state against the current window, which is the canvas child after the node editor ends.
        // The palette popup belongs to the canvas child in the popup hierarchy, so the check skips that hierarchy.
        boolean hovered = ImGui.isWindowHovered(ImGuiHoveredFlags.ChildWindows | ImGuiHoveredFlags.NoPopupHierarchy);
        this.readPositions(graph, positions, placedBefore, zoom);
        this.applySelection(editor, placedBefore);
        this.applyLinks(editor, graph);
        this.applyDelete(editor, graph, graphId, positions, placedIds);
        this.applyWheel(graphId, zoom, originX, originY, hovered);
        editor.setZoom(this.zooms.getOrDefault(graphId, 1F));
        this.drawPalette(editor, originX, originY, zoom, hovered);
    }

    /** The node editor with the imnodes style, the item spacing and the font scaled by the zoom. */
    private void drawEditor(IslandEditor editor, FunctionNodeGraph graph, Map<Integer, Sidecar.Position> positions, Set<Integer> placedIds,
                            ImFont font, float fontSize) {
        float zoom = this.drawZoom;
        ImNodesStyle nodes = ImNodes.getStyle();
        ImGuiStyle style = ImGui.getStyle();
        int nodePushes = 0;
        int stylePushes = 0;
        boolean fontPushed = false;
        try {
            ImNodes.pushStyleVar(ImNodesStyleVar.GridSpacing, nodes.getGridSpacing() * zoom);
            nodePushes++;
            ImNodes.pushStyleVar(ImNodesStyleVar.NodeCornerRounding, nodes.getNodeCornerRounding() * zoom);
            nodePushes++;
            ImNodes.pushStyleVar(ImNodesStyleVar.NodePadding, nodes.getNodePaddingX() * zoom, nodes.getNodePaddingY() * zoom);
            nodePushes++;
            ImNodes.pushStyleVar(ImNodesStyleVar.NodeBorderThickness, nodes.getNodeBorderThickness() * zoom);
            nodePushes++;
            ImNodes.pushStyleVar(ImNodesStyleVar.LinkThickness, nodes.getLinkThickness() * zoom);
            nodePushes++;
            ImNodes.pushStyleVar(ImNodesStyleVar.LinkHoverDistance, nodes.getLinkHoverDistance() * zoom);
            nodePushes++;
            ImNodes.pushStyleVar(ImNodesStyleVar.PinCircleRadius, nodes.getPinCircleRadius() * zoom);
            nodePushes++;
            ImNodes.pushStyleVar(ImNodesStyleVar.PinQuadSideLength, nodes.getPinQuadSideLength() * zoom);
            nodePushes++;
            ImNodes.pushStyleVar(ImNodesStyleVar.PinTriangleSideLength, nodes.getPinTriangleSideLength() * zoom);
            nodePushes++;
            ImNodes.pushStyleVar(ImNodesStyleVar.PinLineThickness, nodes.getPinLineThickness() * zoom);
            nodePushes++;
            ImNodes.pushStyleVar(ImNodesStyleVar.PinHoverRadius, nodes.getPinHoverRadius() * zoom);
            nodePushes++;
            ImNodes.pushStyleVar(ImNodesStyleVar.PinOffset, nodes.getPinOffset() * zoom);
            nodePushes++;
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, style.getFramePaddingX() * zoom, style.getFramePaddingY() * zoom);
            stylePushes++;
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, style.getItemSpacingX() * zoom, style.getItemSpacingY() * zoom);
            stylePushes++;
            ImGui.pushStyleVar(ImGuiStyleVar.ItemInnerSpacing, style.getItemInnerSpacingX() * zoom, style.getItemInnerSpacingY() * zoom);
            stylePushes++;
            ImGui.pushFont(font, fontSize);
            fontPushed = true;
            ImNodes.beginNodeEditor();
            try {
                this.place(GraphLayout.BOX, BOX_NODE, positions, placedIds, zoom);
                this.drawBox(editor, graph);
                for (Map.Entry<Integer, FunctionNode> functionNode : graph.functionNodes().entrySet()) {
                    this.place(functionNode.getKey(), nodeId(functionNode.getKey()), positions, placedIds, zoom);
                    this.drawFunctionNode(editor, functionNode.getKey(), functionNode.getValue());
                }
                this.drawLinks(graph);
                ImNodes.miniMap(MINI_MAP_SIZE, ImNodesMiniMapLocation.BottomRight);
            } finally {
                ImNodes.endNodeEditor();
            }
        } finally {
            if (fontPushed) {
                ImGui.popFont();
            }
            if (stylePushes > 0) {
                ImGui.popStyleVar(stylePushes);
            }
            for (int pop = 0; pop < nodePushes; pop++) {
                ImNodes.popStyleVar();
            }
        }
    }

    /**
     * The wheel over the canvas zooms around the cursor.
     *
     * @param hovered true when the mouse is over the canvas child or one of its children
     */
    private void applyWheel(IslandGraphs.GraphId graphId, float zoom, float originX, float originY, boolean hovered) {
        float notches = ImGui.getIO().getMouseWheel();
        if (notches == 0 || !hovered) {
            return;
        }
        float next = CanvasZoom.wheel(zoom, notches);
        if (next == zoom) {
            return;
        }
        float cursorX = ImGui.getMousePosX() - originX;
        float cursorY = ImGui.getMousePosY() - originY;
        ImNodes.editorContextResetPanning(CanvasZoom.panAround(ImNodes.editorContextGetPanningX(), cursorX, zoom, next),
                CanvasZoom.panAround(ImNodes.editorContextGetPanningY(), cursorY, zoom, next));
        this.zooms.put(graphId, next);
    }

    /** Gives imnodes the stored position of a function node, scaled by the zoom, once per screen and zoom. */
    private void place(int key, int node, Map<Integer, Sidecar.Position> positions, Set<Integer> placedIds, float zoom) {
        if (placedIds.add(key)) {
            Sidecar.Position position = positions.get(key);
            if (position != null) {
                ImNodes.setNodeGridSpacePos(node, CanvasZoom.toGrid(position.x(), zoom), CanvasZoom.toGrid(position.y(), zoom));
            }
        }
    }

    /** A drag moves a function node in imnodes. The stored positions follow it, unscaled. */
    private void readPositions(FunctionNodeGraph graph, Map<Integer, Sidecar.Position> positions, Set<Integer> placedBefore, float zoom) {
        for (int key : placedBefore) {
            if (key != GraphLayout.BOX && !graph.functionNodes().containsKey(key)) {
                continue;
            }
            int node = key == GraphLayout.BOX ? BOX_NODE : nodeId(key);
            float gridX = ImNodes.getNodeGridSpacePosX(node);
            float gridY = ImNodes.getNodeGridSpacePosY(node);
            Sidecar.Position stored = positions.get(key);
            if (stored == null || !CanvasZoom.shows(gridX, stored.x(), zoom) || !CanvasZoom.shows(gridY, stored.y(), zoom)) {
                positions.put(key, new Sidecar.Position(CanvasZoom.toStored(gridX, zoom), CanvasZoom.toStored(gridY, zoom)));
            }
        }
    }

    /** The router box, or the output box of a function graph. */
    private void drawBox(IslandEditor editor, FunctionNodeGraph graph) {
        ImNodes.beginNode(BOX_NODE);
        ImGui.pushID("box");
        try {
            ImNodes.beginNodeTitleBar();
            try {
                ImGui.textUnformatted(graph instanceof RouterGraph ? "noise_router" : "output");
            } finally {
                ImNodes.endNodeTitleBar();
            }
            List<String> outputs = graph.outputs();
            for (int index = 0; index < outputs.size(); index++) {
                String output = outputs.get(index);
                ImNodes.beginInputAttribute(boxAttribute(index));
                try {
                    ImGui.textUnformatted(graph instanceof RouterGraph ? output : "density function");
                    if (graph instanceof RouterGraph router && router.linked(output) == null) {
                        this.drawUnlinked(editor, router, output);
                    }
                } finally {
                    ImNodes.endInputAttribute();
                }
            }
        } finally {
            ImGui.popID();
            ImNodes.endNode();
        }
    }

    /** The switch Dimension default and, for a constant, its number field. */
    private void drawUnlinked(IslandEditor editor, RouterGraph router, String output) {
        RouterGraph.Unlinked value = router.unlinkedValue(output);
        ImGui.sameLine();
        boolean dimensionDefault = value instanceof RouterGraph.DimensionDefault;
        if (ImGui.checkbox("Dimension default##default_" + output, dimensionDefault)) {
            router.set(output, dimensionDefault ? new RouterGraph.Constant(0) : new RouterGraph.DimensionDefault());
            editor.edited();
        }
        if (value instanceof RouterGraph.Constant constant) {
            ImGui.sameLine();
            ImGui.setNextItemWidth(CONSTANT_WIDTH * this.drawZoom);
            ImDouble number = new ImDouble(constant.value());
            if (ImGui.inputDouble("##constant_" + output, number, 0, 0, "%.8g") && number.get() != constant.value()) {
                router.set(output, new RouterGraph.Constant(number.get()));
                editor.edited();
            }
        }
    }

    private static String title(FunctionNode functionNode) {
        return switch (functionNode) {
            case FunctionNode.Typed typed -> typed.type().id().getPath();
            case FunctionNode.Reference ignored -> "reference";
            case FunctionNode.Unknown unknown -> unknown.typeId().toString();
        };
    }

    private void drawFunctionNode(IslandEditor editor, int id, FunctionNode functionNode) {
        int colorPushes = 0;
        try {
            if (this.drawProblems.has(id)) {
                int red = ImGui.colorConvertFloat4ToU32(ERROR_COLOR[0], ERROR_COLOR[1], ERROR_COLOR[2], ERROR_COLOR[3]);
                ImNodes.pushColorStyle(ImNodesCol.TitleBar, red);
                colorPushes++;
                ImNodes.pushColorStyle(ImNodesCol.TitleBarHovered, red);
                colorPushes++;
                ImNodes.pushColorStyle(ImNodesCol.TitleBarSelected, red);
                colorPushes++;
            }
            this.drawFunctionNodeBody(editor, id, functionNode);
        } finally {
            for (int pop = 0; pop < colorPushes; pop++) {
                ImNodes.popColorStyle();
            }
        }
    }

    private void drawFunctionNodeBody(IslandEditor editor, int id, FunctionNode functionNode) {
        ImNodes.beginNode(nodeId(id));
        ImGui.pushID(nodeId(id));
        try {
            ImNodes.beginNodeTitleBar();
            try {
                ImGui.textUnformatted(title(functionNode));
            } finally {
                ImNodes.endNodeTitleBar();
            }
            if (functionNode instanceof FunctionNode.Reference referenceNode) {
                ImGui.textUnformatted(referenceNode.entry().toString());
                if (ImGui.button("Open##open")) {
                    this.deferred.add(() -> editor.openReference(referenceNode.entry()));
                }
            }
            List<String> pins = functionNode.inputPins();
            for (int index = 0; index < pins.size(); index++) {
                ImNodes.beginInputAttribute(inputAttribute(id, index));
                try {
                    ImGui.textUnformatted(pins.get(index));
                } finally {
                    ImNodes.endInputAttribute();
                }
            }
            ImNodes.beginOutputAttribute(outputAttribute(id));
            try {
                ImGui.textUnformatted("out");
            } finally {
                ImNodes.endOutputAttribute();
            }
        } finally {
            ImGui.popID();
            ImNodes.endNode();
        }
    }

    /** A link into a pin has the id of the pin attribute. */
    private void drawLinks(FunctionNodeGraph graph) {
        for (int id : graph.functionNodes().keySet()) {
            FunctionNodeGraph.Target target = graph.target(id);
            if (target instanceof FunctionNodeGraph.Target.Input input) {
                int index = graph.functionNode(input.pin().functionNode()).inputPins().indexOf(input.pin().name());
                int attribute = inputAttribute(input.pin().functionNode(), index);
                ImNodes.link(attribute, outputAttribute(id), attribute);
            } else if (target instanceof FunctionNodeGraph.Target.Output output) {
                int attribute = boxAttribute(graph.outputs().indexOf(output.output()));
                ImNodes.link(attribute, outputAttribute(id), attribute);
            }
        }
    }

    /** A request of the probe selects a function node that imnodes already holds. The imnodes selection then gives the selected function node. */
    private void applySelection(IslandEditor editor, Set<Integer> placedBefore) {
        Integer request = editor.takeSelectRequest();
        if (request != null) {
            if (placedBefore.contains(request)) {
                ImNodes.clearNodeSelection();
                ImNodes.selectNode(nodeId(request));
            } else {
                editor.requestSelect(request);
            }
            return;
        }
        int count = ImNodes.numSelectedNodes();
        Integer selected = null;
        if (count > 0) {
            int[] ids = new int[count];
            ImNodes.getSelectedNodes(ids);
            for (int node : ids) {
                if (node != BOX_NODE) {
                    selected = node - 1;
                }
            }
        }
        editor.select(selected);
    }

    /** The graph refuses a second link from one output and a loop. A refusal shows nothing. */
    private void applyLinks(IslandEditor editor, FunctionNodeGraph graph) {
        if (ImNodes.isLinkCreated(this.start, this.end)) {
            int from = this.start.get();
            int to = this.end.get();
            if (!isOutputAttribute(from)) {
                int swap = from;
                from = to;
                to = swap;
            }
            if (isOutputAttribute(from) && !isOutputAttribute(to) && this.connect(graph, from / SLOTS - 1, to)) {
                editor.edited();
            }
        }
        if (ImNodes.isLinkDestroyed(this.hovered) && this.disconnect(graph, this.hovered.get())) {
            editor.edited();
        }
    }

    private boolean connect(FunctionNodeGraph graph, int source, int attribute) {
        if (!graph.functionNodes().containsKey(source)) {
            return false;
        }
        if (attribute < SLOTS) {
            String output = graph.outputs().get(attribute - 1);
            return graph instanceof RouterGraph router ? router.link(source, output) : ((FunctionGraph) graph).setOutput(source);
        }
        FunctionNodeGraph.Pin pin = pin(graph, attribute);
        return pin != null && graph.link(source, pin);
    }

    /** The pin of an input attribute, or null when the function node or the pin is gone. */
    @Nullable
    private static FunctionNodeGraph.Pin pin(FunctionNodeGraph graph, int attribute) {
        int functionNode = attribute / SLOTS - 1;
        if (!graph.functionNodes().containsKey(functionNode)) {
            return null;
        }
        List<String> pins = graph.functionNode(functionNode).inputPins();
        int index = attribute % SLOTS - 1;
        return index >= 0 && index < pins.size() ? new FunctionNodeGraph.Pin(functionNode, pins.get(index)) : null;
    }

    private boolean disconnect(FunctionNodeGraph graph, int link) {
        Integer source;
        if (link < SLOTS) {
            source = link - 1 < graph.outputs().size() ? graph.outputSource(graph.outputs().get(link - 1)) : null;
        } else {
            FunctionNodeGraph.Pin pin = pin(graph, link);
            source = pin == null ? null : graph.source(pin);
        }
        if (source == null) {
            return false;
        }
        graph.unlink(source);
        return true;
    }

    /** Delete removes the selected links, then the selected function nodes. Their children stay without a link. */
    private void applyDelete(IslandEditor editor, FunctionNodeGraph graph, IslandGraphs.GraphId graphId, Map<Integer, Sidecar.Position> positions,
                             Set<Integer> placedIds) {
        if (!ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows) || ImGui.getIO().getWantTextInput() || !ImGui.isKeyPressed(ImGuiKey.Delete, false)) {
            return;
        }
        boolean changed = false;
        int links = ImNodes.numSelectedLinks();
        if (links > 0) {
            int[] ids = new int[links];
            ImNodes.getSelectedLinks(ids);
            ImNodes.clearLinkSelection();
            for (int link : ids) {
                changed |= this.disconnect(graph, link);
            }
        }
        int nodes = ImNodes.numSelectedNodes();
        if (nodes > 0) {
            int[] ids = new int[nodes];
            ImNodes.getSelectedNodes(ids);
            ImNodes.clearNodeSelection();
            for (int node : ids) {
                int functionNode = node - 1;
                if (node != BOX_NODE && graph.functionNodes().containsKey(functionNode)) {
                    graph.remove(functionNode);
                    positions.remove(functionNode);
                    placedIds.remove(functionNode);
                    changed = true;
                }
            }
            editor.select(null);
        }
        if (changed) {
            editor.edited();
        }
    }

    /** A right click on empty canvas, or a request of the probe, opens the palette at that canvas point. The point is unscaled. */
    private void drawPalette(IslandEditor editor, float originX, float originY, float zoom, boolean hovered) {
        float panX = ImNodes.editorContextGetPanningX();
        float panY = ImNodes.editorContextGetPanningY();
        boolean overItem = ImNodes.isNodeHovered(this.hovered) || ImNodes.isLinkHovered(this.hovered);
        if (hovered && !overItem && ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
            this.paletteAt = new Sidecar.Position(CanvasZoom.toStored(ImGui.getMousePosX() - originX - panX, zoom),
                    CanvasZoom.toStored(ImGui.getMousePosY() - originY - panY, zoom));
            this.openPalette(ImGui.getMousePosX(), ImGui.getMousePosY());
        }
        Sidecar.Position request = editor.takePaletteRequest();
        if (request != null) {
            this.paletteAt = request;
            this.openPalette(originX + panX + CanvasZoom.toGrid(request.x(), zoom), originY + panY + CanvasZoom.toGrid(request.y(), zoom));
        }
        boolean close = editor.takeClosePaletteRequest();
        boolean shown = ImGui.beginPopup(PALETTE);
        if (shown) {
            try {
                if (close) {
                    ImGui.closeCurrentPopup();
                } else {
                    this.drawPaletteEntries(editor);
                }
            } finally {
                ImGui.endPopup();
            }
        }
        editor.setPaletteShown(shown && !close);
    }

    /** Opens the palette at a screen point, moved to fit the game window. A popup outside it becomes a window of its own. */
    private void openPalette(float x, float y) {
        this.filter.set("");
        this.referenceOpen = false;
        ImGui.openPopup(PALETTE);
        ImGuiViewport viewport = ImGui.getMainViewport();
        float padding = 2 * ImGui.getStyle().getWindowPaddingY();
        float height = ImGui.getTextLineHeightWithSpacing() * PALETTE_ROWS + ImGui.getFrameHeightWithSpacing() * 4 + padding;
        float width = PALETTE_WIDTH + 2 * ImGui.getStyle().getWindowPaddingX();
        float fitX = Math.max(viewport.getPosX(), Math.min(x, viewport.getPosX() + viewport.getSizeX() - width));
        float fitY = Math.max(viewport.getPosY(), Math.min(y, viewport.getPosY() + viewport.getSizeY() - height));
        ImGui.setNextWindowPos(fitX, fitY);
    }

    private void drawPaletteEntries(IslandEditor editor) {
        Sidecar.Position at = this.paletteAt != null ? this.paletteAt : new Sidecar.Position(0, 0);
        ImGui.setNextItemWidth(PALETTE_WIDTH);
        if (ImGui.isWindowAppearing()) {
            ImGui.setKeyboardFocusHere();
        }
        ImGui.inputTextWithHint("##filter", "Filter", this.filter);
        if (ImGui.selectable("Constant")) {
            editor.add(FunctionNode.Typed.bareConstant(0), at);
            ImGui.closeCurrentPopup();
        }
        if (ImGui.selectable("Reference...", this.referenceOpen, ImGuiSelectableFlags.NoAutoClosePopups)) {
            this.referenceOpen = !this.referenceOpen;
        }
        if (this.referenceOpen) {
            ImGui.setNextItemWidth(PALETTE_WIDTH - ImGui.calcTextSizeX("Add") - 2 * ImGui.getStyle().getFramePaddingX() - ImGui.getStyle().getItemSpacingX());
            ImGui.inputTextWithHint("##reference", "Entry id", this.reference);
            ImGui.sameLine();
            ResourceLocation entry = ResourceLocation.tryParse(this.reference.get().trim());
            ImGui.beginDisabled(entry == null || this.reference.get().isBlank());
            try {
                if (ImGui.button("Add##add_reference") && entry != null) {
                    editor.add(new FunctionNode.Reference(entry), at);
                    ImGui.closeCurrentPopup();
                }
            } finally {
                ImGui.endDisabled();
            }
        }
        ImGui.separator();
        String needle = this.filter.get().trim().toLowerCase(Locale.ROOT);
        List<FunctionNodeType> types = FunctionNodeTypes.all().stream()
                .filter(type -> type.id().getPath().contains(needle))
                .sorted(Comparator.comparing((FunctionNodeType type) -> type.id().getPath()).thenComparing(type -> type.id().toString()))
                .toList();
        boolean list = ImGui.beginChild("##types", PALETTE_WIDTH, ImGui.getTextLineHeightWithSpacing() * PALETTE_ROWS, ImGuiChildFlags.None);
        try {
            if (list) {
                for (FunctionNodeType type : types) {
                    if (ImGui.selectable(type.id().getPath() + "##" + type.id())) {
                        editor.add(FunctionNodeDefaults.create(type, editor.defaultNoise()), at);
                        ImGui.closeCurrentPopup();
                    }
                }
            }
        } finally {
            ImGui.endChild();
        }
    }

    /** The fields of the selected function node, then the validator problems and the build error. The text wraps to the region width. */
    private void drawFields(IslandEditor editor, FunctionNodeGraph graph) {
        ImGui.pushTextWrapPos(0);
        try {
            this.drawFieldRows(editor, graph);
        } finally {
            ImGui.popTextWrapPos();
        }
    }

    private void drawFieldRows(IslandEditor editor, FunctionNodeGraph graph) {
        Integer selected = editor.selected();
        if (selected == null) {
            ImGui.textDisabled("Select a function node to edit its fields.");
        } else {
            FunctionNode functionNode = graph.functionNode(selected);
            ImGui.pushID(nodeId(selected));
            try {
                switch (functionNode) {
                    case FunctionNode.Typed typed -> {
                        idText(typed.type().id().toString());
                        float widest = 0;
                        for (FieldSpec field : typed.type().fields()) {
                            if (field.kind() != FieldKind.FUNCTION) {
                                widest = Math.max(widest, ImGui.calcTextSizeX(field.name()));
                            }
                        }
                        float column = Math.min(ImGui.getContentRegionAvailX() * LABEL_SHARE, widest + ImGui.getStyle().getItemSpacingX());
                        for (FieldSpec field : typed.type().fields()) {
                            if (field.kind() != FieldKind.FUNCTION) {
                                this.drawField(editor, selected, typed, field, column);
                            }
                        }
                    }
                    case FunctionNode.Reference referenceNode -> {
                        ImGui.alignTextToFramePadding();
                        ImGui.textUnformatted("Reference to " + referenceNode.entry());
                        ImGui.sameLine();
                        if (ImGui.button("Open##open_selected")) {
                            this.deferred.add(() -> editor.openReference(referenceNode.entry()));
                        }
                    }
                    case FunctionNode.Unknown unknown -> {
                        idText(unknown.typeId().toString());
                        ImGui.textDisabled("No function node type describes this type, so it has no field editor.");
                    }
                }
            } finally {
                ImGui.popID();
            }
        }
        IslandEditor.ShownProblems problems = this.drawProblems;
        for (String text : problems.of(selected)) {
            errorText(text);
        }
        for (String text : problems.general()) {
            errorText(text);
        }
        if (problems.notice()) {
            errorText(NOTICE);
        }
    }

    /** A type id clips with an ellipsis and never wraps inside a word. Its tooltip holds the full id. */
    private static void idText(String id) {
        float width = ImGui.getContentRegionAvailX();
        if (ImGui.calcTextSizeX(id) <= width) {
            ImGui.textUnformatted(id);
            return;
        }
        int end = id.length();
        while (end > 0 && ImGui.calcTextSizeX(id.substring(0, end) + ELLIPSIS) > width) {
            end--;
        }
        ImGui.textUnformatted(id.substring(0, end) + ELLIPSIS);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(id);
        }
    }

    /** A label wider than the label column takes its own line, and its field starts the next line. */
    private void drawField(IslandEditor editor, int id, FunctionNode.Typed typed, FieldSpec field, float column) {
        FieldValue value = typed.values().get(field.name());
        ImGui.alignTextToFramePadding();
        ImGui.textUnformatted(field.name());
        if (ImGui.calcTextSizeX(field.name()) + ImGui.getStyle().getItemSpacingX() <= column) {
            ImGui.sameLine(column);
        }
        ImGui.setNextItemWidth(-1);
        String widget = "##field_" + field.name();
        switch (field.kind()) {
            case NUMBER -> {
                ImDouble number = new ImDouble(value instanceof FieldValue.Number current ? current.value() : FunctionNodeDefaults.nearestZero(field));
                if (ImGui.inputDouble(widget, number, 0, 0, "%.8g")) {
                    editor.setField(id, field.name(), new FieldValue.Number(Math.clamp(number.get(), field.min(), field.max())));
                }
            }
            case INTEGER -> {
                ImInt number = new ImInt(value instanceof FieldValue.Int current ? current.value() : (int) FunctionNodeDefaults.nearestZero(field));
                if (ImGui.inputInt(widget, number, 0, 0)) {
                    editor.setField(id, field.name(), new FieldValue.Int((int) Math.clamp(number.get(), field.min(), field.max())));
                }
            }
            case ENUM -> {
                String current = value instanceof FieldValue.Choice choice ? choice.value() : "";
                if (ImGui.beginCombo(widget, current)) {
                    try {
                        for (String choice : field.choices()) {
                            if (ImGui.selectable(choice, choice.equals(current))) {
                                editor.setField(id, field.name(), new FieldValue.Choice(choice));
                            }
                        }
                    } finally {
                        ImGui.endCombo();
                    }
                }
            }
            case NOISE -> this.drawNoise(editor, id, field, value, widget);
            case SPLINE -> ImGui.textDisabled(value instanceof FieldValue.Spline spline ? splineSummary(spline.value()) : "No spline");
            case FUNCTION -> {
            }
        }
    }

    /** A text field for the noise id. An id outside the noise registry gives the field a red frame. */
    private void drawNoise(IslandEditor editor, int id, FieldSpec field, @Nullable FieldValue value, String widget) {
        if (!(value instanceof FieldValue.Noise noise) || noise.id() == null) {
            ImGui.textDisabled("An inline noise object. The editor keeps it as it is.");
            return;
        }
        boolean unknown = !editor.noises().contains(noise.id());
        if (unknown) {
            ImGui.pushStyleColor(ImGuiCol.FrameBg, INVALID_RED, 0.1F, 0.1F, 1);
        }
        try {
            ImString text = new ImString(noise.id().toString(), TEXT_CAPACITY);
            if (ImGui.inputText(widget, text)) {
                ResourceLocation parsed = ResourceLocation.tryParse(text.get().trim());
                if (parsed != null) {
                    editor.setField(id, field.name(), FieldValue.Noise.of(parsed));
                }
            }
        } finally {
            if (unknown) {
                ImGui.popStyleColor();
            }
        }
    }

    /** The point count of each multipoint level, in the sequence of the coordinate pins. Spline edits come in a later step. */
    private static String splineSummary(SplineValue spline) {
        if (spline instanceof SplineValue.Constant constant) {
            return "Constant spline " + constant.value() + ", read only";
        }
        List<Integer> counts = new ArrayList<>();
        countPoints(spline, counts);
        return "Points per level: " + String.join(" / ", counts.stream().map(String::valueOf).toList()) + ", read only";
    }

    private static void countPoints(SplineValue spline, List<Integer> counts) {
        if (spline instanceof SplineValue.Multipoint multipoint) {
            counts.add(multipoint.points().size());
            for (SplineValue.Point point : multipoint.points()) {
                countPoints(point.value(), counts);
            }
        }
    }

    private static void errorText(String text) {
        ImGui.pushStyleColor(ImGuiCol.Text, ERROR_COLOR[0], ERROR_COLOR[1], ERROR_COLOR[2], ERROR_COLOR[3]);
        try {
            ImGui.textWrapped(text.replace("%", "%%"));
        } finally {
            ImGui.popStyleColor();
        }
    }

}
