package com.cosmicproximity.client.audio;

import net.minecraft.world.phys.Vec3;

/** Linear distance attenuation + equal-power left/right pan. */
public final class SpatialAudio {

    /** @return [leftGain, rightGain], each in [0..1]. */
    public static float[] computeGains(Vec3 listener, Vec3 listenerLook, Vec3 source, double maxRange) {
        Vec3 toSource = source.subtract(listener);
        double distance = toSource.length();
        if (distance > maxRange) return new float[]{0f, 0f};
        if (distance < 1e-6) return new float[]{1f, 1f};

        float gain = (float) (1.0 - distance / maxRange);

        Vec3 dir = toSource.scale(1.0 / distance);
        Vec3 lookFlat = new Vec3(listenerLook.x, 0, listenerLook.z);
        if (lookFlat.lengthSqr() < 1e-9) return new float[]{gain, gain};
        lookFlat = lookFlat.normalize();
        // Listener's right vector = look rotated 90° CW around Y
        Vec3 right = new Vec3(-lookFlat.z, 0, lookFlat.x);

        double rightDot = dir.dot(right); // -1 = full left, +1 = full right
        float leftGain  = gain * (float) Math.sqrt(Math.max(0, (1.0 - rightDot) * 0.5));
        float rightGain = gain * (float) Math.sqrt(Math.max(0, (1.0 + rightDot) * 0.5));

        // Floor at 40% so sounds behind/in-front don't collapse to one channel.
        float floor = gain * 0.4f;
        return new float[]{
                Math.max(leftGain, floor),
                Math.max(rightGain, floor)
        };
    }

    private SpatialAudio() {}
}
