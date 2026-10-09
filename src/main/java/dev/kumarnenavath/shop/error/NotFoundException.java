package dev.kumarnenavath.shop.error;

/** Requested resource doesn't exist, or isn't visible to the caller (404). */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
