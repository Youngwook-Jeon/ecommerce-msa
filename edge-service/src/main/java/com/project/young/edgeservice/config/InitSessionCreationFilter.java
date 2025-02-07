package com.project.young.edgeservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
public class InitSessionCreationFilter implements WebFilter {
    private static final String SESSION_INITIALIZED_FLAG = "sessionInitialized";
    private static final String SESSION_KEY = "SESSION";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        boolean hasSessionCookie = exchange.getRequest().getCookies().containsKey(SESSION_KEY);

        if (!hasSessionCookie) {
            return exchange.getSession()
                    .doOnNext(session -> {
                        session.getAttributes().put(SESSION_INITIALIZED_FLAG, true);
                    })
                    .then(chain.filter(exchange));
        }

        log.debug("session value: {}", exchange.getRequest().getCookies().get(SESSION_KEY));

        return chain.filter(exchange);
    }
}
