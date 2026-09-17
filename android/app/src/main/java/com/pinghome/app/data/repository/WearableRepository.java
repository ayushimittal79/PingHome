package com.pinghome.app.data.repository;

import android.content.Context;

import com.pinghome.app.data.api.ApiCallExecutor;
import com.pinghome.app.data.api.ApiClient;
import com.pinghome.app.data.api.PingHomeApi;
import com.pinghome.app.data.api.dto.PairDeviceRequest;
import com.pinghome.app.data.api.dto.WearableDeviceResponse;
import com.pinghome.app.util.ApiCallback;
import com.pinghome.app.util.AppExecutors;

import java.util.List;

public class WearableRepository {

    private final PingHomeApi api;
    private final AppExecutors executors = AppExecutors.getInstance();

    public WearableRepository(Context context) {
        this.api = ApiClient.get(context);
    }

    public void list(ApiCallback<List<WearableDeviceResponse>> callback) {
        executors.runBackground(() -> {
            var result = ApiCallExecutor.execute(api.listWearables());
            executors.runOnMain(() -> callback.onResult(result));
        });
    }

    public void pair(String deviceName, String bleIdentifier, ApiCallback<WearableDeviceResponse> callback) {
        executors.runBackground(() -> {
            var result = ApiCallExecutor.execute(api.pairWearable(new PairDeviceRequest(deviceName, bleIdentifier)));
            executors.runOnMain(() -> callback.onResult(result));
        });
    }

    public void unpair(long deviceId, ApiCallback<Void> callback) {
        executors.runBackground(() -> {
            var result = ApiCallExecutor.execute(api.unpairWearable(deviceId));
            executors.runOnMain(() -> callback.onResult(result));
        });
    }
}
