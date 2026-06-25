package com.project.young.orderservice.web.cart;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "order-service.guest-cart.cookie")
public class GuestCartCookieProperties {

    private String name = "guest_cart_id";
    private String path = "/";
    private long maxAgeSeconds = 2_592_000L;
}
