package com.pinghome.app.data.repository;

import android.content.Context;

import com.pinghome.app.data.SessionManager;
import com.pinghome.app.data.api.ApiCallExecutor;
import com.pinghome.app.data.api.ApiClient;
import com.pinghome.app.data.api.PingHomeApi;
import com.pinghome.app.data.api.dto.*;
import com.pinghome.app.util.ApiCallback;
import com.pinghome.app.util.ApiResult;
import com.pinghome.app.util.AppExecutors;

public class AuthRepository {

    private final PingHomeApi api;
    private final SessionManager sessionManager;
    private final AppExecutors executors = AppExecutors.getInstance();

    public AuthRepository(Context context) {
        this.api = ApiClient.get(context);
        this.sessionManager = SessionManager.getInstance(context);
    }

    public void register(String name, String email, String phone, String password, ApiCallback<AuthResponse> callback) {
        executors.runBackground(() -> {
            ApiResult<AuthResponse> result = ApiCallExecutor.execute(api.register(new RegisterRequest(name, email, phone, password)));
            if (result.success) persistSession(result.data);
            executors.runOnMain(() -> callback.onResult(result));
        });
    }

    public void login(String email, String password, ApiCallback<AuthResponse> callback) {
        executors.runBackground(() -> {
            ApiResult<AuthResponse> result = ApiCallExecutor.execute(api.login(new LoginRequest(email, password)));
            if (result.success) persistSession(result.data);
            executors.runOnMain(() -> callback.onResult(result));
        });
    }

    /** Stateless JWT - logout is purely local: drop the stored token so future requests fail auth. */
    public void logout() {
        sessionManager.clear();
    }

    public boolean isLoggedIn() {
        return sessionManager.isLoggedIn();
    }

    public void updateProfile(String name, String phone, ApiCallback<UserProfileResponse> callback) {
        executors.runBackground(() -> {
            ApiResult<UserProfileResponse> result = ApiCallExecutor.execute(api.updateProfile(new UpdateProfileRequest(name, phone)));
            executors.runOnMain(() -> callback.onResult(result));
        });
    }

    public void registerFcmToken(String fcmToken) {
        if (!sessionManager.isLoggedIn()) return;
        executors.runBackground(() -> ApiCallExecutor.execute(api.updateFcmToken(new FcmTokenRequest(fcmToken))));
    }

    private void persistSession(AuthResponse response) {
        sessionManager.saveSession(response.token, response.user.id, response.user.email, response.user.name);
    }
}
