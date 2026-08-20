package com.project.young.edgeservice.config;

/**
 * Gateway path patterns for anonymous storefront APIs (before rewrite to downstream services).
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
}
