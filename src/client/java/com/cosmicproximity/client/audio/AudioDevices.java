package com.cosmicproximity.client.audio;

import com.cosmicproximity.CosmicProximity;
import com.cosmicproximity.audio.AudioConstants;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.List;

/** Lists mixer names that can record / play back at our chosen audio format. */
public final class AudioDevices {

    public static List<String> captureDeviceNames() {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, AudioConstants.MONO_FORMAT);
        return supportedMixerNames(info);
    }

    public static List<String> playbackDeviceNames() {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, AudioConstants.STEREO_FORMAT);
        return supportedMixerNames(info);
    }

    private static List<String> supportedMixerNames(DataLine.Info info) {
        List<String> out = new ArrayList<>();
        out.add(""); // empty = system default
        for (Mixer.Info m : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(m);
                if (mixer.isLineSupported(info)) {
                    out.add(m.getName());
                }
            } catch (Throwable ignored) {}
        }
        return out;
    }

    /** Dumps every mixer + whether it supports our capture/playback format. Call once at startup. */
    public static void logInventory() {
        DataLine.Info capInfo = new DataLine.Info(TargetDataLine.class, AudioConstants.MONO_FORMAT);
        DataLine.Info playInfo = new DataLine.Info(SourceDataLine.class, AudioConstants.STEREO_FORMAT);
        CosmicProximity.LOGGER.info("=== Audio device inventory ===");
        Mixer.Info[] infos = AudioSystem.getMixerInfo();
        if (infos.length == 0) {
            CosmicProximity.LOGGER.warn("  No audio mixers found at all — Java Sound is not seeing any devices.");
            return;
        }
        for (Mixer.Info m : infos) {
            try {
                Mixer mixer = AudioSystem.getMixer(m);
                boolean cap = mixer.isLineSupported(capInfo);
                boolean play = mixer.isLineSupported(playInfo);
                CosmicProximity.LOGGER.info("  [{}] capture={} playback={} desc='{}'",
                        m.getName(), cap, play, m.getDescription());
            } catch (Throwable t) {
                CosmicProximity.LOGGER.warn("  [{}] errored: {}", m.getName(), t.toString());
            }
        }
        CosmicProximity.LOGGER.info("=== End inventory ===");
    }

    private AudioDevices() {}
}
