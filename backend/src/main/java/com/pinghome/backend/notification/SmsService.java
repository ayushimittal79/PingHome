package com.pinghome.backend.notification;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Optional SMS fallback for CRITICAL alerts only (escalation, SOS). Disabled by default via
 * pinghome.sms.enabled=false - Twilio must never fire during ordinary development/demo runs.
 * See docs/cloud-services.md for how to obtain a Twilio trial account and verified number.
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    private final boolean enabled;
    private final String accountSid;
    private final String authToken;
    private final String fromNumber;
    private boolean initialized = false;

    public SmsService(
            @Value("${pinghome.sms.enabled:false}") boolean enabled,
            @Value("${pinghome.sms.account-sid:}") String accountSid,
            @Value("${pinghome.sms.auth-token:}") String authToken,
            @Value("${pinghome.sms.from-number:}") String fromNumber
    ) {
        this.enabled = enabled;
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
    }

    @PostConstruct
    void init() {
        if (!enabled) {
            log.info("SMS fallback disabled (pinghome.sms.enabled=false) - Twilio will not be called");
            return;
        }
        if (accountSid.isBlank() || authToken.isBlank() || fromNumber.isBlank()) {
            log.warn("SMS fallback enabled but Twilio credentials are incomplete - SMS will be skipped");
            return;
        }
        Twilio.init(accountSid, authToken);
        initialized = true;
        log.info("Twilio SMS fallback initialized");
    }

    public boolean sendSms(String toNumber, String body) {
        if (!initialized || toNumber == null || toNumber.isBlank()) {
            log.info("[SMS skipped] to={} body='{}'", toNumber, body);
            return false;
        }
        try {
            Message.creator(new PhoneNumber(toNumber), new PhoneNumber(fromNumber), body).create();
            return true;
        } catch (Exception e) {
            log.error("Twilio SMS delivery failed: {}", e.getMessage());
            return false;
        }
    }
}
