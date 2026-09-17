package com.pinghome.app.data.api.dto;

import java.util.List;

/** Mirrors com.pinghome.backend.common.exception.ErrorResponse. */
public class ApiErrorResponse {
    public String timestamp;
    public int status;
    public String error;
    public String message;
    public String path;
    public List<String> fieldErrors;
}
