package com.pinghome.backend.contact.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 32) String phone,
        @Email @Size(max = 190) String email,
        @Size(max = 60) String relationship
) {}
