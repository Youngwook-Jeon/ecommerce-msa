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
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.endpoint.ReactiveOAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
@Slf4j
public class ReactiveTokenRefreshFilter implements GlobalFilter, Ordered {

    public static final String AUTH_PREFIX = "Bearer ";

    private final ReactiveOAuth2AuthorizedClientService authorizedClientService;
    private final ReactiveOAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> accessTokenResponseClient;

    public ReactiveTokenRefreshFilter(ReactiveOAuth2AuthorizedClientService authorizedClientService,
                                      ReactiveOAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> accessTokenResponseClient) {
        this.authorizedClientService = authorizedClientService;
        this.accessTokenResponseClient = accessTokenResponseClient;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .filter(principal -> principal instanceof OAuth2AuthenticationToken)
                .cast(OAuth2AuthenticationToken.class)
                .flatMap(oauthToken -> authorizedClientService
                        .loadAuthorizedClient(
                                oauthToken.getAuthorizedClientRegistrationId(),
                                oauthToken.getName()
                        )
                        .flatMap(client -> handleTokenRefreshIfNeeded(client, oauthToken, exchange, chain))
                )
                .onErrorResume(e -> handleUnauthorized(exchange, e))
                .switchIfEmpty(chain.filter(exchange));
    }

    private Mono<Void> handleTokenRefreshIfNeeded(OAuth2AuthorizedClient client, OAuth2AuthenticationToken oauthToken,
                                                  ServerWebExchange exchange, GatewayFilterChain chain) {
        OAuth2AccessToken accessToken = client.getAccessToken();

        if (accessToken == null) {
            log.warn("AccessToken is null for user: {}", oauthToken.getName());
            return chain.filter(exchange);
        }

        if (isTokenExpired(accessToken)) {
            log.info("AccessToken expired for user: {}. Attempting refresh.", oauthToken.getName());
            return refreshAccessToken(client)
                    .flatMap(refreshedClient -> {
                        log.info("Token refreshed successfully for user: {}", oauthToken.getName());
                        return authorizedClientService
                                .saveAuthorizedClient(refreshedClient, oauthToken)
                                .then(chain.filter(addAuthorizationHeader(exchange, refreshedClient.getAccessToken().getTokenValue())));
                        }
                    );
        }

        return chain.filter(addAuthorizationHeader(exchange, accessToken.getTokenValue()));
    }

    private ServerWebExchange addAuthorizationHeader(ServerWebExchange exchange, String tokenValue) {
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(HttpHeaders.AUTHORIZATION, AUTH_PREFIX + tokenValue)
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

    private boolean isTokenExpired(OAuth2AccessToken accessToken) {
        return accessToken.getExpiresAt() != null &&
                accessToken.getExpiresAt().minusSeconds(30).isBefore(Instant.now());
    }

    private Mono<Void> handleUnauthorized(ServerWebExchange exchange, Throwable e) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return SecurityWebFiltersOrder.AUTHENTICATION.getOrder() + 10;
    }
}
