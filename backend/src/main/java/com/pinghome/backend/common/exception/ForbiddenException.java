package com.pinghome.backend.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an authenticated user is not authorized to act on a resource they do not own
 * or are not an authorized watcher for. Never leaks whether the resource exists.
 */
public class ForbiddenException extends ApiException {
    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
