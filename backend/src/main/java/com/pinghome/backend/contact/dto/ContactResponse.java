package com.pinghome.backend.contact.dto;

import com.pinghome.backend.contact.Contact;

public record ContactResponse(Long id, String name, String phone, String email, String relationship) {
    public static ContactResponse from(Contact contact) {
        return new ContactResponse(contact.getId(), contact.getName(), contact.getPhone(),
                contact.getEmail(), contact.getRelationship());
    }
}
