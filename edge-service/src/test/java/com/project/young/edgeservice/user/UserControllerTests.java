package com.project.young.edgeservice.user;

import com.project.young.edgeservice.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static com.project.young.edgeservice.user.UserController.ROLES_CLAIM;
import static org.assertj.core.api.Assertions.assertThat;

@WebFluxTest(UserController.class)
@Import(SecurityConfig.class)
public class UserControllerTests {

    public static final String AUTH_ENDPOINT = "/authentication";

    @Autowired
    WebTestClient webClient;

    @MockBean
    ReactiveClientRegistrationRepository clientRegistrationRepository;

    @Test
    void when_not_authenticated_then_return_anonymous_user() {
        webClient.get()
                .uri(AUTH_ENDPOINT)
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserInfoVm.class).value(user ->
                        assertThat(user.isAuthenticated()).isFalse());
    }

    @Test
    void when_authenticated_then_return_auth_user() {
        var expectedUser = new UserInfoVm(true, "Lucas", "Lucas", "Jeon",
                List.of("ADMIN", "CUSTOMER"));
        webClient.mutateWith(configureMockOidcLogin(expectedUser))
                .get()
                .uri(AUTH_ENDPOINT)
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserInfoVm.class)
                .value(userInfoVm -> assertThat(userInfoVm).isEqualTo(expectedUser));
    }

    private SecurityMockServerConfigurers.OidcLoginMutator configureMockOidcLogin(UserInfoVm expectedUser) {
        return SecurityMockServerConfigurers.mockOidcLogin().idToken(builder -> {
            builder.claim(StandardClaimNames.PREFERRED_USERNAME, expectedUser.username());
            builder.claim(StandardClaimNames.GIVEN_NAME, expectedUser.firstName());
            builder.claim(StandardClaimNames.FAMILY_NAME, expectedUser.lastName());
            builder.claim(ROLES_CLAIM, expectedUser.roles());
        });
    }
}
