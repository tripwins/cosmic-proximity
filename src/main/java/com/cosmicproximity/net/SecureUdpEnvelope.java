package com.cosmicproximity.net;

import javax.crypto.SecretKey;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.UUID;

/**
 * Wire format: [16 byte sessionId][12 byte nonce][ciphertext + 16 byte GCM tag]
 *
 * The sessionId travels in clear because the receiver needs it to pick the right
 * key. GCM authenticates the rest, so a wrong key (or any tampering) produces an
 * AEADBadTagException on unwrap.
 */
public final class SecureUdpEnvelope {
    public static final int HEADER_LEN = 16;

    public static byte[] wrap(UUID sessionId, byte[] plaintext, SecretKey key) {
        byte[] encrypted = CryptoUtil.encrypt(key, plaintext);
        ByteBuffer bb = ByteBuffer.allocate(HEADER_LEN + encrypted.length);
        bb.putLong(sessionId.getMostSignificantBits());
        bb.putLong(sessionId.getLeastSignificantBits());
        bb.put(encrypted);
        return bb.array();
    }

    public static UUID peekSessionId(byte[] envelope) {
        if (envelope.length < HEADER_LEN) {
            throw new IllegalArgumentException("envelope too short: " + envelope.length);
        }
        ByteBuffer bb = ByteBuffer.wrap(envelope, 0, HEADER_LEN);
        return new UUID(bb.getLong(), bb.getLong());
    }

    public static byte[] unwrap(byte[] envelope, SecretKey key) throws GeneralSecurityException {
        if (envelope.length < HEADER_LEN + CryptoUtil.NONCE_LEN + 16) {
            throw new IllegalArgumentException("envelope too short: " + envelope.length);
        }
        byte[] encrypted = new byte[envelope.length - HEADER_LEN];
        System.arraycopy(envelope, HEADER_LEN, encrypted, 0, encrypted.length);
        return CryptoUtil.decrypt(key, encrypted);
    }

    private SecureUdpEnvelope() {}
}
