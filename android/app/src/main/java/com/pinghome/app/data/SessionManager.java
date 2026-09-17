package com.pinghome.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Holds the JWT + logged-in user id on-device. Backed by EncryptedSharedPreferences so a stolen
 * device's storage doesn't hand over a live session token in plaintext. Session persistence
 * (staying logged in across app restarts) is exactly this: presence of a valid stored token.
 */
public class SessionManager {

    private static final String PREFS_NAME = "pinghome_session";
    private static final String KEY_TOKEN = "jwt_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_ACTIVE_TRIP_ID = "active_trip_id";
    private static final String KEY_PAIRED_BLE_ADDRESS = "paired_ble_address";
    private static final String KEY_FAKE_CALL_CALLER_NAME = "fake_call_caller_name";
    private static final String KEY_FAKE_CALL_DELAY_SECONDS = "fake_call_delay_seconds";

    private static volatile SessionManager instance;

    private final SharedPreferences prefs;

    private SessionManager(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            prefs = EncryptedSharedPreferences.create(
                    context, PREFS_NAME, masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Unable to initialize encrypted session storage", e);
        }
    }

    public static SessionManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public void saveSession(String token, long userId, String email, String name) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putLong(KEY_USER_ID, userId)
                .putString(KEY_USER_EMAIL, email)
                .putString(KEY_USER_NAME, name)
                .apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public long getUserId() {
        return prefs.getLong(KEY_USER_ID, -1);
    }

    public String getUserEmail() {
        return prefs.getString(KEY_USER_EMAIL, null);
    }

    public String getUserName() {
        return prefs.getString(KEY_USER_NAME, null);
    }

    public boolean isLoggedIn() {
        return getToken() != null;
    }

    public void clear() {
        prefs.edit().clear().apply();
    }

    /** Tracked locally so background components (WearableWatchService, geofence receiver) know
     * which trip - if any - is currently ACTIVE without an extra network round trip. The backend
     * remains the authoritative source of trip status; this is only a cache. */
    public void setActiveTripId(Long tripId) {
        if (tripId == null) {
            prefs.edit().remove(KEY_ACTIVE_TRIP_ID).apply();
        } else {
            prefs.edit().putLong(KEY_ACTIVE_TRIP_ID, tripId).apply();
        }
    }

    public Long getActiveTripId() {
        long value = prefs.getLong(KEY_ACTIVE_TRIP_ID, -1);
        return value == -1 ? null : value;
    }

    public void setPairedBleAddress(String address) {
        prefs.edit().putString(KEY_PAIRED_BLE_ADDRESS, address).apply();
    }

    public String getPairedBleAddress() {
        return prefs.getString(KEY_PAIRED_BLE_ADDRESS, null);
    }

    public void setFakeCallSettings(String callerName, int delaySeconds) {
        prefs.edit()
                .putString(KEY_FAKE_CALL_CALLER_NAME, callerName)
                .putInt(KEY_FAKE_CALL_DELAY_SECONDS, delaySeconds)
                .apply();
    }

    public String getFakeCallCallerName() {
        return prefs.getString(KEY_FAKE_CALL_CALLER_NAME, "Mom");
    }

    public int getFakeCallDelaySeconds() {
        return prefs.getInt(KEY_FAKE_CALL_DELAY_SECONDS, 8);
    }
}
