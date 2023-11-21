package com.project.young.edgeservice.user;

import com.project.young.common.constant.SecurityConstant;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    @GetMapping("user")
    public ResponseEntity<UserInfoVm> getUser(@AuthenticationPrincipal OidcUser oidcUser) {
        if (oidcUser == null) return ResponseEntity.ok(
                new UserInfoVm(false, null, null, null, null)
        );

        var user = new UserInfoVm(
                true,
                oidcUser.getPreferredUsername(),
                oidcUser.getGivenName(),
                oidcUser.getFamilyName(),
                oidcUser.getClaimAsStringList(SecurityConstant.ROLES_CLAIM)
        );

        return ResponseEntity.ok(user);
    }
}
