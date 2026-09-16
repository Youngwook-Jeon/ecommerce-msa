package com.project.young.orderservice.dataaccess.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@Profile("!test")
public class PaymentReconciliationClientConfig {

    static final String PAYMENT_RECONCILIATION_REGISTRATION_ID = "payment-reconciliation";
    private static final String SERVICE_PRINCIPAL = "order-service";

    @Bean
    OAuth2AuthorizedClientManager paymentReconciliationAuthorizedClientManager(
            ClientRegistrationRepository clientRegistrations,
            OAuth2AuthorizedClientService authorizedClients
    ) {
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrations, authorizedClients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build());
        return manager;
    }

    @Bean
    RestClient paymentReconciliationRestClient(
            PaymentReconciliationClientProperties properties,
            @Qualifier("paymentReconciliationAuthorizedClientManager") OAuth2AuthorizedClientManager authorizedClientManager
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    OAuth2AuthorizedClient client = authorizedClientManager.authorize(OAuth2AuthorizeRequest
                            .withClientRegistrationId(PAYMENT_RECONCILIATION_REGISTRATION_ID)
                            .principal(SERVICE_PRINCIPAL)
                            .build());
                    if (client == null || client.getAccessToken() == null) {
                        throw new IllegalStateException("Unable to obtain Order Service client-credentials token.");
                    }
                    request.getHeaders().setBearerAuth(client.getAccessToken().getTokenValue());
                    return execution.execute(request, body);
                })
                .build();
    }
}
