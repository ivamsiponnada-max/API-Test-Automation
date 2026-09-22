package com.sdet.framework.api;

/**
 * Central registry of resource paths, relative to the configured base URI and base path.
 * Keeping them in one place means a route change is a one-line fix.
 */
public final class Endpoints {

    public static final String USERS = "/users";
    public static final String USER_BY_ID = "/users/{id}";

    public static final String PATH_PARAM_ID = "id";

    private Endpoints() {
    }
}
