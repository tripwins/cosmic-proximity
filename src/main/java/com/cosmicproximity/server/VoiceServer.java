package com.cosmicproximity.server;

import com.cosmicproximity.CosmicProximity;
import com.cosmicproximity.config.ServerConfig;
import com.cosmicproximity.net.CryptoUtil;
import com.cosmicproximity.net.SecureUdpEnvelope;
import com.cosmicproximity.net.UdpTransport;
import com.cosmicproximity.net.VoicePackets;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the UDP socket + session table. On voice packets, decrypts, looks up
 * the sender's snapshot, and forwards to every other player whose snapshot is
 * within range — re-encrypted for each recipient.
 */
public final class VoiceServer implements AutoCloseable {
    private final MinecraftServer mcServer;
    private final ServerConfig config;
    private final UdpTransport udp;

    private final Map<UUID, VoiceSession> sessionById = new ConcurrentHashMap<>();
    private final Map<UUID, VoiceSession> sessionByPlayer = new ConcurrentHashMap<>();
    /** Updated each server tick from ServerTickEvents.END_SERVER_TICK. */
    private final Map<UUID, PlayerSnapshot> snapshots = new ConcurrentHashMap<>();

    public VoiceServer(MinecraftServer mcServer, ServerConfig config) throws SocketException {
        this.mcServer = mcServer;
        this.config = config;
        this.udp = new UdpTransport(config.voicePort, "cosmicproximity-server-udp", this::onUdpPacket);
        CosmicProximity.LOGGER.info("Voice server bound to UDP {}", udp.localPort());
    }

    public int port() { return udp.localPort(); }

    public VoiceSession registerPlayer(ServerPlayer player) {
        UUID sessionId = UUID.randomUUID();
        SecretKey key = CryptoUtil.generateKey();
        VoiceSession s = new VoiceSession(player.getUUID(), sessionId, key);
        sessionById.put(sessionId, s);
        sessionByPlayer.put(player.getUUID(), s);
        return s;
    }

    public void unregisterPlayer(UUID playerId) {
        VoiceSession s = sessionByPlayer.remove(playerId);
        if (s != null) sessionById.remove(s.sessionId);
        snapshots.remove(playerId);
    }

    public void updateSnapshot(ServerPlayer player) {
        snapshots.put(player.getUUID(),
                new PlayerSnapshot(player.position(), player.level().dimension()));
    }

    private void onUdpPacket(InetSocketAddress from, byte[] envelope) {
        UUID sessionId;
        try {
            sessionId = SecureUdpEnvelope.peekSessionId(envelope);
        } catch (Exception e) {
            return;
        }
        VoiceSession session = sessionById.get(sessionId);
        if (session == null) return;
        if (!session.allowPacket(config.maxPacketsPerSecond)) return;

        byte[] plaintext;
        try {
            plaintext = SecureUdpEnvelope.unwrap(envelope, session.aesKey);
        } catch (Exception e) {
            // Tampered, wrong key, replayed, or junk — drop silently.
            return;
        }

        session.lastPacketMs = System.currentTimeMillis();
        byte type = VoicePackets.peekType(plaintext);
        switch (type) {
            case VoicePackets.HELLO -> session.udpAddress = from;
            case VoicePackets.KEEPALIVE -> { /* timestamp already recorded */ }
            case VoicePackets.VOICE   -> handleVoice(session, plaintext);
            case VoicePackets.DISCONNECT -> session.udpAddress = null;
            default -> { /* unknown — ignore */ }
        }
    }

    private void handleVoice(VoiceSession sender, byte[] plaintext) {
        VoicePackets.Voice incoming;
        try {
            incoming = VoicePackets.decodeVoice(plaintext);
        } catch (Exception e) {
            return;
        }

        PlayerSnapshot senderSnap = snapshots.get(sender.playerId);
        if (senderSnap == null) return;

        // Rebuild with authoritative sender UUID + snapshot position (don't trust client-supplied either).
        byte[] authoritative = VoicePackets.encodeVoice(
                sender.playerId, incoming.sequence(), incoming.timestampMs(),
                senderSnap.position().x, senderSnap.position().y, senderSnap.position().z,
                incoming.audio());

        double rangeSq = config.voiceRange * config.voiceRange;
        for (VoiceSession dest : sessionByPlayer.values()) {
            if (dest.playerId.equals(sender.playerId)) continue;
            if (!dest.isReady()) continue;

            PlayerSnapshot destSnap = snapshots.get(dest.playerId);
            if (destSnap == null) continue;
            if (!config.crossDimensionVoice && !destSnap.dimension().equals(senderSnap.dimension())) continue;
            if (destSnap.position().distanceToSqr(senderSnap.position()) > rangeSq) continue;

            byte[] env = SecureUdpEnvelope.wrap(dest.sessionId, authoritative, dest.aesKey);
            try {
                udp.send(dest.udpAddress, env);
            } catch (IOException e) {
                CosmicProximity.LOGGER.debug("Failed forwarding to {}", dest.playerId, e);
            }
        }
    }

    @Override
    public void close() {
        udp.close();
        sessionById.clear();
        sessionByPlayer.clear();
        snapshots.clear();
    }
}
