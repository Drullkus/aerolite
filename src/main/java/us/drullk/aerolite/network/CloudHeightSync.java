package us.drullk.aerolite.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import tamaized.beanification.Component;
import tamaized.beanification.PostConstruct;
import us.drullk.aerolite.world.AeroliteChunkGenerator;

import java.util.function.IntConsumer;

/**
 * Sends the cloud height of the Aerolite generator to each player on login, respawn and dimension change.
 * The client gives the received value to its receiver.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 */
@Component
public final class CloudHeightSync {

    private static final String PROTOCOL_VERSION = "1";

    /** Only the client sets a receiver. A dedicated server never receives the payload. */
    private IntConsumer receiver = cloudHeight -> {
    };

    public CloudHeightSync() {
    }

    public void setReceiver(IntConsumer receiver) {
        this.receiver = receiver;
    }

    @PostConstruct
    public void register(IEventBus modEventBus) {
        modEventBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(this::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onRespawn);
        NeoForge.EVENT_BUS.addListener(this::onChangedDimension);
    }

    /** The payload is optional, so a client with Aerolite can join a server without it. */
    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION).optional().playToClient(CloudHeightPayload.TYPE, CloudHeightPayload.STREAM_CODEC,
                (payload, context) -> this.receiver.accept(payload.cloudHeight()));
    }

    private void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        this.send(event.getEntity());
    }

    private void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        this.send(event.getEntity());
    }

    private void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        this.send(event.getEntity());
    }

    private void send(Player player) {
        if (player instanceof ServerPlayer serverPlayer
                && serverPlayer.serverLevel().getChunkSource().getGenerator() instanceof AeroliteChunkGenerator generator
                && serverPlayer.connection.hasChannel(CloudHeightPayload.TYPE)) {
            PacketDistributor.sendToPlayer(serverPlayer, new CloudHeightPayload(generator.aeroliteSettings().cloudHeight()));
        }
    }

}
