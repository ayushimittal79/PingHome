package com.pinghome.backend.notification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Wraps the Firebase Admin SDK. FCM is the primary push channel for PingHome (nudges,
 * escalation, watcher alerts, trip completion). Credentials come from
 * PINGHOME_FIREBASE_CREDENTIALS_JSON (raw JSON) or PINGHOME_FIREBASE_CREDENTIALS_PATH (a
 * service-account file) - see docs/cloud-services.md. When neither is configured (e.g. running
 * locally without a Firebase project yet), this service logs and no-ops instead of throwing,
 * so the rest of the backend stays fully testable without live credentials. Delivery is
 * best-effort: FCM is not a guaranteed-delivery channel and callers must not assume otherwise.
 */
@Service
public class FcmService {

    private static final Logger log = LoggerFactory.getLogger(FcmService.class);

    private final String credentialsPath;
    private final String credentialsJson;
    private boolean enabled = false;

    public FcmService(
            @Value("${pinghome.firebase.credentials-path:}") String credentialsPath,
            @Value("${pinghome.firebase.credentials-json:}") String credentialsJson
    ) {
        this.credentialsPath = credentialsPath;
        this.credentialsJson = credentialsJson;
    }

    @PostConstruct
    void init() {
        try {
            GoogleCredentials credentials = loadCredentials();
            if (credentials == null) {
                log.warn("Firebase credentials not configured - FCM push notifications are disabled (log-only). " +
                        "Set PINGHOME_FIREBASE_CREDENTIALS_JSON or PINGHOME_FIREBASE_CREDENTIALS_PATH to enable.");
                return;
            }
            FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).build();
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
            enabled = true;
            log.info("Firebase Admin SDK initialized - FCM push notifications enabled");
        } catch (IOException e) {
            log.error("Failed to initialize Firebase Admin SDK; FCM disabled", e);
        }
    }

    private GoogleCredentials loadCredentials() throws IOException {
        if (!credentialsJson.isBlank()) {
            try (InputStream in = new java.io.ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8))) {
                return GoogleCredentials.fromStream(in);
            }
        }
        if (!credentialsPath.isBlank()) {
            try (InputStream in = new FileInputStream(credentialsPath)) {
                return GoogleCredentials.fromStream(in);
            }
        }
        return null;
    }

    /** @return true if a push was actually dispatched to FCM; false if disabled or delivery failed. */
    public boolean sendPush(String fcmToken, String title, String body, Map<String, String> data) {
        if (!enabled) {
            log.info("[FCM disabled] would send to token={} title='{}' body='{}' data={}", mask(fcmToken), title, body, data);
            return false;
        }
        if (fcmToken == null || fcmToken.isBlank()) {
            log.warn("Skipping push '{}' - recipient has no registered FCM token", title);
            return false;
        }

        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data == null ? Map.of() : data)
                .build();

        try {
            FirebaseMessaging.getInstance().send(message);
            return true;
        } catch (FirebaseMessagingException e) {
            log.error("FCM delivery failed for token={}: {}", mask(fcmToken), e.getMessage());
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    private String mask(String token) {
        if (token == null || token.length() < 8) return "***";
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}
