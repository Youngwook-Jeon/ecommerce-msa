package com.project.young.edgeservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.endpoint.ReactiveOAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * After oauth2Login, attach {@code Authorization: Bearer &lt;access_token&gt;} for downstream
 * resource servers (order-service, product-service).
 * <p>
 * Reads/writes authorized clients via {@link ServerOAuth2AuthorizedClientRepository}
 * (same store as oauth2Login), not the default in-memory {@code ReactiveOAuth2AuthorizedClientService}.
 */
@Component
@Slf4j
public class ReactiveTokenRefreshFilter implements GlobalFilter, Ordered {

    public static final String AUTH_PREFIX = "Bearer ";

    private final ServerOAuth2AuthorizedClientRepository authorizedClientRepository;
    private final ReactiveOAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> accessTokenResponseClient;

    public ReactiveTokenRefreshFilter(
            ServerOAuth2AuthorizedClientRepository authorizedClientRepository,
            ReactiveOAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> accessTokenResponseClient
    ) {
        this.authorizedClientRepository = authorizedClientRepository;
        this.accessTokenResponseClient = accessTokenResponseClient;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .filter(OAuth2AuthenticationToken.class::isInstance)
                .cast(OAuth2AuthenticationToken.class)
                .flatMap(oauthToken -> authorizedClientRepository
                        .loadAuthorizedClient(
                                oauthToken.getAuthorizedClientRegistrationId(),
                                oauthToken,
                                exchange
                        )
                        .switchIfEmpty(Mono.defer(() -> {
                            log.warn(
                                    "No authorized client in session for user {} (registration={})",
                                    oauthToken.getName(),
                                    oauthToken.getAuthorizedClientRegistrationId()
                            );
                            return Mono.empty();
                        }))
                        .flatMap(client -> attachBearer(client, oauthToken, exchange))
                        .flatMap(chain::filter)
                        .onErrorResume(error -> {
                            log.error(
                                    "Failed to attach/refresh access token for user {}: {}",
                                    oauthToken.getName(),
                                    error.toString()
                            );
                            return unauthorized(exchange);
                        })
                )
                .switchIfEmpty(chain.filter(exchange));
    }

    private Mono<ServerWebExchange> attachBearer(
            OAuth2AuthorizedClient client,
            OAuth2AuthenticationToken oauthToken,
            ServerWebExchange exchange
    ) {
        OAuth2AccessToken accessToken = client.getAccessToken();
        if (accessToken == null) {
            log.warn("AccessToken is null for user: {}", oauthToken.getName());
            return Mono.just(exchange);
        }

        if (!isTokenExpired(accessToken)) {
            return Mono.just(withAuthorization(exchange, accessToken.getTokenValue()));
        }

        log.info("AccessToken expired for user: {}. Attempting refresh.", oauthToken.getName());
        return refreshAccessToken(client)
                .flatMap(refreshedClient -> authorizedClientRepository
                        .saveAuthorizedClient(refreshedClient, oauthToken, exchange)
                        .thenReturn(withAuthorization(
                                exchange,
                                refreshedClient.getAccessToken().getTokenValue()
                        ))
                );
    }

    private static ServerWebExchange withAuthorization(ServerWebExchange exchange, String tokenValue) {
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(HttpHeaders.AUTHORIZATION);
                    headers.set(HttpHeaders.AUTHORIZATION, AUTH_PREFIX + tokenValue);
                })
                .build();
        return exchange.mutate().request(mutatedRequest).build();
    }

    private Mono<OAuth2AuthorizedClient> refreshAccessToken(OAuth2AuthorizedClient client) {
        OAuth2RefreshToken refreshToken = client.getRefreshToken();
        if (refreshToken == null) {
            return Mono.error(new IllegalStateException("There is no refresh token"));
        }

        OAuth2RefreshTokenGrantRequest refreshTokenRequest = new OAuth2RefreshTokenGrantRequest(
                client.getClientRegistration(),
                client.getAccessToken(),
                refreshToken
        );

        return accessTokenResponseClient.getTokenResponse(refreshTokenRequest)
                .map(response -> new OAuth2AuthorizedClient(
                        client.getClientRegistration(),
                        client.getPrincipalName(),
                        response.getAccessToken(),
                        response.getRefreshToken() != null ? response.getRefreshToken() : refreshToken
                ));
    }

    private static boolean isTokenExpired(OAuth2AccessToken accessToken) {
        return accessToken.getExpiresAt() != null
                && accessToken.getExpiresAt().minusSeconds(30).isBefore(Instant.now());
    }

    private static Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return SecurityWebFiltersOrder.AUTHENTICATION.getOrder() + 10;
    }
}
