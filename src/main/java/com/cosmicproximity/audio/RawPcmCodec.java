package com.cosmicproximity.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * No-compression codec. ~768 kbit/s per active speaker.
 * Fine for LAN / small servers. Swap for Opus in Phase 2.
 */
public final class RawPcmCodec implements VoiceCodec {
    public static final String NAME = "raw-pcm-s16le-48k-mono";

    @Override
    public byte[] encode(short[] pcm) {
        ByteBuffer bb = ByteBuffer.allocate(pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short s : pcm) bb.putShort(s);
        return bb.array();
    }

    @Override
    public short[] decode(byte[] encoded) {
        ByteBuffer bb = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
        short[] out = new short[encoded.length / 2];
        for (int i = 0; i < out.length; i++) out[i] = bb.getShort();
        return out;
    }

    @Override
    public String name() { return NAME; }
}
