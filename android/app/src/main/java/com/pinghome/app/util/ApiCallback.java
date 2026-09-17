package com.pinghome.app.util;

public interface ApiCallback<T> {
    void onResult(ApiResult<T> result);
}
