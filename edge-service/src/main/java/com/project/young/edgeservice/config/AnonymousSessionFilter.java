package com.project.young.edgeservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
public class AnonymousSessionFilter implements WebFilter {
    private static final String SESSION_KEY = "SESSION";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        boolean hasSessionCookie = exchange.getRequest().getCookies().containsKey(SESSION_KEY);
        log.debug("Incoming request: {}", exchange.getRequest().getPath());
        log.debug("Session cookie: {}", hasSessionCookie);

        return exchange.getSession()
                .doOnNext(session -> {
                    if (!hasSessionCookie) {
                        exchange.getResponse().getHeaders().add("Set-Cookie",
                                String.format("SESSION=%s; Path=/; HttpOnly; SameSite=Lax",
                                        session.getId()));
                    }
                })
                .then(chain.filter(exchange));
    }
}
