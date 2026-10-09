package dev.kumarnenavath.shop.error;

/** Missing or invalid credentials, token or webhook signature (401). */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
