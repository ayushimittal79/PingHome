package com.pinghome.backend.alert;

/** What triggered the alert - lets watcher UI and the escalation scheduler tell manual SOS
 * taps apart from physical wearable button presses and from missed-arrival auto-escalation. */
public enum AlertSource {
    APP,
    WEARABLE,
    SCHEDULER
}
