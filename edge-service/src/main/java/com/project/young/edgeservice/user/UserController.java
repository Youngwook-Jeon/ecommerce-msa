package com.project.young.edgeservice.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class UserController {

    @GetMapping("user")
    public Mono<UserInfoVm> getUser(@AuthenticationPrincipal OidcUser oidcUser) {
        if (oidcUser == null) return Mono.just(new UserInfoVm(false, null, null, null, null));
        var user = new UserInfoVm(
                true,
                oidcUser.getPreferredUsername(),
                oidcUser.getGivenName(),
                oidcUser.getFamilyName(),
                oidcUser.getClaimAsStringList("roles")
        );

        return Mono.just(user);
    }
}
