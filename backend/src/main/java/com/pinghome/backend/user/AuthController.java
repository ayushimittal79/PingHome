package com.pinghome.backend.user;

import com.pinghome.backend.user.dto.AuthResponse;
import com.pinghome.backend.user.dto.LoginRequest;
import com.pinghome.backend.user.dto.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Registration and login are the only endpoints that do not require a JWT — everything else
 * in the API sits behind the filter chain configured in SecurityConfig. Logout is a client-side
 * token discard (stateless JWTs), so there is intentionally no server endpoint for it.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }
}
