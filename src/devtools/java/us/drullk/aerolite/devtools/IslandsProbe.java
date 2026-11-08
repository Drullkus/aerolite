package us.drullk.aerolite.devtools;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import us.drullk.aerolite.client.IslandsPresetScreen;
import us.drullk.aerolite.client.preview.PreviewBuilder;
import us.drullk.aerolite.client.preview.PreviewScreen;
import us.drullk.aerolite.world.AeroliteWorldgen;
import us.drullk.aerolite.world.island.VoronoiIslands;
import us.drullk.aerolite.world.island.VoronoiLattice;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * The screenshot probe of the Islands preset. The system property {@value #PROPERTY} turns it on.
 * After the first screen shows, it picks the Islands preset, opens its customize screen and saves it.
 * It opens the islands preview at the radius {@value #PREVIEW_RADIUS}, waits for the build and saves it.
 * Then it creates a creative world of that preset with the seed {@value #SEED} and commands on.
 * It accepts the experimental features warning.
 * It puts the player in spectator mode at the first island of the row 0 and saves a side view and a top view
 * under {@code screenshots/} of the game directory. Then it stops the game.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
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
        this.step("preview done", 1, () -> minecraft.screen instanceof PreviewScreen && SettingsProbe.press(minecraft.screen, "gui.done"));
        this.step("settings done", 10, () -> minecraft.screen instanceof IslandsPresetScreen && SettingsProbe.press(minecraft.screen, "gui.done"));
        this.step("create", 10, () -> SettingsProbe.press(minecraft.screen, "selectWorld.create"));
        // A world of a data pack preset shows the experimental features warning before it loads.
        this.step("confirm experimental", 10, () -> minecraft.level != null || minecraft.screen instanceof ConfirmScreen && SettingsProbe.press(minecraft.screen, "gui.yes"));
        this.step("world loaded", 20, () -> minecraft.level != null && minecraft.player != null && minecraft.screen == null && minecraft.getSingleplayerServer() != null);
        this.step("find island", 1, () -> {
            this.island = firstIsland(minecraft.getSingleplayerServer());
            LOGGER.info("Islands probe: the first island sits at {} {} {}", this.island.siteX(), this.island.siteY(), this.island.siteZ());
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

    private boolean teleport(double x, double y, double z, VoronoiLattice.Cell facing) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        minecraft.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.1f %.1f %.1f facing %.1f %.1f %.1f",
                x, y, z, facing.siteX(), facing.siteY(), facing.siteZ()));
        return true;
    }

    /** The first island of the row 0 along X from the origin. */
    private static VoronoiLattice.Cell firstIsland(MinecraftServer server) {
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
