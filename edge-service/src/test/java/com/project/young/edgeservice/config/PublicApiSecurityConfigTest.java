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
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;

@WebFluxTest
@Import({SecurityConfig.class, OAuth2ClientConfig.class})
@TestPropertySource(properties = {
        "gateway-uri=http://localhost:9000",
        "post-logout-redirect-uri=http://localhost:9000/",
        "api-version=v1"
})
class PublicApiSecurityConfigTest {

    private static final String PUBLIC_PRODUCTS =
            "/api/v1/product_service/public/products";

    private static final String STRIPE_WEBHOOK =
            "/api/v1/payment_service/webhooks/stripe";

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

    @Test
    @DisplayName("POST Stripe webhook — anonymous without CSRF is permitted")
    void postStripeWebhook_withoutCsrf_isPermitted() {
        webTestClient.post()
                .uri(STRIPE_WEBHOOK)
                .bodyValue("{\"id\":\"evt_test\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("ok");
    }

    @TestConfiguration
    static class PublicApiProbeRoutes {

        @Bean
        RouterFunction<ServerResponse> publicApiProbeRoutes() {
            return RouterFunctions.route(
                    GET(PUBLIC_PRODUCTS),
                    request -> ServerResponse.ok().bodyValue("ok")
            ).andRoute(
                    POST(STRIPE_WEBHOOK),
                    request -> ServerResponse.ok().bodyValue("ok")
            );
        }
    }
}
