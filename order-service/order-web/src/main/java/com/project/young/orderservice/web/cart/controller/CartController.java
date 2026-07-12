package com.project.young.orderservice.web.cart.controller;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.orderservice.application.service.CartApplicationService;
import com.project.young.orderservice.application.service.CartOwner;
import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.merge.CartMergeResult;
import com.project.young.orderservice.domain.valueobject.CartId;
import com.project.young.orderservice.domain.valueobject.CartItemId;
import com.project.young.orderservice.domain.valueobject.UserId;
import com.project.young.orderservice.web.cart.CurrentCartSupport;
import com.project.young.orderservice.web.cart.GuestCartPolicy;
import com.project.young.orderservice.web.cart.dto.AddCartItemRequest;
import com.project.young.orderservice.web.cart.dto.CartMergeResponse;
import com.project.young.orderservice.web.cart.dto.CartResponse;
import com.project.young.orderservice.web.cart.dto.CartSyncResponse;
import com.project.young.orderservice.web.cart.dto.UpdateCartItemQuantityRequest;
import com.project.young.orderservice.web.cart.mapper.CartResponseMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/carts")
public class CartController {

    private final CartApplicationService cartApplicationService;
    private final CartResponseMapper cartResponseMapper;
    private final CurrentCartSupport currentCartSupport;

    public CartController(
            CartApplicationService cartApplicationService,
            CartResponseMapper cartResponseMapper,
            CurrentCartSupport currentCartSupport
    ) {
        this.cartApplicationService = cartApplicationService;
        this.cartResponseMapper = cartResponseMapper;
        this.currentCartSupport = currentCartSupport;
    }

    @GetMapping("/current")
    public ResponseEntity<CartResponse> getCurrentCart(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request
    ) {
        log.info("A get request to get current Cart");
        var owner = currentCartSupport.resolveOwner(jwt, request);
        CartResponse response = owner
                .flatMap(cartApplicationService::findCart)
                .map(cartResponseMapper::toResponse)
                .orElseGet(() -> cartResponseMapper.emptyResponse(owner));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/current/items")
    public ResponseEntity<CartResponse> addItem(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddCartItemRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        log.info(
                "A post request to add item to Cart: productId={}, productVariantId={}, quantity={}",
                request.productId(),
                request.productVariantId(),
                request.quantity()
        );
        CartOwner owner = currentCartSupport.resolveOwnerForMutation(
                jwt, httpRequest, httpResponse, GuestCartPolicy.CREATE_IF_ABSENT);
        Cart cart = cartApplicationService.addItem(
                owner,
                new ProductId(request.productId()),
                new ProductVariantId(request.productVariantId()),
                request.quantity());
        return ResponseEntity.ok(cartResponseMapper.toResponse(cart));
    }

    @PatchMapping("/current/items/{itemId}")
    public ResponseEntity<CartResponse> updateItemQuantity(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateCartItemQuantityRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        log.info("A patch request to update Cart item quantity: itemId={}, quantity={}", itemId, request.quantity());
        CartOwner owner = currentCartSupport.resolveOwnerForMutation(
                jwt, httpRequest, httpResponse, GuestCartPolicy.REQUIRE_EXISTING);
        Cart cart = cartApplicationService.updateItemQuantity(
                owner, new CartItemId(itemId), request.quantity());
        return ResponseEntity.ok(cartResponseMapper.toResponse(cart));
    }

    @DeleteMapping("/current/items/{itemId}")
    public ResponseEntity<CartResponse> removeItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID itemId,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        log.info("A delete request to remove Cart item: itemId={}", itemId);
        CartOwner owner = currentCartSupport.resolveOwnerForMutation(
                jwt, httpRequest, httpResponse, GuestCartPolicy.REQUIRE_EXISTING);
        Cart cart = cartApplicationService.removeItem(owner, new CartItemId(itemId));
        return ResponseEntity.ok(cartResponseMapper.toResponse(cart));
    }

    @DeleteMapping("/current/items")
    public ResponseEntity<CartResponse> clearItems(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        log.info("A delete request to clear Cart items");
        CartOwner owner = currentCartSupport.resolveOwnerForMutation(
                jwt, httpRequest, httpResponse, GuestCartPolicy.REQUIRE_EXISTING);
        Cart cart = cartApplicationService.clearCart(owner);
        return ResponseEntity.ok(cartResponseMapper.toResponse(cart));
    }

    @PostMapping("/current/sync")
    public ResponseEntity<CartSyncResponse> syncWithCatalog(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        log.info("A post request to sync Cart with catalog");
        CartOwner owner = currentCartSupport.resolveOwnerForMutation(
                jwt, httpRequest, httpResponse, GuestCartPolicy.CREATE_IF_ABSENT);
        return ResponseEntity.ok(cartResponseMapper.toSyncResponse(cartApplicationService.syncCart(owner)));
    }

    @PostMapping("/current/merge")
    public ResponseEntity<CartMergeResponse> mergeGuestCart(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        log.info("A post request to merge guest Cart for user: {}", jwt.getSubject());
        UserId userId = currentCartSupport.requireAuthenticatedUser(jwt);
        CartOwner owner = CartOwner.forUser(userId);

        Optional<CartId> guestCartId = currentCartSupport.readGuestCartId(httpRequest);
        if (guestCartId.isEmpty()) {
            return ResponseEntity.ok(
                    cartResponseMapper.noOpMergeResponse(cartApplicationService.findCart(owner), owner));
        }

        CartMergeResult result = cartApplicationService.mergeGuestCart(userId, guestCartId.get());
        currentCartSupport.expireGuestCart(httpResponse);
        return ResponseEntity.ok(cartResponseMapper.toMergeResponse(result, owner));
    }
}
