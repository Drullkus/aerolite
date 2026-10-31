package us.drullk.aerolite.client;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * A slider over a fixed count of steps. Each step maps to one setting value. The list row sets its width.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_01-46_f1e9d346-7ee2-499e-8bdf-81792143696f.md}.
 */
public final class StepSlider extends AbstractSliderButton {

    private final int steps;
    private final IntFunction<Component> message;
    private final IntConsumer onStep;

    public StepSlider(int steps, int step, IntFunction<Component> message, IntConsumer onStep) {
        super(0, 0, SettingsList.ROW_WIDTH, 20, CommonComponents.EMPTY, Mth.clamp(step / (double) steps, 0, 1));
        this.steps = steps;
        this.message = message;
        this.onStep = onStep;
        this.updateMessage();
    }

    private int step() {
        return (int) Math.round(this.value * this.steps);
    }

    /** Moves the slider without a change callback. */
    public void setStep(int step) {
        this.value = Mth.clamp(step / (double) this.steps, 0, 1);
        this.updateMessage();
    }

    @Override
    protected void updateMessage() {
        this.setMessage(this.message.apply(this.step()));
    }

    @Override
    protected void applyValue() {
        this.value = this.step() / (double) this.steps;
        this.onStep.accept(this.step());
    }

}
