package us.drullk.aerolite.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A 20x20 button that draws the join arrow of the vanilla server list, with the highlighted arrow on hover.
 * The sprite draws at its own size of 32 pixels, so each texel stays one GUI pixel.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_e43f73bd-d689-4553-8382-7b011c0c142a.md}.
 */
public class ArrowButton extends Button {

    public static final int SIZE = 20;
    private static final ResourceLocation SPRITE = ResourceLocation.withDefaultNamespace("server_list/join");
    private static final ResourceLocation HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("server_list/join_highlighted");
    private static final int SPRITE_SIZE = 32;
    /** The arrow of the sprite has its center at this texel. */
    private static final int ARROW_CENTER_X = 23;
    private static final int ARROW_CENTER_Y = 16;

    /**
     * @param message the narration of the button; the button draws no text
     */
    public ArrowButton(Component message, OnPress onPress) {
        super(0, 0, SIZE, SIZE, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ResourceLocation sprite = this.isHoveredOrFocused() ? HIGHLIGHTED_SPRITE : SPRITE;
        int left = this.getX() + SIZE / 2 - ARROW_CENTER_X;
        int top = this.getY() + SIZE / 2 - ARROW_CENTER_Y;
        graphics.blitSprite(sprite, left, top, SPRITE_SIZE, SPRITE_SIZE);
    }

}
