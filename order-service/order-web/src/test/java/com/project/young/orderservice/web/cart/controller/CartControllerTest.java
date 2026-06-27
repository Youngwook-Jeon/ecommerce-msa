package com.project.young.orderservice.web.cart.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.common.application.web.GlobalExceptionHandler;
import com.project.young.orderservice.application.service.CartApplicationService;
import com.project.young.orderservice.application.service.CartOwner;
import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.valueobject.CartId;
import com.project.young.orderservice.domain.valueobject.UserId;
import com.project.young.orderservice.web.cart.CurrentCartSupport;
import com.project.young.orderservice.web.cart.GuestCartCookieProperties;
import com.project.young.orderservice.web.cart.GuestCartCookieSupport;
import com.project.young.orderservice.web.cart.dto.AddCartItemRequest;
import com.project.young.orderservice.web.cart.mapper.CartResponseMapper;
import com.project.young.orderservice.web.config.SecurityConfig;
import com.project.young.orderservice.web.controller.TestConfig;
import com.project.young.orderservice.web.exception.handler.OrderServiceGlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
@Import({
        SecurityConfig.class,
        TestConfig.class,
        GlobalExceptionHandler.class,
        OrderServiceGlobalExceptionHandler.class,
        CartResponseMapper.class,
        CurrentCartSupport.class,
        GuestCartCookieSupport.class,
        GuestCartCookieProperties.class
})
class CartControllerTest {

    private static final String USER_SUBJECT = "018f0000-0000-7000-8000-000000000101";
    private static final UserId USER_ID = new UserId(USER_SUBJECT);
    private static final CartId CART_ID = new CartId(UUID.fromString("018f0000-0000-7000-8000-000000000201"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CartApplicationService cartApplicationService;

    @Test
    @DisplayName("GET /carts/current: 비인증 게스트는 빈 카트를 반환한다")
    void getCurrentCart_guestWithoutCookie_returnsEmptyCart() throws Exception {
        mockMvc.perform(get("/carts/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerType").value("GUEST"))
                .andExpect(jsonPath("$.itemCount").value(0));
    }

    @Test
    @DisplayName("GET /carts/current: 인증 사용자는 기존 카트를 반환한다")
    void getCurrentCart_authenticatedUser_returnsCart() throws Exception {
        Cart cart = Cart.createForUser(USER_ID, CART_ID);
        when(cartApplicationService.findCart(CartOwner.forUser(USER_ID))).thenReturn(Optional.of(cart));

        mockMvc.perform(get("/carts/current")
                        .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerType").value("USER"))
                .andExpect(jsonPath("$.userId").value(USER_SUBJECT))
                .andExpect(jsonPath("$.cartId").value(CART_ID.getValue().toString()));
    }

    @Test
    @DisplayName("POST /carts/current/items: 게스트 첫 추가 시 쿠키를 발급한다")
    void addItem_guestFirstAdd_setsCookie() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Cart cart = Cart.createForGuest(CART_ID);

        when(cartApplicationService.createGuestCart()).thenReturn(cart);
        when(cartApplicationService.addItem(eq(CartOwner.forGuest(CART_ID)), any(), any(), eq(2))).thenReturn(cart);

        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(productId)
                .productVariantId(variantId)
                .quantity(2)
                .build();

        mockMvc.perform(post("/carts/current/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("guest_cart_id"))
                .andExpect(jsonPath("$.cartId").value(CART_ID.getValue().toString()));

        verify(cartApplicationService).createGuestCart();
        verify(cartApplicationService).addItem(eq(CartOwner.forGuest(CART_ID)), any(), any(), eq(2));
    }

    @Test
    @DisplayName("POST /carts/current/items: 인증 사용자는 userId로 추가한다")
    void addItem_authenticatedUser_usesUserCart() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Cart cart = Cart.createForUser(USER_ID, CART_ID);

        when(cartApplicationService.addItem(eq(CartOwner.forUser(USER_ID)), any(), any(), eq(1))).thenReturn(cart);

        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(productId)
                .productVariantId(variantId)
                .quantity(1)
                .build();

        mockMvc.perform(post("/carts/current/items")
                        .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerType").value("USER"));

        verify(cartApplicationService).addItem(eq(CartOwner.forUser(USER_ID)), any(), any(), eq(1));
    }
}
