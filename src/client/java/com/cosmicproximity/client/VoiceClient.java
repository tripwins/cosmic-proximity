package com.cosmicproximity.client;

import com.cosmicproximity.CosmicProximity;
import com.cosmicproximity.audio.RawPcmCodec;
import com.cosmicproximity.audio.VoiceCodec;
import com.cosmicproximity.client.audio.MicCapture;
import com.cosmicproximity.client.audio.NoiseSuppressor;
import com.cosmicproximity.client.audio.SpatialAudio;
import com.cosmicproximity.client.audio.SpeakerPlayback;
import com.cosmicproximity.client.audio.TestTone;
import com.cosmicproximity.client.audio.Vad;
import com.cosmicproximity.config.ClientConfig;
import com.cosmicproximity.net.CryptoUtil;
import com.cosmicproximity.net.SecureUdpEnvelope;
import com.cosmicproximity.net.UdpTransport;
import com.cosmicproximity.net.VoicePackets;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.crypto.SecretKey;
import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class VoiceClient implements AutoCloseable {

    /** Identity sent in REGISTER for relay mode. Null in MC-server-integrated mode. */
    public record RelayContext(UUID playerUuid, String playerName) {}

    private final InetSocketAddress serverAddr;
    private final UUID sessionId;
    private final SecretKey aesKey;
    private final double voiceRange;
    private final ClientConfig config;
    private final RelayContext relay; // null for MC-server mode

    private final UdpTransport udp;
    private volatile MicCapture mic;
    private volatile SpeakerPlayback speaker;
    private final VoiceCodec codec = new RawPcmCodec();
    private final NoiseSuppressor noiseSuppressor = new NoiseSuppressor();
    private final Vad vad = new Vad();

    private final AtomicInteger sequence = new AtomicInteger();
    private final long startMs = System.currentTimeMillis();
    private volatile long lastServerPacketMs;
    private volatile long lastKeepaliveSentMs;
    private volatile long lastPositionSentMs = 0;
    private volatile long lastVisibleSentMs = 0;
    private volatile long lastTransmittedMs = 0;
    private volatile Vec3 listenerPos = Vec3.ZERO;
    private volatile Vec3 listenerLook = new Vec3(0, 0, 1);
    private volatile int currentDimensionHash = 0;
    private volatile int currentRoomHash = 0;

    /** Recently-heard senders (UUID → display name). Drives the mute UI. */
    private final java.util.concurrent.ConcurrentHashMap<UUID, String> recentSpeakers =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** UUIDs currently connected to the relay — i.e., players who have the mod installed. */
    private volatile java.util.Set<UUID> participants = java.util.Set.of();

    private volatile boolean pttKeyDown = false;
    private volatile boolean micTestMode = false;
    private volatile boolean micLoopback = false;
    /** Set by the relay via MUTED_STATUS. 0 = not muted, Long.MAX_VALUE = permanent, else expiry ms. */
    private volatile long serverMuteExpiry = 0L;

    public VoiceClient(InetSocketAddress serverAddr,
                       UUID sessionId,
                       byte[] aesKey,
                       double voiceRange,
                       ClientConfig config,
                       RelayContext relay) throws SocketException, LineUnavailableException, IOException {
        this.serverAddr = serverAddr;
        this.sessionId = sessionId;
        this.aesKey = CryptoUtil.keyFromBytes(aesKey);
        this.voiceRange = voiceRange;
        this.config = config;
        this.relay = relay;

        this.udp = new UdpTransport(0, "cosmicproximity-client-udp", this::onUdpPacket);
        this.mic = new MicCapture(config.micDevice, this::onMicFrame);
        this.speaker = new SpeakerPlayback(config.speakerDevice);
        this.speaker.setMasterVolume(config.speakerVolume);
        this.speaker.setDeafened(config.deafened);

        if (relay != null) sendRegister();
        else sendHello();
    }

    public boolean isRelay() { return relay != null; }

    public UUID sessionId() { return sessionId; }
    public ClientConfig config() { return config; }
    public double voiceRange() { return voiceRange; }
    public long lastServerPacketMs() { return lastServerPacketMs; }
    /** Mojang account UUID this session was registered with. Null in MC-server-integrated mode. */
    public UUID identityUuid() { return relay == null ? null : relay.playerUuid(); }

    public float micRms() { MicCapture m = mic; return m != null ? m.getLastRms() : 0f; }
    public boolean micIsLive() { MicCapture m = mic; return m != null && m.isRecording(); }
    /** True if we transmitted a frame in the last 100ms — drives the "Speaking" HUD pill. */
    public boolean isTransmitting() {
        return System.currentTimeMillis() - lastTransmittedMs < 100;
    }
    public Set<UUID> currentSpeakers(long maxAgeMs) {
        SpeakerPlayback sp = speaker;
        return sp != null ? sp.getSpeakingSet(maxAgeMs) : Set.of();
    }

    private void sendHello() throws IOException {
        byte[] hello = VoicePackets.encodeHello(sessionId);
        udp.send(serverAddr, SecureUdpEnvelope.wrap(sessionId, hello, aesKey));
    }

    private void sendRegister() throws IOException {
        byte[] reg = VoicePackets.encodeRegister(relay.playerUuid(), relay.playerName(),
                currentDimensionHash, currentRoomHash);
        udp.send(serverAddr, SecureUdpEnvelope.wrap(sessionId, reg, aesKey));
    }

    private void sendPosition() {
        if (relay == null) return;
        try {
            byte[] pos = VoicePackets.encodePosition(
                    System.currentTimeMillis(),
                    listenerPos.x, listenerPos.y, listenerPos.z,
                    currentDimensionHash, currentRoomHash);
            udp.send(serverAddr, SecureUdpEnvelope.wrap(sessionId, pos, aesKey));
        } catch (IOException ignored) {}
    }

    /**
     * Tells the relay which player UUIDs are in our local MC entity list.
     * Used for realm isolation: the relay only forwards voice between players
     * who both see each other in their respective worlds.
     */
    private void sendVisiblePlayers() {
        if (relay == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        java.util.List<UUID> ids = new java.util.ArrayList<>();
        for (Player p : mc.level.players()) {
            ids.add(p.getUUID());
        }
        if (ids.isEmpty()) return;
        try {
            byte[] pkt = VoicePackets.encodeVisiblePlayers(ids);
            udp.send(serverAddr, SecureUdpEnvelope.wrap(sessionId, pkt, aesKey));
        } catch (IOException ignored) {}
    }

    /** Returns a copy of the recent-speakers map. */
    public java.util.Map<UUID, String> recentSpeakers() {
        return java.util.Map.copyOf(recentSpeakers);
    }

    /** UUIDs currently connected to the relay — broadcast every ~5 seconds. */
    public java.util.Set<UUID> participants() { return participants; }

    public void setPttKeyDown(boolean down) { this.pttKeyDown = down; }

    public void setMicTestMode(boolean on) {
        this.micTestMode = on;
        if (on) startMic();
        else applyMicState();
    }
    public boolean micTestMode() { return micTestMode; }

    public void setMicLoopback(boolean on) { this.micLoopback = on; }
    public boolean isMicLoopback() { return micLoopback; }

    /** True if the relay told us we're muted by an admin. */
    public boolean isServerMuted() {
        long exp = serverMuteExpiry;
        if (exp == 0L) return false;
        if (exp == Long.MAX_VALUE) return true;
        if (exp <= System.currentTimeMillis()) {
            serverMuteExpiry = 0L; // expired
            return false;
        }
        return true;
    }

    /** Seconds remaining on a timed mute, or -1 for permanent, 0 for none. */
    public long serverMuteSecondsLeft() {
        long exp = serverMuteExpiry;
        if (exp == 0L) return 0;
        if (exp == Long.MAX_VALUE) return -1;
        long remaining = (exp - System.currentTimeMillis()) / 1000L;
        return Math.max(0, remaining);
    }

    public void setDeafened(boolean d) {
        SpeakerPlayback sp = speaker;
        if (sp != null) sp.setDeafened(d);
    }
    public void setSpeakerVolume(float v) {
        SpeakerPlayback sp = speaker;
        if (sp != null) sp.setMasterVolume(v);
    }

    public void playTestTone() {
        Thread t = new Thread(() -> {
            SpeakerPlayback sp = speaker;
            if (sp == null) return;
            TestTone.play(sp::pushTestFrame);
        }, "cosmicproximity-testtone");
        t.setDaemon(true);
        t.start();
    }

    public synchronized void switchMicDevice(String name) {
        config.micDevice = name;
        try {
            MicCapture next = new MicCapture(name, this::onMicFrame);
            MicCapture old = mic;
            mic = next;
            if (old != null) {
                if (old.isRecording()) next.start();
                old.close();
            }
            applyMicState();
        } catch (Exception e) {
            CosmicProximity.LOGGER.error("Mic device switch to '{}' failed", name, e);
        }
    }

    public synchronized void switchSpeakerDevice(String name) {
        config.speakerDevice = name;
        try {
            SpeakerPlayback next = new SpeakerPlayback(name);
            next.setMasterVolume(config.speakerVolume);
            next.setDeafened(config.deafened);
            SpeakerPlayback old = speaker;
            speaker = next;
            if (old != null) old.close();
        } catch (Exception e) {
            CosmicProximity.LOGGER.error("Speaker device switch to '{}' failed", name, e);
        }
    }

    private void startMic() {
        MicCapture m = mic;
        if (m != null && !m.isRecording()) m.start();
    }
    private void stopMic() {
        MicCapture m = mic;
        if (m != null && m.isRecording()) {
            m.stop();
            vad.reset();
        }
    }

    /** Open-mic mode keeps the mic running continuously. PTT mode runs it only while the key is held. */
    private void applyMicState() {
        if (micTestMode) return;
        if (config.muted) { stopMic(); return; }
        boolean shouldRun = (config.voiceMode == ClientConfig.VoiceMode.OPEN_MIC) || pttKeyDown;
        if (shouldRun) startMic(); else stopMic();
    }

    public void updateListener(Vec3 pos, Vec3 look, int dimensionHash, int roomHash) {
        this.listenerPos = pos;
        this.listenerLook = look;
        this.currentDimensionHash = dimensionHash;
        this.currentRoomHash = roomHash;
    }

    public void tick() {
        applyMicState();

        long now = System.currentTimeMillis();
        if (relay != null && now - lastPositionSentMs > 100) {
            sendPosition();
            lastPositionSentMs = now;
        }
        if (relay != null && now - lastVisibleSentMs > 2000) {
            sendVisiblePlayers();
            lastVisibleSentMs = now;
        }
        if (now - lastKeepaliveSentMs > 5_000) {
            try {
                byte[] ka = VoicePackets.encodeKeepalive(now);
                udp.send(serverAddr, SecureUdpEnvelope.wrap(sessionId, ka, aesKey));
                lastKeepaliveSentMs = now;
            } catch (IOException ignored) {}
        }
    }

    private void onMicFrame(short[] pcm) {
        if (config.muted) return;
        if (isServerMuted()) return; // admin mute — relay would drop these anyway, save the bandwidth

        // 1. Mic boost
        float mv = config.micVolume;
        if (mv != 1f) {
            for (int i = 0; i < pcm.length; i++) {
                int s = (int) (pcm[i] * mv);
                pcm[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, s));
            }
        }

        // 2. Noise suppression (in-place)
        noiseSuppressor.setSuppression(config.noiseSuppression);
        noiseSuppressor.process(pcm);

        // 3. Loopback: push to local speaker even if we won't transmit (used by "Hear Myself")
        if (micLoopback) {
            SpeakerPlayback sp = speaker;
            if (sp != null) sp.pushTestFrame(pcm.clone());
        }

        // 4. Test mode skips network send (used by the settings screen for preview)
        if (micTestMode) return;

        // 5. Decide whether to transmit, based on mode
        boolean shouldTransmit;
        if (config.voiceMode == ClientConfig.VoiceMode.OPEN_MIC) {
            float postRms = computeRms(pcm);
            shouldTransmit = vad.update(postRms, config.vadThreshold);
        } else {
            shouldTransmit = pttKeyDown;
        }

        if (!shouldTransmit) return;

        // 6. Encode + send (carry sender position so receivers can fade by distance
        // without needing to see the sender's player entity in their own world)
        byte[] audio = codec.encode(pcm);
        long ts = System.currentTimeMillis() - startMs;
        int seq = sequence.getAndIncrement();
        Vec3 myPos = listenerPos;
        byte[] voice = VoicePackets.encodeVoice(new UUID(0, 0), seq, ts,
                myPos.x, myPos.y, myPos.z, audio);
        try {
            udp.send(serverAddr, SecureUdpEnvelope.wrap(sessionId, voice, aesKey));
            lastTransmittedMs = System.currentTimeMillis();
        } catch (IOException e) {
            CosmicProximity.LOGGER.debug("send voice failed", e);
        }
    }

    private static float computeRms(short[] pcm) {
        double sum = 0;
        for (short s : pcm) sum += (double) s * s;
        return (float) (Math.sqrt(sum / pcm.length) / Short.MAX_VALUE);
    }

    private void onUdpPacket(InetSocketAddress from, byte[] envelope) {
        try {
            UUID id = SecureUdpEnvelope.peekSessionId(envelope);
            if (!id.equals(sessionId)) return;
            byte[] plaintext = SecureUdpEnvelope.unwrap(envelope, aesKey);
            lastServerPacketMs = System.currentTimeMillis();
            byte type = VoicePackets.peekType(plaintext);
            switch (type) {
                case VoicePackets.VOICE -> handleVoice(plaintext);
                case VoicePackets.KEEPALIVE -> { /* ts recorded */ }
                case VoicePackets.PARTICIPANT_LIST -> {
                    VoicePackets.ParticipantList list = VoicePackets.decodeParticipantList(plaintext);
                    participants = java.util.Set.copyOf(list.uuids());
                }
                case VoicePackets.MUTED_STATUS -> {
                    VoicePackets.MutedStatus s = VoicePackets.decodeMutedStatus(plaintext);
                    long prev = serverMuteExpiry;
                    serverMuteExpiry = s.expiryMillis();
                    if (s.expiryMillis() == 0L && prev != 0L) {
                        CosmicProximity.LOGGER.info("Voice mute lifted by admin.");
                    } else if (s.expiryMillis() != 0L && prev == 0L) {
                        CosmicProximity.LOGGER.info("Voice-muted by admin (expiry={}).",
                                s.expiryMillis() == Long.MAX_VALUE ? "permanent" : s.expiryMillis());
                    }
                }
                default -> { /* ignore */ }
            }
        } catch (Exception ignored) {}
    }

    private void handleVoice(byte[] plaintext) {
        VoicePackets.Voice v;
        try { v = VoicePackets.decodeVoice(plaintext); }
        catch (Exception e) { return; }

        UUID senderId = v.senderId();
        SpeakerPlayback sp = speaker;
        if (sp == null) return;
        short[] pcm = codec.decode(v.audio());

        // Relay TTS broadcast — full volume, no distance fade, doesn't appear in mute list.
        if (VoicePackets.BROADCAST_UUID.equals(senderId)) {
            sp.pushFrame(senderId, pcm, 1f, 1f);
            return;
        }

        // Drop muted senders before mixing
        if (config.mutedPlayers.contains(senderId)) return;

        // Track for the mute UI (look up name from MC world if we can, else UUID prefix)
        recentSpeakers.computeIfAbsent(senderId, this::resolveName);

        Vec3 srcPos = new Vec3(v.x(), v.y(), v.z());
        float[] gains = SpatialAudio.computeGains(listenerPos, listenerLook, srcPos, voiceRange);
        sp.pushFrame(senderId, pcm, gains[0], gains[1]);
    }

    private String resolveName(UUID id) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                Player p = mc.level.getPlayerByUUID(id);
                if (p != null) return p.getName().getString();
            }
            if (mc.getConnection() != null) {
                var info = mc.getConnection().getPlayerInfo(id);
                if (info != null && info.getTabListDisplayName() != null) {
                    return info.getTabListDisplayName().getString();
                }
            }
        } catch (Throwable ignored) {}
        return id.toString().substring(0, 8);
    }

    @Override
    public void close() {
        try {
            byte[] dc = VoicePackets.encodeDisconnect();
            udp.send(serverAddr, SecureUdpEnvelope.wrap(sessionId, dc, aesKey));
        } catch (IOException ignored) {}
        MicCapture m = mic;
        if (m != null) m.close();
        SpeakerPlayback sp = speaker;
        if (sp != null) sp.close();
        udp.close();
    }
}
