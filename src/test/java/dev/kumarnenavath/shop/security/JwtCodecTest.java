package dev.kumarnenavath.shop.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kumarnenavath.shop.ShopProperties;
import dev.kumarnenavath.shop.domain.Role;
import dev.kumarnenavath.shop.error.UnauthorizedException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtCodecTest {

    private static final Instant ISSUED = Instant.parse("2026-10-01T10:00:00Z");
    private static final ShopProperties PROPS = new ShopProperties(
            new ShopProperties.Jwt("test-secret-that-is-at-least-32-bytes", Duration.ofHours(1)),
            new ShopProperties.Webhook("unused", Duration.ofMinutes(5)),
            new ShopProperties.Admin("admin@test", "unused-password"));

    private static JwtCodec codecAt(Instant now) {
        return new JwtCodec(PROPS, Clock.fixed(now, ZoneOffset.UTC), new ObjectMapper());
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(UTF_8));
    }

    @Test
    void issuedTokenVerifiesUntilJustBeforeExpiry() {
        JwtCodec.IssuedToken issued = codecAt(ISSUED).issue(42, "kumar@example.com", Role.ADMIN);

        assertThat(issued.expiresAt()).isEqualTo(ISSUED.plus(Duration.ofHours(1)));
        AuthUser user = codecAt(ISSUED.plus(Duration.ofMinutes(59))).verify(issued.token());
        assertThat(user).isEqualTo(new AuthUser(42, "kumar@example.com", Role.ADMIN));
    }

    @Test
    void tokenIsRejectedOnceExpired() {
        String token = codecAt(ISSUED).issue(42, "kumar@example.com", Role.CUSTOMER).token();

        assertThatThrownBy(() -> codecAt(ISSUED.plus(Duration.ofHours(1))).verify(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Token expired");
    }

    @Test
    void editedClaimsBreakTheSignature() {
        String[] parts = codecAt(ISSUED).issue(42, "kumar@example.com", Role.CUSTOMER).token().split("\\.");
        String promoted = b64("{\"sub\":\"42\",\"email\":\"kumar@example.com\",\"role\":\"ADMIN\",\"exp\":9999999999}");

        assertThatThrownBy(() -> codecAt(ISSUED).verify(parts[0] + "." + promoted + "." + parts[2]))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid token");
    }

    @Test
    void unsignedAndMalformedTokensAreRejected() {
        String unsigned = b64("{\"alg\":\"none\"}") + "." + b64("{\"sub\":\"1\",\"role\":\"ADMIN\",\"exp\":9999999999}") + ".";
        JwtCodec codec = codecAt(ISSUED);

        for (String token : new String[] {unsigned, "not-a-jwt", "a.b.c", ""}) {
            assertThatThrownBy(() -> codec.verify(token)).as(token)
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid token");
        }
    }
}
