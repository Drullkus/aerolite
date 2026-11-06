package us.drullk.aerolite.client;

import com.google.common.collect.ImmutableMap;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import tamaized.beanification.Autowired;
import tamaized.beanification.PostConstruct;
import us.drullk.aerolite.AeroliteCommonConfig;
import us.drullk.aerolite.world.AeroliteSettings;
import us.drullk.aerolite.world.level.PendingShaftSettings;

import javax.annotation.Nullable;

/**
 * Holds the shaft settings of the open world creation flow. The shafts screen, the dimension screen and the preview edit them.
 * A new create world screen starts the flow with the default pillars while the common config asks for them, else with blank settings.
 * Blank settings hold no dimension and the default cloud height.
 * The screen "Preparing world data" of the flow hands settings that are not blank to the pending settings of the server.
 * The title screen and the world list end the flow. They reset the settings and clear the pending settings.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-06_00-00_10881e45-2016-40f4-83b8-2c27043cd0f4.md}.
 */
@tamaized.beanification.Component(dist = Dist.CLIENT)
public final class ShaftSettingsHolder {

    /** The settings of a flow before any edit. They give no shafts. */
    public static final AeroliteSettings BLANK = new AeroliteSettings(AeroliteSettings.DEFAULT_CLOUD_HEIGHT, ImmutableMap.of());

    /** The title of the screen that the create world screen shows before it creates the world. */
    private static final Component PREPARING_WORLD_DATA = Component.translatable("createWorld.preparing");

    private final PendingShaftSettings pending;
    private final AeroliteCommonConfig config;
    private final PresetEdits edits;
    private AeroliteSettings settings = BLANK;
    /** The create world screen of the open flow, or null when no flow is open. */
    @Nullable
    private CreateWorldScreen flow;

    public ShaftSettingsHolder(@Autowired PendingShaftSettings pending, @Autowired AeroliteCommonConfig config, @Autowired PresetEdits edits) {
        this.pending = pending;
        this.config = config;
        this.edits = edits;
    }

    @PostConstruct
    public void register() {
        NeoForge.EVENT_BUS.addListener(this::onOpening);
    }

    public AeroliteSettings settings() {
        return this.settings;
    }

    public void set(AeroliteSettings settings) {
        this.settings = settings;
    }

    /** True when the settings hold no dimension and the default cloud height. */
    public boolean blank() {
        return isBlank(this.settings);
    }

    public static boolean isBlank(AeroliteSettings settings) {
        return settings.dimensions().isEmpty() && settings.cloudHeight() == AeroliteSettings.DEFAULT_CLOUD_HEIGHT;
    }

    private void onOpening(ScreenEvent.Opening event) {
        Screen screen = event.getNewScreen();
        if (screen instanceof CreateWorldScreen create) {
            if (create != this.flow) {
                this.flow = create;
                this.reset();
                if (this.config.applyDefaultPillars()) {
                    this.settings = this.defaults(create.getUiState().getSettings());
                }
            }
        } else if (screen instanceof GenericMessageScreen && PREPARING_WORLD_DATA.equals(screen.getTitle())) {
            if (this.flow != null && !this.blank()) {
                this.pending.offer(this.settings);
            }
            this.flow = null;
            this.settings = BLANK;
        } else if (screen instanceof TitleScreen || screen instanceof SelectWorldScreen) {
            this.flow = null;
            this.reset();
        }
    }

    /** The default pillars of every dimension of the world being created. */
    private AeroliteSettings defaults(WorldCreationContext context) {
        this.edits.useRegistries(context.worldgenLoadContext());
        return this.edits.withDefaults(BLANK, this.edits.worldDimensions(context));
    }

    private void reset() {
        this.settings = BLANK;
        this.pending.clear();
    }

}
