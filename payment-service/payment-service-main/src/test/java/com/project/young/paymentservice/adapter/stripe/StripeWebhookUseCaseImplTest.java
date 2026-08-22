package com.project.young.paymentservice.adapter.stripe;

import com.project.young.paymentservice.application.dto.command.ApplyProviderPaymentResultCommand;
import com.project.young.paymentservice.application.port.output.StripeWebhookPort;
import com.project.young.paymentservice.application.service.PaymentApplicationService;
import com.project.young.paymentservice.domain.valueobject.PaymentProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeWebhookUseCaseImplTest {

    @Mock
    private StripeWebhookPort stripeWebhookPort;

    @Mock
    private PaymentApplicationService paymentApplicationService;

    @InjectMocks
    private StripeWebhookUseCaseImpl useCase;

    @Test
    @DisplayName("handle: 파싱 결과가 있으면 applyProviderPaymentResult 호출")
    void handle_whenParsed_applies() {
        ApplyProviderPaymentResultCommand command = ApplyProviderPaymentResultCommand.succeeded(
                "evt_1",
                PaymentProvider.STRIPE,
                "pi_1"
        );
        when(stripeWebhookPort.verifyAndParse("payload", "sig")).thenReturn(Optional.of(command));
        when(paymentApplicationService.applyProviderPaymentResult(command)).thenReturn(true);

        useCase.handle("payload", "sig");

        verify(paymentApplicationService).applyProviderPaymentResult(command);
    }

    @Test
    @DisplayName("handle: 무시할 이벤트면 apply 하지 않음")
    void handle_whenEmpty_skipsApply() {
        when(stripeWebhookPort.verifyAndParse("payload", "sig")).thenReturn(Optional.empty());

        useCase.handle("payload", "sig");

        verify(paymentApplicationService, never()).applyProviderPaymentResult(any());
    }
}
