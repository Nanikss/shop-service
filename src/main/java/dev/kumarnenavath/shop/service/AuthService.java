package dev.kumarnenavath.shop.service;

import dev.kumarnenavath.shop.domain.Role;
import dev.kumarnenavath.shop.domain.User;
import dev.kumarnenavath.shop.error.ConflictException;
import dev.kumarnenavath.shop.error.UnauthorizedException;
import dev.kumarnenavath.shop.repository.UserRepository;
import dev.kumarnenavath.shop.security.JwtCodec;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtCodec jwt;
    private final Clock clock;
    /** Checked against when the email is unknown, so both failure paths cost one BCrypt comparison. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwords, JwtCodec jwt, Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
        this.clock = clock;
        this.dummyHash = passwords.encode("not-a-real-password");
    }

    @Transactional
    public User signup(String email, String password) {
        return register(normalize(email), password, Role.CUSTOMER);
    }

    @Transactional(readOnly = true)
    public JwtCodec.IssuedToken login(String email, String password) {
        Optional<User> user = users.findByEmail(normalize(email));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwords.matches(password, hash);
        if (user.isEmpty() || !matches) {
            // Same message for unknown email and wrong password: don't reveal which accounts exist.
            throw new UnauthorizedException("Invalid email or password");
        }
        return jwt.issue(user.get().getId(), user.get().getEmail(), user.get().getRole());
    }

    /** Creates the configured admin account on first startup; leaves an existing one untouched. */
    @Transactional
    public void ensureAdmin(String email, String password) {
        String normalized = normalize(email);
        if (!users.existsByEmail(normalized)) register(normalized, password, Role.ADMIN);
    }

    private User register(String email, String password, Role role) {
        if (users.existsByEmail(email)) throw new ConflictException("Email already registered");
        return users.save(new User(email, passwords.encode(password), role, clock.instant()));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
