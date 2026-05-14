package com.cosmicproximity.client.audio;

/**
 * Adaptive noise gate with envelope smoothing. Does what it says on the tin:
 *
 *  - Tracks a slow-moving noise floor estimate (drops quickly during silence,
 *    rises only very slowly during speech, so speech doesn't pollute the estimate).
 *  - When current frame RMS is above noiseFloor + margin → pass at full gain.
 *  - When below → attenuate. Curve sharpness controlled by `suppression` slider.
 *  - Envelope follower (fast attack, slow release) smooths gain changes so we
 *    don't get clicks on word boundaries.
 *
 * Effective against steady-state background noise (fans, AC, hum, room tone).
 * Less effective against impulsive noise (typing, clicks). Roughly comparable to
 * Discord's noise gate on default settings.
 */
public final class NoiseSuppressor {
    private static final double FLOOR_INIT = 0.001;
    private static final double FLOOR_RISE = 0.0002;   // per frame; slow track up
    private static final double FLOOR_DROP = 0.1;      // fast track down to silence

    private volatile float suppression = 0.5f;
    private double noiseFloor = FLOOR_INIT;
    private float envelope = 0f;

    public void setSuppression(float s) {
        this.suppression = Math.max(0, Math.min(1, s));
    }

    public float getSuppression() { return suppression; }
    public double getNoiseFloor() { return noiseFloor; }

    /** Apply suppression to `pcm` in place. Returns the gain applied to this frame. */
    public float process(short[] pcm) {
        if (suppression <= 0) return 1f;

        double sum = 0;
        for (short s : pcm) sum += (double) s * s;
        double rms = Math.sqrt(sum / pcm.length) / Short.MAX_VALUE;

        // Update noise floor: fast down, slow up — speech doesn't drag it up
        if (rms < noiseFloor) {
            noiseFloor = noiseFloor + (rms - noiseFloor) * FLOOR_DROP;
        } else {
            noiseFloor = noiseFloor + FLOOR_RISE * suppression;
        }
        if (noiseFloor < FLOOR_INIT) noiseFloor = FLOOR_INIT;

        // Gate threshold sits above the noise floor by an amount proportional to suppression
        double threshold = noiseFloor + 0.003 + suppression * 0.035;

        float targetGain;
        if (rms > threshold) {
            targetGain = 1f;
        } else {
            float ratio = (float) (rms / Math.max(threshold, 1e-9));
            // Sharper curve at higher suppression
            float exponent = 1f + suppression * 5f;
            targetGain = (float) Math.pow(ratio, exponent);
        }

        // Envelope: fast attack (0.5), slow release (0.08) so words don't get cut off
        float coef = (targetGain > envelope) ? 0.5f : 0.08f;
        envelope = envelope + (targetGain - envelope) * coef;
        if (envelope < 0.001f) envelope = 0f;

        if (envelope < 0.999f) {
            for (int i = 0; i < pcm.length; i++) {
                int s = (int) (pcm[i] * envelope);
                pcm[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, s));
            }
        }
        return envelope;
    }
}
