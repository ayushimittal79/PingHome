package com.pinghome.backend.user;

/**
 * A single PingHome account can act as a traveller on its own trips and as a watcher on
 * trips where one of its contacts has added it as a trusted watcher. ADMIN is reserved for
 * future operational tooling and is not assigned during normal registration.
 */
public enum UserRole {
    USER,
    ADMIN
}
