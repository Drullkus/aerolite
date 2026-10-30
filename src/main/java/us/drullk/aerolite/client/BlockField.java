package us.drullk.aerolite.client;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A text field for one block state. It parses a block id or a block state string.
 * While it has focus and text, a dropdown lists the blocks whose id or name holds the text.
 * The screen draws the dropdown and sends clicks to it, so the dropdown covers the rows below.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 */
public class BlockField extends EditBox {

    public static final int MAX_SUGGESTIONS = 8;
    private static final int ENTRY_HEIGHT = 18;
    private static final int DROPDOWN_WIDTH = 160;
    private static final int INVALID_COLOR = 0xFFFF5555;
    private static final int BACKGROUND_COLOR = 0xF0101010;
    private static final int HIGHLIGHT_COLOR = 0xFF404060;
    private static final int OUTLINE_COLOR = 0xFFA0A0A0;
    private static final String DEFAULT_NAMESPACE = ResourceLocation.DEFAULT_NAMESPACE + ":";

    private final Consumer<BlockState> onChange;
    private final List<Block> suggestions = new ArrayList<>();
    private BlockState state;
    private boolean valid = true;
    private boolean dropdownOpen;
    private int highlighted;
    /** True while the field writes its own text, so the text change opens no dropdown. */
    private boolean writing;

    public BlockField(Font font, BlockState state, Component name, Consumer<BlockState> onChange) {
        super(font, SettingsList.ROW_WIDTH, 20, name);
        this.state = state;
        this.onChange = onChange;
        this.setMaxLength(256);
        this.setHint(name);
        this.writeText(this.text(state));
        this.setResponder(this::onTextChange);
    }

    /** The text of a state: the block id without the default namespace, with the properties when they differ from the default. */
    private String text(BlockState state) {
        String text = state == state.getBlock().defaultBlockState()
                ? BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()
                : BlockStateParser.serialize(state);
        return text.startsWith(DEFAULT_NAMESPACE) ? text.substring(DEFAULT_NAMESPACE.length()) : text;
    }

    /** The state of a text, or null when the text is no block state or names air. */
    @Nullable
    private BlockState parse(String text) {
        StringReader reader = new StringReader(text.trim());
        try {
            BlockState parsed = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), reader, false).blockState();
            return reader.canRead() || parsed.isAir() ? null : parsed;
        } catch (CommandSyntaxException exception) {
            return null;
        }
    }

    public BlockState state() {
        return this.state;
    }

    private void writeText(String text) {
        this.writing = true;
        this.setValue(text);
        this.writing = false;
    }

    private void onTextChange(String text) {
        BlockState parsed = this.parse(text);
        this.valid = parsed != null;
        if (parsed != null && parsed != this.state) {
            this.state = parsed;
            this.onChange.accept(parsed);
        }
        if (!this.writing) {
            this.updateSuggestions(text);
            this.dropdownOpen = true;
        }
    }

    private void updateSuggestions(String text) {
        this.suggestions.clear();
        this.highlighted = 0;
        int properties = text.indexOf('[');
        String query = (properties < 0 ? text : text.substring(0, properties)).trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return;
        }
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block.defaultBlockState().isAir()) {
                continue;
            }
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            if (id.contains(query) || block.getName().getString().toLowerCase(Locale.ROOT).contains(query)) {
                this.suggestions.add(block);
                if (this.suggestions.size() == MAX_SUGGESTIONS) {
                    return;
                }
            }
        }
    }

    /** True when the dropdown shows. */
    public boolean dropdownVisible() {
        return this.dropdownOpen && this.isFocused() && this.isVisible() && !this.getValue().isEmpty() && !this.suggestions.isEmpty();
    }

    public void closeDropdown() {
        this.dropdownOpen = false;
    }

    private void pick(Block block) {
        BlockState picked = block.defaultBlockState();
        this.writeText(this.text(picked));
        this.dropdownOpen = false;
    }

    private int dropdownX(int screenWidth) {
        return Math.max(0, Math.min(this.getX(), screenWidth - DROPDOWN_WIDTH));
    }

    private int dropdownY() {
        return this.getY() + this.getHeight();
    }

    /** Draws the dropdown below the field. */
    public void renderDropdown(GuiGraphics graphics, Font font, int screenWidth, int mouseX, int mouseY) {
        int x = this.dropdownX(screenWidth);
        int y = this.dropdownY();
        int height = this.suggestions.size() * ENTRY_HEIGHT;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.fill(x, y, x + DROPDOWN_WIDTH, y + height, BACKGROUND_COLOR);
        for (int k = 0; k < this.suggestions.size(); k++) {
            int top = y + k * ENTRY_HEIGHT;
            boolean hovered = mouseX >= x && mouseX < x + DROPDOWN_WIDTH && mouseY >= top && mouseY < top + ENTRY_HEIGHT;
            if (hovered || k == this.highlighted) {
                graphics.fill(x, top, x + DROPDOWN_WIDTH, top + ENTRY_HEIGHT, HIGHLIGHT_COLOR);
            }
            Block block = this.suggestions.get(k);
            graphics.renderItem(new ItemStack(block.asItem()), x + 1, top + 1);
            String name = font.plainSubstrByWidth(block.getName().getString(), DROPDOWN_WIDTH - 22);
            graphics.drawString(font, name, x + 20, top + (ENTRY_HEIGHT - font.lineHeight) / 2 + 1, 0xFFFFFFFF);
        }
        graphics.renderOutline(x, y, DROPDOWN_WIDTH, height, OUTLINE_COLOR);
        graphics.pose().popPose();
    }

    /** Picks the dropdown entry under the mouse. Returns true when the click hits the dropdown. */
    public boolean dropdownClicked(double mouseX, double mouseY, int screenWidth) {
        int x = this.dropdownX(screenWidth);
        int y = this.dropdownY();
        if (mouseX < x || mouseX >= x + DROPDOWN_WIDTH || mouseY < y || mouseY >= y + this.suggestions.size() * ENTRY_HEIGHT) {
            return false;
        }
        this.pick(this.suggestions.get((int) ((mouseY - y) / ENTRY_HEIGHT)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.dropdownVisible()) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_DOWN -> {
                    this.highlighted = (this.highlighted + 1) % this.suggestions.size();
                    return true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    this.highlighted = (this.highlighted + this.suggestions.size() - 1) % this.suggestions.size();
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    this.pick(this.suggestions.get(this.highlighted));
                    return true;
                }
                default -> {
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            this.dropdownOpen = false;
        }
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        if (!this.valid) {
            graphics.renderOutline(this.getX(), this.getY(), this.getWidth(), this.getHeight(), INVALID_COLOR);
        }
    }

}
