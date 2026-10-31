package us.drullk.aerolite.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.lwjgl.glfw.GLFW;
import us.drullk.aerolite.client.preview.PreviewScreen;
import us.drullk.aerolite.world.AeroliteChunkGenerator;
import us.drullk.aerolite.world.AeroliteSettings;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The customize screen of the preset in the world creation menu. It lists the Aerolite dimensions.
 * The add row comes first. Each configured dimension then has a row with its name, an arrow button and a minus button.
 * The arrow opens the {@link DimensionSettingsScreen} of the dimension. The minus button removes the dimension.
 * An add or a remove rebuilds the list entries. A return from a dimension screen or the preview also rebuilds them.
 * The Preview button shows only when ImGui loaded. The preview edits a copy of the settings.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_00-41_1fb7a266-bfdf-4ab5-8b4a-1652dc840cb2.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_19-53_bf29e15f-47bb-42fe-94f0-29ae22f43f5a.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_e43f73bd-d689-4553-8382-7b011c0c142a.md}.
 */
public class AerolitePresetScreen extends Screen {

    /** Three footer buttons at the vanilla width run off a narrow window. */
    private static final int FOOTER_BUTTON_WIDTH = Button.DEFAULT_WIDTH / 2;
    private final CreateWorldScreen parent;
    private final WorldCreationContext context;
    private final PresetTools tools;
    private final PresetEdits edits;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    @Nullable
    private SettingsList list;
    private AeroliteSettings settings;
    /** True when an add or a remove waits for the list rebuild of the next tick. */
    private boolean rebuildPending;
    @Nullable
    private DimensionDropdown addDropdown;
    /** The candidate that the add dropdown shows after a rebuild. */
    @Nullable
    private ResourceKey<Level> addChoice;

    public AerolitePresetScreen(CreateWorldScreen parent, WorldCreationContext context, PresetTools tools) {
        super(Component.translatable("createWorld.customize.aerolite.title"));
        this.parent = parent;
        this.context = context;
        this.tools = tools;
        this.edits = tools.edits();
        this.settings = context.selectedDimensions().dimensions().values().stream()
                .map(LevelStem::generator)
                .filter(AeroliteChunkGenerator.class::isInstance)
                .map(generator -> ((AeroliteChunkGenerator) generator).aeroliteSettings())
                .findFirst()
                .orElse(AeroliteSettings.DEFAULT);
    }

    @Override
    protected void init() {
        this.layout.addTitleHeader(this.title, this.font);
        SettingsList list = this.layout.addToContents(new SettingsList(this.minecraft, this.width, this.layout));
        this.list = list;
        this.populate(list);

        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> {
            AeroliteSettings chosen = this.settings;
            this.parent.getUiState().updateDimensions((registries, dimensions) -> this.withSettings(dimensions, chosen));
            this.onClose();
        }).width(FOOTER_BUTTON_WIDTH).build());
        if (this.tools.panel().available()) {
            footer.addChild(Button.builder(Component.translatable("createWorld.customize.aerolite.preview"), button -> this.openPreview()).width(FOOTER_BUTTON_WIDTH).build());
        }
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).width(FOOTER_BUTTON_WIDTH).build());
        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
    }

    /** Opens the preview with a copy of the settings. Done in the preview sends the settings and the seed text back. */
    private void openPreview() {
        new PreviewScreen(this, this.context, this.tools, this.settings, this.parent.getUiState().getSeed(), (chosen, seedText) -> {
            this.settings = chosen;
            this.parent.getUiState().setSeed(seedText);
            this.rebuildPending = true;
        }).open();
    }

    /** Adds the add row, then one row per configured dimension. */
    private void populate(SettingsList list) {
        this.addDimensionRow(list);
        for (ResourceKey<Level> dimension : this.edits.configuredDimensions(this.settings)) {
            this.addDimension(list, dimension);
        }
    }

    /** Adds the row of one dimension: its name, then the arrow that opens its screen and the minus button that removes it. */
    private void addDimension(SettingsList list, ResourceKey<Level> dimension) {
        Component name = this.edits.dimensionName(dimension);
        ArrowButton configure = new ArrowButton(
                CommonComponents.joinForNarration(Component.translatable("createWorld.customize.aerolite.configure_dimension"), name),
                button -> this.openDimension(dimension));
        Button remove = Button.builder(Component.literal("\u2212"), button -> {
            this.settings = this.edits.withoutDimension(this.settings, dimension);
            this.addChoice = dimension;
            this.rebuildPending = true;
        }).createNarration(message -> CommonComponents.joinForNarration(Component.translatable("createWorld.customize.aerolite.remove_dimension"), name)).build();
        list.addTrailingRow(name, configure, remove);
    }

    /** Opens the screen of one dimension with a copy of the settings. Done there sends the edited settings back. */
    private void openDimension(ResourceKey<Level> dimension) {
        this.minecraft.setScreen(new DimensionSettingsScreen(this, this.tools, dimension, this.settings, chosen -> {
            this.settings = chosen;
            this.rebuildPending = true;
        }));
    }

    /** Adds the dropdown of the dimensions that the world holds without settings, and the Add button. */
    private void addDimensionRow(SettingsList list) {
        List<ResourceKey<Level>> candidates = this.edits.addableDimensions(this.settings,
                this.context.selectedDimensions().dimensions().keySet().stream().map(Registries::levelStemToLevel).toList());
        DimensionDropdown dropdown = new DimensionDropdown(candidates, this.addChoice, this.edits::dimensionName,
                Component.translatable("createWorld.customize.aerolite.all_dimensions_configured"));
        this.addDropdown = dropdown;
        Button add = Button.builder(Component.translatable("createWorld.customize.aerolite.add_dimension"), button -> {
            ResourceKey<Level> chosen = dropdown.chosen();
            if (chosen != null) {
                this.settings = this.edits.withAddedDimension(this.settings, chosen);
                this.addChoice = null;
                this.rebuildPending = true;
            }
        }).build();
        add.active = dropdown.chosen() != null;
        list.addRow(dropdown, add);
    }

    /** Rebuilds the list entries from the settings and keeps the scroll position. */
    private void rebuildList() {
        if (this.list == null) {
            return;
        }
        double scroll = this.list.getScrollAmount();
        this.list.clear();
        this.populate(this.list);
        this.list.setScrollAmount(scroll);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.rebuildPending) {
            this.rebuildPending = false;
            this.rebuildList();
        }
    }

    /** The add dropdown when its list shows and the button lies inside the list area, or null. */
    @Nullable
    private DimensionDropdown openDimensionList() {
        DimensionDropdown dropdown = this.addDropdown;
        if (this.list == null || dropdown == null) {
            return null;
        }
        if (!dropdown.listVisible()) {
            dropdown.closeList();
            return null;
        }
        if (dropdown.getY() >= this.list.getY() && dropdown.getBottom() <= this.list.getBottom()) {
            return dropdown;
        }
        dropdown.closeList();
        return null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        DimensionDropdown dimensions = this.openDimensionList();
        if (dimensions != null) {
            dimensions.renderList(graphics, this.font, this.height, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        DimensionDropdown dimensions = this.openDimensionList();
        if (dimensions != null) {
            dimensions.listClicked(mouseX, mouseY, this.height);
            dimensions.closeList();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        DimensionDropdown dimensions = this.openDimensionList();
        if (dimensions != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            dimensions.closeList();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        if (this.list != null) {
            this.list.updateSize(this.width, this.layout);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private WorldDimensions withSettings(WorldDimensions dimensions, AeroliteSettings settings) {
        Map<ResourceKey<LevelStem>, LevelStem> stems = new LinkedHashMap<>();
        dimensions.dimensions().forEach((key, stem) -> stems.put(key, stem.generator() instanceof AeroliteChunkGenerator generator
                ? new LevelStem(stem.type(), generator.withAeroliteSettings(settings))
                : stem));
        return new WorldDimensions(stems);
    }

}
