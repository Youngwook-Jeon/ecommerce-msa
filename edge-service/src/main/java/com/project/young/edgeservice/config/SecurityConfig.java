package com.project.young.edgeservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerRedirectStrategy;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.csrf.XorServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.*;

import static org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult.*;

@Configuration
@EnableWebFluxSecurity
@Slf4j
public class SecurityConfig {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM = "roles";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityWebFilterChain clientFilterChain(
            ServerHttpSecurity http,
            ReactiveClientRegistrationRepository clientRegistrationRepository,
            @Value("${server.reactive.session.cookie.name:SESSION_edge-service}") String sessionKey,
            @Value("${gateway-uri}") URI gatewayUri,
            @Value("${pre-authorization-status:FOUND}") HttpStatus preAuthorizationStatus,
            @Value("${post-authorization-status:FOUND}") HttpStatus postAuthorizationStatus,
            @Value("${post-logout-redirect-uri}") String postLogoutRedirectUri) {

        http.authorizeExchange(auth -> auth
                .pathMatchers("/dashboard/admin/**").hasAuthority("ADMIN")
//                .pathMatchers("/profile/**").authenticated()
                .anyExchange().permitAll()
        );

        http.oauth2Login(login -> {
            login.authorizationRedirectStrategy(new OAuth2ServerRedirectStrategy(preAuthorizationStatus));

            final URI ui = UriComponentsBuilder.fromUri(gatewayUri).build().toUri();
            login.authenticationSuccessHandler(new OAuth2ServerAuthenticationSuccessHandler(postAuthorizationStatus, ui));
            login.authenticationFailureHandler(new OAuth2ServerAuthenticationFailureHandler(postAuthorizationStatus, ui));
        });

//        http.addFilterAt(new AnonymousSessionFilter(), SecurityWebFiltersOrder.ANONYMOUS_AUTHENTICATION);
        http.addFilterAt(new InitSessionCreationFilter(sessionKey), SecurityWebFiltersOrder.ANONYMOUS_AUTHENTICATION);

        http.logout(logout -> logout.logoutSuccessHandler(
                new SpaLogoutSuccessHandler(clientRegistrationRepository, postLogoutRedirectUri)));

        http.csrf(csrf -> csrf
                .requireCsrfProtectionMatcher(this::isCsrfProtectedPath)
                .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse()).csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()));

        return http.build();
    }

    @Bean
    WebFilter csrfCookieWebFilter() {
        return (exchange, chain) -> {
            exchange.getAttributeOrDefault(CsrfToken.class.getName(), Mono.empty()).subscribe();
            return chain.filter(exchange);
        };
    }

    @Bean
    public ReactiveOAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> accessTokenResponseClient() {
        return new WebClientReactiveRefreshTokenTokenResponseClient();
    }

    static class SpaLogoutSuccessHandler implements ServerLogoutSuccessHandler {
        private final OidcClientInitiatedServerLogoutSuccessHandler handler;

        public SpaLogoutSuccessHandler(
                ReactiveClientRegistrationRepository clientRegistrationRepository,
                String postLogoutRedirectUri
        ) {
            this.handler = new OidcClientInitiatedServerLogoutSuccessHandler(clientRegistrationRepository);
            this.handler.setPostLogoutRedirectUri(postLogoutRedirectUri);
        }

        @Override
        public Mono<Void> onLogoutSuccess(WebFilterExchange exchange, Authentication authentication) {
            return this.handler.onLogoutSuccess(exchange, authentication)
                    .then(Mono.fromRunnable(() -> exchange.getExchange().getResponse().setStatusCode(HttpStatus.ACCEPTED)));
        }
    }

    static class OAuth2ServerRedirectStrategy implements ServerRedirectStrategy {
        private final HttpStatus defaultStatus;

        public OAuth2ServerRedirectStrategy(HttpStatus httpStatus) {
            this.defaultStatus = httpStatus;
        }

        @Override
        public Mono<Void> sendRedirect(ServerWebExchange exchange, URI location) {
            return Mono.fromRunnable(() -> {
                ServerHttpResponse response = exchange.getResponse();
                var status = Optional.ofNullable(exchange.getRequest().getHeaders().get("X-RESPONSE-STATUS")).stream()
                        .flatMap(Collection::stream)
                        .filter(StringUtils::hasLength)
                        .findAny()
                        .map(statusStr -> {
                            try {
                                final int statusCode = Integer.parseInt(statusStr);
                                return HttpStatus.valueOf(statusCode);
                            } catch (NumberFormatException e) {
                                return HttpStatus.valueOf(statusStr.toUpperCase());
                            }
                        })
                        .orElse(defaultStatus);
                response.setStatusCode(status);
                response.getHeaders().setLocation(location);
            });
        }
    }

    static class OAuth2ServerAuthenticationSuccessHandler implements ServerAuthenticationSuccessHandler {
        private final OAuth2ServerRedirectStrategy redirectStrategy;
        private final URI redirectUri;

        public OAuth2ServerAuthenticationSuccessHandler(HttpStatus status, URI uri) {
            this.redirectStrategy = new OAuth2ServerRedirectStrategy(status);
            this.redirectUri = uri;
        }

        @Override
        public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
            return redirectStrategy.sendRedirect(webFilterExchange.getExchange(), redirectUri);
        }
    }

    static class OAuth2ServerAuthenticationFailureHandler implements ServerAuthenticationFailureHandler {
        private final URI redirectUri;
        private final OAuth2ServerRedirectStrategy redirectStrategy;

        public OAuth2ServerAuthenticationFailureHandler(HttpStatus status, URI uri) {
            this.redirectStrategy = new OAuth2ServerRedirectStrategy(status);
            this.redirectUri = uri;
        }

        @Override
        public Mono<Void> onAuthenticationFailure(WebFilterExchange webFilterExchange, AuthenticationException exception) {
            return redirectStrategy.sendRedirect(webFilterExchange.getExchange(), redirectUri);
        }
    }

    static class SpaCsrfTokenRequestHandler extends ServerCsrfTokenRequestAttributeHandler {
        private final ServerCsrfTokenRequestAttributeHandler delegate = new XorServerCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(ServerWebExchange exchange, Mono<CsrfToken> csrfToken) {
            this.delegate.handle(exchange, csrfToken);
        }

        @Override
        public Mono<String> resolveCsrfTokenValue(ServerWebExchange exchange, CsrfToken csrfToken) {
            List<String> csrfHeader = exchange.getRequest().getHeaders().get(csrfToken.getHeaderName());
            boolean hasHeader = csrfHeader != null && csrfHeader.stream().anyMatch(StringUtils::hasText);

            return hasHeader ? super.resolveCsrfTokenValue(exchange, csrfToken) :
                    this.delegate.resolveCsrfTokenValue(exchange, csrfToken);
        }
    }



    private Mono<ServerWebExchangeMatcher.MatchResult> isCsrfProtectedPath(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().toString();
        HttpMethod method = exchange.getRequest().getMethod();

        boolean isProtectedPath = Arrays.stream(CsrfProtectedPath.values())
                .anyMatch(rule -> rule.matches(path));

        if (isProtectedPath && (
                HttpMethod.POST.equals(method) || HttpMethod.PUT.equals(method) || HttpMethod.DELETE.equals(method))) {
            return match();
        }

        return notMatch();
    }

    @Bean
    public GrantedAuthoritiesMapper userAuthoritiesMapper() {
        return authorities -> {
            log.debug("[userAuthoritiesMapper] Original authorities: {}", authorities);
            Set<GrantedAuthority> mappedAuthorities = new HashSet<>();
            var authority = authorities.iterator().next();
            if (authority instanceof OidcUserAuthority oidcUserAuthority) {
                var userInfo = oidcUserAuthority.getUserInfo();
                log.debug("[userAuthoritiesMapper] userInfo claims: {}", userInfo.getClaims());

                if (userInfo.hasClaim(ROLES_CLAIM)) {
                    var roles = userInfo.getClaimAsStringList(ROLES_CLAIM);
                    log.debug("[userAuthoritiesMapper] Extracted roles from userInfo: {}", roles);
                    mappedAuthorities.addAll(generateAuthoritiesFromClaim(roles));
                }
            }

            else {
                var oauth2UserAuthority = (OAuth2UserAuthority) authority;
                Map<String, Object> userAttributes = oauth2UserAuthority.getAttributes();

                if (userAttributes.containsKey(REALM_ACCESS_CLAIM)) {
                    var realmAccess = (Map<String, Object>) userAttributes.get(REALM_ACCESS_CLAIM);
                    var roles = (Collection<String>) realmAccess.get(ROLES_CLAIM);
                    mappedAuthorities.addAll(generateAuthoritiesFromClaim(roles));
                }
            }

            return mappedAuthorities;
        };
    }

    Collection<GrantedAuthority> generateAuthoritiesFromClaim(Collection<String> roles) {
        return roles.stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

}
