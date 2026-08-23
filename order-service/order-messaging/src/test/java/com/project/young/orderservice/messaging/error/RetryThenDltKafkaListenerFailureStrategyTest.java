package com.project.young.orderservice.messaging.error;

import com.project.young.orderservice.application.port.output.InventoryReservationClientException;
import com.project.young.orderservice.application.port.output.InventoryReservationUnavailableException;
import com.project.young.orderservice.domain.exception.OrderIllegalTransitionException;
import com.project.young.orderservice.domain.exception.OrderNotFoundException;
import com.project.young.orderservice.domain.exception.OrderStateConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ExceptionClassifier;
import org.springframework.kafka.listener.ListenerExecutionFailedException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RetryThenDltKafkaListenerFailureStrategyTest {

    @Test
    @DisplayName("configure: MANUAL_IMMEDIATE ack를 적용하고 DefaultErrorHandler를 만든다")
    void configure_setsManualAckAndDefaultErrorHandler() {
        RetryThenDltKafkaListenerFailureStrategy strategy = strategy();

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        strategy.configure(factory);

        assertThat(factory.getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        assertThat(strategy.createErrorHandler()).isInstanceOf(DefaultErrorHandler.class);
    }

    @Test
    @DisplayName("createErrorHandler: 영구 실패 예외는 non-retryable, 일시 실패는 retryable")
    void createErrorHandler_classifiesRetryability() throws Exception {
        DefaultErrorHandler handler = (DefaultErrorHandler) strategy().createErrorHandler();
        BinaryExceptionClassifier classifier = classifierOf(handler);

        // false => not retryable (go to DLT immediately)
        assertThat(classifier.classify(wrapped(new OrderNotFoundException("missing")))).isFalse();
        assertThat(classifier.classify(wrapped(new OrderIllegalTransitionException("CANCELLED")))).isFalse();
        assertThat(classifier.classify(wrapped(new InventoryReservationClientException("404")))).isFalse();

        // true => retryable
        assertThat(classifier.classify(wrapped(new InventoryReservationUnavailableException("down", null))))
                .isTrue();
        assertThat(classifier.classify(wrapped(new OrderStateConflictException("concurrently")))).isTrue();
    }

    @Test
    @DisplayName("SagaNonRetryableExceptions: 영구 실패 타입 목록을 제공한다")
    void sagaNonRetryableExceptions_listsPermanentFailures() {
        assertThat(SagaNonRetryableExceptions.types())
                .contains(
                        OrderNotFoundException.class,
                        OrderIllegalTransitionException.class,
                        InventoryReservationClientException.class
                );
    }

    private static RetryThenDltKafkaListenerFailureStrategy strategy() {
        SagaKafkaConsumerErrorProperties properties = new SagaKafkaConsumerErrorProperties(
                "retry-then-dlt",
                new SagaKafkaConsumerErrorProperties.Retry(3L, 500L),
                new SagaKafkaConsumerErrorProperties.Dlt(".DLT")
        );
        @SuppressWarnings("unchecked")
        KafkaTemplate<Object, Object> template = mock(KafkaTemplate.class);
        return new RetryThenDltKafkaListenerFailureStrategy(properties, template);
    }

    private static ListenerExecutionFailedException wrapped(Exception cause) {
        return new ListenerExecutionFailedException("listener failed", cause);
    }

    private static BinaryExceptionClassifier classifierOf(DefaultErrorHandler handler) throws Exception {
        Method method = ExceptionClassifier.class.getDeclaredMethod("getClassifier");
        method.setAccessible(true);
        return (BinaryExceptionClassifier) method.invoke(handler);
    }
}
