package us.drullk.aerolite.client;

import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import tamaized.beanification.Autowired;
import tamaized.beanification.PostConstruct;
import us.drullk.aerolite.world.island.GraphSettings;
import us.drullk.aerolite.world.island.IslandPack;
import us.drullk.aerolite.world.island.graph.IslandGraphs;

import javax.annotation.Nullable;

/**
 * Holds the graphs of the island editor for the open world creation flow. The Islands screen reads and writes them.
 * The first read of a flow loads the router graphs from the dimensions of the world being created.
 * When the temporary data pack folder of the flow holds an island pack, the first read also restores the graphs from it.
 * The graphs also hold the island variations and lists of the flow. The copy and the restore carry them.
 * Done in the Islands screen keeps the graphs and writes the island pack through {@link IslandPackApplier}.
 * A new create world screen, the screen "Preparing world data", the title screen and the world list end the flow and drop the graphs.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-08_04-54_0884af22-dc0d-468d-a9a1-af2cb172af8e.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-14_01-32_b8b553a3-01e1-4ff5-9079-c92db61fb325.md}.
 */
@tamaized.beanification.Component(dist = Dist.CLIENT)
public final class IslandGraphHolder {

    /** The title of the screen that the create world screen shows before it creates the world. */
    private static final Component PREPARING_WORLD_DATA = Component.translatable("createWorld.preparing");

    private final GraphSettings graphSettings;
    private final IslandPack pack;
    private final IslandPackApplier applier;
    @Nullable
    private IslandGraphs graphs;
    /** The create world screen of the open flow, or null when no flow is open. */
    @Nullable
    private CreateWorldScreen flow;

    public IslandGraphHolder(@Autowired GraphSettings graphSettings, @Autowired IslandPack pack, @Autowired IslandPackApplier applier) {
        this.graphSettings = graphSettings;
        this.pack = pack;
        this.applier = applier;
    }

    @PostConstruct
    public void register() {
        NeoForge.EVENT_BUS.addListener(this::onOpening);
    }

    /** A copy of the graphs of the flow. The first read of a flow loads them from the dimensions of the context. */
    public IslandGraphs graphs(WorldCreationContext context) {
        if (this.graphs == null) {
            this.graphs = this.graphSettings.start(context.selectedDimensions(), context.worldgenLoadContext());
            this.pack.restore(this.applier.existing(this.flow), this.graphs);
        }
        return this.graphs.copy();
    }

    /** Keeps a copy of the graphs for the flow. */
    public void set(IslandGraphs graphs) {
        this.graphs = graphs.copy();
    }

    /** Keeps a copy of the graphs for the flow and writes the island pack of the create world screen. */
    public void done(CreateWorldScreen screen, IslandGraphs graphs) {
        this.set(graphs);
        this.applier.apply(screen, graphs);
    }

    private void onOpening(ScreenEvent.Opening event) {
        Screen screen = event.getNewScreen();
        if (screen instanceof CreateWorldScreen create) {
            if (create != this.flow) {
                this.flow = create;
                this.graphs = null;
            }
        } else if (screen instanceof GenericMessageScreen && PREPARING_WORLD_DATA.equals(screen.getTitle())
                || screen instanceof TitleScreen || screen instanceof SelectWorldScreen) {
            this.flow = null;
            this.graphs = null;
        }
    }

}
