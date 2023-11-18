package com.project.young.edgeservice.user;

import java.util.List;

public record UserInfoVm(
        boolean isAuthenticated,
        String username,
        String firstName,
        String lastName,
        List<String> roles
) {
}
