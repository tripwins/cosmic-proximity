package com.cosmicproximity.net;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * AES-256-GCM authenticated encryption for voice packets.
 * Envelope layout on the wire: [12-byte nonce][ciphertext + 16-byte tag]
 */
public final class CryptoUtil {
    private static final String ALG = "AES/GCM/NoPadding";
    public  static final int NONCE_LEN = 12;
    private static final int TAG_LEN_BITS = 128;
    private static final SecureRandom RNG = new SecureRandom();

    public static SecretKey generateKey() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(256);
            return kg.generateKey();
        } catch (Exception e) {
            throw new RuntimeException("AES-256 unavailable", e);
        }
    }

    public static SecretKey keyFromBytes(byte[] raw) {
        if (raw.length != 32) throw new IllegalArgumentException("Expected 32-byte key, got " + raw.length);
        return new SecretKeySpec(raw, "AES");
    }

    /**
     * Derives an AES-256 key from a shared password. Everyone with the password
     * gets the same key, so it acts as a "room password" — adequate for a friend
     * group, NOT for adversarial settings.
     */
    public static SecretKey deriveKeyFromPassword(String password) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "AES");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    public static byte[] encrypt(SecretKey key, byte[] plaintext) {
        try {
            byte[] nonce = new byte[NONCE_LEN];
            RNG.nextBytes(nonce);
            Cipher c = Cipher.getInstance(ALG);
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LEN_BITS, nonce));
            byte[] ct = c.doFinal(plaintext);
            ByteBuffer out = ByteBuffer.allocate(NONCE_LEN + ct.length);
            out.put(nonce).put(ct);
            return out.array();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("encrypt failed", e);
        }
    }

    public static byte[] decrypt(SecretKey key, byte[] envelope) throws GeneralSecurityException {
        if (envelope.length < NONCE_LEN + 16) {
            throw new IllegalArgumentException("envelope too short: " + envelope.length);
        }
        byte[] nonce = new byte[NONCE_LEN];
        System.arraycopy(envelope, 0, nonce, 0, NONCE_LEN);
        Cipher c = Cipher.getInstance(ALG);
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LEN_BITS, nonce));
        return c.doFinal(envelope, NONCE_LEN, envelope.length - NONCE_LEN);
    }

    private CryptoUtil() {}
}
