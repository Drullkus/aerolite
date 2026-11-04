package us.drullk.aerolite.devtools;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.InclusiveRange;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.DualNoiseProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.NoiseProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.NoiseThresholdProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.RandomizedIntStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.RotatedBlockProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.WeightedStateProvider;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import us.drullk.aerolite.client.AerolitePresetScreen;
import us.drullk.aerolite.client.ArrowButton;
import us.drullk.aerolite.client.ChoiceDropdown;
import us.drullk.aerolite.client.DimensionSettingsScreen;
import us.drullk.aerolite.client.PresetEdits;
import us.drullk.aerolite.client.PresetSliders;
import us.drullk.aerolite.client.SettingsList;
import us.drullk.aerolite.client.preview.PreviewBuilder;
import us.drullk.aerolite.client.preview.PreviewScreen;
import us.drullk.aerolite.client.provider.ProviderScreen;
import us.drullk.aerolite.client.provider.ProviderType;
import us.drullk.aerolite.client.provider.VariationBlock;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.DimensionSettings;
import us.drullk.aerolite.world.PillarShape;
import us.drullk.aerolite.world.ShaftPlacement;
import us.drullk.aerolite.world.ShaftVariation;
import us.drullk.aerolite.world.WeightedVariation;
import us.drullk.aerolite.world.pattern.ChaosSettings;
import us.drullk.aerolite.world.pattern.MazeSettings;
import us.drullk.aerolite.world.pattern.PatternType;
import us.drullk.aerolite.world.pattern.TilesSettings;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * The screenshot probe of the settings screens. The system property {@value #PROPERTY} turns it on.
 * After the first screen shows, it opens the create world screen, the pillars screen with the defaults,
 * the dimension screen of the Overworld on the tab Pillar Variations with each pattern type picked in turn, and the preview with the panel.
 * On the tab Pillar Variations it also opens the provider screen of each block field with one provider type each.
 * The last preview state shows the panel at its narrowest width. The run keeps that width only until the next resize.
 * The last steps open the provider tabs of the preview panel, one per provider type.
 * Then the shape scenes show an I-beam and a Bridge with columns.
 * It saves one PNG per state from the window front buffer under {@code screenshots/} of the game directory. Then it stops the game.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class SettingsProbe {

    public static final String PROPERTY = "aerolite.devtools.settingsProbe";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "createWorld.customize.aerolite.";
    private static final int TITLE_WAIT = 40;
    private static final int RENDER_WAIT = 10;
    /** The longest wait of one step before the probe gives up on it. */
    private static final int STEP_LIMIT = 200;
    /** The probe window size in screen coordinates and its GUI scale. */
    private static final int WINDOW_WIDTH = 1200;
    private static final int WINDOW_HEIGHT = 860;
    private static final int GUI_SCALE = 2;
    /** The list scroll that shows the lower rows of the first variation. */
    private static final int LOWER_ROWS = 170;
    /** The wheel steps that scroll the panel to the block fields of the first variation. */
    private static final int PANEL_SCROLL = 6;
    /** The narrowest panel width in ImGui units. */
    private static final int NARROW_PANEL = 240;
    /** The height of one entry of an open combo list. */
    private static final int ENTRY_HEIGHT = 14;
    /** The count of block fields of a Bridge variation. */
    private static final int BRIDGE_FIELDS = 6;
    /** The panel width of the editor steps in ImGui units. */
    private static final int EDITOR_PANEL = 360;
    /** The wheel steps from the tab bar to the lower rows of a tall editor. */
    private static final int EDITOR_SCROLL = 4;

    private final List<Step> steps = new ArrayList<>();
    private int ticks;
    private int index;
    private int waited;
    private boolean started;

    public SettingsProbe() {
        this.queue();
    }

    /** One step: it waits {@code delay} ticks after the step before it, then runs its action until the action returns true. */
    private record Step(String name, int delay, BooleanSupplier action) {
    }

    private void step(String name, int delay, BooleanSupplier action) {
        this.steps.add(new Step(name, delay, action));
    }

    private void act(String name, Runnable action) {
        this.step(name, 1, () -> {
            action.run();
            return true;
        });
    }

    private void grab(String file) {
        this.step("grab " + file, RENDER_WAIT, () -> {
            grabFront(file);
            return true;
        });
    }

    private void queue() {
        Minecraft minecraft = Minecraft.getInstance();
        this.act("window", () -> {
            GLFW.glfwSetWindowSize(minecraft.getWindow().getWindow(), WINDOW_WIDTH, WINDOW_HEIGHT);
            minecraft.options.guiScale().set(GUI_SCALE);
            minecraft.resizeDisplay();
        });
        this.act("create world", () -> CreateWorldScreen.openFresh(minecraft, minecraft.screen));
        this.step("world tab", 1, () -> {
            if (!(minecraft.screen instanceof CreateWorldScreen screen)) {
                return false;
            }
            widgets(screen, TabNavigationBar.class).getFirst().selectTab(1, false);
            return true;
        });
        this.grab("settings-01-create-world.png");
        this.act("settings button", () -> press(minecraft.screen, KEY + "settings"));
        this.step("defaults", RENDER_WAIT, () -> minecraft.screen instanceof AerolitePresetScreen && press(minecraft.screen, KEY + "defaults"));
        this.grab("settings-02-pillars.png");
        this.act("configure overworld", () -> widgets(minecraft.screen, ArrowButton.class).getFirst().onPress());
        this.step("variations tab", RENDER_WAIT, () -> {
            if (!(minecraft.screen instanceof DimensionSettingsScreen screen)) {
                return false;
            }
            widgets(screen, TabNavigationBar.class).getFirst().selectTab(1, false);
            return true;
        });
        this.grab("settings-03-variations-mazes.png");
        this.scrollList(LOWER_ROWS);
        this.grab("settings-03b-variations-mazes-lower.png");
        this.scrollList(0);
        this.openCombo("pattern");
        this.grab("settings-04-pattern-list.png");
        this.act("close pattern list", () -> combo(minecraft.screen, "pattern").closeList());
        this.pick("maze_type", MazeSettings.Type.RIBS.ordinal());
        this.grab("settings-05-mazes-ribs.png");
        this.pick("maze_type", MazeSettings.Type.LINES.ordinal());
        this.grab("settings-06-mazes-lines.png");
        this.pick("pattern", PatternType.BRICKS.ordinal());
        this.grab("settings-07-bricks.png");
        this.pick("pattern", PatternType.TILES.ordinal());
        this.pick("size", TilesSettings.Size.SMALL.ordinal());
        this.act("encased", () -> checkbox(minecraft.screen, KEY + "encased").onPress());
        this.act("surface paths", () -> checkbox(minecraft.screen, KEY + "surface_paths").onPress());
        this.openCombo("stagger");
        this.grab("settings-08-tiles-encased.png");
        this.act("close stagger list", () -> combo(minecraft.screen, "stagger").closeList());
        this.pick("pattern", PatternType.SCALES.ordinal());
        this.act("scales surface paths", () -> checkbox(minecraft.screen, KEY + "surface_paths").onPress());
        this.act("scales loops off", () -> checkbox(minecraft.screen, KEY + "allow_loops").onPress());
        this.grab("settings-09-scales.png");
        this.scrollList(LOWER_ROWS);
        this.grab("settings-09b-scales-lower.png");
        this.pick("pattern", PatternType.CHAOS.ordinal());
        this.pick("shape", PillarShape.BRIDGE.ordinal());
        this.scrollList(0);
        this.grab("settings-10-chaos-bridge.png");
        this.scrollList(LOWER_ROWS);
        this.grab("settings-10b-chaos-bridge-lower.png");
        this.queueProviders();
        this.act("done", () -> press(minecraft.screen, "gui.done"));
        this.step("preview", RENDER_WAIT, () -> minecraft.screen instanceof AerolitePresetScreen && press(minecraft.screen, KEY + "preview"));
        this.step("preview built", RENDER_WAIT, () -> minecraft.screen instanceof PreviewScreen screen && screen.progress().phase() == PreviewBuilder.Phase.DONE);
        this.grab("settings-11-preview-chaos-bridge.png");
        this.scrollPanel(-PANEL_SCROLL);
        this.grab("settings-11b-preview-chaos-bridge-lower.png");
        this.scrollPanel(PANEL_SCROLL * 4);
        PresetEdits edits = new PresetEdits(new PresetSliders());
        for (PatternType type : List.of(PatternType.MAZES, PatternType.BRICKS, PatternType.TILES, PatternType.SCALES)) {
            this.act("preview " + type.getSerializedName(), () -> {
                PreviewScreen screen = (PreviewScreen) minecraft.screen;
                AeroliteSettings settings = screen.settings();
                ShaftVariation variation = edits.withPatternType(settings.variations(Level.OVERWORLD).getFirst().variation(), type);
                if (type == PatternType.MAZES) {
                    variation = variation.withPattern(MazeSettings.DEFAULT.withType(MazeSettings.Type.RIBS).withStrength(0.5F)).withShape(PillarShape.I_BEAM);
                } else {
                    variation = edits.withPaths(variation, paths -> paths.withSurfacePaths(type != PatternType.BRICKS))
                            .withShape(type == PatternType.SCALES ? PillarShape.BRIDGE : PillarShape.PILLAR);
                }
                screen.setSettings(settings.withVariation(Level.OVERWORLD, 0, variation));
            });
            String name = "settings-" + (12 + type.ordinal()) + "-preview-" + type.getSerializedName().substring(type.getSerializedName().indexOf(':') + 1);
            this.grab(name + ".png");
            this.scrollPanel(-PANEL_SCROLL);
            this.grab(name + "-lower.png");
            this.scrollPanel(PANEL_SCROLL * 4);
        }
        this.act("narrow panel", () -> ((PreviewScreen) minecraft.screen).applyPanelUnits(NARROW_PANEL));
        this.grab("settings-16-preview-scales-narrow.png");
        this.scrollPanel(-PANEL_SCROLL);
        this.grab("settings-16-preview-scales-narrow-lower.png");
        this.queueImGuiEditors();
        this.queueShapes();
        this.step("stop", 60, () -> {
            LOGGER.info("Settings probe: done");
            minecraft.stop();
            return true;
        });
    }

    /**
     * The steps of the shape scenes: one I-beam scene and one Bridge scene, each with columns, from afar, along Z and close up.
     * Each scene waits for its full build.
     */
    private void queueShapes() {
        Minecraft minecraft = Minecraft.getInstance();
        ShaftVariation iBeam = ShaftVariation.standard(ShaftVariation.UNIFORM_BIAS, BlockStateProvider.simple(Blocks.STONE),
                        BlockStateProvider.simple(Blocks.POLISHED_ANDESITE), BlockStateProvider.simple(Blocks.MOSSY_COBBLESTONE))
                .withShape(PillarShape.I_BEAM).withDecayThreshold(0.8F).withErosionThreshold(0.9F);
        ShaftVariation bridge = iBeam.withShape(PillarShape.BRIDGE).withWall(Blocks.STONE_BRICKS.defaultBlockState())
                .withPath(Blocks.MOSSY_STONE_BRICKS.defaultBlockState()).withTrusses(Blocks.OXIDIZED_COPPER.defaultBlockState());
        ShaftPlacement placement = new ShaftPlacement(48, 64, 0.6F);
        for (ShaftVariation variation : List.of(iBeam, bridge)) {
            String name = variation.shape() == PillarShape.BRIDGE ? "bridge" : "ibeam";
            this.act("shapes " + name, () -> {
                PreviewScreen screen = (PreviewScreen) minecraft.screen;
                screen.setSettings(screen.settings().withDimension(Level.OVERWORLD, new DimensionSettings(placement, List.of(new WeightedVariation(1, variation)))));
                screen.setRadius(3);
                screen.camera().setYaw(35);
                screen.camera().setPitch(25);
            });
            this.step("shapes " + name + " built", 20, () -> minecraft.screen instanceof PreviewScreen screen && screen.progress().phase() == PreviewBuilder.Phase.DONE);
            this.grab("shapes-" + name + ".png");
            // The region border cuts the Z pillars and shows their sections.
            this.act("shapes " + name + " end", () -> {
                PreviewScreen screen = (PreviewScreen) minecraft.screen;
                screen.camera().setYaw(0);
                screen.camera().setPitch(10);
                screen.mouseScrolled(300, 400, 0, 4);
            });
            this.grab("shapes-" + name + "-end.png");
            this.act("shapes " + name + " zoom", () -> minecraft.screen.mouseScrolled(300, 400, 0, 4));
            this.grab("shapes-" + name + "-close.png");
            this.act("shapes " + name + " zoom out", () -> minecraft.screen.mouseScrolled(300, 400, 0, -8));
        }
    }

    /**
     * The steps of the provider editor. The Wall field opens with its simple state, then the type changes to weighted.
     * A paste gives the six block fields one provider type each. Each Edit button then opens its provider screen.
     * The last steps add a weighted entry with Done, and hover the cut summary of the Wall field.
     */
    private void queueProviders() {
        Minecraft minecraft = Minecraft.getInstance();
        this.revealBlocks();
        this.grab("provider-01-dimension-simple.png");
        this.pressEdit(0);
        this.grab("provider-02-simple.png");
        this.pick("provider.type", ProviderType.WEIGHTED.ordinal());
        this.grab("provider-03-simple-to-weighted.png");
        this.act("cancel simple", () -> press(minecraft.screen, "gui.cancel"));
        this.act("paste samples", () -> {
            String kept = minecraft.keyboardHandler.getClipboard();
            minecraft.keyboardHandler.setClipboard(ShaftVariation.CODEC.encodeStart(JsonOps.INSTANCE, sampleVariation()).getOrThrow().toString());
            press(minecraft.screen, KEY + "paste_variation");
            minecraft.keyboardHandler.setClipboard(kept);
        });
        this.step("rebuild paste", 2, () -> true);
        this.revealBlocks();
        this.grab("provider-04-dimension-summaries.png");
        String[] names = {"weighted", "noise-threshold", "noise", "dual-noise", "rotated", "randomized-int"};
        for (int field = 0; field < names.length; field++) {
            String name = String.format(Locale.ROOT, "provider-%02d-%s", 5 + field, names[field]);
            this.pressEdit(field);
            this.grab(name + ".png");
            if (field == 1 || field == 3) {
                this.scrollList(1000);
                this.grab(name + "-lower.png");
            }
            this.act("cancel " + names[field], () -> press(minecraft.screen, "gui.cancel"));
        }
        this.grab("provider-11-dimension-return.png");
        this.pressEdit(0);
        this.act("add entry", () -> press(minecraft.screen, KEY + "provider.add_entry"));
        this.step("rebuild entries", 2, () -> true);
        this.grab("provider-12-weighted-added.png");
        this.act("done weighted", () -> press(minecraft.screen, "gui.done"));
        this.step("rebuild done", 2, () -> minecraft.screen instanceof DimensionSettingsScreen);
        this.act("hover summary", () -> {
            StringWidget summary = widgets(minecraft.screen, StringWidget.class).stream().filter(widget -> widget.getTooltip() != null).findFirst().orElseThrow();
            moveCursor(summary.getX() + summary.getWidth() / 2.0, summary.getY() + summary.getHeight() / 2.0);
        });
        this.grab("provider-13-dimension-after-done.png");
        this.act("cursor away", () -> moveCursor(0, 0));
    }

    /**
     * The steps of the provider tabs of the preview panel. The first variation of the Overworld takes the sample providers with a plain maze.
     * The tab of one field opens for each provider type, the simple state first. The last steps select the tab Blocks with every tab open,
     * then show the dual noise tab and the tab Blocks at the narrowest panel width.
     */
    private void queueImGuiEditors() {
        Minecraft minecraft = Minecraft.getInstance();
        this.act("editors panel width", () -> ((PreviewScreen) minecraft.screen).applyPanelUnits(EDITOR_PANEL));
        this.act("editors simple", () -> {
            PreviewScreen screen = (PreviewScreen) minecraft.screen;
            ShaftVariation variation = editorVariation().withWall(BlockStateProvider.simple(Blocks.STONE_BRICKS));
            screen.setSettings(screen.settings().withVariation(Level.OVERWORLD, 0, variation));
            screen.providerTabs().of(Level.OVERWORLD, 0).open(VariationBlock.WALL);
        });
        this.scrollPanel(PANEL_SCROLL * 4);
        this.scrollPanel(-PANEL_SCROLL);
        this.grab("editors-01-simple.png");
        this.act("editors samples", () -> {
            PreviewScreen screen = (PreviewScreen) minecraft.screen;
            screen.setSettings(screen.settings().withVariation(Level.OVERWORLD, 0, editorVariation()));
        });
        String[] names = {"weighted", "noise-threshold", "noise", "dual-noise", "rotated", "randomized-int"};
        List<VariationBlock> fields = List.of(VariationBlock.WALL, VariationBlock.PATH, VariationBlock.INTERIOR,
                VariationBlock.JUNCTION, VariationBlock.SURFACE, VariationBlock.TRUSSES);
        for (int field = 0; field < names.length; field++) {
            VariationBlock block = fields.get(field);
            String name = String.format(Locale.ROOT, "editors-%02d-%s", 2 + field, names[field]);
            this.act("editors open " + names[field], () -> ((PreviewScreen) minecraft.screen).providerTabs().of(Level.OVERWORLD, 0).open(block));
            this.grab(name + ".png");
            if (block == VariationBlock.JUNCTION) {
                this.scrollPanel(-EDITOR_SCROLL);
                this.grab(name + "-lower.png");
                this.scrollPanel(EDITOR_SCROLL);
            }
        }
        this.act("editors blocks", () -> ((PreviewScreen) minecraft.screen).providerTabs().of(Level.OVERWORLD, 0).selectBlocks());
        this.grab("editors-08-blocks-all-open.png");
        this.act("editors narrow", () -> ((PreviewScreen) minecraft.screen).applyPanelUnits(NARROW_PANEL));
        this.grab("editors-09-blocks-narrow.png");
        this.act("editors narrow dual noise", () -> ((PreviewScreen) minecraft.screen).providerTabs().of(Level.OVERWORLD, 0).open(VariationBlock.JUNCTION));
        this.grab("editors-10-dual-noise-narrow.png");
        this.scrollPanel(-EDITOR_SCROLL);
        this.grab("editors-10-dual-noise-narrow-lower.png");
        this.scrollPanel(EDITOR_SCROLL);
        this.act("editors narrow weighted", () -> ((PreviewScreen) minecraft.screen).providerTabs().of(Level.OVERWORLD, 0).open(VariationBlock.WALL));
        this.grab("editors-11-weighted-narrow.png");
        this.act("editors narrow noise threshold", () -> ((PreviewScreen) minecraft.screen).providerTabs().of(Level.OVERWORLD, 0).open(VariationBlock.PATH));
        this.grab("editors-12-noise-threshold-narrow.png");
    }

    /** The sample providers of {@link #sampleVariation()} with a plain maze, so the tab bar sits high in the panel. */
    private static ShaftVariation editorVariation() {
        return sampleVariation().withPattern(MazeSettings.DEFAULT);
    }

    /** Six providers of the six provider types other than simple, with the values of {@code prototypes/ui/providers.js}. */
    private static ShaftVariation sampleVariation() {
        NormalNoise.NoiseParameters noise = new NormalNoise.NoiseParameters(-3, 1.0);
        BlockStateProvider weighted = new WeightedStateProvider(SimpleWeightedRandomList.<BlockState>builder()
                .add(Blocks.STONE_BRICKS.defaultBlockState(), 3)
                .add(Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), 1)
                .add(Blocks.ANDESITE.defaultBlockState(), 1));
        BlockStateProvider threshold = new NoiseThresholdProvider(2345L, noise, 0.01F, -0.8F, 0.35F, Blocks.STONE_BRICKS.defaultBlockState(),
                List.of(Blocks.MOSSY_STONE_BRICKS.defaultBlockState()), List.of(Blocks.COBBLESTONE.defaultBlockState(), Blocks.GRAVEL.defaultBlockState()));
        BlockStateProvider noiseProvider = new NoiseProvider(2345L, new NormalNoise.NoiseParameters(-3, 1.0, 1.0), 0.02F,
                List.of(Blocks.STONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(), Blocks.TUFF.defaultBlockState()));
        BlockStateProvider dual = new DualNoiseProvider(new InclusiveRange<>(1, 3), new NormalNoise.NoiseParameters(-10, 1.0), 1.0F, 2345L, noise, 0.05F,
                List.of(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
                        Blocks.COBBLESTONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState()));
        BlockStateProvider rotated = new RotatedBlockProvider(Blocks.OAK_LOG);
        BlockStateProvider randomized = new RandomizedIntStateProvider(BlockStateProvider.simple(Blocks.SEA_PICKLE), SeaPickleBlock.PICKLES, UniformInt.of(1, 4));
        return ShaftVariation.standard(ShaftVariation.UNIFORM_BIAS, noiseProvider, weighted, threshold)
                .withPattern(ChaosSettings.DEFAULT)
                .withShape(PillarShape.BRIDGE)
                .withJunction(dual)
                .withSurface(rotated)
                .withTrusses(randomized);
    }

    /** Presses the Edit button of one block field row of the dimension screen. */
    private void pressEdit(int field) {
        this.act("edit " + field, () -> widgets(Minecraft.getInstance().screen, Button.class).stream()
                .filter(button -> hasKey(button.getMessage(), KEY + "provider.edit"))
                .skip(field).findFirst().orElseThrow().onPress());
        this.step("provider screen " + field, 2, () -> Minecraft.getInstance().screen instanceof ProviderScreen);
    }

    /**
     * Scrolls the settings list from the top until the six Edit buttons of the first variation show inside the list.
     * A row that the list never drew reports the position 0.
     */
    private void revealBlocks() {
        this.scrollList(0);
        this.step("reveal blocks", 2, () -> {
            Screen screen = Minecraft.getInstance().screen;
            SettingsList list = widgets(screen, SettingsList.class).getFirst();
            List<Button> edits = widgets(screen, Button.class).stream().filter(button -> hasKey(button.getMessage(), KEY + "provider.edit")).limit(BRIDGE_FIELDS).toList();
            if (edits.stream().allMatch(edit -> edit.getY() >= list.getY() && edit.getBottom() <= list.getBottom())) {
                return true;
            }
            list.setScrollAmount(list.getScrollAmount() + ENTRY_HEIGHT);
            return false;
        });
    }

    /** Moves the cursor to a point in GUI units through the window callback, which the mouse handler reads. */
    private static void moveCursor(double x, double y) {
        Minecraft minecraft = Minecraft.getInstance();
        long window = minecraft.getWindow().getWindow();
        double scale = minecraft.getWindow().getScreenWidth() / (double) minecraft.getWindow().getGuiScaledWidth();
        GLFWCursorPosCallback move = GLFW.glfwSetCursorPosCallback(window, null);
        GLFW.glfwSetCursorPosCallback(window, move);
        if (move != null) {
            move.invoke(window, x * scale, y * scale);
        }
    }

    /** Opens the list of the first combo whose button text has the key {@code createWorld.customize.aerolite.<key>}. */
    private void openCombo(String key) {
        this.act("open " + key, () -> {
            ChoiceDropdown<?> combo = combo(Minecraft.getInstance().screen, key);
            combo.setFocused(true);
            combo.onPress();
        });
    }

    /** Sets the scroll of the settings list of the screen. */
    private void scrollList(int scroll) {
        this.act("scroll " + scroll, () -> widgets(Minecraft.getInstance().screen, SettingsList.class).getFirst().setScrollAmount(scroll));
    }

    /**
     * Moves the cursor over the panel and sends wheel steps through the window callbacks, which ImGui reads.
     * A negative count scrolls down.
     */
    private void scrollPanel(int steps) {
        this.act("panel scroll " + steps, () -> {
            long window = Minecraft.getInstance().getWindow().getWindow();
            GLFWCursorPosCallback move = GLFW.glfwSetCursorPosCallback(window, null);
            GLFW.glfwSetCursorPosCallback(window, move);
            if (move != null) {
                move.invoke(window, WINDOW_WIDTH - 150, WINDOW_HEIGHT / 2.0);
            }
        });
        this.act("panel wheel " + steps, () -> {
            long window = Minecraft.getInstance().getWindow().getWindow();
            GLFWScrollCallback wheel = GLFW.glfwSetScrollCallback(window, null);
            GLFW.glfwSetScrollCallback(window, wheel);
            if (wheel != null) {
                wheel.invoke(window, 0, steps);
            }
        });
    }

    /**
     * Scrolls the settings list from the top until the combo of the key and room for its list show inside the list.
     * Rows that the list never drew report the position 0, so they count as hidden.
     */
    private void reveal(String key) {
        this.scrollList(0);
        this.step("reveal " + key, 2, () -> {
            Screen screen = Minecraft.getInstance().screen;
            SettingsList list = widgets(screen, SettingsList.class).getFirst();
            ChoiceDropdown<?> combo = combo(screen, key);
            if (combo.getY() >= list.getY() && combo.getBottom() + 5 * ENTRY_HEIGHT <= list.getBottom()) {
                return true;
            }
            list.setScrollAmount(list.getScrollAmount() + ENTRY_HEIGHT);
            return false;
        });
    }

    /** Picks an entry of the first combo of the key with a click on its list, like the player. */
    private void pick(String key, int entry) {
        this.reveal(key);
        this.openCombo(key);
        this.act("pick " + key + " " + entry, () -> {
            Screen screen = Minecraft.getInstance().screen;
            ChoiceDropdown<?> combo = combo(screen, key);
            double x = combo.getX() + combo.getWidth() / 2.0;
            double y = combo.getBottom() + entry * ENTRY_HEIGHT + ENTRY_HEIGHT / 2.0;
            if (screen != null) {
                screen.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            }
        });
        // The variation rows rebuild on the next screen tick.
        this.step("rebuild " + key, 2, () -> true);
    }

    public void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!this.started) {
            if (minecraft.screen != null && minecraft.getOverlay() == null && ++this.ticks >= TITLE_WAIT) {
                this.started = true;
                this.ticks = 0;
                LOGGER.info("Settings probe: start");
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
            LOGGER.error("Settings probe: step {} failed on {}", step.name(), screenName(minecraft.screen), exception);
            done = true;
        }
        if (!done && ++this.waited < STEP_LIMIT) {
            return;
        }
        if (!done) {
            LOGGER.error("Settings probe: step {} timed out on {}", step.name(), screenName(minecraft.screen));
        }
        this.index++;
        this.ticks = 0;
        this.waited = 0;
    }

    private static String screenName(@Nullable Screen screen) {
        return screen == null ? "no screen" : screen.getClass().getSimpleName();
    }

    /** Every widget of the type under the container, in the sequence of the children. */
    private static <T> List<T> widgets(@Nullable ContainerEventHandler container, Class<T> type) {
        List<T> found = new ArrayList<>();
        if (container != null) {
            collect(container, type, found);
        }
        return found;
    }

    private static <T> void collect(ContainerEventHandler container, Class<T> type, List<T> found) {
        for (GuiEventListener child : container.children()) {
            if (type.isInstance(child)) {
                found.add(type.cast(child));
            }
            if (child instanceof ContainerEventHandler inner) {
                collect(inner, type, found);
            }
        }
    }

    private static boolean hasKey(Component message, String key) {
        return message.getContents() instanceof TranslatableContents contents && contents.getKey().equals(key);
    }

    /** Presses the first button whose text has the key. Returns false when no such button exists. */
    private static boolean press(@Nullable Screen screen, String key) {
        for (AbstractWidget widget : widgets(screen, AbstractWidget.class)) {
            if (widget instanceof Button button && hasKey(button.getMessage(), key)) {
                button.onPress();
                return true;
            }
        }
        return false;
    }

    private static Checkbox checkbox(@Nullable Screen screen, String key) {
        return widgets(screen, Checkbox.class).stream().filter(box -> hasKey(box.getMessage(), key)).findFirst().orElseThrow();
    }

    private static ChoiceDropdown<?> combo(@Nullable Screen screen, String key) {
        return widgets(screen, ChoiceDropdown.class).stream()
                .filter(combo -> hasKey(combo.getMessage(), KEY + key))
                .<ChoiceDropdown<?>>map(combo -> combo)
                .findFirst().orElseThrow();
    }

    /** Saves the window front buffer, which also holds the ImGui panel, as a PNG. */
    private static void grabFront(String file) {
        Minecraft minecraft = Minecraft.getInstance();
        int[] width = new int[1];
        int[] height = new int[1];
        GLFW.glfwGetFramebufferSize(minecraft.getWindow().getWindow(), width, height);
        ByteBuffer pixels = BufferUtils.createByteBuffer(width[0] * height[0] * 4).order(ByteOrder.LITTLE_ENDIAN);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL11.glReadBuffer(GL11.GL_FRONT);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, width[0], height[0], GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        GL11.glReadBuffer(GL11.GL_BACK);
        minecraft.getMainRenderTarget().bindWrite(true);
        try (NativeImage image = new NativeImage(width[0], height[0], false)) {
            for (int y = 0; y < height[0]; y++) {
                for (int x = 0; x < width[0]; x++) {
                    // RGBA bytes read as a little endian int give the ABGR value of the image.
                    image.setPixelRGBA(x, height[0] - 1 - y, pixels.getInt((y * width[0] + x) * 4) | 0xFF000000);
                }
            }
            Path directory = minecraft.gameDirectory.toPath().resolve("screenshots");
            Files.createDirectories(directory);
            image.writeToFile(directory.resolve(file));
            LOGGER.info("Settings probe: saved {} of {} at {}x{}", file, screenName(minecraft.screen), width[0], height[0]);
        } catch (IOException exception) {
            LOGGER.error("Settings probe: could not save {}", file, exception);
        }
    }

}
