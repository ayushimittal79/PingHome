package com.pinghome.backend.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Small helper so controllers/services don't repeat the SecurityContext boilerplate. */
public final class CurrentUser {

    private CurrentUser() {}

    public static UserPrincipal get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new IllegalStateException("No authenticated user in security context");
        }
        return principal;
    }

    public static Long id() {
        return get().getId();
    }
}
