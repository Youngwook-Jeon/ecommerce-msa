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
}
