package com.project.young.orderservice.web.cart;

import com.project.young.orderservice.domain.valueobject.CartId;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Component
public class GuestCartCookieSupport {

    private final GuestCartCookieProperties properties;

    public GuestCartCookieSupport(GuestCartCookieProperties properties) {
        this.properties = properties;
    }

    public Optional<CartId> readCartId(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return Optional.empty();
        }

        return Arrays.stream(request.getCookies())
                .filter(cookie -> properties.getName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .map(this::parseCartId)
                .flatMap(Optional::stream)
                .findFirst();
    }

    public void writeCartId(HttpServletResponse response, CartId cartId) {
        if (response == null) {
            throw new IllegalArgumentException("response must not be null");
        }
        if (cartId == null) {
            throw new IllegalArgumentException("cartId must not be null");
        }

        ResponseCookie cookie = ResponseCookie.from(properties.getName(), cartId.getValue().toString())
                .httpOnly(true)
                .path(properties.getPath())
                .maxAge(properties.getMaxAgeSeconds())
                .sameSite("Lax")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public void expire(HttpServletResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("response must not be null");
        }

        ResponseCookie cookie = ResponseCookie.from(properties.getName(), "")
                .httpOnly(true)
                .path(properties.getPath())
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private Optional<CartId> parseCartId(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new CartId(UUID.fromString(rawValue.trim())));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
