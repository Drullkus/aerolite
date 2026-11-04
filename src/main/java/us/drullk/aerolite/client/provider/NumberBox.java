package us.drullk.aerolite.client.provider;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import us.drullk.aerolite.client.SettingsList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A text field for a number or a list of numbers. Bad text gives the field a red border. The value keeps the last good parse.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 *
 * @param <T> the type of the value
 */
public class NumberBox<T> extends EditBox {

    private static final int INVALID_COLOR = 0xFFFF5555;
    private static final int MAX_LENGTH = 128;

    private final Function<String, T> parse;
    private boolean valid;

    /**
     * @param parse gives the value of a text, or null for bad text
     */
    public NumberBox(Font font, String text, Component name, Function<String, T> parse, Consumer<T> onValue) {
        super(font, SettingsList.ROW_WIDTH, 20, name);
        this.parse = parse;
        this.setMaxLength(MAX_LENGTH);
        this.setValue(text);
        this.valid = parse.apply(text) != null;
        this.setResponder(value -> {
            T parsed = this.parse.apply(value);
            this.valid = parsed != null;
            if (parsed != null) {
                onValue.accept(parsed);
            }
        });
    }

    /** True when the text parses. */
    public boolean valid() {
        return this.valid;
    }

    /** The visible part of the text follows the width, so the text shows from its start. */
    @Override
    public void setWidth(int width) {
        super.setWidth(width);
        this.moveCursorToStart(false);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        if (!this.valid) {
            graphics.renderOutline(this.getX(), this.getY(), this.getWidth(), this.getHeight(), INVALID_COLOR);
        }
    }

    /** A whole number, or null. */
    @Nullable
    public static Long parseLong(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** A whole number of the int range from {@code min} to {@code max}, or null. */
    @Nullable
    public static Integer parseInt(String text, int min, int max) {
        Long value = parseLong(text);
        return value == null || value < min || value > max ? null : value.intValue();
    }

    /** A finite number from {@code min} to {@code max}, or null. An open bound excludes its value. */
    @Nullable
    public static Float parseFloat(String text, float min, boolean openMin, float max) {
        try {
            float value = Float.parseFloat(text.trim());
            boolean aboveMin = openMin ? value > min : value >= min;
            return Float.isFinite(value) && aboveMin && value <= max ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** A comma list of one or more finite numbers, or null. */
    @Nullable
    public static List<Double> parseDoubles(String text) {
        List<Double> values = new ArrayList<>();
        for (String part : text.split(",")) {
            try {
                double value = Double.parseDouble(part.trim());
                if (!Double.isFinite(value)) {
                    return null;
                }
                values.add(value);
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return values.isEmpty() ? null : values;
    }

    /** The text of a number list, such as {@code 1.0, 0.5}. */
    public static String doublesText(List<Double> values) {
        return String.join(", ", values.stream().map(String::valueOf).toList());
    }

}
