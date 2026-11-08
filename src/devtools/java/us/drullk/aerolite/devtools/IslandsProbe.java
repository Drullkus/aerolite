package us.drullk.aerolite.devtools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import us.drullk.aerolite.client.IslandsPresetScreen;
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
import us.drullk.aerolite.world.island.graph.IslandSettingsFunctionNode;
import us.drullk.aerolite.world.island.graph.RouterGraph;

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
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/**
 * The screenshot probe of the Islands preset. The system property {@value #PROPERTY} turns it on.
 * After the first screen shows, it picks the Islands preset, opens its customize screen and saves it.
 * It opens the islands preview at the radius {@value #PREVIEW_RADIUS}, waits for the build and saves it.
 * Then it saves the island editor: the router graph of the Overworld, the function graph of the Overworld island entry,
 * the palette, the field editor of the selected island function node after a field edit, and the red error of a router constant out of range.
 * It keeps the cell size {@value #PROBE_CELL_SIZE} of the field edit and undoes the router edit.
 * Done in the preview and in the Islands screen write the island pack and reload the world creation context.
 * Then it creates a creative world of that preset with the seed {@value #SEED} and commands on.
 * It accepts the experimental features warning.
 * In the new world it checks the island pack folder, the cell size of the server registries and the cell size of the first island.
 * It puts the player in spectator mode at the first island of the row 0 and saves a side view and a top view
 * under {@code screenshots/} of the game directory. Then it stops the game.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 */
public final class IslandsProbe {

    public static final String PROPERTY = "aerolite.devtools.islandsProbe";
    public static final String SEED = "1234";
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
    /** The canvas point of the palette. */
    private static final float PALETTE_X = 40;
    private static final float PALETTE_Y = 40;
    /** The cell size of the field edit. The edit stays through Done, so the island pack carries it. */
    private static final int PROBE_CELL_SIZE = 384;
    /** The camera pitch of the editor captures. The editor window covers the low half of the game view. */
    private static final float EDITOR_PITCH = 70;
    /** The canvas panning that shows the final density row of the router box. */
    private static final float ROUTER_PAN_Y = -240;
    /** A constant outside the vanilla range of a router output. */
    private static final double OUT_OF_RANGE = 2000000;

    /** The files that the island pack of the probe holds. */
    private static final List<String> PACK_FILES = List.of(
            IslandPack.PACK_META,
            IslandPack.entryPath(Registries.DENSITY_FUNCTION, OVERWORLD_ISLANDS),
            IslandPack.entrySidecarPath(OVERWORLD_ISLANDS));

    private record Step(String name, int delay, BooleanSupplier action) {
    }

    private final List<Step> steps = new ArrayList<>();
    private int ticks;
    private int index;
    private int waited;
    private boolean started;
    @Nullable
    private VoronoiLattice.Cell island;

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
            WorldCreationUiState state = screen.getUiState();
            WorldCreationUiState.WorldTypeEntry islands = state.getNormalPresetList().stream()
                    .filter(entry -> entry.preset() != null && entry.preset().is(AeroliteWorldgen.ISLANDS_PRESET))
                    .findFirst().orElseThrow(() -> new IllegalStateException("The Islands preset is not in the normal list"));
            state.setWorldType(islands);
            state.setSeed(SEED);
            state.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            state.setAllowCommands(true);
            state.setName("Islands probe");
            return true;
        });
        // The Customize button sits in the World tab.
        this.step("world tab", 1, () -> {
            if (!(minecraft.screen instanceof CreateWorldScreen screen)) {
                return false;
            }
            SettingsProbe.widgets(screen, TabNavigationBar.class).getFirst().selectTab(1, false);
            return true;
        });
        this.step("customize", 10, () -> SettingsProbe.press(minecraft.screen, "selectWorld.customizeType"));
        this.step("grab settings", 10, () -> {
            if (!(minecraft.screen instanceof IslandsPresetScreen)) {
                return false;
            }
            SettingsProbe.grabFront("islands-00-settings.png");
            return true;
        });
        this.step("preview", 1, () -> SettingsProbe.press(minecraft.screen, "createWorld.customize.aerolite.preview"));
        this.step("preview radius", 10, () -> {
            if (!(minecraft.screen instanceof PreviewScreen screen)) {
                return false;
            }
            screen.setRadius(PREVIEW_RADIUS);
            LOGGER.info("Islands probe: preview build start");
            return true;
        });
        this.step("preview built", 20, () -> minecraft.screen instanceof PreviewScreen screen && screen.progress().phase() == PreviewBuilder.Phase.DONE);
        this.step("grab preview", 10, () -> {
            LOGGER.info("Islands probe: preview build done");
            SettingsProbe.grabFront("islands-01-preview.png");
            return true;
        });
        this.queueEditor();
        this.step("preview done", 1, () -> minecraft.screen instanceof PreviewScreen && SettingsProbe.press(minecraft.screen, "gui.done"));
        this.step("settings done", 10, () -> minecraft.screen instanceof IslandsPresetScreen && SettingsProbe.press(minecraft.screen, "gui.done"));
        this.step("pack reloaded", 10, () -> {
            if (!(minecraft.screen instanceof CreateWorldScreen screen)) {
                return false;
            }
            List<String> enabled = screen.getUiState().getSettings().dataConfiguration().dataPacks().getEnabled();
            LOGGER.info("Islands probe: the enabled data packs after Done are {}", enabled);
            return enabled.contains(IslandPack.PACK_ID);
        });
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
            SettingsProbe.grabFront("islands-02-side.png");
            return true;
        });
        this.step("top view", 1, () -> {
            VoronoiLattice.Cell cell = this.island;
            return this.teleport(cell.siteX() + 1, cell.siteY() + TOP_HEIGHT, cell.siteZ() + 1, cell);
        });
        this.step("grab top", CHUNK_WAIT, () -> {
            SettingsProbe.grabFront("islands-03-top.png");
            return true;
        });
        this.step("stop", 10, () -> {
            minecraft.stop();
            return true;
        });
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

    private void queueEditor() {
        float[] pitch = new float[1];
        this.step("editor camera", 1, () -> {
            if (!(Minecraft.getInstance().screen instanceof PreviewScreen screen)) {
                return false;
            }
            pitch[0] = screen.camera().pitch();
            screen.camera().setPitch(EDITOR_PITCH);
            return true;
        });
        this.step("grab editor router", EDITOR_WAIT, () -> {
            if (editor() == null) {
                return false;
            }
            SettingsProbe.grabFront("islands-04-editor-router.png");
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
            SettingsProbe.grabFront("islands-05-function-graph.png");
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
            SettingsProbe.grabFront("islands-06-palette.png");
            IslandEditor editor = editor();
            if (editor != null) {
                editor.requestClosePalette();
            }
            return true;
        });
        int[] cellSize = new int[1];
        this.step("select island function node", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            Integer functionNode = editor == null ? null : islandsFunctionNode(editor);
            FunctionNodeGraph graph = editor == null ? null : editor.graph();
            if (functionNode == null || graph == null || !(graph.functionNode(functionNode) instanceof FunctionNode.Typed typed)
                    || !(typed.values().get("cell_size") instanceof FieldValue.Int current)) {
                return false;
            }
            cellSize[0] = current.value();
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
        this.step("break the router", 1, () -> {
            IslandEditor editor = editor();
            if (editor == null) {
                return false;
            }
            editor.back();
            editor.requestPan(0, ROUTER_PAN_Y);
            editor.setRouterOutput("final_density", new RouterGraph.Constant(OUT_OF_RANGE));
            return editor.buildError() != null;
        });
        this.step("grab error", EDITOR_WAIT, () -> {
            SettingsProbe.grabFront("islands-08-build-error.png");
            IslandEditor editor = editor();
            if (editor != null) {
                editor.setRouterOutput("final_density", new RouterGraph.DimensionDefault());
            }
            return true;
        });
        this.step("editor restored", EDITOR_WAIT, () -> {
            IslandEditor editor = editor();
            LOGGER.info("Islands probe: the editor build error after the restore is {}", editor == null ? "absent" : editor.buildError());
            if (Minecraft.getInstance().screen instanceof PreviewScreen screen) {
                screen.camera().setPitch(pitch[0]);
            }
            return editor != null && editor.buildError() == null;
        });
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
        List<String> files;
        try (Stream<Path> paths = Files.walk(pack)) {
            files = paths.filter(Files::isRegularFile).map(path -> pack.relativize(path).toString().replace('\\', '/')).sorted().toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Pack check (a) failed: the folder " + pack + " does not list", exception);
        }
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

    /** The island density function of the Overworld of the server. */
    private static VoronoiIslands islands(MinecraftServer server) {
        DensityFunction function = server.overworld().getChunkSource().randomState().router().finalDensity();
        while (true) {
            if (function instanceof DensityFunctions.HolderHolder holder) {
                function = holder.function().value();
            } else if (function instanceof DensityFunctions.MarkerOrMarked marker) {
                function = marker.wrapped();
            } else {
                break;
            }
        }
        if (!(function instanceof VoronoiIslands islands)) {
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
