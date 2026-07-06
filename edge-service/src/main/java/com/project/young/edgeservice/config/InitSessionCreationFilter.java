package com.project.young.edgeservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
public class InitSessionCreationFilter implements WebFilter {
    private static final String SESSION_INITIALIZED_FLAG = "sessionInitialized";
    private final String sessionKey;

    public InitSessionCreationFilter(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        boolean hasSessionCookie = exchange.getRequest().getCookies().containsKey(sessionKey);

        if (hasSessionCookie || !requiresSession(exchange)) {
            if (hasSessionCookie) {
                log.debug("session value: {}", exchange.getRequest().getCookies().get(sessionKey));
            }
            return chain.filter(exchange);
        }

        return exchange.getSession()
                .doOnNext(session -> session.getAttributes().put(SESSION_INITIALIZED_FLAG, true))
                .then(chain.filter(exchange));
    }

    private boolean requiresSession(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        if (path.startsWith("/oauth2")
                || "/logout".equals(path)
                || "/authentication".equals(path)) {
            return true;
        }

        return path.startsWith("/api")
                && method != null
                && method != HttpMethod.GET
                && method != HttpMethod.HEAD;
    }
}
