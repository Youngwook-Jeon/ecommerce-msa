package com.project.young.paymentservice.web.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentInternalAuthoritiesConverterTest {

    @Test
    void convert_exposesInternalAuthorityOnlyWhenTokenTargetsPayment() {
        PaymentInternalAuthoritiesConverter converter = new PaymentInternalAuthoritiesConverter("payment-service");

        assertThat(converter.convert(jwt("payment-service")).stream().map(Object::toString))
                .contains("INTERNAL_PAYMENT_RECONCILIATION_READ", "CUSTOMER");
        assertThat(converter.convert(jwt("product-service")).stream().map(Object::toString))
                .contains("CUSTOMER")
                .doesNotContain("INTERNAL_PAYMENT_RECONCILIATION_READ");
    }

    private static Jwt jwt(String audience) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuer("http://localhost:8080/realms/Ecomart")
                .audience(List.of(audience))
                .claim(SecurityConfig.ROLES_CLAIM,
                        List.of("CUSTOMER", SecurityConfig.INTERNAL_PAYMENT_RECONCILIATION_READ))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }
}
