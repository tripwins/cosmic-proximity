package com.cosmicproximity.server;

import javax.crypto.SecretKey;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Per-player server-side voice session state. */
public final class VoiceSession {
    public final UUID playerId;
    public final UUID sessionId;
    public final SecretKey aesKey;

    /** Set when the client sends its first HELLO over UDP. Until then we don't know where to send voice. */
    public volatile InetSocketAddress udpAddress;
    public volatile long lastPacketMs;

    private final AtomicInteger packetsThisSecond = new AtomicInteger();
    private volatile long secondMark;

    public VoiceSession(UUID playerId, UUID sessionId, SecretKey aesKey) {
        this.playerId = playerId;
        this.sessionId = sessionId;
        this.aesKey = aesKey;
    }

    public boolean allowPacket(int maxPerSecond) {
        long now = System.currentTimeMillis() / 1000L;
        if (now != secondMark) {
            secondMark = now;
            packetsThisSecond.set(0);
        }
        return packetsThisSecond.incrementAndGet() <= maxPerSecond;
    }

    public boolean isReady() { return udpAddress != null; }
}
