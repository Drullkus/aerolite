package us.drullk.aerolite.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import tamaized.beanification.Autowired;
import tamaized.beanification.PostConstruct;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The row "Amazing Pillars" of the World tab of the create world screen: a label, the button Load Defaults and the button Settings.
 * The screen init event adds the widgets. Each frame places them one switch row under the Bonus Chest row, like a third switch row.
 * The widgets show only while the screen holds the Bonus Chest label, so only on the World tab.
 * Load Defaults is on only while the settings are blank.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
@tamaized.beanification.Component(dist = Dist.CLIENT)
public final class WorldTabRow {

    private static final String KEY = "createWorld.customize.aerolite.";
    /** The label of the Bonus Chest switch row of the vanilla World tab. */
    private static final Component BONUS_CHEST = Component.translatable("selectWorld.bonusItems");
    /** The row spacing of the vanilla switch grid. */
    private static final int ROW_SPACING = 4;
    private static final int BUTTON_GAP = 4;
    /** The space on each side of the text of a row button. */
    private static final int BUTTON_PADDING = 8;
    private static final int BUTTON_HEIGHT = 20;

    private final PresetTools tools;
    /** The create world screen that holds the widgets below. */
    @Nullable
    private CreateWorldScreen screen;
    @Nullable
    private StringWidget label;
    @Nullable
    private Button loadDefaults;
    @Nullable
    private Button settings;

    public WorldTabRow(@Autowired AeroliteClient client) {
        this.tools = client.tools();
    }

    @PostConstruct
    public void register() {
        NeoForge.EVENT_BUS.addListener(this::onInit);
        NeoForge.EVENT_BUS.addListener(this::onRender);
    }

    private void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CreateWorldScreen create)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        StringWidget label = new StringWidget(Component.translatable(KEY + "pillars"), minecraft.font).alignLeft();
        Component loadText = Component.translatable(KEY + "load_defaults");
        Button loadDefaults = Button.builder(loadText, button -> this.loadDefaults(create))
                .size(minecraft.font.width(loadText) + 2 * BUTTON_PADDING, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable(KEY + "load_defaults_tooltip")))
                .build();
        Component settingsText = Component.translatable(KEY + "settings");
        Button settings = Button.builder(settingsText, button -> minecraft.setScreen(new AerolitePresetScreen(create, this.tools)))
                .size(minecraft.font.width(settingsText) + 2 * BUTTON_PADDING, BUTTON_HEIGHT)
                .build();
        event.addListener(label);
        event.addListener(loadDefaults);
        event.addListener(settings);
        this.screen = create;
        this.label = label;
        this.loadDefaults = loadDefaults;
        this.settings = settings;
        this.place(create);
    }

    private void onRender(ScreenEvent.Render.Pre event) {
        if (event.getScreen() == this.screen) {
            this.place(this.screen);
        }
    }

    /** Adds every dimension of the world that has its own variation list. Blank settings are the only settings it replaces. */
    private void loadDefaults(CreateWorldScreen create) {
        ShaftSettingsHolder holder = this.tools.holder();
        if (!holder.settings().dimensions().isEmpty()) {
            return;
        }
        WorldCreationContext context = create.getUiState().getSettings();
        PresetEdits edits = this.tools.edits();
        edits.useRegistries(context.worldgenLoadContext());
        holder.set(edits.withDefaults(holder.settings(), edits.worldDimensions(context)));
    }

    /** Places the row under the Bonus Chest row, or hides it when the World tab is not selected. */
    private void place(CreateWorldScreen create) {
        StringWidget label = this.label;
        Button loadDefaults = this.loadDefaults;
        Button settings = this.settings;
        if (label == null || loadDefaults == null || settings == null) {
            return;
        }
        List<? extends GuiEventListener> children = create.children();
        StringWidget bonusLabel = bonusLabel(children);
        AbstractWidget bonusSwitch = bonusLabel == null ? null : bonusSwitch(children, bonusLabel);
        boolean shown = bonusSwitch != null;
        label.visible = shown;
        loadDefaults.visible = shown;
        settings.visible = shown;
        loadDefaults.active = this.tools.holder().settings().dimensions().isEmpty();
        if (!shown) {
            return;
        }
        int top = bonusSwitch.getBottom() + ROW_SPACING;
        label.setPosition(bonusLabel.getX(), top + bonusLabel.getY() - bonusSwitch.getY());
        settings.setPosition(bonusSwitch.getRight() - settings.getWidth(), top);
        loadDefaults.setPosition(settings.getX() - BUTTON_GAP - loadDefaults.getWidth(), top);
        label.setWidth(Math.max(0, loadDefaults.getX() - BUTTON_GAP - label.getX()));
    }

    /** The label of the Bonus Chest switch. The vanilla World tab adds it only while the tab is selected. */
    @Nullable
    private static StringWidget bonusLabel(List<? extends GuiEventListener> children) {
        for (GuiEventListener child : children) {
            if (child instanceof StringWidget widget && BONUS_CHEST.equals(widget.getMessage())) {
                return widget;
            }
        }
        return null;
    }

    /** The switch on the row of the label, right of it. */
    @Nullable
    private static AbstractWidget bonusSwitch(List<? extends GuiEventListener> children, StringWidget label) {
        int middle = label.getY() + label.getHeight() / 2;
        for (GuiEventListener child : children) {
            if (child instanceof CycleButton<?> button && button.getX() > label.getX() && button.getY() <= middle && middle < button.getBottom()) {
                return button;
            }
        }
        return null;
    }

}
