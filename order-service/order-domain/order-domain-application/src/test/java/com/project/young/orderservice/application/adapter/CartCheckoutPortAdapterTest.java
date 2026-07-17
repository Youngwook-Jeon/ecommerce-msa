package com.project.young.orderservice.application.adapter;

import com.project.young.orderservice.application.service.CartApplicationService;
import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.entity.Order;
import com.project.young.orderservice.domain.sync.CartSyncResult;
import com.project.young.orderservice.domain.valueobject.CartId;
import com.project.young.orderservice.domain.valueobject.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartCheckoutPortAdapterTest {

    private static final UserId USER_ID = new UserId("user-checkout-1");

    @Mock
    private CartApplicationService cartApplicationService;

    @InjectMocks
    private CartCheckoutPortAdapter cartCheckoutPortAdapter;

    @Test
    @DisplayName("syncForCheckout: CartApplicationService.syncExistingUserCart에 위임한다")
    void syncForCheckout_delegatesToCartApplicationService() {
        Cart cart = Cart.createForUser(USER_ID, new CartId(UUID.randomUUID()));
        CartSyncResult expected = new CartSyncResult(cart, List.of());
        when(cartApplicationService.syncExistingUserCart(USER_ID)).thenReturn(expected);

        CartSyncResult result = cartCheckoutPortAdapter.syncForCheckout(USER_ID);

        assertThat(result).isSameAs(expected);
        verify(cartApplicationService).syncExistingUserCart(USER_ID);
    }

    @Test
    @DisplayName("clearAfterPayment: CartApplicationService.clearCartAfterPayment에 위임한다")
    void clearAfterPayment_delegatesToCartApplicationService() {
        Order order = org.mockito.Mockito.mock(Order.class);

        cartCheckoutPortAdapter.clearAfterPayment(order);

        verify(cartApplicationService).clearCartAfterPayment(order);
    }
}
