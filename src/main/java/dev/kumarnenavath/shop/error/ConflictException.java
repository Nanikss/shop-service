package dev.kumarnenavath.shop.error;

/** Request conflicts with current state, e.g. not enough stock or an invalid order transition (409). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
