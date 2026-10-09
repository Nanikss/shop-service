package dev.kumarnenavath.shop.security;

import dev.kumarnenavath.shop.domain.Role;
import dev.kumarnenavath.shop.error.ForbiddenException;
import dev.kumarnenavath.shop.error.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Secure by default: every handler needs a valid bearer token unless it's marked
 * {@link PublicEndpoint}. Exceptions thrown here go through the normal ProblemDetail handler.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    static final String ATTRIBUTE = AuthUser.class.getName();
    private static final String BEARER = "Bearer ";

    private final JwtCodec jwt;

    public AuthInterceptor(JwtCodec jwt) {
        this.jwt = jwt;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || isPublic(method)) return true;

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            throw new UnauthorizedException("Missing bearer token");
        }
        AuthUser user = jwt.verify(header.substring(BEARER.length()).trim());
        if (method.hasMethodAnnotation(AdminOnly.class) && user.role() != Role.ADMIN) {
            throw new ForbiddenException("Admin role required");
        }
        request.setAttribute(ATTRIBUTE, user);
        return true;
    }

    private static boolean isPublic(HandlerMethod method) {
        return method.hasMethodAnnotation(PublicEndpoint.class)
                || method.getBeanType().isAnnotationPresent(PublicEndpoint.class);
    }
}
