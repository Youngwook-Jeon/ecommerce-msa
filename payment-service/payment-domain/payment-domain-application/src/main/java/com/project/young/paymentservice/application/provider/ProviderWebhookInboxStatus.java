package com.project.young.paymentservice.application.provider;

/**
 * Lifecycle of a verified provider webhook retained until it is reconciled with a payment.
 */
public enum ProviderWebhookInboxStatus {
    RECEIVED,
    WAITING_FOR_PAYMENT,
    PROCESSING,
    APPLIED,
    ESCALATED
}
