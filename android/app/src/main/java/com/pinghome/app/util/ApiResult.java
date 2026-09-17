package com.pinghome.app.util;

/** Small Result wrapper so ViewModels don't have to deal with raw Retrofit Response/Throwable
 * plumbing at every call site. */
public class ApiResult<T> {

    public final boolean success;
    public final T data;
    public final String errorMessage;
    public final int httpStatus;

    private ApiResult(boolean success, T data, String errorMessage, int httpStatus) {
        this.success = success;
        this.data = data;
        this.errorMessage = errorMessage;
        this.httpStatus = httpStatus;
    }

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(true, data, null, 200);
    }

    public static <T> ApiResult<T> failure(String message, int httpStatus) {
        return new ApiResult<>(false, null, message, httpStatus);
    }
}
