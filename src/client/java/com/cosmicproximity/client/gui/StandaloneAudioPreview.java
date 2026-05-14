package com.cosmicproximity.client.gui;

import com.cosmicproximity.CosmicProximity;
import com.cosmicproximity.client.audio.MicCapture;
import com.cosmicproximity.client.audio.SpeakerPlayback;
import com.cosmicproximity.client.audio.TestTone;

/**
 * Owns its own mic + speaker lines for the settings screen so tests work
 * regardless of whether a voice session is active. Replaces no state on the
 * VoiceClient — it just opens fresh JavaSound lines.
 */
public final class StandaloneAudioPreview implements AutoCloseable {

    private volatile MicCapture mic;
    private volatile SpeakerPlayback speaker;
    private volatile String lastError;
    private volatile boolean loopback = false;

    public StandaloneAudioPreview(String micDevice, String speakerDevice) {
        openMic(micDevice);
        openSpeaker(speakerDevice);
    }

    public float micRms() {
        MicCapture m = mic;
        return m != null ? m.getLastRms() : 0f;
    }

    public long micBytesRead() {
        MicCapture m = mic;
        return m != null ? m.getTotalBytesRead() : 0;
    }

    public boolean micOpen() { return mic != null; }
    public boolean speakerOpen() { return speaker != null; }
    public String lastError() { return lastError; }
    public boolean isLoopback() { return loopback; }

    public void setLoopback(boolean on) {
        this.loopback = on;
        MicCapture m = mic;
        SpeakerPlayback sp = speaker;
        if (m == null) return;
        if (on && sp != null) {
            m.setConsumer(sp::pushTestFrame);
        } else {
            m.setConsumer(pcm -> {});
        }
    }

    public synchronized void switchMic(String device) {
        MicCapture old = mic;
        openMic(device);
        if (old != null && old != mic) old.close();
        if (loopback) setLoopback(true); // re-wire consumer on the new line
    }

    public synchronized void switchSpeaker(String device) {
        SpeakerPlayback old = speaker;
        openSpeaker(device);
        if (old != null && old != speaker) old.close();
        if (loopback) setLoopback(true);
    }

    public void playTestTone() {
        SpeakerPlayback sp = speaker;
        if (sp == null) { lastError = "No speaker line open"; return; }
        Thread t = new Thread(() -> TestTone.play(sp::pushTestFrame), "cosmicproximity-preview-tone");
        t.setDaemon(true);
        t.start();
    }

    private void openMic(String device) {
        try {
            MicCapture m = new MicCapture(device, pcm -> {});
            m.start();
            mic = m;
            CosmicProximity.LOGGER.info("Preview mic opened (device='{}')", device.isEmpty() ? "default" : device);
        } catch (Throwable e) {
            mic = null;
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            CosmicProximity.LOGGER.error("Preview mic failed to open (device='{}'): {}", device, e.toString());
        }
    }

    private void openSpeaker(String device) {
        try {
            SpeakerPlayback s = new SpeakerPlayback(device);
            speaker = s;
            CosmicProximity.LOGGER.info("Preview speaker opened (device='{}')", device.isEmpty() ? "default" : device);
        } catch (Throwable e) {
            speaker = null;
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            CosmicProximity.LOGGER.error("Preview speaker failed to open (device='{}'): {}", device, e.toString());
        }
    }

    @Override
    public void close() {
        MicCapture m = mic;
        if (m != null) m.close();
        SpeakerPlayback s = speaker;
        if (s != null) s.close();
        mic = null;
        speaker = null;
    }
}
