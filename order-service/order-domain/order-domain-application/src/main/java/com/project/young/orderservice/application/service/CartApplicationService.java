package com.project.young.orderservice.application.service;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.orderservice.application.mapper.CartCatalogMapper;
import com.project.young.orderservice.application.port.output.CartCatalogLineKey;
import com.project.young.orderservice.application.port.output.IdGenerator;
import com.project.young.orderservice.application.port.output.ProductCatalogPort;
import com.project.young.orderservice.application.port.output.view.CartCatalogLineView;
import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.entity.Order;
import com.project.young.orderservice.domain.exception.CartDomainException;
import com.project.young.orderservice.domain.exception.CartNotFoundException;
import com.project.young.orderservice.domain.repository.CartRepository;
import com.project.young.orderservice.domain.repository.GuestCartRepository;
import com.project.young.orderservice.domain.entity.CartItem;
import com.project.young.orderservice.domain.merge.CartMergeResult;
import com.project.young.orderservice.domain.merge.CartMergeSkipReason;
import com.project.young.orderservice.domain.merge.CartMergeSkippedLine;
import com.project.young.orderservice.domain.sync.CartCatalogLineState;
import com.project.young.orderservice.domain.sync.CartSyncRemovalReason;
import com.project.young.orderservice.domain.sync.CartSyncResult;
import com.project.young.orderservice.domain.valueobject.CartItemSnapshot;
import com.project.young.orderservice.domain.valueobject.CartId;
import com.project.young.orderservice.domain.valueobject.CartItemId;
import com.project.young.orderservice.domain.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class CartApplicationService {

    private static final Logger log = LoggerFactory.getLogger(CartApplicationService.class);

    private static final int MAX_MERGE_ATTEMPTS = 2;

    private final CartRepository cartRepository;
    private final GuestCartRepository guestCartRepository;
    private final ProductCatalogPort productCatalogPort;
    private final IdGenerator idGenerator;

    /**
     * Self-reference through the Spring proxy so the retry wrapper can start a fresh
     * transaction per attempt. Injected lazily to break the circular dependency.
     */
    private CartApplicationService self;

    public CartApplicationService(
            CartRepository cartRepository,
            GuestCartRepository guestCartRepository,
            ProductCatalogPort productCatalogPort,
            IdGenerator idGenerator
    ) {
        this.cartRepository = cartRepository;
        this.guestCartRepository = guestCartRepository;
        this.productCatalogPort = productCatalogPort;
        this.idGenerator = idGenerator;
    }

    @Autowired
    public void setSelf(@Lazy CartApplicationService self) {
        this.self = self;
    }

    @Transactional(readOnly = true)
    public Optional<Cart> findCart(CartOwner owner) {
        Objects.requireNonNull(owner, "owner must not be null");
        return switch (owner) {
            case CartOwner.User user -> {
                Objects.requireNonNull(user.userId(), "userId must not be null");
                yield cartRepository.findByUserId(user.userId());
            }
            case CartOwner.Guest guest -> {
                Objects.requireNonNull(guest.cartId(), "cartId must not be null");
                yield guestCartRepository.findById(guest.cartId());
            }
        };
    }

    @Transactional
    public Cart createGuestCart() {
        Cart cart = Cart.createForGuest(new CartId(idGenerator.generateId()));
        guestCartRepository.insert(cart);
        log.debug("Created guest cart {}", cart.getId().getValue());
        return cart;
    }

    @Transactional
    public Cart addItem(CartOwner owner, ProductId productId, ProductVariantId variantId, int quantity) {
        Objects.requireNonNull(owner, "owner must not be null");
        return addItem(getOrCreateCart(owner), productId, variantId, quantity);
    }

    @Transactional
    public Cart updateItemQuantity(CartOwner owner, CartItemId itemId, int quantity) {
        Objects.requireNonNull(owner, "owner must not be null");
        return updateItemQuantity(requireCart(owner), itemId, quantity);
    }

    @Transactional
    public Cart removeItem(CartOwner owner, CartItemId itemId) {
        Objects.requireNonNull(owner, "owner must not be null");
        return removeItem(requireCart(owner), itemId);
    }

    @Transactional
    public Cart clearCart(CartOwner owner) {
        Objects.requireNonNull(owner, "owner must not be null");
        return clearItems(requireCart(owner));
    }

    @Transactional
    public Cart clearCart(Cart cart) {
        Objects.requireNonNull(cart, "cart must not be null");
        return clearItems(cart);
    }

    /**
     * Clears only an unchanged checkout cart. If the user edited the cart while payment
     * was pending, preserve it rather than deleting new or changed items.
     */
    @Transactional
    public void clearCartAfterPayment(Order order) {
        Objects.requireNonNull(order, "order must not be null");
        Optional<Cart> cartOptional = cartRepository.findByUserId(order.getUserId());
        if (cartOptional.isEmpty()) {
            return;
        }

        Cart cart = cartOptional.get();
        if (!cart.hasSameItemsAs(order.getLines())) {
            log.info(
                    "Preserving changed cart {} after payment for order {}",
                    cart.getId().getValue(),
                    order.getId().getValue()
            );
            return;
        }
        clearItems(cart);
    }

    @Transactional
    public CartSyncResult syncCart(CartOwner owner) {
        Objects.requireNonNull(owner, "owner must not be null");
        return syncWithCatalog(getOrCreateCart(owner));
    }

    /**
     * Syncs an existing authenticated user's cart without creating a new one.
     * Used at checkout when an empty cart must be rejected.
     */
    @Transactional
    public CartSyncResult syncExistingUserCart(UserId userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartDomainException("Cart is empty."));
        if (cart.isEmpty()) {
            throw new CartDomainException("Cart is empty.");
        }
        return syncWithCatalog(cart);
    }

    /**
     * Merges a guest cart into the authenticated user's cart with optimistic-lock retry.
     * The actual merge runs in {@link #mergeGuestCartIntoUser(UserId, CartId)}; on a lost-update
     * conflict this retries in a fresh transaction. The retry is safe because the merge is
     * idempotent: once a winning transaction deletes the guest cart, a later attempt finds none
     * and returns the (already merged) user cart unchanged.
     */
    public CartMergeResult mergeGuestCart(UserId userId, CartId guestCartId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(guestCartId, "guestCartId must not be null");

        OptimisticLockingFailureException lastConflict = null;
        for (int attempt = 1; attempt <= MAX_MERGE_ATTEMPTS; attempt++) {
            try {
                return self.mergeGuestCartIntoUser(userId, guestCartId);
            } catch (OptimisticLockingFailureException ex) {
                lastConflict = ex;
                log.warn(
                        "Optimistic lock conflict merging guest cart {} into user {} (attempt {}/{})",
                        guestCartId.getValue(), userId.value(), attempt, MAX_MERGE_ATTEMPTS
                );
            }
        }
        throw lastConflict;
    }

    /**
     * Merges a guest cart into the authenticated user's cart, then syncs with catalog.
     * Idempotent when the guest cart is missing or already deleted. Prefer calling
     * {@link #mergeGuestCart(UserId, CartId)} which adds optimistic-lock retry.
     */
    @Transactional
    public CartMergeResult mergeGuestCartIntoUser(UserId userId, CartId guestCartId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(guestCartId, "guestCartId must not be null");

        Optional<Cart> guestOptional = guestCartRepository.findById(guestCartId);
        if (guestOptional.isEmpty()) {
            return CartMergeResult.noGuestCart(cartRepository.findByUserId(userId).orElse(null));
        }

        Cart guestCart = guestOptional.get();
        if (guestCart.isEmpty()) {
            guestCartRepository.delete(guestCartId);
            return CartMergeResult.emptyGuest(cartRepository.findByUserId(userId).orElse(null));
        }

        Map<UUID, CartCatalogLineView> guestCatalog = productCatalogPort.resolveLines(toCatalogLineKeys(guestCart));

        Cart userCart = getOrCreateUserCart(userId);
        List<CartMergeSkippedLine> skippedLines = new ArrayList<>();
        int mergedLineCount = 0;

        for (CartItem guestItem : guestCart.getItems()) {
            UUID variantId = guestItem.getProductVariantId().getValue();
            CartCatalogLineView view = guestCatalog.get(variantId);
            if (view == null) {
                skippedLines.add(CartMergeSkippedLine.fromGuestItem(guestItem, CartMergeSkipReason.VARIANT_NOT_FOUND));
                continue;
            }
            if (!view.productId().equals(guestItem.getProductId().getValue())) {
                skippedLines.add(CartMergeSkippedLine.fromGuestItem(guestItem, CartMergeSkipReason.PRODUCT_NOT_FOUND));
                continue;
            }

            boolean isNewLine = userCart.getItems().stream()
                    .noneMatch(item -> item.getProductVariantId().equals(guestItem.getProductVariantId()));
            if (isNewLine && userCart.itemCount() >= Cart.MAX_LINE_ITEMS) {
                skippedLines.add(CartMergeSkippedLine.fromGuestItem(
                        guestItem,
                        CartMergeSkipReason.MAX_LINE_ITEMS_EXCEEDED
                ));
                continue;
            }

            CartItemSnapshot snapshot = CartCatalogMapper.toSnapshot(view);
            userCart.addOrMergeItem(
                    guestItem.getProductId(),
                    guestItem.getProductVariantId(),
                    snapshot,
                    guestItem.getQuantity(),
                    new CartItemId(idGenerator.generateId())
            );
            mergedLineCount++;
        }

        CartSyncResult syncResult = reconcileCartWithCatalog(userCart);
        cartRepository.update(userCart);
        guestCartRepository.delete(guestCartId);

        log.debug(
                "Merged guest cart {} into user cart {} ({} lines, {} skipped)",
                guestCartId.getValue(),
                userCart.getId().getValue(),
                mergedLineCount,
                skippedLines.size()
        );

        return new CartMergeResult(userCart, mergedLineCount, skippedLines, syncResult.changes());
    }

    private Cart getOrCreateCart(CartOwner owner) {
        return switch (owner) {
            case CartOwner.User user -> getOrCreateUserCart(user.userId());
            case CartOwner.Guest guest -> getOrCreateGuestCart(guest.cartId());
        };
    }

    private Cart requireCart(CartOwner owner) {
        return switch (owner) {
            case CartOwner.User user -> requireUserCart(user.userId());
            case CartOwner.Guest guest -> requireGuestCart(guest.cartId());
        };
    }

    private Cart getOrCreateUserCart(UserId userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Cart cart = Cart.createForUser(userId, new CartId(idGenerator.generateId()));
                    cartRepository.insert(cart);
                    log.debug("Created user cart {}", cart.getId().getValue());
                    return cart;
                });
    }

    private Cart getOrCreateGuestCart(CartId cartId) {
        Objects.requireNonNull(cartId, "cartId must not be null");
        return guestCartRepository.findById(cartId)
                .orElseGet(() -> {
                    Cart cart = Cart.createForGuest(cartId);
                    guestCartRepository.insert(cart);
                    log.debug("Created guest cart {}", cart.getId().getValue());
                    return cart;
                });
    }

    private Cart addItem(Cart cart, ProductId productId, ProductVariantId variantId, int quantity) {
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(variantId, "variantId must not be null");
        if (quantity <= 0) {
            throw new CartDomainException("Quantity must be positive.");
        }

        CartCatalogLineView view = resolveRequiredLine(productId, variantId);
        CartCatalogLineState state = CartCatalogMapper.toLineState(view);
        if (!state.available()) {
            throw new CartDomainException(unavailableMessage(state.removalReason()));
        }

        int targetQuantity = cart.getItems().stream()
                .filter(item -> item.getProductVariantId().equals(variantId))
                .findFirst()
                .map(item -> item.getQuantity() + quantity)
                .orElse(quantity);
        if (state.stockQuantity() < targetQuantity) {
            throw new CartDomainException("Insufficient stock. Available: " + state.stockQuantity());
        }

        cart.addOrMergeItem(
                productId,
                variantId,
                state.snapshot(),
                quantity,
                new CartItemId(idGenerator.generateId())
        );
        updateCart(cart);
        return cart;
    }

    private Cart updateItemQuantity(Cart cart, CartItemId itemId, int quantity) {
        cart.updateItemQuantity(itemId, quantity);
        updateCart(cart);
        return cart;
    }

    private Cart removeItem(Cart cart, CartItemId itemId) {
        cart.removeItem(itemId);
        updateCart(cart);
        return cart;
    }

    private Cart clearItems(Cart cart) {
        cart.clearItems();
        updateCart(cart);
        return cart;
    }

    private CartSyncResult syncWithCatalog(Cart cart) {
        if (cart.isEmpty()) {
            return new CartSyncResult(cart, List.of());
        }
        CartSyncResult result = reconcileCartWithCatalog(cart);
        if (!result.changes().isEmpty()) {
            updateCart(cart);
        }
        return result;
    }

    private CartSyncResult reconcileCartWithCatalog(Cart cart) {
        if (cart.isEmpty()) {
            return new CartSyncResult(cart, List.of());
        }

        Map<UUID, CartCatalogLineView> resolved = productCatalogPort.resolveLines(toCatalogLineKeys(cart));
        return cart.reconcileWithCatalog(CartCatalogMapper.toLineStateByItemId(cart, resolved));
    }

    private Cart requireUserCart(UserId userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found for user: " + userId.value()));
    }

    private Cart requireGuestCart(CartId cartId) {
        Objects.requireNonNull(cartId, "cartId must not be null");
        return guestCartRepository.findById(cartId)
                .orElseThrow(() -> new CartNotFoundException("Guest cart not found: " + cartId.getValue()));
    }

    private CartCatalogLineView resolveRequiredLine(ProductId productId, ProductVariantId variantId) {
        Map<UUID, CartCatalogLineView> resolved = productCatalogPort.resolveLines(List.of(
                new CartCatalogLineKey(productId.getValue(), variantId.getValue())
        ));
        CartCatalogLineView view = resolved.get(variantId.getValue());
        if (view == null) {
            throw new CartDomainException("Product variant not found: " + variantId.getValue());
        }
        return view;
    }

    private List<CartCatalogLineKey> toCatalogLineKeys(Cart cart) {
        return cart.getItems().stream()
                .map(item -> new CartCatalogLineKey(
                        item.getProductId().getValue(),
                        item.getProductVariantId().getValue()
                ))
                .toList();
    }

    private void updateCart(Cart cart) {
        if (cart.isUserCart()) {
            cartRepository.update(cart);
        } else {
            guestCartRepository.update(cart);
        }
    }

    private static String unavailableMessage(CartSyncRemovalReason reason) {
        return switch (reason) {
            case OUT_OF_STOCK -> "Product variant is out of stock.";
            case NOT_PURCHASABLE -> "Product variant is not purchasable.";
            case PRODUCT_NOT_FOUND -> "Product does not match the selected variant.";
            case VARIANT_NOT_FOUND -> "Product variant not found.";
        };
    }
}
