package com.cosmicproximity.client.audio;

import com.cosmicproximity.CosmicProximity;
import com.cosmicproximity.audio.AudioConstants;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stereo mixer. Each sender has a JitterBuffer; the mix loop pulls one frame
 * per sender per tick, applies their stored gains, sums into stereo, clips, writes
 * to the SourceDataLine. line.write() blocks until space is free, which paces the loop.
 */
public final class SpeakerPlayback implements AutoCloseable {

    private static final class SenderStream {
        final JitterBuffer buffer = new JitterBuffer(8);
        volatile float leftGain = 1f;
        volatile float rightGain = 1f;
        volatile long lastFrameMs = 0L;
    }

    private final SourceDataLine line;
    private final Map<UUID, SenderStream> streams = new ConcurrentHashMap<>();
    private final Thread mixThread;
    private volatile boolean running = true;
    private volatile float masterVolume = 1.0f;
    private volatile boolean deafened = false;

    public SpeakerPlayback(String mixerName) throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, AudioConstants.STEREO_FORMAT);
        Mixer.Info found = findMixer(mixerName);
        if (found != null) {
            this.line = (SourceDataLine) AudioSystem.getMixer(found).getLine(info);
        } else {
            this.line = (SourceDataLine) AudioSystem.getLine(info);
        }
        this.line.open(AudioConstants.STEREO_FORMAT, AudioConstants.BYTES_PER_FRAME * 8);
        this.line.start();
        CosmicProximity.LOGGER.info("Speaker line opened: device='{}' format={}",
                found != null ? found.getName() : "default", AudioConstants.STEREO_FORMAT);
        this.mixThread = new Thread(this::mixLoop, "cosmicproximity-mixer");
        this.mixThread.setDaemon(true);
        this.mixThread.start();
    }

    public void pushFrame(UUID senderId, short[] pcm, float leftGain, float rightGain) {
        SenderStream s = streams.computeIfAbsent(senderId, k -> new SenderStream());
        s.leftGain = leftGain;
        s.rightGain = rightGain;
        s.lastFrameMs = System.currentTimeMillis();
        s.buffer.push(pcm);
    }

    /** Direct push for local test tones — bypasses gains, mixes at full master volume. */
    public void pushTestFrame(short[] pcm) {
        SenderStream s = streams.computeIfAbsent(TEST_TONE_ID, k -> new SenderStream());
        s.leftGain = 1f;
        s.rightGain = 1f;
        s.lastFrameMs = System.currentTimeMillis();
        s.buffer.push(pcm);
    }

    /** Set of senders that pushed a frame within the last `maxAgeMs`. */
    public Set<UUID> getSpeakingSet(long maxAgeMs) {
        long cutoff = System.currentTimeMillis() - maxAgeMs;
        Set<UUID> out = new HashSet<>();
        for (Map.Entry<UUID, SenderStream> e : streams.entrySet()) {
            if (e.getValue().lastFrameMs >= cutoff) out.add(e.getKey());
        }
        return out;
    }

    public void removeSender(UUID senderId) { streams.remove(senderId); }
    public void setMasterVolume(float v) { this.masterVolume = v; }
    public void setDeafened(boolean d) { this.deafened = d; }

    public static final UUID TEST_TONE_ID = UUID.fromString("c057c057-c057-c057-c057-c057c057c057");

    private void mixLoop() {
        final int samplesPerFrame = AudioConstants.SAMPLES_PER_FRAME;
        int[] mix = new int[samplesPerFrame * 2];
        byte[] out = new byte[samplesPerFrame * 4]; // stereo 16-bit
        ByteBuffer bb = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN);

        while (running) {
            Arrays.fill(mix, 0);
            if (!deafened) {
                for (SenderStream s : streams.values()) {
                    short[] frame = s.buffer.pop();
                    if (frame == null) continue;
                    float lg = s.leftGain * masterVolume;
                    float rg = s.rightGain * masterVolume;
                    int n = Math.min(frame.length, samplesPerFrame);
                    for (int i = 0; i < n; i++) {
                        int sample = frame[i];
                        mix[i * 2]     += (int) (sample * lg);
                        mix[i * 2 + 1] += (int) (sample * rg);
                    }
                }
            }
            bb.clear();
            for (int v : mix) {
                int clipped = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v));
                bb.putShort((short) clipped);
            }
            // Blocking write paces the mixer to real time.
            line.write(out, 0, out.length);
        }
    }

    private static Mixer.Info findMixer(String name) {
        if (name == null || name.isEmpty()) return null;
        for (Mixer.Info m : AudioSystem.getMixerInfo()) {
            if (m.getName().equalsIgnoreCase(name)) return m;
        }
        return null;
    }

    @Override
    public void close() {
        running = false;
        mixThread.interrupt();
        line.drain();
        line.close();
    }
}
