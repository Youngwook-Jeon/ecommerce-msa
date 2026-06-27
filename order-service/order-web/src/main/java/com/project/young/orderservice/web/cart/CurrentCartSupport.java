package com.project.young.orderservice.web.cart;

import com.project.young.orderservice.application.service.CartApplicationService;
import com.project.young.orderservice.application.service.CartOwner;
import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.exception.CartNotFoundException;
import com.project.young.orderservice.domain.valueobject.CartId;
import com.project.young.orderservice.domain.valueobject.UserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CurrentCartSupport {

    private final GuestCartCookieSupport guestCartCookieSupport;
    private final CartApplicationService cartApplicationService;

    public CurrentCartSupport(
            GuestCartCookieSupport guestCartCookieSupport,
            CartApplicationService cartApplicationService
    ) {
        this.guestCartCookieSupport = guestCartCookieSupport;
        this.cartApplicationService = cartApplicationService;
    }

    public Optional<CartOwner> resolveOwner(Jwt jwt, HttpServletRequest request) {
        if (isAuthenticated(jwt)) {
            return Optional.of(CartOwner.forUser(new UserId(jwt.getSubject())));
        }
        return guestCartCookieSupport.readCartId(request).map(CartOwner::forGuest);
    }

    public CartOwner resolveOwnerForMutation(
            Jwt jwt,
            HttpServletRequest request,
            HttpServletResponse response,
            GuestCartPolicy guestCartPolicy
    ) {
        if (isAuthenticated(jwt)) {
            return CartOwner.forUser(new UserId(jwt.getSubject()));
        }
        return guestCartCookieSupport.readCartId(request)
                .map(CartOwner::forGuest)
                .orElseGet(() -> createGuestOwner(response, guestCartPolicy));
    }

    private CartOwner createGuestOwner(HttpServletResponse response, GuestCartPolicy guestCartPolicy) {
        if (guestCartPolicy == GuestCartPolicy.REQUIRE_EXISTING) {
            throw new CartNotFoundException("Guest cart not found.");
        }
        Cart cart = cartApplicationService.createGuestCart();
        guestCartCookieSupport.writeCartId(response, cart.getId());
        return CartOwner.forGuest(cart.getId());
    }

    private boolean isAuthenticated(Jwt jwt) {
        return jwt != null && jwt.getSubject() != null && !jwt.getSubject().isBlank();
    }
}
