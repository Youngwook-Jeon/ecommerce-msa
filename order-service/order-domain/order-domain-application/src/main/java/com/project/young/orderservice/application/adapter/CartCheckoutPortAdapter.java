package com.project.young.orderservice.application.adapter;

import com.project.young.orderservice.application.port.output.CartCheckoutPort;
import com.project.young.orderservice.application.service.CartApplicationService;
import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.sync.CartSyncResult;
import com.project.young.orderservice.domain.valueobject.UserId;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class CartCheckoutPortAdapter implements CartCheckoutPort {

    private final CartApplicationService cartApplicationService;

    public CartCheckoutPortAdapter(CartApplicationService cartApplicationService) {
        this.cartApplicationService = cartApplicationService;
    }

    @Override
    public CartSyncResult syncForCheckout(UserId userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return cartApplicationService.syncExistingUserCart(userId);
    }

    @Override
    public void clearAfterOrder(Cart cart) {
        Objects.requireNonNull(cart, "cart must not be null");
        cartApplicationService.clearCart(cart);
    }
}
