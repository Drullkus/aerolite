package us.drullk.aerolite.devtools.providers;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import us.drullk.aerolite.client.SettingsList;

import javax.annotation.Nullable;

/**
 * The hub of the provider editor prototypes. It lists one button per permutation.
 * A button says "(kept)" when Done kept a sample of that permutation. Reset Samples drops the kept samples.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public class HubScreen extends Screen {

    @Nullable
    private final Screen parent;
    private final ProviderHub hub;

    public HubScreen(@Nullable Screen parent, ProviderHub hub) {
        super(Component.literal("Provider Editor Prototypes"));
        this.parent = parent;
        this.hub = hub;
    }

    @Override
    protected void init() {
        HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
        layout.addTitleHeader(this.title, this.font);
        SettingsList list = layout.addToContents(new SettingsList(this.minecraft, this.width, layout));
        for (Permutation permutation : Permutation.values()) {
            String text = permutation.title() + (this.hub.edited(permutation) ? " (kept)" : "");
            list.addRow(Button.builder(Component.literal(text), button -> this.hub.open(permutation, this))
                    .tooltip(Tooltip.create(Component.literal(permutation.description())))
                    .build());
        }
        list.addSpacer();
        list.addRow(Button.builder(Component.literal("Reset Samples"), button -> {
            this.hub.reset();
            this.rebuildWidgets();
        }).tooltip(Tooltip.create(Component.literal("Drop the kept samples of every permutation"))).build());
        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).build());
        layout.visitWidgets(this::addRenderableWidget);
        layout.arrangeElements();
        list.updateSize(this.width, layout);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

}
