package com.pinghome.app.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.pinghome.app.R;
import com.pinghome.app.data.api.dto.TripStatus;

/** Central place mapping trip status -> colour (green = safe, amber = warning, red reserved for
 * SOS/escalation, primary for in-progress) so every screen renders status consistently. */
public final class StatusPillHelper {

    private StatusPillHelper() {}

    public static void apply(TextView pill, Context context, TripStatus status) {
        int color;
        String label;
        switch (status) {
            case ACTIVE -> { color = themeColor(context, androidx.appcompat.R.attr.colorPrimary); label = "ACTIVE"; }
            case COMPLETED -> { color = context.getColor(R.color.safe_green); label = "COMPLETED"; }
            case ESCALATED -> { color = context.getColor(R.color.sos_red); label = "ESCALATED"; }
            case CANCELLED -> { color = context.getColor(R.color.neutral_pill); label = "CANCELLED"; }
            default -> { color = context.getColor(R.color.neutral_pill); label = status.name(); }
        }
        pill.setText(label);
        Drawable background = DrawableCompat.wrap(ContextCompat.getDrawable(context, R.drawable.bg_pill).mutate());
        DrawableCompat.setTint(background, color);
        pill.setBackground(background);
        pill.setTextColor(context.getColor(R.color.white));
    }

    private static int themeColor(Context context, int attr) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attr, value, true);
        return value.data;
    }
}
