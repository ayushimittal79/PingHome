package com.pinghome.backend.user;

import com.pinghome.backend.user.dto.AuthResponse;
import com.pinghome.backend.user.dto.LoginRequest;
import com.pinghome.backend.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthFlowTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void registerThenLoginSucceeds() {
        RegisterRequest register = new RegisterRequest("Asha Traveller", "asha@example.com", "+10000000001", "correct-horse-1");
        var registerResponse = rest.postForEntity("/api/auth/register", register, AuthResponse.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerResponse.getBody().token()).isNotBlank();
        assertThat(registerResponse.getBody().user().email()).isEqualTo("asha@example.com");

        var loginResponse = rest.postForEntity("/api/auth/login",
                new LoginRequest("asha@example.com", "correct-horse-1"), AuthResponse.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody().token()).isNotBlank();
    }

    @Test
    void duplicateEmailIsRejected() {
        RegisterRequest register = new RegisterRequest("Bala", "bala@example.com", null, "correct-horse-2");
        rest.postForEntity("/api/auth/register", register, AuthResponse.class);

        var second = rest.postForEntity("/api/auth/register", register, AuthResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void wrongPasswordIsRejected() {
        rest.postForEntity("/api/auth/register",
                new RegisterRequest("Chitra", "chitra@example.com", null, "correct-horse-3"), AuthResponse.class);

        var login = rest.postForEntity("/api/auth/login",
                new LoginRequest("chitra@example.com", "wrong-password"), AuthResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedEndpointRejectsMissingToken() {
        var response = rest.getForEntity("/api/users/me", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
