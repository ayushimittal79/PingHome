package com.pinghome.backend.user.dto;

import com.pinghome.backend.user.User;

public record UserProfileResponse(Long id, String name, String email, String phone) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(user.getId(), user.getName(), user.getEmail(), user.getPhone());
    }
}
