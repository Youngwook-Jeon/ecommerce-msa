package com.project.young.paymentservice.web.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Exposes the internal reconciliation authority only to a token explicitly issued for Payment.
 * Other realm roles remain available to the normal user-facing Payment API.
 */
final class PaymentInternalAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final String internalApiAudience;

    PaymentInternalAuthoritiesConverter(String internalApiAudience) {
        this.internalApiAudience = Objects.requireNonNull(internalApiAudience, "internalApiAudience must not be null");
    }

    @Override
    public List<GrantedAuthority> convert(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(SecurityConfig.ROLES_CLAIM);
        if (roles == null) {
            return List.of();
        }
        boolean paymentAudience = jwt.getAudience().contains(internalApiAudience);
        return roles.stream()
                .filter(role -> !SecurityConfig.INTERNAL_PAYMENT_RECONCILIATION_READ.equals(role) || paymentAudience)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }
}
