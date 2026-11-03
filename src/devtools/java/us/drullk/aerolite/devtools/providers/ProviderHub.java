package us.drullk.aerolite.devtools.providers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import us.drullk.aerolite.client.BlockSearch;

import java.util.EnumMap;
import java.util.Map;

/**
 * The hub of the provider editor prototypes and its memory for the game session.
 * Each permutation keeps its own sample. Done in a permutation screen keeps the edited sample here. Cancel keeps nothing.
 * The client command {@code /aerolitedevtools providers} opens the hub.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class ProviderHub {

    private final BlockSearch blocks = new BlockSearch();
    private final Map<Permutation, VariationSample> saved = new EnumMap<>(Permutation.class);

    public BlockSearch blocks() {
        return this.blocks;
    }

    public void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("aerolitedevtools")
                .then(Commands.literal("providers").executes(context -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreen(new HubScreen(minecraft.screen, this));
                    return 1;
                })));
    }

    /** True when Done kept a sample of the permutation in this session. */
    public boolean edited(Permutation permutation) {
        return this.saved.containsKey(permutation);
    }

    /** Drops every kept sample. */
    public void reset() {
        this.saved.clear();
    }

    /** Opens the screen of a permutation with a copy of its kept sample, or of the default sample. */
    public PermutationScreen open(Permutation permutation, Screen parent) {
        VariationSample kept = this.saved.get(permutation);
        VariationSample sample = kept != null ? kept.copy() : VariationSample.defaults();
        PermutationScreen screen = permutation.create(parent, this, sample, edited -> this.saved.put(permutation, edited.copy()));
        Minecraft.getInstance().setScreen(screen);
        return screen;
    }

}
