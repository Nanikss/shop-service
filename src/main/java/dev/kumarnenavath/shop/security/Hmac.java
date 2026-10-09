package dev.kumarnenavath.shop.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

/** HMAC-SHA256, shared by JWT signing and webhook signature checks. */
public final class Hmac {

    private static final String ALGORITHM = "HmacSHA256";

    private Hmac() {}

    public static byte[] sha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            // HmacSHA256 is mandatory on every Java platform, so this means a broken JVM.
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
