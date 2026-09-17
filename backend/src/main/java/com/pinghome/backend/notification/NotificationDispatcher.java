package com.pinghome.backend.notification;

import com.pinghome.backend.user.User;
import com.pinghome.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Single choke point for outbound notifications so every caller (escalation scheduler, SOS
 * endpoint, trip completion) gets the same best-effort delivery + fallback behaviour instead of
 * re-implementing it. FCM is always attempted first; Twilio SMS is only attempted for `critical`
 * sends (SOS/escalation) and only if pinghome.sms.enabled=true. Delivery failures are logged,
 * never thrown - a notification failure must not fail the safety operation that triggered it.
 */
@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final FcmService fcmService;
    private final SmsService smsService;
    private final UserRepository userRepository;

    public NotificationDispatcher(FcmService fcmService, SmsService smsService, UserRepository userRepository) {
        this.fcmService = fcmService;
        this.smsService = smsService;
        this.userRepository = userRepository;
    }

    public void notifyUser(Long userId, String title, String body, Map<String, String> data, boolean critical) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("Cannot notify user {} - not found", userId);
            return;
        }

        boolean pushed = fcmService.sendPush(user.getFcmToken(), title, body, data);

        if (critical && !pushed) {
            smsService.sendSms(user.getPhone(), title + ": " + body);
        }
    }
}
