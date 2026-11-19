package cn.secmsg;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Pattern;

public final class Security {
    private static final Pattern HEADER = Pattern.compile("HMAC-SHA256 ([0-9]{1,19}),\\s*([A-Za-z0-9+/]{43}=)");
    private Security() { }

    public static String authorization(byte[] body, String key, long timestampMs) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(body);
            byte[] digest = mac.doFinal(Long.toString(timestampMs).getBytes(StandardCharsets.US_ASCII));
            // The server splits on spaces before parsing the timestamp/signature pair.
            return "HMAC-SHA256 " + timestampMs + "," + Base64.getEncoder().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("cannot initialize HMAC key", e);
        }
    }

    public static boolean verify(byte[] body, String header, String key, long nowMs) {
        if (header == null) return false;
        var matcher = HEADER.matcher(header);
        if (!matcher.matches()) return false;
        final long timestamp;
        try {
            timestamp = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return false;
        }
        if (timestamp < nowMs - 1_800_000 || timestamp > nowMs + 1_800_000) return false;
        String expected = authorization(body, key, timestamp).split(",", 2)[1];
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                matcher.group(2).getBytes(StandardCharsets.US_ASCII));
    }

    public static String encrypt(byte[] plaintext, String key) {
        return Base64.getEncoder().encodeToString(crypt(plaintext, key, Cipher.ENCRYPT_MODE));
    }

    public static byte[] decrypt(String ciphertext, String key) {
        byte[] raw = Base64.getDecoder().decode(ciphertext);
        if (raw.length == 0 || raw.length % 16 != 0) throw new IllegalArgumentException("invalid AES ciphertext length");
        return crypt(raw, key, Cipher.DECRYPT_MODE);
    }

    private static byte[] crypt(byte[] data, String key, int mode) {
        String normalized = key.length() >= 16 ? key.substring(0, 16) : "a".repeat(16 - key.length()) + key;
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(mode, new SecretKeySpec(normalized.getBytes(StandardCharsets.UTF_8), "AES"));
            return cipher.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("AES key or ciphertext is invalid", e);
        }
    }
}
