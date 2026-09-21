package com.pinghome.app.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.pinghome.app.R;

/**
 * Small surface-coloured pill with a pulsing dot: "Live location on" while a trip is sharing
 * location, "Location off" otherwise. Compact mode shows only the dot.
 */
public class LiveBadgeView extends LinearLayout {

    private final FrameLayout pulseDot;
    private final FrameLayout dot;
    private final ImageView icon;
    private final TextView text;
    private final boolean compact;
    private final int liveColor;
    private final int mutedColor;
    private ValueAnimator pulse;
    private boolean live;

    public LiveBadgeView(Context context) {
        this(context, null);
    }

    public LiveBadgeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        boolean compactAttr = false;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.LiveBadgeView);
            compactAttr = a.getBoolean(R.styleable.LiveBadgeView_compact, false);
            a.recycle();
        }
        compact = compactAttr;
        liveColor = context.getColor(R.color.safe_green);
        mutedColor = themeColor(R.attr.colorMuted);

        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundResource(R.drawable.bg_surface_pill);
        int h = dp(compact ? 8 : 12), v = dp(compact ? 4 : 6);
        setPadding(h, v, h, v);

        FrameLayout dotHolder = new FrameLayout(context);
        pulseDot = circle(dp(14), liveColor);
        dot = circle(dp(8), mutedColor);
        dotHolder.addView(pulseDot);
        dotHolder.addView(dot);
        addView(dotHolder, new LayoutParams(dp(14), dp(14)));

        icon = new ImageView(context);
        icon.setImageResource(R.drawable.ic_location);
        LayoutParams iconParams = new LayoutParams(dp(14), dp(14));
        iconParams.setMarginStart(dp(6));
        addView(icon, iconParams);

        text = new TextView(context);
        text.setTextAppearance(R.style.TextAppearance_PingHome_LabelSmall);
        LayoutParams textParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        textParams.setMarginStart(dp(6));
        addView(text, textParams);

        if (compact) {
            icon.setVisibility(GONE);
            text.setVisibility(GONE);
        }
        setLive(false);
    }

    public void setLive(boolean isLive) {
        this.live = isLive;
        ((GradientDrawable) dot.getBackground()).setColor(isLive ? liveColor : mutedColor);
        icon.setColorFilter(isLive ? liveColor : mutedColor);
        text.setText(isLive ? R.string.badge_live_on : R.string.badge_live_off);
        text.setTextColor(isLive ? themeColor(com.google.android.material.R.attr.colorOnSurface) : mutedColor);
        if (isLive) startPulse(); else stopPulse();
    }

    public boolean isLive() {
        return live;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (live) startPulse();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopPulse();
        super.onDetachedFromWindow();
    }

    private void startPulse() {
        if (pulse != null && pulse.isRunning()) return;
        pulse = ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(1200);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new LinearInterpolator());
        pulse.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            float scale = 1f + 0.9f * t;
            pulseDot.setScaleX(scale);
            pulseDot.setScaleY(scale);
            pulseDot.setAlpha(0.55f * (1f - t));
        });
        pulse.start();
    }

    private void stopPulse() {
        if (pulse != null) {
            pulse.cancel();
            pulse = null;
        }
        pulseDot.setAlpha(0f);
    }

    private FrameLayout circle(int diameter, int color) {
        FrameLayout view = new FrameLayout(getContext());
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        view.setBackground(shape);
        view.setLayoutParams(new FrameLayout.LayoutParams(diameter, diameter, Gravity.CENTER));
        return view;
    }

    private int themeColor(int attr) {
        TypedValue value = new TypedValue();
        getContext().getTheme().resolveAttribute(attr, value, true);
        return value.data;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
