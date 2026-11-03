package us.drullk.aerolite.devtools.providers;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A button that shows the summary of a closed field. It reads the text again on each frame, so an edit shows at once.
 * A long text scrolls on hover, like the text of any vanilla button. The tooltip shows the full text.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public class SummaryButton extends Button {

    private final Supplier<String> text;
    private final Supplier<String> tooltip;
    private String shownTooltip = "";

    public SummaryButton(Supplier<String> text, Supplier<String> tooltip, OnPress onPress) {
        super(0, 0, 20, 20, Component.literal(text.get()), onPress, DEFAULT_NARRATION);
        this.text = text;
        this.tooltip = tooltip;
        this.refresh();
    }

    private void refresh() {
        this.setMessage(Component.literal(this.text.get()));
        String next = this.tooltip.get();
        if (!Objects.equals(next, this.shownTooltip)) {
            this.shownTooltip = next;
            this.setTooltip(Tooltip.create(Component.literal(next)));
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.refresh();
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }

}
