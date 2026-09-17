package com.pinghome.app.data.api;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pinghome.app.BuildConfig;
import com.pinghome.app.data.SessionManager;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    private static volatile PingHomeApi api;

    public static PingHomeApi get(Context context) {
        if (api == null) {
            synchronized (ApiClient.class) {
                if (api == null) {
                    api = build(context.getApplicationContext());
                }
            }
        }
        return api;
    }

    private static PingHomeApi build(Context context) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
                .create();

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BODY
                : HttpLoggingInterceptor.Level.NONE);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new AuthInterceptor(SessionManager.getInstance(context)))
                .addInterceptor(logging)
                // Poor-connectivity resilience: retry transient connection failures once at the
                // transport level; call-site retry/backoff (e.g. SosSyncWorker) handles the rest.
                .retryOnConnectionFailure(true)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        return retrofit.create(PingHomeApi.class);
    }
}
