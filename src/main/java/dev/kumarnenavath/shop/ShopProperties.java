package dev.kumarnenavath.shop;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Typed view of the {@code shop.*} settings; the app fails fast on startup if any are missing. */
@Validated
@ConfigurationProperties("shop")
public record ShopProperties(@Valid @NotNull Jwt jwt, @Valid @NotNull Webhook webhook, @Valid @NotNull Admin admin) {

    /** HS256 key must be at least 256 bits. */
    public record Jwt(@NotBlank @Size(min = 32) String secret, @NotNull Duration ttl) {}

    public record Webhook(@NotBlank String secret, @NotNull Duration tolerance) {}

    /** Bootstrap admin account, created on startup if it doesn't exist. */
    public record Admin(@NotBlank String email, @NotBlank @Size(min = 8) String password) {}
}
