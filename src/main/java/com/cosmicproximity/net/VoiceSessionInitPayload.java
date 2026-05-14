package com.cosmicproximity.net;

import com.cosmicproximity.CosmicProximity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * Server → Client. Sent once after login.
 * Tells the client where the voice UDP socket is and gives it the session key.
 *
 * NOTE: The AES key travels over Minecraft's TCP channel, which is encrypted only on
 * online-mode servers (Mojang auth). Offline-mode servers transmit this in cleartext.
 * Phase 2 will replace this with an ECDH handshake.
 */
public record VoiceSessionInitPayload(int udpPort, UUID sessionId, byte[] aesKey, double voiceRange)
        implements CustomPacketPayload {

    public static final Type<VoiceSessionInitPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(CosmicProximity.MOD_ID, "session_init"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VoiceSessionInitPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.udpPort());
                        buf.writeUUID(p.sessionId());
                        buf.writeByteArray(p.aesKey());
                        buf.writeDouble(p.voiceRange());
                    },
                    buf -> new VoiceSessionInitPayload(
                            buf.readVarInt(),
                            buf.readUUID(),
                            buf.readByteArray(64),
                            buf.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
