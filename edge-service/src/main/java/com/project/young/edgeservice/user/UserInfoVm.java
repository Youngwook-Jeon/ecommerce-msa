package com.project.young.edgeservice.user;

import lombok.Builder;

import java.util.List;

@Builder
public record UserInfoVm(
        boolean isAuthenticated,
        String username,
        String firstName,
        String lastName,
        List<String> roles
) {
}
