package com.project.young.edgeservice.user;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    public static final String ROLES_CLAIM = "roles";

    @GetMapping("authentication")
    public ResponseEntity<UserInfoVm> getAuthUser(@AuthenticationPrincipal OidcUser oidcUser) {
        if (oidcUser == null) return ResponseEntity.ok(
                UserInfoVm.builder()
                        .isAuthenticated(false)
                        .build()
        );

        var userInfo = UserInfoVm.builder()
                .isAuthenticated(true)
                .username(oidcUser.getPreferredUsername())
                .firstName(oidcUser.getGivenName())
                .lastName(oidcUser.getFamilyName())
                .roles(oidcUser.getClaimAsStringList(ROLES_CLAIM))
                .build();

        return ResponseEntity.ok(userInfo);
    }

    @PostMapping("post_test")
    public ResponseEntity<String> postTest() {
        return ResponseEntity.ok("post_test endpoint hit");
    }
}
