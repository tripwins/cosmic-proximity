package com.cosmicproximity.client.audio;

import com.cosmicproximity.audio.AudioConstants;

/** Generates a fading 440 Hz sine for speaker testing, 20 ms at a time. */
public final class TestTone {
    private static final double FREQ = 440.0;
    private static final int DURATION_FRAMES = 50; // 50 × 20ms = 1 second

    public interface FrameSink {
        void push(short[] pcm);
    }

    /** Pushes one second of tone into the sink, blocking on the calling thread. */
    public static void play(FrameSink sink) {
        int samples = AudioConstants.SAMPLES_PER_FRAME;
        double phaseStep = 2 * Math.PI * FREQ / AudioConstants.SAMPLE_RATE;
        double phase = 0;

        for (int f = 0; f < DURATION_FRAMES; f++) {
            short[] frame = new short[samples];
            // Cosine envelope so it fades in and out instead of clicking.
            double envelope = 0.5 - 0.5 * Math.cos(2 * Math.PI * f / DURATION_FRAMES);
            for (int i = 0; i < samples; i++) {
                double v = Math.sin(phase) * envelope * 0.4; // -8 dB headroom
                frame[i] = (short) (v * Short.MAX_VALUE);
                phase += phaseStep;
                if (phase > 2 * Math.PI) phase -= 2 * Math.PI;
            }
            sink.push(frame);
            try { Thread.sleep(AudioConstants.FRAME_DURATION_MS); } catch (InterruptedException e) { return; }
        }
    }

    private TestTone() {}
}
