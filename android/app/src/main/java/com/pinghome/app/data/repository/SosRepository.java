package com.pinghome.app.data.repository;

import android.content.Context;

import com.pinghome.app.data.api.ApiCallExecutor;
import com.pinghome.app.data.api.ApiClient;
import com.pinghome.app.data.api.PingHomeApi;
import com.pinghome.app.data.api.dto.SosRequest;
import com.pinghome.app.util.ApiCallback;
import com.pinghome.app.util.ApiResult;
import com.pinghome.app.util.AppExecutors;

/** SOS is the single highest-priority action in the app - both the in-app button and the BLE
 * wearable relay go through here so retry/queueing behaviour (SosSyncWorker) is identical either
 * way. See ui.trip.SosSyncWorker for what happens when this immediate attempt fails offline. */
public class SosRepository {

    private final PingHomeApi api;
    private final AppExecutors executors = AppExecutors.getInstance();

    public SosRepository(Context context) {
        this.api = ApiClient.get(context);
    }

    public void triggerManualSos(Long tripId, double lat, double lng, ApiCallback<Void> callback) {
        executors.runBackground(() -> {
            ApiResult<Void> result = ApiCallExecutor.execute(api.manualSos(new SosRequest(tripId, lat, lng, null)));
            executors.runOnMain(() -> callback.onResult(result));
        });
    }

    public void triggerWearableSos(Long tripId, double lat, double lng, String bleIdentifier, ApiCallback<Void> callback) {
        executors.runBackground(() -> {
            ApiResult<Void> result = ApiCallExecutor.execute(api.wearableSos(new SosRequest(tripId, lat, lng, bleIdentifier)));
            executors.runOnMain(() -> callback.onResult(result));
        });
    }
}
