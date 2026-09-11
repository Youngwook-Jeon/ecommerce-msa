package com.project.young.paymentservice.adapter.stripe;

import com.project.young.paymentservice.application.dto.command.ApplyProviderPaymentResultCommand;
import com.project.young.paymentservice.application.exception.InvalidStripeWebhookException;
import com.project.young.paymentservice.application.provider.ProviderPaymentResultOutcome;
import com.project.young.paymentservice.config.StripeProperties;
import com.project.young.paymentservice.domain.valueobject.PaymentProvider;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeWebhookAdapterTest {

    @Mock
    private Event event;

    @Mock
    private EventDataObjectDeserializer deserializer;

    @Mock
    private PaymentIntent paymentIntent;

    private final StripeWebhookAdapter adapter =
            new StripeWebhookAdapter(new StripeProperties("sk_test", "whsec_test"));

    @Test
    @DisplayName("mapEvent: payment_intent.succeeded → success command")
    void mapEvent_succeeded() {
        stubPaymentIntentEvent(StripeWebhookAdapter.EVENT_PAYMENT_INTENT_SUCCEEDED, "evt_ok", "pi_1");

        Optional<ApplyProviderPaymentResultCommand> command = adapter.mapEvent(event);

        assertThat(command).isPresent();
        assertThat(command.get().success()).isTrue();
        assertThat(command.get().eventId()).isEqualTo("evt_ok");
        assertThat(command.get().provider()).isEqualTo(PaymentProvider.STRIPE);
        assertThat(command.get().providerPaymentId()).isEqualTo("pi_1");
    }

    @Test
    @DisplayName("mapEvent: payment_intent.payment_failed → non-terminal attempt failure command")
    void mapEvent_paymentFailed() {
        StripeError error = mock(StripeError.class);
        when(error.getMessage()).thenReturn("Your card was declined.");
        when(paymentIntent.getLastPaymentError()).thenReturn(error);
        stubPaymentIntentEvent(StripeWebhookAdapter.EVENT_PAYMENT_INTENT_PAYMENT_FAILED, "evt_fail", "pi_2");

        Optional<ApplyProviderPaymentResultCommand> command = adapter.mapEvent(event);

        assertThat(command).isPresent();
        assertThat(command.get().success()).isFalse();
        assertThat(command.get().outcome()).isEqualTo(ProviderPaymentResultOutcome.ATTEMPT_FAILED);
        assertThat(command.get().failureReason()).isEqualTo("Your card was declined.");
    }

    @Test
    @DisplayName("mapEvent: payment_intent.canceled → final failure command")
    void mapEvent_canceled() {
        when(paymentIntent.getLastPaymentError()).thenReturn(null);
        stubPaymentIntentEvent(StripeWebhookAdapter.EVENT_PAYMENT_INTENT_CANCELED, "evt_cancel", "pi_3");

        Optional<ApplyProviderPaymentResultCommand> command = adapter.mapEvent(event);

        assertThat(command).isPresent();
        assertThat(command.get().success()).isFalse();
        assertThat(command.get().outcome()).isEqualTo(ProviderPaymentResultOutcome.FINAL_FAILED);
        assertThat(command.get().failureReason()).contains("canceled");
    }

    @Test
    @DisplayName("mapEvent: 지원하지 않는 타입은 empty")
    void mapEvent_unsupportedType_empty() {
        stubPaymentIntentEvent("charge.succeeded", "evt_other", "pi_4");

        assertThat(adapter.mapEvent(event)).isEmpty();
    }

    @Test
    @DisplayName("verifyAndParse: webhook secret 없으면 InvalidStripeWebhookException")
    void verifyAndParse_whenSecretMissing_throws() {
        StripeWebhookAdapter noSecret = new StripeWebhookAdapter(new StripeProperties("sk_test", null));

        assertThatThrownBy(() -> noSecret.verifyAndParse("{}", "t=1,v1=abc"))
                .isInstanceOf(InvalidStripeWebhookException.class)
                .hasMessageContaining("webhook-secret");
    }

    private void stubPaymentIntentEvent(String type, String eventId, String paymentIntentId) {
        when(event.getType()).thenReturn(type);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of(paymentIntent));
        when(paymentIntent.getId()).thenReturn(paymentIntentId);
        // Only stub when mapped commands need event id (supported types).
        if (!"charge.succeeded".equals(type)) {
            when(event.getId()).thenReturn(eventId);
        }
    }
}
