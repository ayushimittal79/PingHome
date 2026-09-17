package com.pinghome.app.data.api;

import com.pinghome.app.data.api.dto.*;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.*;

public interface PingHomeApi {

    @POST("api/auth/register")
    Call<AuthResponse> register(@Body RegisterRequest request);

    @POST("api/auth/login")
    Call<AuthResponse> login(@Body LoginRequest request);

    @GET("api/users/me")
    Call<UserProfileResponse> me();

    @PUT("api/users/me")
    Call<UserProfileResponse> updateProfile(@Body UpdateProfileRequest request);

    @PUT("api/users/me/fcm-token")
    Call<Void> updateFcmToken(@Body FcmTokenRequest request);

    @GET("api/contacts")
    Call<List<ContactResponse>> listContacts();

    @POST("api/contacts")
    Call<ContactResponse> createContact(@Body ContactRequest request);

    @PUT("api/contacts/{contactId}")
    Call<ContactResponse> updateContact(@Path("contactId") long contactId, @Body ContactRequest request);

    @DELETE("api/contacts/{contactId}")
    Call<Void> deleteContact(@Path("contactId") long contactId);

    @POST("api/trips")
    Call<TripResponse> createTrip(@Body CreateTripRequest request);

    @GET("api/trips/mine")
    Call<List<TripResponse>> myTrips();

    @GET("api/trips/watching")
    Call<List<TripResponse>> watchingTrips();

    @GET("api/trips/{tripId}")
    Call<TripResponse> getTrip(@Path("tripId") long tripId);

    @POST("api/trips/{tripId}/confirm-safe")
    Call<TripResponse> confirmSafe(@Path("tripId") long tripId);

    @POST("api/trips/{tripId}/complete-arrival")
    Call<TripResponse> completeArrival(@Path("tripId") long tripId);

    @POST("api/trips/{tripId}/cancel")
    Call<TripResponse> cancelTrip(@Path("tripId") long tripId);

    @POST("api/trips/{tripId}/locations")
    Call<Void> pushLocation(@Path("tripId") long tripId, @Body LocationUpdateRequest request);

    @GET("api/trips/{tripId}/locations/latest")
    Call<LocationResponse> latestLocation(@Path("tripId") long tripId);

    @POST("api/sos")
    Call<Void> manualSos(@Body SosRequest request);

    @POST("api/sos/wearable")
    Call<Void> wearableSos(@Body SosRequest request);

    @GET("api/alerts/mine")
    Call<List<AlertResponse>> myAlerts();

    @GET("api/alerts/trip/{tripId}")
    Call<List<AlertResponse>> alertsForTrip(@Path("tripId") long tripId);

    @PATCH("api/alerts/{alertId}/resolve")
    Call<AlertResponse> resolveAlert(@Path("alertId") long alertId);

    @GET("api/wearables")
    Call<List<WearableDeviceResponse>> listWearables();

    @POST("api/wearables")
    Call<WearableDeviceResponse> pairWearable(@Body PairDeviceRequest request);

    @DELETE("api/wearables/{deviceId}")
    Call<Void> unpairWearable(@Path("deviceId") long deviceId);
}
