package com.project.young.edgeservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.server.WebSessionServerOAuth2AuthorizedClientRepository;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;

@Configuration
public class SecurityConfig {

    @Bean
    ServerOAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new WebSessionServerOAuth2AuthorizedClientRepository();
    }

    @Bean
    SecurityWebFilterChain securityFilterChain(
            ServerHttpSecurity http,
            ReactiveClientRegistrationRepository clientRegistrationRepository
    ) {
        return http
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/profile/**").authenticated()
                        .anyExchange().permitAll()
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)))
                .oauth2Login(Customizer.withDefaults())
                .logout(logout -> logout.logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository)))
//                .csrf(csrf -> csrf
//                        .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
//                        .csrfTokenRequestHandler(new XorServerCsrfTokenRequestAttributeHandler()::handle))
                .build();
    }

//    @Bean
//    public GrantedAuthoritiesMapper authoritiesMapper() {
//        return authorities -> {
//            Set<GrantedAuthority> authoritiesSet = new HashSet<>();
//
//            if (!authorities.isEmpty()) {
//                var authority = authorities.iterator().next();
//
//                if (authority instanceof OidcUserAuthority oidcUserAuthority) {
//                    var userInfo = oidcUserAuthority.getUserInfo();
//
//                    if (userInfo.hasClaim(REALM_ACCESS_CLAIM)) {
//                        var realmAccess = userInfo.getClaimAsMap(REALM_ACCESS_CLAIM);
//                        var roles = (Collection<String>) realmAccess.get(ROLES_CLAIM);
//                        authoritiesSet.addAll(mapToAuthority(roles));
//                    }
//                } else if (authority instanceof OAuth2UserAuthority oauth2UserAuthority) {
//                    var userAttributes = oauth2UserAuthority.getAttributes();
//
//                    if (userAttributes.containsKey(REALM_ACCESS_CLAIM)) {
//                        var realmAccess = (Map<String, Object>) userAttributes.get(REALM_ACCESS_CLAIM);
//                        var roles = (Collection<String>) realmAccess.get(ROLES_CLAIM);
//                        authoritiesSet.addAll(mapToAuthority(roles));
//                    }
//                }
//            }
//
//            return authoritiesSet;
//        };
//    }
//
//    private Collection<GrantedAuthority> mapToAuthority(Collection<String> roles) {
//        return roles.stream()
//                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
//                .collect(Collectors.toList());
//    }

    private ServerLogoutSuccessHandler oidcLogoutSuccessHandler(ReactiveClientRegistrationRepository clientRegistrationRepository) {
        var oidcLogoutSuccessHandler = new OidcClientInitiatedServerLogoutSuccessHandler(clientRegistrationRepository);
        oidcLogoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}");
        return oidcLogoutSuccessHandler;
    }

//    @Bean
//    WebFilter csrfWebFilter() {
//        return (exchange, chain) -> {
//            exchange.getResponse().beforeCommit(() -> Mono.defer(() -> {
//                Mono<CsrfToken> csrfToken = exchange.getAttribute(CsrfToken.class.getName());
//                return csrfToken != null ? csrfToken.then() : Mono.empty();
//            }));
//            return chain.filter(exchange);
//        };
//    }
}
