package com.project.young.paymentservice.application.port.output;

public record PaymentGatewayResult(
        boolean success,
        String failureReason
) {
    public PaymentGatewayResult {
        if (!success && (failureReason == null || failureReason.isBlank())) {
            throw new IllegalArgumentException("failureReason must not be blank when payment fails");
        }
        if (success && failureReason != null) {
            throw new IllegalArgumentException("failureReason must be null when payment succeeds");
        }
    }

    public static PaymentGatewayResult succeeded() {
        return new PaymentGatewayResult(true, null);
    }

    public static PaymentGatewayResult failed(String failureReason) {
        return new PaymentGatewayResult(false, failureReason);
    }
}
