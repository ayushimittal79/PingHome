package com.pinghome.app.data.api.dto;

public class WatcherAssignment {
    public long contactId;
    public WatcherPermission permission;

    public WatcherAssignment(long contactId, WatcherPermission permission) {
        this.contactId = contactId;
        this.permission = permission;
    }
}
