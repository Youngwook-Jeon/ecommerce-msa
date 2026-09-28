package com.project.young.saga.e2e;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.testcontainers.Testcontainers;

/** Test-only issuer: exercises real JWT validation and client-credentials HTTP calls without Keycloak. */
final class SagaTestIdentityProvider implements AutoCloseable {

    private final HttpServer server;
    private final RSAKey key;
    private final String issuer;

    SagaTestIdentityProvider() {
        try {
            key = new RSAKeyGenerator(2048).keyID("saga-e2e").generate();
            server = HttpServer.create(new InetSocketAddress(0), 0);
            issuer = "http://host.testcontainers.internal:" + server.getAddress().getPort();
            server.createContext("/jwks", exchange -> {
                byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
            server.createContext("/protocol/openid-connect/token", exchange -> {
                byte[] body = ("{\"access_token\":\"" + token("order-service",
                        List.of("INTERNAL_PAYMENT_RECONCILIATION_READ"))
                        + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
            server.start();
            Testcontainers.exposeHostPorts(server.getAddress().getPort());
        } catch (IOException | JOSEException ex) {
            throw new IllegalStateException("Could not start test identity provider", ex);
        }
    }

    String issuer() {
        return issuer;
    }

    String customerToken(String userId) {
        return token(userId, List.of());
    }

    private String token(String subject, List<String> roles) {
        Instant now = Instant.now();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT).keyID(key.getKeyID()).build(),
                new JWTClaimsSet.Builder().issuer(issuer).subject(subject).audience("payment-service")
                        .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(3600)))
                        .claim("roles", roles).build());
        try {
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Could not sign test token", ex);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
