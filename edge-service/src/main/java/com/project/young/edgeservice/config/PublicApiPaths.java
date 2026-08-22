package com.project.young.edgeservice.config;

/**
 * Gateway path patterns for storefront / provider APIs (before rewrite to downstream services).
 */
public final class PublicApiPaths {

    private PublicApiPaths() {
    }

    public static String productPublic(String apiVersion) {
        return "/api/" + apiVersion + "/product_service/public/**";
    }

    /** Block browser access; order-service calls product-service directly. */
    public static String productInternal(String apiVersion) {
        return "/api/" + apiVersion + "/product_service/internal/**";
    }

    /** Checkout / order APIs — require gateway login session. */
    public static String orderOrders(String apiVersion) {
        return "/api/" + apiVersion + "/order_service/orders/**";
    }

    /** Guest→user cart merge — requires gateway login session. */
    public static String orderCartMerge(String apiVersion) {
        return "/api/" + apiVersion + "/order_service/carts/current/merge";
    }

    /**
     * Stripe webhook (anonymous). Gateway URL maps to payment-service {@code POST /webhooks/stripe}.
     */
    public static String paymentStripeWebhook(String apiVersion) {
        return "/api/" + apiVersion + "/payment_service/webhooks/stripe";
    }

    /** Embedded Elements client-secret — require gateway login session. */
    public static String paymentClientSecret(String apiVersion) {
        return "/api/" + apiVersion + "/payment_service/payments/orders/*/client-secret";
    }

    /**
     * CSRF exclusion helper: matches {@code /api/{version}/payment_service/webhooks/stripe}.
     */
    public static boolean isPaymentStripeWebhook(String requestPath) {
        if (requestPath == null || !requestPath.startsWith("/api/")) {
            return false;
        }
        return requestPath.endsWith("/payment_service/webhooks/stripe");
    }
}
