package com.codewild.food.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Centralised OTP throttle policy (Redis-backed; see {@code docs/architecture/auth.md}).
 *
 * <p>Keys (all with TTLs — Redis is never the system of record):
 * <ul>
 *   <li>{@code otp:{e164}} — {@code hex(sha256(code + OTP_SECRET)):attempts} (5-min TTL).</li>
 *   <li>{@code otp:resend:{e164}} — resend cooldown marker (30 s).</li>
 *   <li>{@code otp:req:{e164}} — rolling per-phone request counter (1 h TTL).</li>
 *   <li>{@code otp:req:ip:{ip}} — rolling per-IP request counter (1 h TTL, SMS-pumping defence).</li>
 * </ul>
 *
 * <p>Enforced by {@code AuthService} + {@code AuthController}:
 * SHA-256 hash storage, 5-min TTL, 5 verification attempts then void,
 * 30 s resend cooldown, 5 sends/phone/hour + 20 sends/IP/hour, CSPRNG codes,
 * delete-on-success, fail-closed on Redis miss ({@code OTP_EXPIRED}).
 *
 * <p>Never log codes, hashes, or full phones — masked form only.
 */
public final class OtpSecurityPolicy {

    private OtpSecurityPolicy() {
    }

    /** OTP validity window. */
    public static final int OTP_TTL_SECONDS = 300; // 5 minutes

    /** Code length and character set: 6 numeric digits from a CSPRNG. */
    public static final int OTP_LENGTH = 6;

    /** Max verification attempts per code before it is voided. */
    public static final int MAX_ATTEMPTS = 5;

    /** Minimum gap between sends to the same phone. */
    public static final int RESEND_COOLDOWN_SECONDS = 30;

    /** Max OTP sends per phone per rolling hour (abuse cap). */
    public static final int MAX_SENDS_PER_HOUR = 5;

    /** Max sends per IP per rolling hour (SMS-pumping defence). */
    public static final int MAX_SENDS_PER_IP_PER_HOUR = 20;

    /** Redis key prefixes. */
    public static final String KEY_OTP = "otp:";
    public static final String KEY_RESEND = "otp:resend:";
    public static final String KEY_REQ = "otp:req:";
    public static final String KEY_REQ_IP = "otp:req:ip:";

    /** Normalise to E.164-ish digits before using as a key suffix. */
    public static String keySuffix(String phoneE164) {
        if (phoneE164 == null) {
            return "unknown";
        }
        return phoneE164.replaceAll("[^0-9+]", "");
    }

    public static String otpKey(String phoneE164) {
        return KEY_OTP + keySuffix(phoneE164);
    }

    public static String resendKey(String phoneE164) {
        return KEY_RESEND + keySuffix(phoneE164);
    }

    public static String phoneCounterKey(String phoneE164) {
        return KEY_REQ + keySuffix(phoneE164);
    }

    public static String ipCounterKey(String clientIp) {
        String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
        return KEY_REQ_IP + ip.replaceAll("[^0-9a-zA-Z.:]", "_");
    }

    /**
     * SHA-256 hex of {@code code + secret}. The code is never stored or compared
     * in plaintext; comparison uses {@link #constantTimeEquals}.
     */
    public static String hash(String code, String secret) {
        String s = secret == null ? "" : secret;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((code + s).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Constant-time hex-hash comparison (prevents timing oracles on attempts). */
    public static boolean constantTimeEquals(String aHex, String bHex) {
        if (aHex == null || bHex == null) {
            return false;
        }
        byte[] a = aHex.getBytes(StandardCharsets.UTF_8);
        byte[] b = bHex.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    /** Encode stored value as {@code hash:attempts}. */
    public static String encodeStoredValue(String hashHex, int attempts) {
        return hashHex + ":" + attempts;
    }

    /** Parse stored {@code hash:attempts}; returns null on corrupt values (fail closed). */
    public static long[] parseStoredValue(String stored) {
        if (stored == null) {
            return null;
        }
        int idx = stored.lastIndexOf(':');
        if (idx < 0) {
            return null;
        }
        // Hash is hex; attempts must be a small int. We return attempts via array
        // to keep the signature simple without allocating a record.
        try {
            int attempts = Integer.parseInt(stored.substring(idx + 1));
            if (attempts < 0 || attempts > 100) {
                return null;
            }
            return new long[]{attempts};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Extract the hash half of a stored {@code hash:attempts} value. */
    public static String storedHash(String stored) {
        if (stored == null) {
            return null;
        }
        int idx = stored.lastIndexOf(':');
        return idx < 0 ? null : stored.substring(0, idx);
    }

    /** Extract the attempts half; -1 on corrupt values (caller fails closed). */
    public static int storedAttempts(String stored) {
        long[] parsed = parseStoredValue(stored);
        return parsed == null ? -1 : (int) parsed[0];
    }
}
