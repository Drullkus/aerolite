package us.drullk.aerolite.devtools.providers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * The pushed editor of one field, like the vanilla single biome screen.
 * It edits a copy of the field. Done gives the copy to the parent screen. Cancel and Escape drop it.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public class ProviderScreen extends Screen {

    private final Screen parent;
    private final ProviderSample field;
    private final Consumer<ProviderSample> onDone;
    private final Overlays overlays = new Overlays();
    private final ProviderEditor editor;
    @Nullable
    private ProviderList list;
    /** True when a change of the row set waits for the list rebuild of the next tick. */
    private boolean rebuildPending;

    public ProviderScreen(Screen parent, ProviderHub hub, Component title, ProviderSample field, Consumer<ProviderSample> onDone) {
        super(title);
        this.parent = parent;
        this.field = field;
        this.onDone = onDone;
        this.editor = new ProviderEditor(Minecraft.getInstance().font, hub.blocks(), this.overlays);
    }

    @Override
    protected void init() {
        HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
        layout.addTitleHeader(this.title, this.font);
        if (this.list != null) {
            this.overlays.forget(this.list);
        }
        ProviderList list = layout.addToContents(new ProviderList(this.minecraft, this.width, layout));
        this.list = list;
        this.editor.addRows(list, 0, this.field, () -> this.rebuildPending = true);
        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> {
            this.onDone.accept(this.field);
            this.onClose();
        }).build());
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).build());
        layout.visitWidgets(this::addRenderableWidget);
        layout.arrangeElements();
        list.updateSize(this.width, layout);
    }

    @Override
    public void tick() {
        super.tick();
        ProviderList list = this.list;
        if (this.rebuildPending && list != null) {
            this.rebuildPending = false;
            double scroll = list.getScrollAmount();
            this.overlays.forget(list);
            list.clear();
            this.editor.addRows(list, 0, this.field, () -> this.rebuildPending = true);
            list.setScrollAmount(scroll);
        }
    }

    private boolean shown(ProviderList candidate) {
        return candidate == this.list;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.overlays.render(graphics, this.font, this.width, this.height, mouseX, mouseY, this::shown);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.overlays.mouseClicked(mouseX, mouseY, this.width, this.height, this::shown)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.overlays.escape(this::shown)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Cancel and Escape return without the edits. */
    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

}
