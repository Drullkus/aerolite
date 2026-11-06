package us.drullk.aerolite.world.level;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import tamaized.beanification.Component;
import tamaized.beanification.PostConstruct;
import us.drullk.aerolite.Aerolite;
import us.drullk.aerolite.world.AeroliteSettings;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Carries the settings of the world creation menu to the integrated server of the new world.
 * The client offers the settings before the server starts. The server claims them at its start, and only for a new world.
 * A world is new while its Overworld data is not initialized, so the world has no spawn yet.
 * An old world drops the offered settings. The server stop clears both values.
 * On a dedicated server, {@link DedicatedDefaultPillars} makes the offer.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-06_00-00_10881e45-2016-40f4-83b8-2c27043cd0f4.md}.
 */
@Component
public final class PendingShaftSettings {

    /** The client thread writes this value. The server thread reads it. */
    @Nullable
    private volatile AeroliteSettings offered;
    /** The settings of the running server. */
    @Nullable
    private volatile AeroliteSettings claimed;

    public PendingShaftSettings() {
    }

    @PostConstruct
    public void register() {
        NeoForge.EVENT_BUS.addListener(this::onAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onStopped);
    }

    /** Offers the settings to the next server start. A later offer replaces an earlier one. */
    public void offer(AeroliteSettings settings) {
        this.offered = settings;
    }

    /** Drops the offered settings. The claimed settings of a running server stay. */
    public void clear() {
        this.offered = null;
    }

    /** The offered settings that no server has claimed. */
    public Optional<AeroliteSettings> offered() {
        return Optional.ofNullable(this.offered);
    }

    /** The settings that the running server claimed. */
    public Optional<AeroliteSettings> claimed() {
        return Optional.ofNullable(this.claimed);
    }

    /** Takes the offered settings. A new world claims them. An old world drops them. */
    public Optional<AeroliteSettings> claim(boolean newWorld) {
        AeroliteSettings settings = this.offered;
        this.offered = null;
        this.claimed = newWorld ? settings : null;
        return this.claimed();
    }

    /** Clears the offered and the claimed settings. */
    public void release() {
        this.offered = null;
        this.claimed = null;
    }

    /** The overworld data of a world without a spawn is not initialized. The server sets the spawn after the Overworld load. */
    private void onAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        boolean newWorld = !server.getWorldData().overworldData().isInitialized();
        boolean offeredAny = this.offered != null;
        if (this.claim(newWorld).isPresent()) {
            Aerolite.LOGGER.info("The new world takes the offered shaft settings");
        } else if (offeredAny) {
            Aerolite.LOGGER.info("Dropped the offered shaft settings, because the world is not new");
        }
    }

    private void onStopped(ServerStoppedEvent event) {
        this.release();
    }

}
