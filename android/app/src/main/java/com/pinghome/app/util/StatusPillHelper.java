package com.pinghome.app.util;

import android.content.Context;
import android.widget.TextView;

import com.pinghome.app.R;
import com.pinghome.app.data.api.dto.TripStatus;

/** Central place mapping trip status -> the palette from SPEC.md's UI direction (green=safe,
 * amber=warning, red reserved for SOS/escalation) so every screen renders status consistently. */
public final class StatusPillHelper {

    private StatusPillHelper() {}

    public static void apply(TextView pill, Context context, TripStatus status) {
        int colorRes;
        String label;
        switch (status) {
            case ACTIVE -> { colorRes = R.color.blue_muted; label = "ACTIVE"; }
            case COMPLETED -> { colorRes = R.color.safe_green; label = "COMPLETED"; }
            case ESCALATED -> { colorRes = R.color.sos_red; label = "ESCALATED"; }
            case CANCELLED -> { colorRes = R.color.charcoal_700; label = "CANCELLED"; }
            default -> { colorRes = R.color.charcoal_700; label = status.name(); }
        }
        pill.setText(label);
        pill.setBackgroundColor(context.getColor(colorRes));
    }
}
