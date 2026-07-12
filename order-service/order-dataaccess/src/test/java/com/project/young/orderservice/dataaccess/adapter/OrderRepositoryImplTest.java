package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.dataaccess.entity.OrderEntity;
import com.project.young.orderservice.dataaccess.mapper.OrderAggregateMapper;
import com.project.young.orderservice.dataaccess.mapper.OrderDataAccessMapper;
import com.project.young.orderservice.dataaccess.repository.OrderJpaRepository;
import com.project.young.orderservice.domain.entity.Order;
import com.project.young.orderservice.domain.entity.OrderLine;
import com.project.young.orderservice.domain.valueobject.OrderId;
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

import static com.project.young.orderservice.dataaccess.support.OrderMapperTestFixtures.ORDER_ID;
import static com.project.young.orderservice.dataaccess.support.OrderMapperTestFixtures.USER_ID;
import static com.project.young.orderservice.dataaccess.support.OrderMapperTestFixtures.domainOrderWithOneLine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderRepositoryImplTest {

    @Mock
    private OrderJpaRepository orderJpaRepository;

    @Mock
    private OrderDataAccessMapper orderDataAccessMapper;

    @Mock
    private OrderAggregateMapper orderAggregateMapper;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private OrderRepositoryImpl orderRepository;

    @Nested
    @DisplayName("insert 테스트")
    class InsertTests {

        @Test
        @DisplayName("insert: null order 저장 시 예외 발생")
        void insert_nullOrder_throwsException() {
            assertThatThrownBy(() -> orderRepository.insert(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("order must not be null");

            verifyNoInteractions(orderJpaRepository, orderDataAccessMapper, entityManager);
        }

        @Test
        @DisplayName("insert: order id가 없으면 예외 발생")
        void insert_missingOrderId_throwsException() {
            Order order = mock(Order.class);
            when(order.getId()).thenReturn(null);

            assertThatThrownBy(() -> orderRepository.insert(order))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("order id must not be null for insert");
        }

        @Test
        @DisplayName("insert: order line id가 없으면 예외 발생")
        void insert_missingLineId_throwsException() {
            Order order = mock(Order.class);
            OrderLine line = mock(OrderLine.class);
            when(order.getId()).thenReturn(ORDER_ID);
            when(order.getLines()).thenReturn(List.of(line));
            when(line.getId()).thenReturn(null);

            assertThatThrownBy(() -> orderRepository.insert(order))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("order line id must not be null for insert");
        }

        @Test
        @DisplayName("insert: 주문 저장 성공")
        void insert_newOrder_success() {
            Order order = domainOrderWithOneLine();
            OrderEntity toPersist = mock(OrderEntity.class);

            when(orderDataAccessMapper.orderToOrderEntity(order)).thenReturn(toPersist);

            orderRepository.insert(order);

            verify(orderDataAccessMapper).orderToOrderEntity(order);
            verify(entityManager).persist(toPersist);
            verify(orderJpaRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("조회 테스트")
    class FindTests {

        @Test
        @DisplayName("findById: 주문 ID 조회 성공")
        void findById_success() {
            OrderEntity entity = mock(OrderEntity.class);
            Order domainOrder = domainOrderWithOneLine();

            when(orderJpaRepository.findWithLinesById(ORDER_ID.getValue())).thenReturn(Optional.of(entity));
            when(orderAggregateMapper.toOrder(entity)).thenReturn(domainOrder);

            Optional<Order> result = orderRepository.findById(ORDER_ID);

            assertThat(result).contains(domainOrder);
            verify(orderJpaRepository).findWithLinesById(ORDER_ID.getValue());
            verify(orderAggregateMapper).toOrder(entity);
        }

        @Test
        @DisplayName("findById: null orderId면 예외 발생")
        void findById_nullOrderId_throwsException() {
            assertThatThrownBy(() -> orderRepository.findById(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("orderId must not be null");

            verifyNoInteractions(orderJpaRepository);
        }

        @Test
        @DisplayName("findByIdAndUserId: 사용자 주문 조회 성공")
        void findByIdAndUserId_success() {
            OrderEntity entity = mock(OrderEntity.class);
            Order domainOrder = domainOrderWithOneLine();

            when(orderJpaRepository.findWithLinesByIdAndUserId(ORDER_ID.getValue(), USER_ID.value()))
                    .thenReturn(Optional.of(entity));
            when(orderAggregateMapper.toOrder(entity)).thenReturn(domainOrder);

            Optional<Order> result = orderRepository.findByIdAndUserId(ORDER_ID, USER_ID);

            assertThat(result).contains(domainOrder);
            verify(orderJpaRepository).findWithLinesByIdAndUserId(ORDER_ID.getValue(), USER_ID.value());
            verify(orderAggregateMapper).toOrder(entity);
        }

        @Test
        @DisplayName("findByIdAndUserId: null userId면 예외 발생")
        void findByIdAndUserId_nullUserId_throwsException() {
            assertThatThrownBy(() -> orderRepository.findByIdAndUserId(ORDER_ID, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("userId must not be null");

            verifyNoInteractions(orderJpaRepository);
        }

        @Test
        @DisplayName("findByIdAndUserId: null orderId면 예외 발생")
        void findByIdAndUserId_nullOrderId_throwsException() {
            assertThatThrownBy(() -> orderRepository.findByIdAndUserId(null, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("orderId must not be null");

            verifyNoInteractions(orderJpaRepository);
        }
    }
}
