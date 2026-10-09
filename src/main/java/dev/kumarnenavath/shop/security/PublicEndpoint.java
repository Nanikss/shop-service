package dev.kumarnenavath.shop.security;

import java.lang.annotation.*;

/** Marks a controller or handler method that doesn't need a bearer token. Everything else does. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublicEndpoint {}
