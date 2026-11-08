package us.drullk.aerolite.devtools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import us.drullk.aerolite.client.IslandsPresetScreen;
import us.drullk.aerolite.client.preview.CanvasZoom;
import us.drullk.aerolite.client.preview.IslandEditor;
import us.drullk.aerolite.client.preview.PreviewBuilder;
import us.drullk.aerolite.client.preview.PreviewScreen;
import us.drullk.aerolite.world.AeroliteWorldgen;
import us.drullk.aerolite.world.island.IslandPack;
import us.drullk.aerolite.world.island.IslandSettings;
import us.drullk.aerolite.world.island.VoronoiIslands;
import us.drullk.aerolite.world.island.VoronoiLattice;
import us.drullk.aerolite.world.island.graph.FieldValue;
import us.drullk.aerolite.world.island.graph.FunctionNode;
import us.drullk.aerolite.world.island.graph.FunctionNodeGraph;
import us.drullk.aerolite.world.island.graph.GraphLayout;
import us.drullk.aerolite.world.island.graph.IslandGraphs;
import us.drullk.aerolite.world.island.graph.IslandSettingsFunctionNode;
import us.drullk.aerolite.world.island.graph.RouterGraph;
import us.drullk.aerolite.world.island.graph.Sidecar;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * The screenshot probe of the Islands preset. The system property {@value #PROPERTY} turns it on.
 * Each check logs one line with its result. A failed check stops its step with an error in the log.
 * The probe saves these states under {@code screenshots/} of the game directory, in this sequence:
 * <ol start="0">
 *     <li>{@code islands-00-settings}: the Islands screen of the Islands preset.</li>
 *     <li>{@code islands-01-preview}: the islands preview at the radius {@value #PREVIEW_RADIUS}, with the editor closed and the Node Editor button.
 *     The check states that the editor starts closed. Then {@link IslandEditor#open()} opens the editor.</li>
 *     <li>{@code islands-02-editor-router}: the router graph of the Overworld.</li>
 *     <li>{@code islands-03-function-graph}: the function graph of the Overworld island entry.</li>
 *     <li>{@code islands-04-palette}: the palette, opened through a request.</li>
 *     <li>{@code islands-05-palette-wheel}: the palette after a real right click on empty canvas and a wheel over its list.
 *     The check states that the palette stays open and the canvas zoom stays.</li>
 *     <li>{@code islands-06-palette-right-click}: the palette after a right click over it. The check states that the palette stays open.</li>
 *     <li>{@code islands-07-field-editor}: the field editor of the island function node after the cell size edit {@value #PROBE_CELL_SIZE}.</li>
 *     <li>{@code islands-08-build-error}: the red error of a router constant out of range.</li>
 *     <li>{@code islands-09-node-problem}: a detached constant with a red title bar and its problem text.</li>
 *     <li>{@code islands-10-field-focus}: a focused text field of the field editor.</li>
 *     <li>{@code islands-11-escape-in-field}: the editor after Escape in that field. The check states that the editor stays open.</li>
 *     <li>{@code islands-12-other-selected}: another selected function node. The check states that the problem text is gone and the title bar stays red.</li>
 *     <li>{@code islands-13-router-panned}: the router graph panned to the final density chain.</li>
 *     <li>{@code islands-14-router-zoomed-out}: the router graph at the zoom that fits it, or at the lowest zoom with the router box centered.</li>
 *     <li>{@code islands-15-wheel-canvas}: the canvas after a wheel over it. A closed editor then gives the wheel over the game view to the camera.</li>
 *     <li>{@code islands-16-wheel-fields}: the field editor of a resized editor window after a wheel over it.</li>
 *     <li>{@code islands-17-splitter}: the field editor after a drag of its splitter. The next open puts the window back at its margin.</li>
 *     <li>{@code islands-18-escape}: the preview after Escape closed the editor.</li>
 *     <li>{@code islands-19-second-escape}: the Islands screen after a second Escape closed the preview.</li>
 *     <li>{@code islands-20-dimension-overworld}: the Islands screen with its dimension cycle button on the Overworld.
 *     A second preview first edits the cell size, a router constant and the router box position, and its Done keeps them.</li>
 *     <li>{@code islands-21-dimension-nether}: the Islands screen after a press of the cycle button. The check states that the sliders show the Nether.</li>
 *     <li>{@code islands-22-reload-failed}: the vanilla failure screen of a reload with a reference to a missing entry.</li>
 *     <li>{@code islands-23-reload-returned}: the create world screen after that failure. The check states that the old pack files stay.
 *     Then another preview logs the graphs that the graph holder keeps.</li>
 *     <li>{@code islands-24-side} and {@code islands-25-top}: a side view and a top view of the first island of a new world with the seed {@value #SEED}.
 *     Before them, the pack checks (a), (b) and (c) run in that world.</li>
 *     <li>{@code islands-26-recreate}: the World tab of the create world screen after Recreate of that world. The check states the world type Aerolite Islands.</li>
 *     <li>{@code islands-27-recreate-editor}: the editor of the recreated flow. The check states the saved router box position and the restored graphs.
 *     Then the probe edits the cell size back to its start value and checks that the pack writes no file for that entry.</li>
 * </ol>
 * Then it stops the game.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_18-19_754b3009-7e46-47f9-99e4-01f82356bea1.md}.
 */
public final class IslandsProbe {

    public static final String PROPERTY = "aerolite.devtools.islandsProbe";
    public static final String SEED = "1234";
    private static final String WORLD_NAME = "Islands probe";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int TITLE_WAIT = 40;
    /** The ticks the probe gives the chunks around a new camera position. */
    private static final int CHUNK_WAIT = 300;
    private static final int STEP_LIMIT = 1200;
    private static final int WINDOW_WIDTH = 1200;
    private static final int WINDOW_HEIGHT = 860;
    private static final int RENDER_DISTANCE = 24;
    /** The side view stands this far from the site along X and Z, and this high above it. */
    private static final int SIDE_DISTANCE = 220;
    private static final int SIDE_HEIGHT = 120;
    private static final int TOP_HEIGHT = 200;
    private static final int PREVIEW_RADIUS = 10;
    /** The ticks the probe gives the island editor to draw a new state. */
    private static final int EDITOR_WAIT = 10;
    /** The density function entry of the island function of the Overworld. */
    private static final ResourceLocation OVERWORLD_ISLANDS = ResourceLocation.fromNamespaceAndPath("aerolite", "islands/overworld");
    private static final ResourceLocation NETHER_ISLANDS = ResourceLocation.fromNamespaceAndPath("aerolite", "islands/nether");
    /** An entry that no pack holds. A reference to it fails the reload of the world creation context. */
    private static final ResourceLocation MISSING_ENTRY = ResourceLocation.fromNamespaceAndPath("aerolite", "probe/missing");
    /** A router output that the island noise settings do not read, because ore veins are off. */
    private static final String SPARE_OUTPUT = "vein_gap";
    private static final double SPARE_VALUE = 0.5;
    /** The canvas point of the palette. */
    private static final float PALETTE_X = 40;
    private static final float PALETTE_Y = 40;
    /** The panning that moves every function node out of the top left part of the canvas. */
    private static final float EMPTY_PAN = 4000;
    /** The canvas distance of the right click that opens the palette. */
    private static final float PALETTE_CLICK = 40;
    /** The distance from the palette corner to a point over its type list. */
    private static final float PALETTE_LIST_X = 130;
    private static final float PALETTE_LIST_Y = 220;
    /** The cell size of the field edit. The edit stays through Done, so the island pack carries it. */
    private static final int PROBE_CELL_SIZE = 384;
    /** The canvas panning that shows the final density row of the router box. */
    private static final float ROUTER_PAN_Y = -240;
    /** The canvas distance of a panned target from the top left corner of the canvas. */
    private static final float PAN_MARGIN = 40;
    /** The shift of the saved router box from its layout position, along X and Y. */
    private static final float ROUTER_BOX_SHIFT = 160;
    /** A constant outside the vanilla range of a router output. */
    private static final double OUT_OF_RANGE = 2000000;
    /** The wheel notches of each wheel check. */
    private static final int WHEEL_NOTCHES = 3;
    private static final float ZOOM_TOLERANCE = 0.001F;
    private static final float POSITION_TOLERANCE = 0.5F;
    /** A window point of the game view, clear of the view gizmo. */
    private static final double GAME_VIEW_POINT = 40;
    /** The half width of the splitter left of the field editor, and the distance of the splitter drag. */
    private static final float SPLITTER_HALF = 3;
    private static final float SPLITTER_DRAG = 120;
    /** The distance from the field editor corner to the resize grip of the editor window. */
    private static final float GRIP_OFFSET = 4;
    /** The upward drag of the resize grip. */
    private static final float RESIZE_DRAG = 480;

    /** The files that the island pack of the probe holds. */
    private static final List<String> PACK_FILES = List.of(
            IslandPack.PACK_META,
            IslandPack.entryPath(Registries.DENSITY_FUNCTION, OVERWORLD_ISLANDS),
            IslandPack.entrySidecarPath(OVERWORLD_ISLANDS),
            IslandPack.startJsonPath(OVERWORLD_ISLANDS),
            IslandPack.entryPath(Registries.NOISE_SETTINGS, OVERWORLD_ISLANDS),
            IslandPack.routerSidecarPath(Level.OVERWORLD),
            IslandPack.dimensionDefaultPath(Level.OVERWORLD));
    /** The files of the island pack that belong to the Overworld island entry. */
    private static final List<String> ENTRY_FILES = List.of(
            IslandPack.entryPath(Registries.DENSITY_FUNCTION, OVERWORLD_ISLANDS),
            IslandPack.entrySidecarPath(OVERWORLD_ISLANDS),
            IslandPack.startJsonPath(OVERWORLD_ISLANDS));

    private record Step(String name, int delay, BooleanSupplier action) {
    }

    private final List<Step> steps = new ArrayList<>();
    private int ticks;
    private int index;
    private int waited;
    private boolean started;
    @Nullable
    private VoronoiLattice.Cell island;
    /** The create world screen of the current flow. */
    @Nullable
    private CreateWorldScreen createScreen;
    /** The router box position that the island pack keeps. */
    @Nullable
    private Sidecar.Position savedBox;

    public IslandsProbe() {
        this.queue();
    }

    private void step(String name, int delay, BooleanSupplier action) {
        this.steps.add(new Step(name, delay, action));
    }

    private void queue() {
        Minecraft minecraft = Minecraft.getInstance();
        this.step("window", 1, () -> {
            GLFW.glfwSetWindowSize(minecraft.getWindow().getWindow(), WINDOW_WIDTH, WINDOW_HEIGHT);
            minecraft.options.renderDistance().set(RENDER_DISTANCE);
            minecraft.resizeDisplay();
            return true;
        });
        this.step("create world", 1, () -> {
            CreateWorldScreen.openFresh(minecraft, minecraft.screen);
            return true;
        });
        this.step("islands preset", 1, () -> {
            if (!(minecraft.screen instanceof CreateWorldScreen screen)) {
                return false;
            }
            this.createScreen = screen;
            WorldCreationUiState state = screen.getUiState();
            WorldCreationUiState.WorldTypeEntry islands = state.getNormalPresetList().stream()
                    .filter(entry -> entry.preset() != null && entry.preset().is(AeroliteWorldgen.ISLANDS_PRESET))
                    .findFirst().orElseThrow(() -> new IllegalStateException("The Islands preset is not in the normal list"));
            state.setWorldType(islands);
            state.setSeed(SEED);
            state.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            state.setAllowCommands(true);
            state.setName(WORLD_NAME);
            return true;
        });
        this.queueCustomize("");
        this.step("grab settings", 10, () -> {
            if (!(minecraft.screen instanceof IslandsPresetScreen)) {
                return false;
            }
            SettingsProbe.grabFront("islands-00-settings.png");
            return true;
        });
        this.queuePreview("");
        this.step("grab preview", 10, () -> {
            IslandEditor editor = editor();
            if (editor == null) {
                return false;
            }
            LOGGER.info("Islands probe: preview build done");
            SettingsProbe.grabFront("islands-01-preview.png");
            LOGGER.info("Islands probe: at the preview start the editor is open {}", editor.isOpen());
            if (editor.isOpen()) {
                throw new IllegalStateException("Editor check (closed at start) failed: the editor is open");
            }
            LOGGER.info("Islands probe: editor check (closed at start) passed");
            editor.open();
            return true;
        });
        this.queueEditor();
        this.queueEscape();
        this.queueDropdown();
        this.step("settings done", 10, () -> minecraft.screen instanceof IslandsPresetScreen && SettingsProbe.press(minecraft.screen, "gui.done"));
        this.step("pack reloaded", 10, () -> {
            if (!(minecraft.screen instanceof CreateWorldScreen screen)) {
                return false;
            }
            List<String> enabled = screen.getUiState().getSettings().dataConfiguration().dataPacks().getEnabled();
            LOGGER.info("Islands probe: the enabled data packs after Done are {}", enabled);
            return enabled.contains(IslandPack.PACK_ID);
        });
        this.queueReloadFailure();
        this.step("create", 10, () -> SettingsProbe.press(minecraft.screen, "selectWorld.create"));
        // A world of a data pack preset shows the experimental features warning before it loads.
        this.step("confirm experimental", 10, () -> minecraft.level != null || minecraft.screen instanceof ConfirmScreen && SettingsProbe.press(minecraft.screen, "gui.yes"));
        this.step("world loaded", 20, () -> minecraft.level != null && minecraft.player != null && minecraft.screen == null && minecraft.getSingleplayerServer() != null);
        this.step("pack folder", 1, () -> {
            checkPackFolder(minecraft.getSingleplayerServer());
            return true;
        });
        this.step("pack registries", 1, () -> {
            checkRegistries(minecraft.getSingleplayerServer());
            return true;
        });
        this.step("find island", 1, () -> {
            MinecraftServer server = minecraft.getSingleplayerServer();
            this.island = firstIsland(server);
            LOGGER.info("Islands probe: the first island sits at {} {} {}", this.island.siteX(), this.island.siteY(), this.island.siteZ());
            int cellSize = IslandSettings.of(islands(server)).cellSize();
            if (cellSize != PROBE_CELL_SIZE) {
                throw new IllegalStateException("Pack check (c) failed: the island function of the Overworld has the cell size " + cellSize);
            }
            LOGGER.info("Islands probe: pack check (c) passed: the island function of the Overworld has the cell size {}", cellSize);
            minecraft.player.connection.sendCommand("gamemode spectator");
            return true;
        });
        this.step("side view", 10, () -> {
            VoronoiLattice.Cell cell = this.island;
            return this.teleport(cell.siteX() - SIDE_DISTANCE, cell.siteY() + SIDE_HEIGHT, cell.siteZ() - SIDE_DISTANCE, cell);
        });
        this.step("grab side", CHUNK_WAIT, () -> {
            SettingsProbe.grabFront("islands-24-side.png");
            return true;
        });
        this.step("top view", 1, () -> {
            VoronoiLattice.Cell cell = this.island;
            return this.teleport(cell.siteX() + 1, cell.siteY() + TOP_HEIGHT, cell.siteZ() + 1, cell);
        });
        this.step("grab top", CHUNK_WAIT, () -> {
            SettingsProbe.grabFront("islands-25-top.png");
            return true;
        });
        this.queueRecreate();
        this.step("stop", 10, () -> {
            minecraft.stop();
            return true;
        });
    }

    /** Opens the Islands screen from the World tab of the create world screen. */
    private void queueCustomize(String flow) {
        Minecraft minecraft = Minecraft.getInstance();
        // The Customize button sits in the World tab.
        this.step(flow + "world tab", 1, () -> {
            if (!(minecraft.screen instanceof CreateWorldScreen screen)) {
                return false;
            }
            SettingsProbe.widgets(screen, TabNavigationBar.class).getFirst().selectTab(1, false);
            return true;
        });
        this.step(flow + "customize", 10, () -> SettingsProbe.press(minecraft.screen, "selectWorld.customizeType"));
    }

    /** Opens the islands preview from the Islands screen and waits for its build. */
    private void queuePreview(String flow) {
        Minecraft minecraft = Minecraft.getInstance();
        this.step(flow + "preview", 1, () -> minecraft.screen instanceof IslandsPresetScreen
                && SettingsProbe.press(minecraft.screen, "createWorld.customize.aerolite.preview"));
        this.step(flow + "preview radius", 10, () -> {
            if (!(minecraft.screen instanceof PreviewScreen screen)) {
                return false;
            }
            screen.setRadius(PREVIEW_RADIUS);
            LOGGER.info("Islands probe: {}preview build start", flow);
            return true;
        });
        this.step(flow + "preview built", 20, () -> minecraft.screen instanceof PreviewScreen screen && screen.progress().phase() == PreviewBuilder.Phase.DONE);
    }

    /** The island editor of the open islands preview, or null. */
    @Nullable
    private static IslandEditor editor() {
        return Minecraft.getInstance().screen instanceof PreviewScreen screen ? screen.islandEditor() : null;
    }

    /** The first island function node of the shown graph, or null. */
    @Nullable
    private static Integer islandsFunctionNode(IslandEditor editor) {
        FunctionNodeGraph graph = editor.graph();
        if (graph == null) {
            return null;
        }
        for (Map.Entry<Integer, FunctionNode> entry : graph.functionNodes().entrySet()) {
            if (IslandSettingsFunctionNode.isIslands(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** The cell size of the first island function node of the shown graph, or null. */
    @Nullable
    private static Integer shownCellSize(IslandEditor editor) {
        Integer functionNode = islandsFunctionNode(editor);
        FunctionNodeGraph graph = editor.graph();
        if (functionNode == null || graph == null || !(graph.functionNode(functionNode) instanceof FunctionNode.Typed typed)
                || !(typed.values().get("cell_size") instanceof FieldValue.Int current)) {
            return null;
        }
        return current.value();
    }

    private void queueEditor() {
        this.step("grab editor router", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            if (editor == null || !editor.isOpen()) {
                return false;
            }
            SettingsProbe.grabFront("islands-02-editor-router.png");
            return true;
        });
        this.step("open reference", 1, () -> {
            IslandEditor editor = editor();
            if (editor == null) {
                return false;
            }
            editor.openReference(OVERWORLD_ISLANDS);
            return editor.openEntry() != null;
        });
        this.step("grab function graph", EDITOR_WAIT, () -> {
            SettingsProbe.grabFront("islands-03-function-graph.png");
            return true;
        });
        // ImGui closes a popup when the game window loses the focus, so the probe opens the palette again until a frame shows it.
        this.step("grab palette", EDITOR_WAIT, () -> {
            IslandEditor shown = editor();
            if (shown == null || !shown.paletteShown()) {
                if (shown != null) {
                    shown.requestPalette(PALETTE_X, PALETTE_Y);
                }
                return false;
            }
            SettingsProbe.grabFront("islands-04-palette.png");
            shown.requestClosePalette();
            return true;
        });
        this.queuePalette();
        int[] cellSize = new int[1];
        this.step("select island function node", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            Integer functionNode = editor == null ? null : islandsFunctionNode(editor);
            Integer current = editor == null ? null : shownCellSize(editor);
            if (functionNode == null || current == null) {
                return false;
            }
            cellSize[0] = current;
            editor.requestSelect(functionNode);
            editor.setField(functionNode, "cell_size", new FieldValue.Int(PROBE_CELL_SIZE));
            return true;
        });
        this.step("field edit built", EDITOR_WAIT, () -> Minecraft.getInstance().screen instanceof PreviewScreen screen
                && screen.progress().phase() == PreviewBuilder.Phase.DONE);
        this.step("grab field editor", EDITOR_WAIT, () -> {
            SettingsProbe.grabFront("islands-07-field-editor.png");
            LOGGER.info("Islands probe: the cell size {} changed to {}", cellSize[0], PROBE_CELL_SIZE);
            return true;
        });
        int[] finalDensity = new int[1];
        this.step("break the router", 1, () -> {
            IslandEditor editor = editor();
            RouterGraph router = editor == null ? null : router(editor);
            Integer root = router == null ? null : router.linked("final_density");
            if (root == null) {
                return false;
            }
            finalDensity[0] = root;
            editor.back();
            Sidecar.Position box = routerPositions(editor).getOrDefault(GraphLayout.BOX, new Sidecar.Position(0, 0));
            editor.requestPan(PAN_MARGIN + GraphLayout.COLUMN_WIDTH - box.x(), ROUTER_PAN_Y - box.y());
            editor.setRouterOutput("final_density", new RouterGraph.Constant(OUT_OF_RANGE));
            return editor.buildError() != null;
        });
        this.step("grab error", EDITOR_WAIT, () -> {
            SettingsProbe.grabFront("islands-08-build-error.png");
            IslandEditor editor = editor();
            RouterGraph router = editor == null ? null : router(editor);
            if (router != null) {
                router.set("final_density", new RouterGraph.DimensionDefault());
                router.link(finalDensity[0], "final_density");
                editor.changed();
            }
            return true;
        });
        this.queueProblem();
        this.step("editor restored", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            LOGGER.info("Islands probe: the editor build error after the restore is {}", editor == null ? "absent" : editor.buildError());
            return editor != null && editor.buildError() == null;
        });
        this.step("pan to final density", 1, () -> {
            IslandEditor editor = editor();
            RouterGraph router = editor == null ? null : router(editor);
            if (router == null) {
                return false;
            }
            Map<Integer, Sidecar.Position> positions = routerPositions(editor);
            float left = Float.POSITIVE_INFINITY;
            float top = Float.POSITIVE_INFINITY;
            for (int id : router.paths().entrySet().stream().filter(path -> path.getKey().startsWith("final_density"))
                    .map(Map.Entry::getValue).toList()) {
                Sidecar.Position position = positions.get(id);
                if (position != null) {
                    left = Math.min(left, position.x());
                    top = Math.min(top, position.y());
                }
            }
            if (left == Float.POSITIVE_INFINITY) {
                return false;
            }
            editor.requestPan(PAN_MARGIN - left, PAN_MARGIN - top);
            return true;
        });
        this.step("grab router panned", EDITOR_WAIT, () -> {
            SettingsProbe.grabFront("islands-13-router-panned.png");
            return true;
        });
        this.step("zoom out router", 1, () -> {
            IslandEditor editor = editor();
            RouterGraph router = editor == null ? null : router(editor);
            if (router == null) {
                return false;
            }
            Map<Integer, Sidecar.Position> positions = routerPositions(editor);
            float left = Float.POSITIVE_INFINITY;
            float top = Float.POSITIVE_INFINITY;
            float right = Float.NEGATIVE_INFINITY;
            float bottom = Float.NEGATIVE_INFINITY;
            for (Map.Entry<Integer, Sidecar.Position> entry : positions.entrySet()) {
                Sidecar.Position position = entry.getValue();
                int pins = router.functionNodes().containsKey(entry.getKey()) ? router.functionNode(entry.getKey()).inputPins().size() : 0;
                float height = entry.getKey() == GraphLayout.BOX ? GraphLayout.boxHeight(router) : GraphLayout.ROW_HEIGHT + GraphLayout.PIN_HEIGHT * pins;
                left = Math.min(left, position.x());
                top = Math.min(top, position.y());
                right = Math.max(right, position.x() + GraphLayout.COLUMN_WIDTH);
                bottom = Math.max(bottom, position.y() + height);
            }
            Sidecar.Position canvas = editor.canvasSize();
            if (left == Float.POSITIVE_INFINITY || canvas.x() <= 0 || canvas.y() <= 0) {
                return false;
            }
            float fit = Math.min((canvas.x() - 2 * PAN_MARGIN) / (right - left), (canvas.y() - 2 * PAN_MARGIN) / (bottom - top));
            float zoom = CanvasZoom.clamp(fit);
            LOGGER.info("Islands probe: the router graph spans {} by {} canvas units, the fit zoom is {}, the zoom is {}{}",
                    right - left, bottom - top, fit, zoom, fit < CanvasZoom.MIN ? " (the lowest zoom)" : " (the whole graph)");
            editor.requestZoom(zoom);
            Sidecar.Position box = positions.get(GraphLayout.BOX);
            if (fit >= CanvasZoom.MIN || box == null) {
                editor.requestPan(PAN_MARGIN - left * zoom, PAN_MARGIN - top * zoom);
            } else {
                float boxMiddle = box.y() + GraphLayout.boxHeight(router) / 2;
                editor.requestPan(PAN_MARGIN - left * zoom, canvas.y() / 2 - boxMiddle * zoom);
            }
            return true;
        });
        this.step("grab router zoomed out", EDITOR_WAIT, () -> {
            SettingsProbe.grabFront("islands-14-router-zoomed-out.png");
            IslandEditor editor = editor();
            if (editor != null) {
                editor.requestZoom(1);
            }
            return true;
        });
        this.queueWheel();
    }

    /** A real right click on empty canvas opens the palette. Then the wheel and a right click act over the palette. */
    private void queuePalette() {
        float[] zoom = new float[1];
        int[] phase = new int[1];
        this.step("palette clear canvas", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            if (editor == null) {
                return false;
            }
            if (editor.paletteShown()) {
                editor.requestClosePalette();
                return false;
            }
            zoom[0] = editor.zoom();
            editor.requestPan(EMPTY_PAN, EMPTY_PAN);
            phase[0] = 0;
            return true;
        });
        this.step("palette right click", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            if (editor == null || editor.canvasArea().width() <= 0) {
                return false;
            }
            return clickUntil(phase, editor.canvasArea().x() + PALETTE_CLICK, editor.canvasArea().y() + PALETTE_CLICK, GlfwInput::rightButton,
                    editor::paletteShown, "Palette right click failed: no right click on empty canvas opened the palette");
        });
        this.step("palette wheel cursor", 2, () -> {
            IslandEditor editor = editor();
            if (editor == null) {
                return false;
            }
            LOGGER.info("Islands probe: a right click on empty canvas opened the palette {}", editor.paletteShown());
            GlfwInput.cursor(editor.canvasArea().x() + PALETTE_CLICK + PALETTE_LIST_X, editor.canvasArea().y() + PALETTE_CLICK + PALETTE_LIST_Y);
            return true;
        });
        this.step("palette wheel", 2, () -> {
            GlfwInput.scroll(0, -WHEEL_NOTCHES);
            return true;
        });
        this.step("grab palette wheel", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            if (editor == null) {
                return false;
            }
            SettingsProbe.grabFront("islands-05-palette-wheel.png");
            LOGGER.info("Islands probe: the wheel over the palette leaves the palette open {} and the zoom {} (from {}). The list scroll is visual only",
                    editor.paletteShown(), editor.zoom(), zoom[0]);
            if (!editor.paletteShown() || Math.abs(editor.zoom() - zoom[0]) > ZOOM_TOLERANCE) {
                throw new IllegalStateException("Palette wheel check failed: palette open " + editor.paletteShown() + ", zoom " + editor.zoom());
            }
            LOGGER.info("Islands probe: palette wheel check passed");
            return true;
        });
        int[] clicked = new int[1];
        this.step("palette right click over the palette", 2, () -> {
            switch (clicked[0]++) {
                case 0 -> GlfwInput.rightButton(true);
                case 1 -> GlfwInput.rightButton(false);
                default -> {
                    return true;
                }
            }
            return false;
        });
        this.step("grab palette right click", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            if (editor == null) {
                return false;
            }
            SettingsProbe.grabFront("islands-06-palette-right-click.png");
            LOGGER.info("Islands probe: after a right click over the palette the palette is open {}. The palette count is visual only", editor.paletteShown());
            if (!editor.paletteShown()) {
                throw new IllegalStateException("Palette right click check failed: the palette closed");
            }
            LOGGER.info("Islands probe: palette right click check passed");
            editor.requestClosePalette();
            editor.requestPan(0, 0);
            return true;
        });
    }

    /**
     * Moves the cursor, then presses and releases a mouse button on the next ticks, until the condition holds.
     * Each try moves down by 4 units. Gives true when the condition holds.
     */
    private static boolean clickUntil(int[] phase, double x, double y, Consumer<Boolean> button, BooleanSupplier condition, String failure) {
        int step = phase[0] % 4;
        double tryY = y + 4 * (phase[0] / 4);
        if (tryY > y + 200) {
            throw new IllegalStateException(failure);
        }
        switch (step) {
            case 0 -> GlfwInput.cursor(x, tryY);
            case 1 -> button.accept(true);
            case 2 -> button.accept(false);
            default -> {
                if (condition.getAsBoolean()) {
                    return true;
                }
            }
        }
        phase[0]++;
        return false;
    }

    /** A detached constant shows its problem while selected. Escape in a text field ends the field edit only. */
    private void queueProblem() {
        int[] problemNode = new int[1];
        this.step("add problem node", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            if (editor == null || editor.openEntry() != null || editor.buildError() != null) {
                return false;
            }
            Sidecar.Position box = routerPositions(editor).getOrDefault(GraphLayout.BOX, new Sidecar.Position(0, 0));
            Sidecar.Position at = new Sidecar.Position(box.x() - 2 * GraphLayout.COLUMN_WIDTH, box.y());
            problemNode[0] = editor.add(FunctionNode.Typed.bareConstant(1), at);
            editor.requestPan(PAN_MARGIN - at.x(), PAN_MARGIN + 40 - at.y());
            editor.requestSelect(problemNode[0]);
            return true;
        });
        this.step("grab node problem", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            if (editor == null || editor.buildError() == null || !Integer.valueOf(problemNode[0]).equals(editor.selected())) {
                return false;
            }
            SettingsProbe.grabFront("islands-09-node-problem.png");
            IslandEditor.ShownProblems shown = editor.shownProblems();
            LOGGER.info("Islands probe: the problem node {} is red {} with the texts {}, the general texts {}, the notice {}",
                    problemNode[0], shown.has(problemNode[0]), shown.of(problemNode[0]), shown.general(), shown.notice());
            if (!shown.has(problemNode[0]) || shown.of(editor.selected()).isEmpty() || !shown.notice()) {
                throw new IllegalStateException("Node problem check failed");
            }
            LOGGER.info("Islands probe: node problem check passed");
            return true;
        });
        int[] phase = new int[1];
        this.step("focus field", 1, () -> {
            IslandEditor editor = editor();
            PreviewScreen screen = previewScreen();
            if (editor == null || screen == null || editor.fieldsArea().width() <= 0) {
                return false;
            }
            IslandEditor.Area area = editor.fieldsArea();
            if (clickUntil(phase, area.x() + area.width() * 0.8, area.y() + 4, GlfwInput::leftButton,
                    () -> screen.tools().panel().wantsTextInput(), "Focus field failed: no click gave a text field the focus")) {
                LOGGER.info("Islands probe: a text field of the field editor has focus");
                return true;
            }
            return false;
        });
        this.step("grab field focus", EDITOR_WAIT, () -> {
            SettingsProbe.grabFront("islands-10-field-focus.png");
            GlfwInput.key(GLFW.GLFW_KEY_ESCAPE);
            return true;
        });
        this.step("grab escape in field", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            PreviewScreen screen = previewScreen();
            SettingsProbe.grabFront("islands-11-escape-in-field.png");
            boolean open = editor != null && editor.isOpen();
            boolean textInput = screen != null && screen.tools().panel().wantsTextInput();
            LOGGER.info("Islands probe: after Escape in a text field the screen is the preview {}, the editor is open {}, the text input {}",
                    screen != null, open, textInput);
            if (screen == null || !open || textInput) {
                throw new IllegalStateException("Escape in field check failed");
            }
            LOGGER.info("Islands probe: escape in field check passed");
            return true;
        });
        this.step("select other node", 1, () -> {
            IslandEditor editor = editor();
            RouterGraph router = editor == null ? null : router(editor);
            Integer other = router == null ? null : router.linked("final_density");
            if (other == null) {
                return false;
            }
            editor.requestSelect(other);
            return true;
        });
        this.step("grab other selected", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            if (editor == null || editor.selected() == null || editor.selected() == problemNode[0]) {
                return false;
            }
            SettingsProbe.grabFront("islands-12-other-selected.png");
            IslandEditor.ShownProblems shown = editor.shownProblems();
            LOGGER.info("Islands probe: with node {} selected the node texts are {}, the problem node stays red {}",
                    editor.selected(), shown.of(editor.selected()), shown.has(problemNode[0]));
            if (!shown.of(editor.selected()).isEmpty() || !shown.has(problemNode[0])) {
                throw new IllegalStateException("Other node check failed");
            }
            LOGGER.info("Islands probe: other node check passed");
            RouterGraph router = router(editor);
            if (router != null) {
                router.remove(problemNode[0]);
                editor.changed();
            }
            return true;
        });
    }

    /** The probe screen of the open islands preview, or null. */
    @Nullable
    private static PreviewScreen previewScreen() {
        return Minecraft.getInstance().screen instanceof PreviewScreen screen ? screen : null;
    }

    /** Drives the wheel through the GLFW callbacks over the canvas, the game view and the field editor. */
    private void queueWheel() {
        float[] before = new float[2];
        this.step("wheel canvas cursor", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            PreviewScreen screen = previewScreen();
            if (editor == null || screen == null || editor.canvasArea().width() <= 0 || editor.zoom() != 1) {
                return false;
            }
            before[0] = editor.zoom();
            before[1] = screen.camera().distance();
            GlfwInput.cursor(editor.canvasArea().centerX(), editor.canvasArea().centerY());
            return true;
        });
        this.step("wheel canvas", 2, () -> {
            GlfwInput.scroll(0, WHEEL_NOTCHES);
            return true;
        });
        this.step("grab wheel canvas", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            PreviewScreen screen = previewScreen();
            if (editor == null || screen == null) {
                return false;
            }
            SettingsProbe.grabFront("islands-15-wheel-canvas.png");
            float expected = CanvasZoom.wheel(before[0], WHEEL_NOTCHES);
            LOGGER.info("Islands probe: the wheel over the canvas gives the zoom {} (from {}, expected {}) and the camera distance {} (from {})",
                    editor.zoom(), before[0], expected, screen.camera().distance(), before[1]);
            boolean zoomed = Math.abs(editor.zoom() - expected) < ZOOM_TOLERANCE;
            boolean cameraKept = screen.camera().distance() == before[1];
            editor.requestZoom(1);
            if (!zoomed || !cameraKept) {
                throw new IllegalStateException("Wheel check (canvas) failed: zoom " + zoomed + ", camera kept " + cameraKept);
            }
            LOGGER.info("Islands probe: wheel check (canvas) passed");
            return true;
        });
        // The open editor covers the game view, so the game view wheel runs with the editor closed.
        this.step("wheel game view cursor", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            PreviewScreen screen = previewScreen();
            if (editor == null || screen == null || editor.zoom() != 1) {
                return false;
            }
            editor.close();
            before[0] = editor.zoom();
            before[1] = screen.camera().distance();
            GlfwInput.cursor(GAME_VIEW_POINT, GAME_VIEW_POINT);
            return true;
        });
        this.step("wheel game view", 2, () -> {
            GlfwInput.scroll(0, WHEEL_NOTCHES);
            return true;
        });
        this.step("check wheel game view", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            PreviewScreen screen = previewScreen();
            if (editor == null || screen == null) {
                return false;
            }
            LOGGER.info("Islands probe: the wheel over the game view gives the camera distance {} (from {}) and the zoom {} (from {})",
                    screen.camera().distance(), before[1], editor.zoom(), before[0]);
            boolean cameraZoomed = screen.camera().distance() != before[1];
            boolean zoomKept = editor.zoom() == before[0];
            editor.open();
            if (!cameraZoomed || !zoomKept) {
                throw new IllegalStateException("Wheel check (game view) failed: camera zoomed " + cameraZoomed + ", zoom kept " + zoomKept);
            }
            LOGGER.info("Islands probe: wheel check (game view) passed");
            return true;
        });
        this.step("wheel fields select", 1, () -> {
            IslandEditor editor = editor();
            if (editor == null) {
                return false;
            }
            if (editor.openEntry() == null) {
                editor.openReference(OVERWORLD_ISLANDS);
                return false;
            }
            Integer functionNode = islandsFunctionNode(editor);
            if (functionNode == null) {
                return false;
            }
            editor.requestSelect(functionNode);
            return true;
        });
        // The island fields fit the full window, so a drag of the resize grip makes the field editor short enough to scroll.
        float[] fullHeight = new float[1];
        this.step("resize cursor", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            if (editor == null || editor.fieldsArea().width() <= 0) {
                return false;
            }
            IslandEditor.Area area = editor.fieldsArea();
            fullHeight[0] = area.height();
            GlfwInput.cursor(area.x() + area.width() + GRIP_OFFSET, area.y() + area.height() + GRIP_OFFSET);
            return true;
        });
        this.step("resize press", 2, () -> {
            GlfwInput.leftButton(true);
            return true;
        });
        this.step("resize drag", 2, () -> {
            IslandEditor editor = editor();
            if (editor == null) {
                return false;
            }
            IslandEditor.Area area = editor.fieldsArea();
            GlfwInput.cursor(area.x() + area.width() + GRIP_OFFSET, area.y() + area.height() + GRIP_OFFSET - RESIZE_DRAG);
            return true;
        });
        this.step("resize release", 2, () -> {
            GlfwInput.leftButton(false);
            return true;
        });
        this.step("check resize", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            if (editor == null) {
                return false;
            }
            LOGGER.info("Islands probe: the resize grip drag gives the field editor {} (from the height {}) with the scroll maximum {}",
                    editor.fieldsArea(), fullHeight[0], editor.fieldsScrollMax());
            if (editor.fieldsArea().height() > fullHeight[0] - RESIZE_DRAG / 2) {
                throw new IllegalStateException("Resize check failed: the field editor height is " + editor.fieldsArea().height());
            }
            LOGGER.info("Islands probe: resize check passed");
            return true;
        });
        this.step("wheel fields cursor", 1, () -> {
            IslandEditor editor = editor();
            if (editor == null || editor.fieldsArea().width() <= 0) {
                return false;
            }
            LOGGER.info("Islands probe: the field editor is {} with the scroll {} of {}", editor.fieldsArea(), editor.fieldsScroll(), editor.fieldsScrollMax());
            before[0] = editor.fieldsScroll();
            GlfwInput.cursor(editor.fieldsArea().centerX(), editor.fieldsArea().centerY());
            return true;
        });
        this.step("wheel fields", 2, () -> {
            GlfwInput.scroll(0, -WHEEL_NOTCHES);
            return true;
        });
        this.step("grab wheel fields", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            if (editor == null) {
                return false;
            }
            SettingsProbe.grabFront("islands-16-wheel-fields.png");
            LOGGER.info("Islands probe: the wheel over the field editor gives the scroll {} of {} (from {})",
                    editor.fieldsScroll(), editor.fieldsScrollMax(), before[0]);
            if (editor.fieldsScrollMax() <= 0) {
                throw new IllegalStateException("Wheel check (field editor) failed: the fields fit the field editor, so it cannot scroll");
            }
            if (editor.fieldsScroll() <= before[0]) {
                throw new IllegalStateException("Wheel check (field editor) failed: the scroll did not change");
            }
            LOGGER.info("Islands probe: wheel check (field editor) passed");
            return true;
        });
        int[] width = new int[1];
        this.step("splitter cursor", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            PreviewScreen screen = previewScreen();
            if (editor == null || screen == null || editor.fieldsArea().width() <= 0) {
                return false;
            }
            width[0] = screen.tools().config().fieldsWidth();
            before[0] = editor.fieldsArea().width();
            GlfwInput.cursor(editor.fieldsArea().x() - SPLITTER_HALF, editor.fieldsArea().centerY());
            return true;
        });
        this.step("splitter press", 2, () -> {
            GlfwInput.leftButton(true);
            return true;
        });
        this.step("splitter drag", 2, () -> {
            IslandEditor editor = editor();
            if (editor == null) {
                return false;
            }
            GlfwInput.cursor(editor.fieldsArea().x() - SPLITTER_HALF - SPLITTER_DRAG, editor.fieldsArea().centerY());
            return true;
        });
        this.step("splitter release", 2, () -> {
            GlfwInput.leftButton(false);
            return true;
        });
        this.step("grab splitter", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            PreviewScreen screen = previewScreen();
            if (editor == null || screen == null) {
                return false;
            }
            SettingsProbe.grabFront("islands-17-splitter.png");
            int stored = screen.tools().config().fieldsWidth();
            LOGGER.info("Islands probe: the splitter drag of {} gives the field editor width {} (from {}) and the config width {} (from {})",
                    SPLITTER_DRAG, editor.fieldsArea().width(), before[0], stored, width[0]);
            screen.tools().config().setFieldsWidth(width[0]);
            if (Math.abs(stored - (before[0] + SPLITTER_DRAG)) > 1 || Math.abs(editor.fieldsArea().width() - stored) > 1) {
                throw new IllegalStateException("Splitter check failed: the config width is " + stored);
            }
            LOGGER.info("Islands probe: splitter check passed");
            editor.close();
            return true;
        });
        this.step("reopen", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            if (editor == null) {
                return false;
            }
            editor.open();
            return true;
        });
        this.step("check reopen margin", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            if (editor == null) {
                return false;
            }
            LOGGER.info("Islands probe: after a close and an open the field editor height is {} (at the first open {})",
                    editor.fieldsArea().height(), fullHeight[0]);
            if (Math.abs(editor.fieldsArea().height() - fullHeight[0]) > 1) {
                throw new IllegalStateException("Reopen check failed: the editor window did not return to its margin");
            }
            LOGGER.info("Islands probe: reopen check passed");
            editor.back();
            return true;
        });
    }

    /**
     * Escape closes the editor, then the preview. A second preview then edits the cell size, a router constant and the router box.
     * Its Done keeps them for the island pack.
     */
    private void queueEscape() {
        Minecraft minecraft = Minecraft.getInstance();
        this.step("escape editor", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            if (editor == null || !editor.isOpen()) {
                return false;
            }
            GlfwInput.key(GLFW.GLFW_KEY_ESCAPE);
            return true;
        });
        this.step("grab escape", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            SettingsProbe.grabFront("islands-18-escape.png");
            LOGGER.info("Islands probe: after Escape the screen is the preview {}, the editor is open {}", editor != null, editor != null && editor.isOpen());
            if (editor == null || editor.isOpen()) {
                throw new IllegalStateException("Escape check failed");
            }
            LOGGER.info("Islands probe: escape check passed");
            GlfwInput.key(GLFW.GLFW_KEY_ESCAPE);
            return true;
        });
        this.step("grab second escape", EDITOR_WAIT, () -> {
            SettingsProbe.grabFront("islands-19-second-escape.png");
            LOGGER.info("Islands probe: after the second Escape the screen is {}", minecraft.screen == null ? "none" : minecraft.screen.getClass().getSimpleName());
            if (!(minecraft.screen instanceof IslandsPresetScreen)) {
                throw new IllegalStateException("Second escape check failed");
            }
            LOGGER.info("Islands probe: second escape check passed");
            return true;
        });
        this.queuePreview("second ");
        this.step("field edit again", 1, () -> {
            IslandEditor editor = editor();
            if (editor == null) {
                return false;
            }
            editor.open();
            if (editor.openEntry() == null) {
                editor.openReference(OVERWORLD_ISLANDS);
                return false;
            }
            Integer functionNode = islandsFunctionNode(editor);
            if (functionNode == null) {
                return false;
            }
            editor.setField(functionNode, "cell_size", new FieldValue.Int(PROBE_CELL_SIZE));
            editor.back();
            return true;
        });
        this.step("router edit", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            RouterGraph router = editor == null ? null : router(editor);
            Integer spare = router == null ? null : router.linked(SPARE_OUTPUT);
            Sidecar.Position box = editor == null ? null : routerPositions(editor).get(GraphLayout.BOX);
            if (spare == null || box == null) {
                return false;
            }
            editor.setField(spare, "argument", new FieldValue.Number(SPARE_VALUE));
            // The open canvas writes its own positions back, so the box moves after the close.
            editor.close();
            this.savedBox = new Sidecar.Position(box.x() + ROUTER_BOX_SHIFT, box.y() + ROUTER_BOX_SHIFT);
            routerPositions(editor).put(GraphLayout.BOX, this.savedBox);
            LOGGER.info("Islands probe: the router box moves from {} to {}, and {} becomes {}", box, this.savedBox, SPARE_OUTPUT, SPARE_VALUE);
            return true;
        });
        this.step("preview done", 1, () -> minecraft.screen instanceof PreviewScreen && SettingsProbe.press(minecraft.screen, "gui.done"));
    }

    /** The dimension cycle button of the Islands screen, or null. */
    @Nullable
    private static CycleButton<?> dimensionButton(IslandsPresetScreen screen) {
        return SettingsProbe.widgets(screen, CycleButton.class).stream().findFirst().orElse(null);
    }

    /** The dimension cycle button picks the router graph of the sliders. */
    private void queueDropdown() {
        Minecraft minecraft = Minecraft.getInstance();
        this.step("grab dimension overworld", 10, () -> {
            if (!(minecraft.screen instanceof IslandsPresetScreen screen)) {
                return false;
            }
            CycleButton<?> button = dimensionButton(screen);
            if (button == null) {
                throw new IllegalStateException("Dimension check failed: the Islands screen has no dimension cycle button");
            }
            SettingsProbe.grabFront("islands-20-dimension-overworld.png");
            LOGGER.info("Islands probe: the dimension cycle button shows {} with the cell size {}", button.getValue(), screen.settings().cellSize());
            if (!Level.OVERWORLD.equals(button.getValue()) || screen.settings().cellSize() != PROBE_CELL_SIZE) {
                throw new IllegalStateException("Dimension check failed: the screen starts on " + button.getValue());
            }
            button.onPress();
            return true;
        });
        this.step("grab dimension nether", 10, () -> {
            if (!(minecraft.screen instanceof IslandsPresetScreen screen)) {
                return false;
            }
            CycleButton<?> button = dimensionButton(screen);
            if (button == null || !Level.NETHER.equals(button.getValue())) {
                return false;
            }
            SettingsProbe.grabFront("islands-21-dimension-nether.png");
            IslandSettings expected = shippedIslands(NETHER_ISLANDS);
            LOGGER.info("Islands probe: on the Nether the sliders show {}, the shipped Nether entry holds {}", screen.settings(), expected);
            if (!screen.settings().equals(expected) || screen.settings().cellSize() == PROBE_CELL_SIZE) {
                throw new IllegalStateException("Dimension check failed: the sliders do not show the Nether");
            }
            LOGGER.info("Islands probe: dimension check passed");
            return true;
        });
        Object[] pressed = new Object[1];
        this.step("dimension back", 2, () -> {
            if (!(minecraft.screen instanceof IslandsPresetScreen screen)) {
                return false;
            }
            CycleButton<?> button = dimensionButton(screen);
            if (button == null || button == pressed[0]) {
                return false;
            }
            if (Level.OVERWORLD.equals(button.getValue())) {
                if (screen.settings().cellSize() != PROBE_CELL_SIZE) {
                    throw new IllegalStateException("Dimension check failed: back on the Overworld the cell size is " + screen.settings().cellSize());
                }
                return true;
            }
            // A press rebuilds the rows on the next tick, so each press waits for a new button.
            pressed[0] = button;
            button.onPress();
            return false;
        });
    }

    /** The island settings of a density function entry of the creation context of the flow. */
    private IslandSettings shippedIslands(ResourceLocation id) {
        CreateWorldScreen screen = this.createScreen;
        if (screen == null) {
            throw new IllegalStateException("The flow has no create world screen");
        }
        DensityFunction function = screen.getUiState().getSettings().worldgenLoadContext().registryOrThrow(Registries.DENSITY_FUNCTION).get(id);
        if (!(unwrap(function) instanceof VoronoiIslands islands)) {
            throw new IllegalStateException("The entry " + id + " is not an island density function");
        }
        return IslandSettings.of(islands);
    }

    /** A reference to a missing entry fails the vanilla reload. The old island pack then returns. */
    private void queueReloadFailure() {
        Minecraft minecraft = Minecraft.getInstance();
        List<Map<String, JsonElement>> before = new ArrayList<>();
        this.step("failure pack before", 1, () -> {
            if (!(minecraft.screen instanceof CreateWorldScreen screen)) {
                return false;
            }
            before.clear();
            before.add(packFiles(tempPack(screen)));
            LOGGER.info("Islands probe: before the failing Done the temporary island pack holds {}", before.getFirst().keySet());
            return !before.getFirst().isEmpty();
        });
        this.queueCustomize("failure ");
        this.step("failure preview", 10, () -> minecraft.screen instanceof IslandsPresetScreen
                && SettingsProbe.press(minecraft.screen, "createWorld.customize.aerolite.preview"));
        this.step("failure missing reference", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            RouterGraph router = editor == null ? null : router(editor);
            Integer spare = router == null ? null : router.linked(SPARE_OUTPUT);
            if (spare == null) {
                return false;
            }
            router.replace(spare, new FunctionNode.Reference(MISSING_ENTRY));
            editor.changed();
            LOGGER.info("Islands probe: the router output {} now names the missing entry {}", SPARE_OUTPUT, MISSING_ENTRY);
            return true;
        });
        this.step("failure preview done", 1, () -> minecraft.screen instanceof PreviewScreen && SettingsProbe.press(minecraft.screen, "gui.done"));
        this.step("failure settings done", 10, () -> minecraft.screen instanceof IslandsPresetScreen && SettingsProbe.press(minecraft.screen, "gui.done"));
        this.step("grab reload failed", 10, () -> {
            if (!(minecraft.screen instanceof ConfirmScreen screen) || !hasKey(screen.getTitle(), "dataPack.validation.failed")) {
                return false;
            }
            SettingsProbe.grabFront("islands-22-reload-failed.png");
            LOGGER.info("Islands probe: the reload with the missing entry shows the vanilla failure screen");
            return SettingsProbe.press(screen, "dataPack.validation.back");
        });
        this.step("grab reload returned", 10, () -> {
            if (!(minecraft.screen instanceof CreateWorldScreen screen)) {
                return false;
            }
            SettingsProbe.grabFront("islands-23-reload-returned.png");
            Map<String, JsonElement> after = packFiles(tempPack(screen));
            List<String> enabled = screen.getUiState().getSettings().dataConfiguration().dataPacks().getEnabled();
            LOGGER.info("Islands probe: after the failed reload the temporary island pack holds {}, the enabled data packs are {}", after.keySet(), enabled);
            if (!after.equals(before.getFirst()) || !enabled.contains(IslandPack.PACK_ID)) {
                throw new IllegalStateException("Reload failure check failed: the old island pack files did not return");
            }
            LOGGER.info("Islands probe: reload failure check passed");
            return true;
        });
        // No rule picks the graphs of the holder after a failed reload, so this step only logs them.
        this.queueCustomize("failure graphs ");
        this.step("failure graphs preview", 10, () -> minecraft.screen instanceof IslandsPresetScreen
                && SettingsProbe.press(minecraft.screen, "createWorld.customize.aerolite.preview"));
        this.step("failure graphs", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            RouterGraph router = editor == null ? null : router(editor);
            Integer spare = router == null ? null : router.linked(SPARE_OUTPUT);
            if (spare == null) {
                return false;
            }
            FunctionNode node = router.functionNode(spare);
            LOGGER.info("Islands probe: after the failed reload the graph holder links {} to {}, the graph of the failed Done {}",
                    SPARE_OUTPUT, node, node instanceof FunctionNode.Reference reference && reference.entry().equals(MISSING_ENTRY));
            return SettingsProbe.press(minecraft.screen, "gui.cancel");
        });
        this.step("failure graphs cancel", 10, () -> minecraft.screen instanceof IslandsPresetScreen && SettingsProbe.press(minecraft.screen, "gui.cancel"));
    }

    /** Recreate of the world restores the preset and the graphs. An entry edited back to its start JSON writes no file. */
    private void queueRecreate() {
        Minecraft minecraft = Minecraft.getInstance();
        this.step("leave world", 10, () -> {
            if (minecraft.level == null) {
                return false;
            }
            minecraft.level.disconnect();
            minecraft.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
            minecraft.setScreen(new TitleScreen());
            return true;
        });
        this.step("singleplayer", 10, () -> minecraft.screen instanceof TitleScreen && SettingsProbe.press(minecraft.screen, "menu.singleplayer"));
        this.step("recreate", 10, () -> {
            if (!(minecraft.screen instanceof SelectWorldScreen screen)) {
                return false;
            }
            // The world list sorts the most recently played world first.
            WorldSelectionList.WorldListEntry entry = SettingsProbe.widgets(screen, WorldSelectionList.WorldListEntry.class).stream()
                    .filter(candidate -> WORLD_NAME.equals(candidate.getLevelName())).findFirst().orElse(null);
            List<WorldSelectionList> lists = SettingsProbe.widgets(screen, WorldSelectionList.class);
            if (entry == null || lists.isEmpty()) {
                return false;
            }
            lists.getFirst().setSelected(entry);
            return SettingsProbe.press(screen, "selectWorld.recreate");
        });
        // The World tab shows the world type in the capture.
        this.step("recreate world tab", 10, () -> {
            if (!(minecraft.screen instanceof CreateWorldScreen screen)) {
                return false;
            }
            SettingsProbe.widgets(screen, TabNavigationBar.class).getFirst().selectTab(1, false);
            return true;
        });
        this.step("grab recreate", 10, () -> {
            if (!(minecraft.screen instanceof CreateWorldScreen screen)) {
                return false;
            }
            this.createScreen = screen;
            SettingsProbe.grabFront("islands-26-recreate.png");
            Holder<WorldPreset> preset = screen.getUiState().getWorldType().preset();
            LOGGER.info("Islands probe: the recreated world has the world type {} and the temporary data packs {}",
                    preset == null ? "none" : preset.unwrapKey().map(key -> key.location().toString()).orElse("direct"), folderNames(screen.tempDataPackDir));
            if (preset == null || !preset.is(AeroliteWorldgen.ISLANDS_PRESET)) {
                throw new IllegalStateException("Recreate check failed: the world type is not Aerolite Islands");
            }
            LOGGER.info("Islands probe: recreate check passed");
            return true;
        });
        this.queueCustomize("recreate ");
        this.queuePreview("recreate ");
        this.step("recreate editor open", 1, () -> {
            IslandEditor editor = editor();
            Sidecar.Position saved = this.savedBox;
            if (editor == null || saved == null) {
                return false;
            }
            editor.open();
            editor.requestPan(PAN_MARGIN - saved.x(), PAN_MARGIN - saved.y());
            return true;
        });
        int[] startCellSize = new int[1];
        this.step("grab recreate editor", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            RouterGraph router = editor == null ? null : router(editor);
            if (router == null || editor.canvasArea().width() <= 0) {
                return false;
            }
            SettingsProbe.grabFront("islands-27-recreate-editor.png");
            Sidecar.Position box = routerPositions(editor).get(GraphLayout.BOX);
            Sidecar.Position saved = this.savedBox;
            Integer spare = router.linked(SPARE_OUTPUT);
            FieldValue spareValue = spare != null && router.functionNode(spare) instanceof FunctionNode.Typed typed ? typed.values().get("argument") : null;
            JsonElement start = editor.graphs().startJson(OVERWORLD_ISLANDS);
            Integer startCell = start == null ? null : jsonCellSize(start);
            editor.openReference(OVERWORLD_ISLANDS);
            Integer restoredCell = shownCellSize(editor);
            editor.back();
            LOGGER.info("Islands probe: the recreated router box sits at {} (saved {}), {} holds {}, the entry has the cell size {} and the start cell size {}",
                    box, saved, SPARE_OUTPUT, spareValue, restoredCell, startCell);
            boolean boxKept = box != null && saved != null && Math.abs(box.x() - saved.x()) < POSITION_TOLERANCE && Math.abs(box.y() - saved.y()) < POSITION_TOLERANCE;
            boolean spareKept = spareValue instanceof FieldValue.Number number && number.value() == SPARE_VALUE;
            if (!boxKept || !spareKept || restoredCell == null || restoredCell != PROBE_CELL_SIZE || startCell == null || startCell == PROBE_CELL_SIZE) {
                throw new IllegalStateException("Recreate editor check failed: box " + boxKept + ", router constant " + spareKept
                        + ", cell size " + restoredCell + ", start cell size " + startCell);
            }
            LOGGER.info("Islands probe: recreate editor check passed");
            startCellSize[0] = startCell;
            return true;
        });
        this.step("recreate revert entry", 1, () -> {
            IslandEditor editor = editor();
            if (editor == null) {
                return false;
            }
            if (editor.openEntry() == null) {
                editor.openReference(OVERWORLD_ISLANDS);
                return false;
            }
            Integer functionNode = islandsFunctionNode(editor);
            if (functionNode == null) {
                return false;
            }
            editor.setField(functionNode, "cell_size", new FieldValue.Int(startCellSize[0]));
            editor.back();
            editor.close();
            return true;
        });
        this.step("recreate preview done", EDITOR_WAIT, () -> minecraft.screen instanceof PreviewScreen && SettingsProbe.press(minecraft.screen, "gui.done"));
        this.step("recreate settings done", 10, () -> minecraft.screen instanceof IslandsPresetScreen && SettingsProbe.press(minecraft.screen, "gui.done"));
        this.step("check start json", 10, () -> {
            if (!(minecraft.screen instanceof CreateWorldScreen screen)) {
                return false;
            }
            Set<String> files = packFiles(tempPack(screen)).keySet();
            LOGGER.info("Islands probe: after Done with the entry at its start JSON the temporary island pack holds {}", files);
            boolean entryGone = ENTRY_FILES.stream().noneMatch(files::contains);
            boolean routerKept = files.contains(IslandPack.routerSidecarPath(Level.OVERWORLD))
                    && files.contains(IslandPack.entryPath(Registries.NOISE_SETTINGS, OVERWORLD_ISLANDS));
            if (!entryGone || !routerKept) {
                throw new IllegalStateException("Start JSON check failed: entry files gone " + entryGone + ", router files kept " + routerKept);
            }
            LOGGER.info("Islands probe: start JSON check passed");
            return true;
        });
    }

    private static boolean hasKey(Component text, String key) {
        return text.getContents() instanceof TranslatableContents contents && contents.getKey().equals(key);
    }

    /** The island pack folder in the temporary data pack folder of a create world screen. */
    private static Path tempPack(CreateWorldScreen screen) {
        Path folder = screen.tempDataPackDir;
        if (folder == null) {
            throw new IllegalStateException("The create world screen has no temporary data pack folder");
        }
        return folder.resolve(IslandPack.NAME);
    }

    private static List<String> folderNames(@Nullable Path folder) {
        if (folder == null) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(folder)) {
            return children.map(path -> path.getFileName().toString()).sorted().toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    /** The JSON files of a pack folder by path inside the pack. A missing folder gives none. */
    private static Map<String, JsonElement> packFiles(Path pack) {
        Map<String, JsonElement> files = new TreeMap<>();
        if (!Files.isDirectory(pack)) {
            return files;
        }
        try (Stream<Path> paths = Files.walk(pack)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                files.put(pack.relativize(path).toString().replace('\\', '/'), JsonParser.parseString(Files.readString(path)));
            }
        } catch (IOException | JsonParseException exception) {
            throw new IllegalStateException("The pack " + pack + " does not read", exception);
        }
        return files;
    }

    /** The cell size of the first {@code aerolite:voronoi_islands} object of a JSON tree, without a registry. */
    @Nullable
    private static Integer jsonCellSize(JsonElement json) {
        if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            if (object.has("type") && "aerolite:voronoi_islands".equals(object.get("type").getAsString())) {
                return object.get("cell_size").getAsInt();
            }
            for (Map.Entry<String, JsonElement> field : object.entrySet()) {
                Integer found = jsonCellSize(field.getValue());
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** The router graph of the previewed dimension, or null. */
    @Nullable
    private static RouterGraph router(IslandEditor editor) {
        return editor.dimension() == null ? null : editor.graphs().router(editor.dimension());
    }

    /** The canvas positions of the router graph of the previewed dimension. */
    private static Map<Integer, Sidecar.Position> routerPositions(IslandEditor editor) {
        return editor.dimension() == null ? Map.of() : editor.graphs().positions(new IslandGraphs.GraphId.Router(editor.dimension()));
    }

    private boolean teleport(double x, double y, double z, VoronoiLattice.Cell facing) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        minecraft.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.1f %.1f %.1f facing %.1f %.1f %.1f",
                x, y, z, facing.siteX(), facing.siteY(), facing.siteZ()));
        return true;
    }

    /** Pack check (a): the world holds the island pack with the expected files. */
    private static void checkPackFolder(MinecraftServer server) {
        Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(IslandPack.NAME);
        Set<String> files = packFiles(pack).keySet();
        LOGGER.info("Islands probe: the island pack {} holds {}", pack, files);
        if (!files.containsAll(PACK_FILES)) {
            throw new IllegalStateException("Pack check (a) failed: the island pack lacks one of " + PACK_FILES);
        }
        LOGGER.info("Islands probe: pack check (a) passed");
    }

    /** Pack check (b): the router of the Overworld noise settings of the server reaches an island function of the probe cell size. */
    private static void checkRegistries(MinecraftServer server) {
        RegistryAccess registries = server.registryAccess();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        NoiseGeneratorSettings settings = registries.registryOrThrow(Registries.NOISE_SETTINGS).get(OVERWORLD_ISLANDS);
        if (settings == null) {
            throw new IllegalStateException("Pack check (b) failed: the server has no noise settings " + OVERWORLD_ISLANDS);
        }
        JsonElement router = NoiseGeneratorSettings.DIRECT_CODEC.encodeStart(ops, settings).getOrThrow().getAsJsonObject().get("noise_router");
        Integer cellSize = cellSize(router, registries, ops, new HashSet<>());
        if (cellSize == null || cellSize != PROBE_CELL_SIZE) {
            throw new IllegalStateException("Pack check (b) failed: the router of " + OVERWORLD_ISLANDS + " reaches the cell size " + cellSize);
        }
        LOGGER.info("Islands probe: pack check (b) passed: the router of {} reaches voronoi_islands with cell_size {}", OVERWORLD_ISLANDS, cellSize);
    }

    /** The cell size of the first {@code aerolite:voronoi_islands} object in the JSON. A string follows its density function entry. */
    @Nullable
    private static Integer cellSize(JsonElement json, RegistryAccess registries, RegistryOps<JsonElement> ops, Set<String> visited) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString() && visited.add(json.getAsString())) {
            ResourceLocation id = ResourceLocation.tryParse(json.getAsString());
            DensityFunction entry = id == null ? null : registries.registryOrThrow(Registries.DENSITY_FUNCTION).get(id);
            return entry == null ? null : DensityFunction.DIRECT_CODEC.encodeStart(ops, entry).result()
                    .map(entryJson -> cellSize(entryJson, registries, ops, visited)).orElse(null);
        }
        if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            if (object.has("type") && "aerolite:voronoi_islands".equals(object.get("type").getAsString())) {
                return object.get("cell_size").getAsInt();
            }
            for (Map.Entry<String, JsonElement> field : object.entrySet()) {
                Integer found = cellSize(field.getValue(), registries, ops, visited);
                if (found != null) {
                    return found;
                }
            }
        }
        if (json.isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray()) {
                Integer found = cellSize(element, registries, ops, visited);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** A density function without its holders and markers. */
    @Nullable
    private static DensityFunction unwrap(@Nullable DensityFunction function) {
        while (true) {
            if (function instanceof DensityFunctions.HolderHolder holder) {
                function = holder.function().value();
            } else if (function instanceof DensityFunctions.MarkerOrMarked marker) {
                function = marker.wrapped();
            } else {
                return function;
            }
        }
    }

    /** The island density function of the Overworld of the server. */
    private static VoronoiIslands islands(MinecraftServer server) {
        if (!(unwrap(server.overworld().getChunkSource().randomState().router().finalDensity()) instanceof VoronoiIslands islands)) {
            throw new IllegalStateException("The final density of the Overworld is not the island density function");
        }
        return islands;
    }

    /** The first island of the row 0 along X from the origin. */
    private static VoronoiLattice.Cell firstIsland(MinecraftServer server) {
        VoronoiIslands islands = islands(server);
        for (int ix = 0; ix < 16; ix++) {
            VoronoiLattice.Cell cell = islands.lattice().cell(ix, 0, 0);
            if (islands.hasIsland(cell)) {
                return cell;
            }
        }
        throw new IllegalStateException("No island in the first 16 cells");
    }

    public void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!this.started) {
            if (minecraft.screen != null && minecraft.getOverlay() == null && ++this.ticks >= TITLE_WAIT) {
                this.started = true;
                this.ticks = 0;
                LOGGER.info("Islands probe: start");
            }
            return;
        }
        if (this.index >= this.steps.size()) {
            return;
        }
        Step step = this.steps.get(this.index);
        if (++this.ticks < step.delay()) {
            return;
        }
        boolean done;
        try {
            done = step.action().getAsBoolean();
        } catch (RuntimeException exception) {
            LOGGER.error("Islands probe: step {} failed", step.name(), exception);
            done = true;
        }
        if (!done && ++this.waited < STEP_LIMIT) {
            return;
        }
        if (!done) {
            LOGGER.error("Islands probe: step {} timed out", step.name());
        }
        this.index++;
        this.ticks = 0;
        this.waited = 0;
    }

}
