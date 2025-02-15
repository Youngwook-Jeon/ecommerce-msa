package com.project.young.edgeservice.config;

public enum CsrfProtectedPath {
    API("/api");

    private final String path;

    CsrfProtectedPath(String path) {
        this.path = path;
    }

    public boolean matches(String requestPath) {
        return requestPath.startsWith(this.path);
    }
}
