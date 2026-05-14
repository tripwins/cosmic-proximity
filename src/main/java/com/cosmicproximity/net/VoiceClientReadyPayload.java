package com.cosmicproximity.net;

import com.cosmicproximity.CosmicProximity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → Server marker. Sent after the client has bound its UDP socket
 * and is ready to receive voice routing decisions. No payload body.
 */
public record VoiceClientReadyPayload() implements CustomPacketPayload {
    public static final VoiceClientReadyPayload INSTANCE = new VoiceClientReadyPayload();

    public static final Type<VoiceClientReadyPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(CosmicProximity.MOD_ID, "client_ready"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VoiceClientReadyPayload> CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
