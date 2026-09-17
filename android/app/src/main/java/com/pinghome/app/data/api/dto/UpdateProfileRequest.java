package com.pinghome.app.data.api.dto;

public class UpdateProfileRequest {
    public String name;
    public String phone;

    public UpdateProfileRequest(String name, String phone) {
        this.name = name;
        this.phone = phone;
    }
}
