package com.pinghome.backend.user.dto;

public record AuthResponse(String token, UserProfileResponse user) {}
