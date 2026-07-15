package com.project.young.orderservice.application.port.output;

import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.sync.CartSyncResult;
import com.project.young.orderservice.domain.valueobject.UserId;

/**
 * Checkout-time cart operations required by order placement.
 * Decouples {@link com.project.young.orderservice.application.service.OrderApplicationService}
 * from cart application service implementation details.
 */
public interface CartCheckoutPort {

    /**
     * Syncs the authenticated user's existing cart with catalog before checkout.
     * Must not create a new cart when none exists.
     */
    CartSyncResult syncForCheckout(UserId userId);

    /**
     * Clears the synced cart after payment succeeds (not at order placement).
     * Kept here so payment confirmation can reuse the same checkout port.
     */
    void clearAfterOrder(Cart cart);
}
