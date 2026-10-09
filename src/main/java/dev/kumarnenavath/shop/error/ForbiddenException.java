package dev.kumarnenavath.shop.error;

/** Authenticated but not allowed, e.g. a customer calling an admin endpoint (403). */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
