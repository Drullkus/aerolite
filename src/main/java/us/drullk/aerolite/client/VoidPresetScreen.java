package us.drullk.aerolite.client;

import net.minecraft.Util;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.CreateBuffetWorldScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import us.drullk.aerolite.world.AeroliteWorldgen;
import us.drullk.aerolite.world.VoidPresetBiomes;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;

/**
 * The customize screen "Aerolite Void" of the world preset. It edits a copy of the biome choice of each preset dimension and a copy of the cloud height.
 * Each dimension row holds the dimension name, the biome name or "Vanilla biomes", the Biome button and the Reset button.
 * The Biome button opens the vanilla single biome screen. Reset gives the dimension its vanilla biome source again.
 * Done applies the biomes to the dimensions of the world and writes the cloud height to the {@link ShaftSettingsHolder}. Cancel and Escape drop both.
 * The Islands screen extends it with rows above the dimension rows and an edit of the dimensions after the biomes.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-07_01-04_30006f9a-885c-4e1b-8259-941ec5aa5133.md}.
 */
public class VoidPresetScreen extends Screen {

    protected static final String KEY = "createWorld.customize.aerolite.";
    /** The space on each side of the text of a row button. */
    private static final int BUTTON_PADDING = 8;

    private final CreateWorldScreen parent;
    private final WorldCreationContext context;
    private final PresetTools tools;
    private final VoidPresetBiomes presetBiomes;
    private final ResourceKey<WorldPreset> preset;
    /** The fixed biome of each preset dimension, or empty for its vanilla biome source. */
    private final Map<ResourceKey<LevelStem>, Optional<Holder<Biome>>> biomes;
    private int cloudHeight;
    @Nullable
    private SettingsList list;
    /** True when a reset waits for the list rebuild of the next tick. */
    private boolean rebuildPending;

    public VoidPresetScreen(CreateWorldScreen parent, WorldCreationContext context, PresetTools tools, VoidPresetBiomes presetBiomes) {
        this(parent, context, tools, presetBiomes, AeroliteWorldgen.PRESET, Component.translatable(KEY + "void.title"));
    }

    protected VoidPresetScreen(CreateWorldScreen parent, WorldCreationContext context, PresetTools tools, VoidPresetBiomes presetBiomes,
                               ResourceKey<WorldPreset> preset, Component title) {
        super(title);
        this.parent = parent;
        this.context = context;
        this.tools = tools;
        this.presetBiomes = presetBiomes;
        this.preset = preset;
        this.biomes = presetBiomes.fixedBiomes(context.selectedDimensions());
        this.cloudHeight = tools.holder().settings().cloudHeight();
    }

    @Override
    protected void init() {
        HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
        layout.addTitleHeader(this.title, this.font);
        SettingsList list = layout.addToContents(new SettingsList(this.minecraft, this.width, layout));
        this.list = list;
        this.populate(list);
        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        this.addFooterButtons(footer);
        layout.visitWidgets(this::addRenderableWidget);
        layout.arrangeElements();
        list.updateSize(this.width, layout);
    }

    protected PresetTools tools() {
        return this.tools;
    }

    protected CreateWorldScreen parent() {
        return this.parent;
    }

    /** Rebuilds the list on the next tick and keeps the scroll position. */
    protected void requestRebuild() {
        this.rebuildPending = true;
    }

    /** Adds the footer buttons: Done and Cancel. */
    protected void addFooterButtons(LinearLayout footer) {
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.done()).build());
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).build());
    }

    /** Applies the edits and closes the screen. */
    protected void done() {
        this.applyEdits();
        this.onClose();
    }

    protected WorldCreationContext context() {
        return this.context;
    }

    /** Adds the top rows, then one row per preset dimension, then the header Clouds and the cloud height slider. */
    private void populate(SettingsList list) {
        this.addTopRows(list);
        for (ResourceKey<LevelStem> stem : VoidPresetBiomes.DIMENSIONS) {
            Component name = this.tools.edits().dimensionName(Registries.levelStemToLevel(stem));
            Component biomeText = this.button("void.biome");
            Button biome = Button.builder(biomeText, button -> this.openBiomes(stem))
                    .width(this.font.width(biomeText) + 2 * BUTTON_PADDING)
                    .createNarration(message -> CommonComponents.joinForNarration(message.get(), name))
                    .build();
            Component resetText = this.button("void.reset");
            Button reset = Button.builder(resetText, button -> {
                        this.biomes.put(stem, Optional.empty());
                        this.rebuildPending = true;
                    })
                    .width(this.font.width(resetText) + 2 * BUTTON_PADDING)
                    .createNarration(message -> CommonComponents.joinForNarration(message.get(), name))
                    .build();
            reset.active = this.biome(stem).isPresent();
            list.addValueRow(name, this.biomeName(stem), biome, reset);
        }
        list.addHeader(Component.translatable(KEY + "clouds"));
        list.addRow(new StepSlider(PresetSliders.CLOUD_HEIGHT_STEPS, this.tools.sliders().cloudHeightStep(this.cloudHeight),
                step -> Component.translatable(KEY + "cloud_height", this.tools.sliders().cloudHeight(step)),
                step -> this.cloudHeight = this.tools.sliders().cloudHeight(step)));
    }

    /** Adds the rows above the dimension rows. The Void screen adds none. */
    protected void addTopRows(SettingsList list) {
    }

    /** The dimensions of the world after Done: the current dimensions with the biome choices. */
    protected WorldDimensions dimensions(RegistryAccess registries, WorldDimensions current, Map<ResourceKey<LevelStem>, Optional<Holder<Biome>>> biomes) {
        return this.presetBiomes.withBiomes(registries, this.preset, current, biomes);
    }

    private Component button(String key) {
        return Component.translatable(KEY + key);
    }

    private Optional<Holder<Biome>> biome(ResourceKey<LevelStem> stem) {
        return this.biomes.getOrDefault(stem, Optional.empty());
    }

    /** The name of the fixed biome of the dimension, or "Vanilla biomes". */
    private Component biomeName(ResourceKey<LevelStem> stem) {
        return this.biome(stem)
                .flatMap(Holder::unwrapKey)
                .<Component>map(key -> Component.translatable(Util.makeDescriptionId("biome", key.location())))
                .orElseGet(() -> Component.translatable(KEY + "void.vanilla_biomes"));
    }

    /**
     * Opens the vanilla single biome screen for one dimension. The screen preselects the first biome of the dimension's source.
     * It reads that source from the Overworld of its context, so its context holds the edited dimension as the Overworld.
     */
    private void openBiomes(ResourceKey<LevelStem> stem) {
        WorldDimensions edited = this.presetBiomes.withBiomes(this.context.worldgenLoadContext(), this.preset, this.context.selectedDimensions(), this.biomes);
        LevelStem shown = edited.dimensions().get(stem);
        WorldCreationContext context = shown == null
                ? this.context
                : this.context.withSettings(this.context.options(), new WorldDimensions(Map.of(LevelStem.OVERWORLD, shown)));
        this.minecraft.setScreen(new CreateBuffetWorldScreen(this, context, biome -> this.biomes.put(stem, Optional.of(biome))));
    }

    /** Applies the edited dimensions to the world and the cloud height to the holder. */
    private void applyEdits() {
        Map<ResourceKey<LevelStem>, Optional<Holder<Biome>>> chosen = Map.copyOf(this.biomes);
        this.parent.getUiState().updateDimensions((registries, dimensions) -> this.dimensions(registries, dimensions, chosen));
        ShaftSettingsHolder holder = this.tools.holder();
        holder.set(holder.settings().withCloudHeight(this.cloudHeight));
    }

    /** Rebuilds the list entries from the edited values and keeps the scroll position. */
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

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

}
