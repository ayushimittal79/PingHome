package com.pinghome.app.data.api.dto;

public class ContactRequest {
    public String name;
    public String phone;
    public String email;
    public String relationship;

    public ContactRequest(String name, String phone, String email, String relationship) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.relationship = relationship;
    }
}
