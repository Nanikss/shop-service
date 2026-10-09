package dev.kumarnenavath.shop.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kumarnenavath.shop.ShopProperties;
import dev.kumarnenavath.shop.domain.Role;
import dev.kumarnenavath.shop.error.UnauthorizedException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Minimal HS256 JWT issue/verify. Only the one algorithm is accepted, so "alg: none" and
 * algorithm-confusion tokens are rejected before any claim is trusted.
 */
@Component
public class JwtCodec {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DECODER = Base64.getUrlDecoder();
    private static final String HEADER = B64.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(UTF_8));

    public record IssuedToken(String token, Instant expiresAt) {}

    private final byte[] key;
    private final Duration ttl;
    private final Clock clock;
    private final ObjectMapper json;

    public JwtCodec(ShopProperties props, Clock clock, ObjectMapper json) {
        this.key = props.jwt().secret().getBytes(UTF_8);
        this.ttl = props.jwt().ttl();
        this.clock = clock;
        this.json = json;
    }

    public IssuedToken issue(long userId, String email, Role role) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(ttl);
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", Long.toString(userId));
        claims.put("email", email);
        claims.put("role", role.name());
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());
        try {
            String signingInput = HEADER + "." + B64.encodeToString(json.writeValueAsBytes(claims));
            return new IssuedToken(signingInput + "." + B64.encodeToString(sign(signingInput)), expiresAt);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Couldn't serialize token claims", e);
        }
    }

    public AuthUser verify(String token) {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) throw invalid();
        try {
            byte[] provided = B64_DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(sign(parts[0] + "." + parts[1]), provided)) throw invalid();

            JsonNode header = json.readTree(B64_DECODER.decode(parts[0]));
            if (!"HS256".equals(header.path("alg").asText())) throw invalid();

            JsonNode claims = json.readTree(B64_DECODER.decode(parts[1]));
            if (!claims.path("exp").canConvertToLong()) throw invalid();
            if (!clock.instant().isBefore(Instant.ofEpochSecond(claims.get("exp").asLong()))) {
                throw new UnauthorizedException("Token expired");
            }
            return new AuthUser(Long.parseLong(claims.path("sub").asText()),
                    claims.path("email").asText(),
                    Role.valueOf(claims.path("role").asText()));
        } catch (IOException | IllegalArgumentException e) {
            // Bad Base64, bad JSON, non-numeric sub or unknown role.
            throw invalid();
        }
    }

    private byte[] sign(String signingInput) {
        return Hmac.sha256(key, signingInput.getBytes(US_ASCII));
    }

    private static UnauthorizedException invalid() {
        return new UnauthorizedException("Invalid token");
    }
}
