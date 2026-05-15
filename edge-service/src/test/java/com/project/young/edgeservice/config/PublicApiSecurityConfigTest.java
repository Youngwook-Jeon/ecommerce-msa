package com.project.young.edgeservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

@WebFluxTest
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "gateway-uri=http://localhost:9000",
        "post-logout-redirect-uri=http://localhost:9000/",
        "api-version=v1"
})
class PublicApiSecurityConfigTest {

    private static final String PUBLIC_PRODUCTS =
            "/api/v1/product_service/public/products";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ReactiveClientRegistrationRepository clientRegistrationRepository;

    @Test
    @DisplayName("GET public product API — anonymous access is permitted")
    void getPublicProducts_withoutAuthentication_isPermitted() {
        webTestClient.get()
                .uri(PUBLIC_PRODUCTS)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("ok");
    }

    @TestConfiguration
    static class PublicApiProbeRoutes {

        @Bean
        RouterFunction<ServerResponse> publicProductsRoute() {
            return RouterFunctions.route(
                    GET(PUBLIC_PRODUCTS),
                    request -> ServerResponse.ok().bodyValue("ok")
            );
        }
    }
}
