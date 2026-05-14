package com.cosmicproximity.audio;

public interface VoiceCodec {
    byte[] encode(short[] pcm);
    short[] decode(byte[] encoded);

    /** Identifier so client and server can negotiate. */
    String name();
}
