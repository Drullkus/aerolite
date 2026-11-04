package us.drullk.aerolite.client.provider;

import com.mojang.serialization.DataResult;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import org.lwjgl.glfw.GLFW;
import us.drullk.aerolite.client.BlockField;
import us.drullk.aerolite.client.BlockSearch;
import us.drullk.aerolite.client.ChoiceDropdown;
import us.drullk.aerolite.client.ProviderText;
import us.drullk.aerolite.client.SettingsList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The editor of one block field, like the vanilla single biome screen.
 * It holds the type dropdown and the rows of the chosen type in one settings list, and a footer with Done and Cancel.
 * It edits a draft of the provider. Done gives the built provider to the parent screen. Cancel and Escape drop the draft.
 * Done is off while the values build no provider. Its tooltip then shows the reason.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public class ProviderScreen extends Screen {

    private final Screen parent;
    private final BlockSearch blocks;
    private final ProviderText text;
    private final ProviderDraft draft;
    private final Consumer<BlockStateProvider> onDone;
    private final List<BlockField> blockFields = new ArrayList<>();
    private final List<ChoiceDropdown<?>> dropdowns = new ArrayList<>();
    @Nullable
    private SettingsList list;
    @Nullable
    private Button done;
    @Nullable
    private String shownError;
    /** True when a change of the row set waits for the list rebuild of the next tick. */
    private boolean rebuildPending;

    public ProviderScreen(Screen parent, BlockSearch blocks, ProviderText text, Component title, BlockStateProvider provider, Consumer<BlockStateProvider> onDone) {
        super(title);
        this.parent = parent;
        this.blocks = blocks;
        this.text = text;
        this.draft = ProviderDraft.of(provider);
        this.onDone = onDone;
    }

    /** The draft that the rows edit. */
    public ProviderDraft draft() {
        return this.draft;
    }

    @Override
    protected void init() {
        HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
        layout.addTitleHeader(this.title, this.font);
        SettingsList list = layout.addToContents(new SettingsList(this.minecraft, this.width, layout));
        this.list = list;
        this.populate(list);
        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        this.done = footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.done()).build());
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).build());
        layout.visitWidgets(this::addRenderableWidget);
        layout.arrangeElements();
        list.updateSize(this.width, layout);
        this.shownError = null;
        this.updateDone();
    }

    private void populate(SettingsList list) {
        this.blockFields.clear();
        this.dropdowns.clear();
        new ProviderRows(list, this.font, this.blocks, this.text, this.blockFields::add, this.dropdowns::add, () -> this.rebuildPending = true).add(this.draft);
    }

    private void done() {
        this.draft.build().ifSuccess(provider -> {
            this.onDone.accept(provider);
            this.onClose();
        });
    }

    /** Turns Done off while the draft builds no provider, with the reason as its tooltip. */
    private void updateDone() {
        Button button = this.done;
        if (button == null) {
            return;
        }
        DataResult<BlockStateProvider> built = this.draft.build();
        String error = built.error().map(DataResult.Error::message).orElse(null);
        button.active = error == null;
        if (!Objects.equals(error, this.shownError)) {
            this.shownError = error;
            button.setTooltip(error == null ? null : Tooltip.create(Component.literal(error)));
        }
    }

    @Override
    public void tick() {
        super.tick();
        SettingsList list = this.list;
        if (this.rebuildPending && list != null) {
            this.rebuildPending = false;
            double scroll = list.getScrollAmount();
            list.clear();
            this.populate(list);
            list.setScrollAmount(scroll);
        }
        this.updateDone();
    }

    /** The block field whose dropdown shows inside the list area, or null. */
    @Nullable
    private BlockField openField() {
        SettingsList list = this.list;
        if (list == null) {
            return null;
        }
        for (BlockField field : this.blockFields) {
            if (field.dropdownVisible() && field.getY() >= list.getY() && field.getBottom() <= list.getBottom()) {
                return field;
            }
        }
        return null;
    }

    /** The combo whose list shows while its button lies inside the list area, or null. It closes the other lists. */
    @Nullable
    private ChoiceDropdown<?> openChoice() {
        SettingsList list = this.list;
        ChoiceDropdown<?> open = null;
        for (ChoiceDropdown<?> dropdown : this.dropdowns) {
            boolean inside = list != null && dropdown.getY() >= list.getY() && dropdown.getBottom() <= list.getBottom();
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
        BlockField field = this.openField();
        if (field != null) {
            field.renderDropdown(graphics, this.font, this.width, mouseX, mouseY);
        }
        ChoiceDropdown<?> choice = this.openChoice();
        if (choice != null) {
            this.clearTooltipForNextRenderPass();
            choice.renderList(graphics, this.font, this.height, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ChoiceDropdown<?> choice = this.openChoice();
        if (choice != null) {
            choice.listClicked(mouseX, mouseY, this.height);
            choice.closeList();
            return true;
        }
        BlockField field = this.openField();
        if (field != null && field.dropdownClicked(mouseX, mouseY, this.width)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            ChoiceDropdown<?> choice = this.openChoice();
            if (choice != null) {
                choice.closeList();
                return true;
            }
            BlockField field = this.openField();
            if (field != null) {
                field.closeDropdown();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Cancel and Escape return without the edits. */
    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

}
