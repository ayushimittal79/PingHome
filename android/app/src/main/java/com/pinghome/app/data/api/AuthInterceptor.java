package com.pinghome.app.data.api;

import androidx.annotation.NonNull;
import com.pinghome.app.data.SessionManager;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {

    private final SessionManager sessionManager;

    public AuthInterceptor(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        String token = sessionManager.getToken();

        if (token == null || original.url().encodedPath().startsWith("/api/auth/")) {
            return chain.proceed(original);
        }

        Request authorized = original.newBuilder()
                .header("Authorization", "Bearer " + token)
                .build();
        return chain.proceed(authorized);
    }
}
