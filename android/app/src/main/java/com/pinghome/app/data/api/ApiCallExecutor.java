package com.pinghome.app.data.api;

import com.google.gson.Gson;
import com.pinghome.app.data.api.dto.ApiErrorResponse;
import com.pinghome.app.util.ApiResult;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

/** Runs a Retrofit call synchronously (caller must already be off the main thread) and folds the
 * result into an ApiResult, decoding the backend's ErrorResponse JSON when present. */
public final class ApiCallExecutor {

    private static final Gson GSON = new Gson();

    private ApiCallExecutor() {}

    public static <T> ApiResult<T> execute(Call<T> call) {
        try {
            Response<T> response = call.execute();
            if (response.isSuccessful()) {
                return ApiResult.success(response.body());
            }
            return ApiResult.failure(parseErrorMessage(response), response.code());
        } catch (IOException e) {
            return ApiResult.failure("Network error - check your connection and try again.", 0);
        } catch (Exception e) {
            return ApiResult.failure("Unexpected error: " + e.getMessage(), -1);
        }
    }

    private static String parseErrorMessage(Response<?> response) {
        try (ResponseBody errorBody = response.errorBody()) {
            if (errorBody == null) return "Request failed (" + response.code() + ")";
            ApiErrorResponse error = GSON.fromJson(errorBody.string(), ApiErrorResponse.class);
            return error != null && error.message != null ? error.message : "Request failed (" + response.code() + ")";
        } catch (Exception e) {
            return "Request failed (" + response.code() + ")";
        }
    }
}
