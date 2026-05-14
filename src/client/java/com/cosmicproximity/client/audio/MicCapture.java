package com.cosmicproximity.client.audio;

import com.cosmicproximity.CosmicProximity;
import com.cosmicproximity.audio.AudioConstants;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Opens the system microphone (or a named mixer), reads 20ms PCM frames on a
 * background thread, and pushes each to a consumer. start()/stop() gates whether
 * frames flow without tearing down the line.
 */
public final class MicCapture implements AutoCloseable {

    public interface FrameConsumer {
        /** Called from the capture thread. Keep it fast. */
        void onFrame(short[] pcm);
    }

    private final TargetDataLine line;
    private final Thread captureThread;
    private volatile FrameConsumer consumer;
    private volatile boolean running = true;
    private volatile boolean recording = false;
    /** RMS of the most recent captured frame, in [0..1] (normalized from int16). */
    private volatile float lastRms = 0f;
    /** Total bytes read from the line since open — useful for diagnosing dead mics. */
    private volatile long totalBytesRead = 0;

    public MicCapture(String mixerName, FrameConsumer consumer) throws LineUnavailableException {
        this.consumer = consumer;
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, AudioConstants.MONO_FORMAT);
        Mixer.Info found = findMixer(mixerName);
        if (found != null) {
            this.line = (TargetDataLine) AudioSystem.getMixer(found).getLine(info);
        } else {
            this.line = (TargetDataLine) AudioSystem.getLine(info);
        }
        this.line.open(AudioConstants.MONO_FORMAT, AudioConstants.BYTES_PER_FRAME * 4);
        CosmicProximity.LOGGER.info("Mic line opened: device='{}' format={}",
                found != null ? found.getName() : "default", AudioConstants.MONO_FORMAT);
        this.captureThread = new Thread(this::captureLoop, "cosmicproximity-mic");
        this.captureThread.setDaemon(true);
        this.captureThread.start();
    }

    public void start() {
        if (!recording) {
            line.start();
            recording = true;
        }
    }

    public void stop() {
        if (recording) {
            line.stop();
            line.flush();
            recording = false;
        }
    }

    public boolean isRecording() { return recording; }

    public float getLastRms() { return lastRms; }

    public long getTotalBytesRead() { return totalBytesRead; }

    public void setConsumer(FrameConsumer consumer) { this.consumer = consumer; }

    private void captureLoop() {
        byte[] buf = new byte[AudioConstants.BYTES_PER_FRAME];
        while (running) {
            if (!recording) {
                try { Thread.sleep(10); } catch (InterruptedException e) { return; }
                continue;
            }
            int read = 0;
            while (read < buf.length && recording && running) {
                int n = line.read(buf, read, buf.length - read);
                if (n < 0) return;
                read += n;
            }
            if (read != buf.length) continue;
            totalBytesRead += read;
            short[] pcm = bytesToShorts(buf);
            lastRms = computeRms(pcm);
            FrameConsumer c = consumer;
            if (c != null) {
                try {
                    c.onFrame(pcm);
                } catch (Throwable t) {
                    CosmicProximity.LOGGER.warn("mic consumer threw", t);
                }
            }
        }
    }

    private static float computeRms(short[] pcm) {
        double sum = 0;
        for (short s : pcm) sum += (double) s * s;
        return (float) (Math.sqrt(sum / pcm.length) / Short.MAX_VALUE);
    }

    private static short[] bytesToShorts(byte[] b) {
        ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        short[] out = new short[b.length / 2];
        for (int i = 0; i < out.length; i++) out[i] = bb.getShort();
        return out;
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
        captureThread.interrupt();
        line.close();
    }
}
