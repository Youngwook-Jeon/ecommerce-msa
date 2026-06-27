package com.project.young.orderservice.web.cart;

/**
 * How to behave when a guest request has no cart cookie yet.
 */
public enum GuestCartPolicy {
    /** Throws {@link com.project.young.orderservice.domain.exception.CartNotFoundException}. */
    REQUIRE_EXISTING,
    /** Creates a guest cart and sets the cookie on the response. */
    CREATE_IF_ABSENT
}
