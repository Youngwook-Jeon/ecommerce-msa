package com.project.young.edgeservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitSessionCreationFilterTest {

    private static final String SESSION_COOKIE = "SESSION_edge-service";

    @Mock
    private ServerWebExchange exchange;

    @Mock
    private ServerHttpRequest request;

    @Mock
    private RequestPath requestPath;

    @Mock
    private WebFilterChain chain;

    @Mock
    private WebSession webSession;

    private final InitSessionCreationFilter filter = new InitSessionCreationFilter(SESSION_COOKIE);

    @Test
    @DisplayName("Stripe webhook POST는 Redis 세션을 만들지 않는다")
    void stripeWebhook_doesNotCreateSession() {
        when(exchange.getRequest()).thenReturn(request);
        when(request.getPath()).thenReturn(requestPath);
        when(requestPath.value()).thenReturn("/api/v1/payment_service/webhooks/stripe");
        when(request.getCookies()).thenReturn(new LinkedMultiValueMap<>());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(exchange, never()).getSession();
        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("일반 API POST는 세션 쿠키가 없으면 세션을 초기화한다")
    void mutatingApi_withoutCookie_createsSession() {
        stubPathMethod("/api/v1/order_service/orders", HttpMethod.POST);
        when(request.getCookies()).thenReturn(new LinkedMultiValueMap<>());
        Map<String, Object> attrs = new HashMap<>();
        when(webSession.getAttributes()).thenReturn(attrs);
        when(exchange.getSession()).thenReturn(Mono.just(webSession));
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(exchange).getSession();
    }

    @Test
    @DisplayName("세션 쿠키가 있으면 getSession으로 새로 만들지 않고 통과한다")
    void withSessionCookie_skipsInit() {
        when(exchange.getRequest()).thenReturn(request);
        MultiValueMap<String, HttpCookie> cookies = new LinkedMultiValueMap<>();
        cookies.add(SESSION_COOKIE, new HttpCookie(SESSION_COOKIE, "existing"));
        when(request.getCookies()).thenReturn(cookies);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(exchange, never()).getSession();
        verify(chain).filter(exchange);
    }

    private void stubPathMethod(String path, HttpMethod method) {
        when(exchange.getRequest()).thenReturn(request);
        when(request.getPath()).thenReturn(requestPath);
        when(requestPath.value()).thenReturn(path);
        when(request.getMethod()).thenReturn(method);
    }
}
