package dev.kumarnenavath.shop.security;

import dev.kumarnenavath.shop.domain.Role;

/** The caller, as proven by a verified token. Controllers receive it as a method parameter. */
public record AuthUser(long id, String email, Role role) {}
