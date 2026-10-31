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
import tamaized.beanification.Autowired;
import us.drullk.aerolite.world.level.LevelShafts;
import us.drullk.aerolite.world.level.LevelShaftsBinding;

import java.util.function.IntConsumer;

/**
 * Sends the cloud height of the player's level to each player on login, respawn and dimension change.
 * The cloud height comes from the shaft data of the level. A level without a cloud height sends nothing.
 * The client gives the received value to its receiver.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-31_06-49_80f433d5-803f-4444-95a1-477f494ca3c5.md}.
 */
@Component
public final class CloudHeightSync {

    private static final String PROTOCOL_VERSION = "1";

    /** Only the client sets a receiver. A dedicated server never receives the payload. */
    private IntConsumer receiver = cloudHeight -> {
    };

    private final LevelShaftsBinding binding;

    public CloudHeightSync(@Autowired LevelShaftsBinding binding) {
        this.binding = binding;
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
        if (!(player instanceof ServerPlayer serverPlayer) || !serverPlayer.connection.hasChannel(CloudHeightPayload.TYPE)) {
            return;
        }
        LevelShafts shafts = this.binding.get(serverPlayer.serverLevel());
        if (shafts != null && shafts.cloudHeight().isPresent()) {
            PacketDistributor.sendToPlayer(serverPlayer, new CloudHeightPayload(shafts.cloudHeight().getAsInt()));
        }
    }

}
