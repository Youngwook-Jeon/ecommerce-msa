package com.project.young.edgeservice.user;

import com.project.young.common.constant.SecurityConstant;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    @GetMapping("authenticate")
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
                .roles(oidcUser.getClaimAsStringList(SecurityConstant.ROLES_CLAIM))
                .build();

        return ResponseEntity.ok(userInfo);
    }
}
