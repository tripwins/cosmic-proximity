package com.cosmicproximity.audio;

import javax.sound.sampled.AudioFormat;

public final class AudioConstants {
    public static final int SAMPLE_RATE = 48_000;
    public static final int CHANNELS = 1;
    public static final int BITS_PER_SAMPLE = 16;
    public static final int FRAME_DURATION_MS = 20;
    public static final int SAMPLES_PER_FRAME = (SAMPLE_RATE / 1000) * FRAME_DURATION_MS;
    public static final int BYTES_PER_FRAME = SAMPLES_PER_FRAME * (BITS_PER_SAMPLE / 8) * CHANNELS;

    public static final AudioFormat MONO_FORMAT = new AudioFormat(
            SAMPLE_RATE, BITS_PER_SAMPLE, CHANNELS, true, false);

    public static final AudioFormat STEREO_FORMAT = new AudioFormat(
            SAMPLE_RATE, BITS_PER_SAMPLE, 2, true, false);

    private AudioConstants() {}
}
