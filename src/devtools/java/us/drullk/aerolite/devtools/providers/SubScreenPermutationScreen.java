package us.drullk.aerolite.devtools.providers;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Permutation 2: a pushed sub-screen.
 * Each field has one row like a dimension row of the Aerolite Void screen: the label, the summary in gray and an Edit button.
 * A simple state keeps its block field in the place of the summary.
 * Edit pushes a {@link ProviderScreen} with its own Done and Cancel. Its Done writes the field to the copy of this screen.
 * This screen keeps its own Done and Cancel, so the player confirms on two levels.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public class SubScreenPermutationScreen extends PermutationScreen {

    private static final int BUTTON_PADDING = 8;

    public SubScreenPermutationScreen(Screen parent, ProviderHub hub, VariationSample sample, Consumer<VariationSample> onDone) {
        super(Permutation.SUB_SCREEN, parent, hub, sample, onDone);
    }

    @Override
    public void openField(int index) {
        Component title = Component.literal(VariationSample.label(index) + ": Amazing Pillars: Overworld");
        this.minecraft.setScreen(new ProviderScreen(this, this.hub, title, this.sample.fields.get(index).copy(), edited -> {
            this.sample.fields.set(index, edited);
            this.requestRebuild();
        }));
    }

    @Override
    protected void populateBlocks(ProviderList list) {
        list.addText(0, Component.literal("Blocks"));
        for (int index = 0; index < this.sample.fields.size(); index++) {
            int field = index;
            ProviderSample provider = this.sample.fields.get(index);
            AbstractWidget value;
            if (provider.type == ProviderType.SIMPLE) {
                value = this.closedField(list, index, "", () -> this.openField(field));
            } else {
                String summary = provider.typedSummary(this.hub.blocks());
                StringWidget text = new StringWidget(0, this.font.lineHeight, Component.literal(summary), this.font).alignLeft().setColor(ProviderList.LABEL_COLOR);
                text.setTooltip(Tooltip.create(Component.literal(provider.type.id() + "\n" + summary)));
                value = text;
            }
            Component editText = Component.literal("Edit");
            Button edit = Button.builder(editText, button -> this.openField(field))
                    .width(this.font.width(editText) + 2 * BUTTON_PADDING)
                    .createNarration(message -> Component.empty().append(message.get()).append(" ").append(VariationSample.label(field)))
                    .build();
            list.addLine(0, 1, list.label(Component.literal(VariationSample.label(index))), value, edit);
        }
    }

}
