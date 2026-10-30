package us.drullk.aerolite.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import us.drullk.aerolite.Aerolite;

/**
 * Carries the cloud height of the generator settings from the server to the client.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-30_01-09_0bfcebed-136b-4c6c-8d2b-c8166d6cf577.md}.
 *
 * @param cloudHeight the Y level of the clouds
 */
public record CloudHeightPayload(int cloudHeight) implements CustomPacketPayload {

    public static final Type<CloudHeightPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Aerolite.MODID, "cloud_height"));

    public static final StreamCodec<ByteBuf, CloudHeightPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CloudHeightPayload::cloudHeight,
            CloudHeightPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}
