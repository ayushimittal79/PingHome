package com.pinghome.app.data.repository;

import android.content.Context;

import com.pinghome.app.data.api.ApiCallExecutor;
import com.pinghome.app.data.api.ApiClient;
import com.pinghome.app.data.api.PingHomeApi;
import com.pinghome.app.data.api.dto.*;
import com.pinghome.app.util.ApiCallback;
import com.pinghome.app.util.AppExecutors;

import java.util.List;

public class TripRepository {

    private final PingHomeApi api;
    private final AppExecutors executors = AppExecutors.getInstance();

    public TripRepository(Context context) {
        this.api = ApiClient.get(context);
    }

    public void createTrip(CreateTripRequest request, ApiCallback<TripResponse> callback) {
        executors.runBackground(() -> {
            var result = ApiCallExecutor.execute(api.createTrip(request));
            executors.runOnMain(() -> callback.onResult(result));
        });
    }

    public void myTrips(ApiCallback<List<TripResponse>> callback) {
        executors.runBackground(() -> {
            var result = ApiCallExecutor.execute(api.myTrips());
            executors.runOnMain(() -> callback.onResult(result));
        });
    }

    public void watchingTrips(ApiCallback<List<TripResponse>> callback) {
        executors.runBackground(() -> {
            var result = ApiCallExecutor.execute(api.watchingTrips());
            executors.runOnMain(() -> callback.onResult(result));
        });
    }

    public void getTrip(long tripId, ApiCallback<TripResponse> callback) {
        executors.runBackground(() -> {
            var result = ApiCallExecutor.execute(api.getTrip(tripId));
            executors.runOnMain(() -> callback.onResult(result));
        });
    }

    public void confirmSafe(long tripId, ApiCallback<TripResponse> callback) {
        executors.runBackground(() -> {
            var result = ApiCallExecutor.execute(api.confirmSafe(tripId));
            executors.runOnMain(() -> callback.onResult(result));
        });
    }

    public void completeArrival(long tripId, ApiCallback<TripResponse> callback) {
        executors.runBackground(() -> {
            var result = ApiCallExecutor.execute(api.completeArrival(tripId));
            executors.runOnMain(() -> callback.onResult(result));
        });
    }

    public void cancelTrip(long tripId, ApiCallback<TripResponse> callback) {
        executors.runBackground(() -> {
            var result = ApiCallExecutor.execute(api.cancelTrip(tripId));
            executors.runOnMain(() -> callback.onResult(result));
        });
    }

    /** Fire-and-forget from the periodic location ticker; failures are logged, not surfaced to the
     * user, and simply resume on the next tick once connectivity returns. */
    public void pushLocationBestEffort(long tripId, double lat, double lng) {
        executors.runBackground(() -> ApiCallExecutor.execute(api.pushLocation(tripId, new LocationUpdateRequest(lat, lng))));
    }

    public void latestLocation(long tripId, ApiCallback<LocationResponse> callback) {
        executors.runBackground(() -> {
            var result = ApiCallExecutor.execute(api.latestLocation(tripId));
            executors.runOnMain(() -> callback.onResult(result));
        });
    }

    public void alertsForTrip(long tripId, ApiCallback<List<AlertResponse>> callback) {
        executors.runBackground(() -> {
            var result = ApiCallExecutor.execute(api.alertsForTrip(tripId));
            executors.runOnMain(() -> callback.onResult(result));
        });
    }

    public void resolveAlert(long alertId, ApiCallback<AlertResponse> callback) {
        executors.runBackground(() -> {
            var result = ApiCallExecutor.execute(api.resolveAlert(alertId));
            executors.runOnMain(() -> callback.onResult(result));
        });
    }
}
