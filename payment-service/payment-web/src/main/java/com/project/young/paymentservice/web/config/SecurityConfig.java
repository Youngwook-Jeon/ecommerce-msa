package com.project.young.paymentservice.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;

@Configuration
@EnableMethodSecurity
@EnableWebSecurity
public class SecurityConfig {

    public static final String ROLES_CLAIM = "roles";
    public static final String INTERNAL_PAYMENT_RECONCILIATION_READ = "INTERNAL_PAYMENT_RECONCILIATION_READ";

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.csrf(CsrfConfigurer::disable);

        http.authorizeHttpRequests(requests -> requests
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                .requestMatchers(HttpMethod.POST, "/webhooks/stripe").permitAll()
                .requestMatchers(HttpMethod.GET, "/internal/refund-compensations/*")
                .hasAuthority(INTERNAL_PAYMENT_RECONCILIATION_READ)
                .requestMatchers(HttpMethod.POST, "/internal/orders/payment-statuses")
                .hasAuthority(INTERNAL_PAYMENT_RECONCILIATION_READ)
                .requestMatchers(HttpMethod.GET, "/payments/orders/*/client-secret").authenticated()
                .anyRequest().authenticated());

        return http.build();
    }

    @Bean
    Converter<Jwt, Collection<GrantedAuthority>> jwtGrantedAuthoritiesConverter(
            @Value("${payment-service.internal-api.audience:payment-service}")
            String internalApiAudience
    ) {
        return new PaymentInternalAuthoritiesConverter(internalApiAudience);
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter(
            Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter
    ) {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        jwtAuthenticationConverter.setPrincipalClaimName(StandardClaimNames.SUB);
        return jwtAuthenticationConverter;
    }
}
