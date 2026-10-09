package dev.kumarnenavath.shop.security;

import dev.kumarnenavath.shop.ShopProperties;
import dev.kumarnenavath.shop.error.UnauthorizedException;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

/** {@code X-Signature} = lowercase hex HMAC-SHA256 of the raw request body, keyed with the shared secret. */
@Component
public class WebhookSignature {

    private final byte[] secret;

    public WebhookSignature(ShopProperties props) {
        this.secret = props.webhook().secret().getBytes(UTF_8);
    }

    public String sign(byte[] body) {
        return HexFormat.of().formatHex(Hmac.sha256(secret, body));
    }

    public void verify(byte[] body, String signature) {
        if (signature == null || signature.isBlank()) {
            throw new UnauthorizedException("Missing X-Signature header");
        }
        byte[] expected = sign(body).getBytes(US_ASCII);
        byte[] provided = signature.trim().toLowerCase(Locale.ROOT).getBytes(US_ASCII);
        // Constant-time comparison, so response timing doesn't reveal how many leading characters matched.
        if (!MessageDigest.isEqual(expected, provided)) {
            throw new UnauthorizedException("Invalid webhook signature");
        }
    }
}
