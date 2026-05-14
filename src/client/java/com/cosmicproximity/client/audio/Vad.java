package com.cosmicproximity.client.audio;

/**
 * Energy-based voice activity detector with hold/release. RMS above threshold
 * for ATTACK_FRAMES starts transmitting; below threshold for RELEASE_FRAMES stops.
 * Hysteresis means a single quiet frame between words doesn't cut off speech.
 */
public final class Vad {
    private static final int ATTACK_FRAMES = 2;   // 40 ms to engage
    private static final int RELEASE_FRAMES = 25; // 500 ms tail before disengage

    private boolean transmitting = false;
    private int above = 0;
    private int below = 0;

    public boolean update(float rms, float threshold) {
        if (rms > threshold) {
            above++;
            below = 0;
            if (!transmitting && above >= ATTACK_FRAMES) transmitting = true;
        } else {
            below++;
            above = 0;
            if (transmitting && below >= RELEASE_FRAMES) transmitting = false;
        }
        return transmitting;
    }

    public boolean isTransmitting() { return transmitting; }

    public void reset() {
        transmitting = false;
        above = 0;
        below = 0;
    }
}
