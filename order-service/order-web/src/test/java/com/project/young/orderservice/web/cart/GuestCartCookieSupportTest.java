package com.project.young.orderservice.web.cart;

import com.project.young.orderservice.domain.valueobject.CartId;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GuestCartCookieSupportTest {

    private GuestCartCookieSupport cookieSupport;

    @BeforeEach
    void setUp() {
        GuestCartCookieProperties properties = new GuestCartCookieProperties();
        properties.setName("guest_cart_id");
        properties.setPath("/");
        properties.setMaxAgeSeconds(2_592_000L);
        cookieSupport = new GuestCartCookieSupport(properties);
    }

    @Test
    @DisplayName("readCartId: guest_cart_id 쿠키에서 CartId를 읽는다")
    void readCartId_success() {
        UUID rawId = UUID.fromString("018f0000-0000-7000-8000-000000000001");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("guest_cart_id", rawId.toString()));

        assertThat(cookieSupport.readCartId(request))
                .contains(new CartId(rawId));
    }

    @Test
    @DisplayName("readCartId: 잘못된 UUID는 무시한다")
    void readCartId_invalidValue_returnsEmpty() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("guest_cart_id", "not-a-uuid"));

        assertThat(cookieSupport.readCartId(request)).isEmpty();
    }

    @Test
    @DisplayName("writeCartId: HttpOnly SameSite=Lax 쿠키를 설정한다")
    void writeCartId_setsCookieHeader() {
        CartId cartId = new CartId(UUID.fromString("018f0000-0000-7000-8000-000000000001"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieSupport.writeCartId(response, cartId);

        String header = response.getHeader("Set-Cookie");
        assertThat(header)
                .contains("guest_cart_id=" + cartId.getValue())
                .contains("Path=/")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
    }

    @Test
    @DisplayName("expire: Max-Age=0 쿠키로 만료한다")
    void expire_clearsCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieSupport.expire(response);

        String header = response.getHeader("Set-Cookie");
        assertThat(header)
                .contains("guest_cart_id=")
                .contains("Max-Age=0");
    }
}
