package dev.kumarnenavath.shop.security;

import java.lang.annotation.*;

/** Handler method that requires the ADMIN role in the caller's token. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AdminOnly {}
