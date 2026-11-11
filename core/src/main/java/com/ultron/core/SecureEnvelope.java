package com.ultron.core;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/** AES-256-GCM authenticated payload helper. The 32-byte key must come from an explicit pairing flow. */
public final class SecureEnvelope {
    private static final SecureRandom RANDOM = new SecureRandom();
    private SecureEnvelope() {}

    public static String seal(byte[] pairedKey, String plaintext) throws Exception {
        requireKey(pairedKey);
        byte[] nonce = new byte[12]; RANDOM.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(pairedKey, "AES"), new GCMParameterSpec(128, nonce));
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] packet = new byte[nonce.length + encrypted.length];
        System.arraycopy(nonce,0,packet,0,nonce.length);
        System.arraycopy(encrypted,0,packet,nonce.length,encrypted.length);
        return Base64.getEncoder().encodeToString(packet);
    }

    public static String open(byte[] pairedKey, String envelope) throws Exception {
        requireKey(pairedKey);
        byte[] packet = Base64.getDecoder().decode(envelope);
        if (packet.length < 12 + 16) throw new IllegalArgumentException("Invalid encrypted envelope.");
        byte[] nonce = java.util.Arrays.copyOfRange(packet,0,12);
        byte[] encrypted = java.util.Arrays.copyOfRange(packet,12,packet.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(pairedKey,"AES"), new GCMParameterSpec(128,nonce));
        return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void requireKey(byte[] key) {
        if (key == null || key.length != 32) throw new IllegalArgumentException("Pairing key must be 256 bits.");
    }
}
