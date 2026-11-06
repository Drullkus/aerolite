package us.drullk.aerolite.client;

import com.mojang.serialization.DataResult;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;
import us.drullk.aerolite.client.preview.PreviewScreen;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.VariationList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The shafts screen "Amazing Pillars" of the world creation menu. It edits a copy of the settings of the {@link ShaftSettingsHolder}.
 * Done writes the copy to the holder. Cancel and Escape drop it.
 * The add row comes first: the dimension dropdown, the list dropdown and the Add button.
 * An open dropdown list hides the widget tooltips.
 * Each configured dimension then has a row with its name, an arrow button and a minus button.
 * The arrow opens the {@link DimensionSettingsScreen} of the dimension. The minus button removes the dimension.
 * An add or a remove rebuilds the list entries. A return from a dimension screen or the preview also rebuilds them.
 * The Preview button shows only when ImGui loaded. The preview edits a copy of the settings.
 * The Copy and Paste buttons at the right end of the header move the settings through the system clipboard.
 * A good paste replaces the settings and rebuilds the list. A bad paste shows a toast and keeps the settings.
 * The Defaults button right of Paste adds each missing dimension that has its own variation list, and rebuilds the list.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_07-05_5f965fb4-ad37-4b6a-8b5d-da8c3a179dc6.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-29_20-21_80fbac8f-2f3a-4fd7-813a-624e3db43bca.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_00-41_1fb7a266-bfdf-4ab5-8b4a-1652dc840cb2.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_07-08_90875d90-ad30-47fe-80ab-ff3a7b6f2b3c.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_19-53_bf29e15f-47bb-42fe-94f0-29ae22f43f5a.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_20-42_565ea277-886a-4768-9539-b9f2ca85c88e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-06_00-33_bb95aa4e-2e64-45f7-a528-d3415d48c0f3.md}.
 */
public class AerolitePresetScreen extends Screen {

    /** Three footer buttons at the vanilla width run off a narrow window. */
    private static final int FOOTER_BUTTON_WIDTH = Button.DEFAULT_WIDTH / 2;
    /** The space on each side of the text of a header button. */
    private static final int HEADER_BUTTON_PADDING = 8;
    private static final int HEADER_BUTTON_HEIGHT = 20;
    private static final int HEADER_BUTTON_GAP = 4;
    private static final int HEADER_RIGHT_MARGIN = 8;
    private final CreateWorldScreen parent;
    private final WorldCreationContext context;
    private final PresetTools tools;
    private final PresetEdits edits;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    /** The header row at the full window width. It holds the centered title, the clipboard buttons and Defaults. */
    private final FrameLayout header = new FrameLayout();
    /** The dropdowns of the add row. The screen draws their open lists over the rows below. */
    private final List<ChoiceDropdown<?>> dropdowns = new ArrayList<>();
    @Nullable
    private SettingsList list;
    private AeroliteSettings settings;
    /** True when an add or a remove waits for the list rebuild of the next tick. */
    private boolean rebuildPending;
    /** The candidate that the add dropdown shows after a rebuild. */
    @Nullable
    private ResourceKey<Level> addChoice;

    public AerolitePresetScreen(CreateWorldScreen parent, PresetTools tools) {
        super(Component.translatable("createWorld.customize.aerolite.title"));
        this.parent = parent;
        this.context = parent.getUiState().getSettings();
        this.tools = tools;
        this.edits = tools.edits();
        this.edits.useRegistries(this.context.worldgenLoadContext());
        this.settings = tools.holder().settings();
    }

    @Override
    protected void init() {
        this.header.addChild(new StringWidget(this.title, this.font), LayoutSettings::alignHorizontallyCenter);
        LinearLayout clipboard = this.header.addChild(LinearLayout.horizontal().spacing(HEADER_BUTTON_GAP),
                settings -> settings.alignHorizontallyRight().paddingRight(HEADER_RIGHT_MARGIN));
        clipboard.addChild(this.headerButton("copy", button -> this.tools.json().copy(this.settings, this.edits.registries())));
        clipboard.addChild(this.headerButton("paste", button -> this.paste()));
        clipboard.addChild(this.headerButton("defaults", button -> this.loadDefaults()));
        this.layout.addToHeader(this.header);
        SettingsList list = this.layout.addToContents(new SettingsList(this.minecraft, this.width, this.layout));
        this.list = list;
        this.populate(list);

        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> {
            this.tools.holder().set(this.settings);
            this.onClose();
        }).width(FOOTER_BUTTON_WIDTH).build());
        if (this.tools.panel().available()) {
            footer.addChild(Button.builder(Component.translatable("createWorld.customize.aerolite.preview"), button -> this.openPreview()).width(FOOTER_BUTTON_WIDTH).build());
        }
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).width(FOOTER_BUTTON_WIDTH).build());
        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
    }

    /** A header button of the height 20 that fits its text. Its tooltip has the key of the button plus {@code _tooltip}. */
    private Button headerButton(String key, Button.OnPress onPress) {
        Component message = Component.translatable("createWorld.customize.aerolite." + key);
        return Button.builder(message, onPress)
                .size(this.font.width(message) + 2 * HEADER_BUTTON_PADDING, HEADER_BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("createWorld.customize.aerolite." + key + "_tooltip")))
                .build();
    }

    /** Replaces the settings with the clipboard settings. A bad parse shows a toast and keeps the settings. */
    private void paste() {
        DataResult<AeroliteSettings> pasted = this.tools.json().paste(this.edits.registries());
        pasted.ifSuccess(chosen -> {
            this.settings = chosen;
            this.addChoice = null;
            this.rebuildPending = true;
        }).ifError(error -> this.tools.json().showPasteFailed(error.message()));
    }

    /** Adds each dimension of the world that has its own variation list and no settings. Configured dimensions stay. */
    private void loadDefaults() {
        this.settings = this.edits.withDefaults(this.settings, this.edits.worldDimensions(this.context));
        this.addChoice = null;
        this.rebuildPending = true;
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
        this.minecraft.setScreen(new DimensionSettingsScreen(this, this.tools, dimension, YLimits.of(this.context, dimension), this.settings, chosen -> {
            this.settings = chosen;
            this.rebuildPending = true;
        }));
    }

    /**
     * Adds the dropdown of the dimensions of the world without settings, the dropdown of the variation lists and the Add button.
     * The list dropdown shows the own list of the chosen dimension, or No prefab.
     * Its tooltip names the chosen dimension.
     */
    private void addDimensionRow(SettingsList list) {
        List<ResourceKey<Level>> candidates = this.edits.addableDimensions(this.settings, this.edits.worldDimensions(this.context));
        ChoiceDropdown<ResourceKey<Level>> dimensions = new ChoiceDropdown<>(candidates, this.addChoice, this.edits::dimensionName,
                Component.translatable("createWorld.customize.aerolite.all_dimensions_configured"));
        List<Optional<ResourceKey<VariationList>>> choices = this.edits.listChoices();
        ChoiceDropdown<Optional<ResourceKey<VariationList>>> lists = new ChoiceDropdown<>(choices, this.ownList(dimensions.chosen()),
                this.edits::listName, this.edits.listName(Optional.empty()));
        lists.active = dimensions.active;
        lists.setTooltip(Tooltip.create(this.edits.listTooltip(dimensions.chosen())));
        dimensions.onPick(chosen -> {
            lists.choose(this.ownList(chosen));
            lists.setTooltip(Tooltip.create(this.edits.listTooltip(chosen)));
        });
        this.dropdowns.clear();
        this.dropdowns.add(dimensions);
        this.dropdowns.add(lists);
        Button add = Button.builder(Component.translatable("createWorld.customize.aerolite.add_dimension"), button -> {
            ResourceKey<Level> chosen = dimensions.chosen();
            Optional<ResourceKey<VariationList>> chosenList = lists.chosen();
            if (chosen != null && chosenList != null) {
                this.settings = this.edits.withAddedDimension(this.settings, chosen, chosenList);
                this.addChoice = null;
                this.rebuildPending = true;
            }
        }).build();
        add.active = dimensions.chosen() != null;
        list.addRow(dimensions, lists, add);
    }

    /** The own list of a dimension, or No prefab. */
    private Optional<ResourceKey<VariationList>> ownList(@Nullable ResourceKey<Level> dimension) {
        return dimension == null ? Optional.empty() : this.edits.ownList(dimension);
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

    /** The add row dropdown whose list shows while its button lies inside the list area, or null. It closes the other lists. */
    @Nullable
    private ChoiceDropdown<?> openDropdown() {
        ChoiceDropdown<?> open = null;
        for (ChoiceDropdown<?> dropdown : this.dropdowns) {
            boolean inside = this.list != null && dropdown.getY() >= this.list.getY() && dropdown.getBottom() <= this.list.getBottom();
            if (open == null && dropdown.listVisible() && inside) {
                open = dropdown;
            } else {
                dropdown.closeList();
            }
        }
        return open;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        ChoiceDropdown<?> open = this.openDropdown();
        if (open != null) {
            this.clearTooltipForNextRenderPass();
            open.renderList(graphics, this.font, this.height, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ChoiceDropdown<?> open = this.openDropdown();
        if (open != null) {
            open.listClicked(mouseX, mouseY, this.height);
            open.closeList();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        ChoiceDropdown<?> open = this.openDropdown();
        if (open != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            open.closeList();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void repositionElements() {
        this.header.setMinDimensions(this.width, this.layout.getHeaderHeight());
        this.layout.arrangeElements();
        if (this.list != null) {
            this.list.updateSize(this.width, this.layout);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

}
