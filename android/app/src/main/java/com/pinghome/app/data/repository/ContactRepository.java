package com.pinghome.app.data.repository;

import android.content.Context;

import com.pinghome.app.data.api.ApiCallExecutor;
import com.pinghome.app.data.api.ApiClient;
import com.pinghome.app.data.api.PingHomeApi;
import com.pinghome.app.data.api.dto.ContactRequest;
import com.pinghome.app.data.api.dto.ContactResponse;
import com.pinghome.app.util.ApiCallback;
import com.pinghome.app.util.AppExecutors;

import java.util.List;

public class ContactRepository {

    private final PingHomeApi api;
    private final AppExecutors executors = AppExecutors.getInstance();

    public ContactRepository(Context context) {
        this.api = ApiClient.get(context);
    }

    public void list(ApiCallback<List<ContactResponse>> callback) {
        executors.runBackground(() -> {
            var result = ApiCallExecutor.execute(api.listContacts());
            executors.runOnMain(() -> callback.onResult(result));
        });
    }

    public void create(String name, String phone, String email, String relationship, ApiCallback<ContactResponse> callback) {
        executors.runBackground(() -> {
            var result = ApiCallExecutor.execute(api.createContact(new ContactRequest(name, phone, email, relationship)));
            executors.runOnMain(() -> callback.onResult(result));
        });
    }

    public void update(long contactId, String name, String phone, String email, String relationship, ApiCallback<ContactResponse> callback) {
        executors.runBackground(() -> {
            var result = ApiCallExecutor.execute(api.updateContact(contactId, new ContactRequest(name, phone, email, relationship)));
            executors.runOnMain(() -> callback.onResult(result));
        });
    }

    public void delete(long contactId, ApiCallback<Void> callback) {
        executors.runBackground(() -> {
            var result = ApiCallExecutor.execute(api.deleteContact(contactId));
            executors.runOnMain(() -> callback.onResult(result));
        });
    }
}
