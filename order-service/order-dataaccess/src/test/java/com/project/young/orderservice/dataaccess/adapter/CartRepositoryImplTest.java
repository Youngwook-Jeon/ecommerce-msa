package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.dataaccess.entity.CartEntity;
import com.project.young.orderservice.dataaccess.mapper.CartAggregateMapper;
import com.project.young.orderservice.dataaccess.mapper.CartDataAccessMapper;
import com.project.young.orderservice.dataaccess.repository.CartJpaRepository;
import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.entity.CartItem;
import com.project.young.orderservice.domain.exception.CartNotFoundException;
import com.project.young.orderservice.domain.valueobject.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.CART_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.USER_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.domainCartWithOneItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartRepositoryImplTest {

  @Mock
  private CartJpaRepository cartJpaRepository;

  @Mock
  private CartDataAccessMapper cartDataAccessMapper;

  @Mock
  private CartAggregateMapper cartAggregateMapper;

  @Mock
  private EntityManager entityManager;

  @InjectMocks
  private CartRepositoryImpl cartRepository;

  @Nested
  @DisplayName("insert/update 테스트")
  class SaveTests {

    @Test
    @DisplayName("insert: null cart 저장 시 예외 발생")
    void insert_nullCart_throwsException() {
      assertThatThrownBy(() -> cartRepository.insert(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("cart must not be null");

      verifyNoInteractions(cartJpaRepository, cartDataAccessMapper, entityManager);
    }

    @Test
    @DisplayName("insert: cart id가 없으면 예외 발생")
    void insert_missingCartId_throwsException() {
      Cart cart = mock(Cart.class);
      when(cart.getId()).thenReturn(null);

      assertThatThrownBy(() -> cartRepository.insert(cart))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("cart id must not be null for insert");
    }

    @Test
    @DisplayName("insert: 사용자 카트 저장 성공")
    void insert_newCart_success() {
      Cart cart = domainCartWithOneItem();
      CartEntity toPersist = mock(CartEntity.class);

      when(cartDataAccessMapper.cartToCartEntity(cart)).thenReturn(toPersist);

      cartRepository.insert(cart);

      verify(cartDataAccessMapper).cartToCartEntity(cart);
      verify(entityManager).persist(toPersist);
      verify(cartJpaRepository, never()).save(any());
    }

    @Test
    @DisplayName("insert: 게스트 카트는 거부한다")
    void insert_guestCart_throws() {
      Cart guestCart = Cart.createForGuest(CART_ID);

      assertThatThrownBy(() -> cartRepository.insert(guestCart))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("user-owned carts");

      verifyNoInteractions(cartDataAccessMapper, entityManager);
    }

    @Test
    @DisplayName("insert: cart item id가 없으면 예외 발생")
    void insert_missingItemId_throwsException() {
      Cart cart = mock(Cart.class);
      CartItem item = mock(CartItem.class);
      when(cart.getId()).thenReturn(CART_ID);
      when(cart.getItems()).thenReturn(List.of(item));
      when(item.getId()).thenReturn(null);

      assertThatThrownBy(() -> cartRepository.insert(cart))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("cart item id must not be null for insert");
    }

    @Test
    @DisplayName("update: 기존 카트 업데이트 성공")
    void update_existingCart_success() {
      Cart cart = domainCartWithOneItem();
      CartEntity existingEntity = mock(CartEntity.class);

      when(cartJpaRepository.findWithItemsById(CART_ID.getValue())).thenReturn(Optional.of(existingEntity));

      cartRepository.update(cart);

      verify(cartJpaRepository).findWithItemsById(CART_ID.getValue());
      verify(cartDataAccessMapper).updateEntityFromDomain(cart, existingEntity);
      verify(cartJpaRepository, never()).save(any());
      verify(entityManager, never()).persist(any());
    }

    @Test
    @DisplayName("update: 존재하지 않는 카트 업데이트 시 CartNotFoundException 발생")
    void update_missingCart_throwsException() {
      Cart cart = domainCartWithOneItem();

      when(cartJpaRepository.findWithItemsById(CART_ID.getValue())).thenReturn(Optional.empty());

      assertThatThrownBy(() -> cartRepository.update(cart))
          .isInstanceOf(CartNotFoundException.class)
          .hasMessageContaining("Cart not found");

      verify(cartJpaRepository).findWithItemsById(CART_ID.getValue());
      verify(cartDataAccessMapper, never()).updateEntityFromDomain(any(), any());
    }
  }

  @Nested
  @DisplayName("조회 테스트")
  class FindTests {

    @Test
    @DisplayName("findByUserId: 사용자 카트 조회 성공")
    void findByUserId_success() {
      CartEntity entity = mock(CartEntity.class);
      Cart domainCart = domainCartWithOneItem();

      when(cartJpaRepository.findByUserId(USER_ID.value())).thenReturn(Optional.of(entity));
      when(cartAggregateMapper.toCart(entity)).thenReturn(domainCart);

      Optional<Cart> result = cartRepository.findByUserId(USER_ID);

      assertThat(result).contains(domainCart);
      verify(cartJpaRepository).findByUserId(USER_ID.value());
      verify(cartAggregateMapper).toCart(entity);
    }

    @Test
    @DisplayName("findByUserId: null userId면 예외 발생")
    void findByUserId_nullUserId_throwsException() {
      assertThatThrownBy(() -> cartRepository.findByUserId(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("userId must not be null");

      verifyNoInteractions(cartJpaRepository);
    }

    @Test
    @DisplayName("findById: 카트 ID 조회 성공")
    void findById_success() {
      CartEntity entity = mock(CartEntity.class);
      Cart domainCart = domainCartWithOneItem();

      when(cartJpaRepository.findWithItemsById(CART_ID.getValue())).thenReturn(Optional.of(entity));
      when(cartAggregateMapper.toCart(entity)).thenReturn(domainCart);

      Optional<Cart> result = cartRepository.findById(CART_ID);

      assertThat(result).contains(domainCart);
      verify(cartJpaRepository).findWithItemsById(CART_ID.getValue());
      verify(cartAggregateMapper).toCart(entity);
    }

    @Test
    @DisplayName("findById: null cartId면 예외 발생")
    void findById_nullCartId_throwsException() {
      assertThatThrownBy(() -> cartRepository.findById(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("cartId must not be null");

      verifyNoInteractions(cartJpaRepository);
    }
  }
}
