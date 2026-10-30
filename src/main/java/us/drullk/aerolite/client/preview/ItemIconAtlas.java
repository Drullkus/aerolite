package us.drullk.aerolite.client.preview;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Matrix4f;
import tamaized.beanification.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders item icons on demand into one render target. The ImGui panel shows the cells of the target.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 */
@Component(dist = Dist.CLIENT)
public final class ItemIconAtlas {

    public static final int CELL = 16;
    private static final int COLUMNS = 16;
    private static final int START_ROWS = 4;

    /** The texture coordinates of one cell. The v axis points up, as in every render target. */
    public record Cell(float u0, float v0, float u1, float v1) {
    }

    private final Map<Item, Integer> slots = new HashMap<>();
    private final List<Item> pending = new ArrayList<>();
    @Nullable
    private TextureTarget target;
    private int rows = START_ROWS;

    public ItemIconAtlas() {
    }

    /** The render target of the atlas, or null before the first icon. */
    @Nullable
    public RenderTarget target() {
        return this.target;
    }

    /** The cell of the item, or null until the next {@link #flush} draws it. Air has no cell. */
    @Nullable
    public Cell cell(Item item) {
        if (item == Items.AIR) {
            return null;
        }
        Integer slot = this.slots.get(item);
        if (slot == null) {
            this.slots.put(item, this.slots.size());
            this.pending.add(item);
            return null;
        }
        if (this.target == null || this.pending.contains(item)) {
            return null;
        }
        int x = slot % COLUMNS * CELL;
        int y = slot / COLUMNS * CELL;
        float width = this.target.width;
        float height = this.target.height;
        return new Cell(x / width, 1 - y / height, (x + CELL) / width, 1 - (y + CELL) / height);
    }

    /** Draws the pending icons. Call it in a screen render, while the main render target is bound. */
    public void flush(GuiGraphics graphics) {
        if (this.pending.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int neededRows = (this.slots.size() + COLUMNS - 1) / COLUMNS;
        if (this.target == null || neededRows > this.rows) {
            while (neededRows > this.rows) {
                this.rows *= 2;
            }
            if (this.target != null) {
                this.target.destroyBuffers();
            }
            this.target = new TextureTarget(COLUMNS * CELL, this.rows * CELL, true, Minecraft.ON_OSX);
            this.target.setClearColor(0, 0, 0, 0);
            this.target.clear(Minecraft.ON_OSX);
            this.pending.clear();
            this.pending.addAll(this.slots.keySet());
        }
        graphics.flush();
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        this.target.bindWrite(true);
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, this.target.width, this.target.height, 0, 1000, ClientHooks.getGuiFarPlane()), VertexSorting.ORTHOGRAPHIC_Z);
        GuiGraphics atlas = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
        for (Item item : this.pending) {
            int slot = this.slots.get(item);
            atlas.renderItem(new ItemStack(item), slot % COLUMNS * CELL, slot / COLUMNS * CELL);
        }
        atlas.flush();
        this.pending.clear();
        minecraft.getMainRenderTarget().bindWrite(true);
        RenderSystem.setProjectionMatrix(projection, sorting);
    }

    /** Frees the render target. The next icon builds a new one. */
    public void clear() {
        if (this.target != null) {
            this.target.destroyBuffers();
            this.target = null;
        }
        this.slots.clear();
        this.pending.clear();
        this.rows = START_ROWS;
    }

}
