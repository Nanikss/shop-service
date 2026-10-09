package dev.kumarnenavath.shop.web;

import dev.kumarnenavath.shop.domain.User;
import dev.kumarnenavath.shop.security.JwtCodec;
import dev.kumarnenavath.shop.security.PublicEndpoint;
import dev.kumarnenavath.shop.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
@PublicEndpoint
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    /** BCrypt only uses the first 72 bytes of a password, so longer ones are rejected rather than silently truncated. */
    public record SignupRequest(@NotBlank @Email String email, @NotBlank @Size(min = 8, max = 72) String password) {}
    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record UserView(long id, String email, String role) {
        static UserView of(User u) {
            return new UserView(u.getId(), u.getEmail(), u.getRole().name());
        }
    }

    public record TokenView(String token, String tokenType, Instant expiresAt) {
        static TokenView of(JwtCodec.IssuedToken t) {
            return new TokenView(t.token(), "Bearer", t.expiresAt());
        }
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView signup(@Valid @RequestBody SignupRequest req) {
        return UserView.of(auth.signup(req.email(), req.password()));
    }

    @PostMapping("/login")
    public TokenView login(@Valid @RequestBody LoginRequest req) {
        return TokenView.of(auth.login(req.email(), req.password()));
    }
}
