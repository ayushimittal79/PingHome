package com.pinghome.app.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.pinghome.app.R;

/**
 * The signature SOS control: a solid red circle with a static halo and a continuously pulsing
 * outer ring. Only the solid inner circle is tappable, so a brush against the glow does nothing.
 */
public class SosButtonView extends FrameLayout {

    private static final float RING_MAX_SCALE = 1.35f;
    private static final long PULSE_MS = 1600;

    private final FrameLayout ring;
    private final FrameLayout core;
    private final TextView label;
    private ValueAnimator pulse;

    public SosButtonView(Context context) {
        this(context, null);
    }

    public SosButtonView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        int size = dp(190);
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SosButtonView);
            size = a.getDimensionPixelSize(R.styleable.SosButtonView_sosSize, size);
            a.recycle();
        }
        int glow = context.getColor(R.color.sos_glow);
        int red = context.getColor(R.color.sos_red);

        ring = circle(size, glow);
        FrameLayout halo = circle((int) (size * 0.82f), glow);
        core = circle((int) (size * 0.62f), red);

        label = new TextView(context);
        label.setText(R.string.action_sos);
        label.setTextColor(context.getColor(R.color.white));
        label.setTextSize(28);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setLetterSpacing(0.05f);
        core.addView(label, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        int outer = (int) (size * 1.5f);
        setMinimumWidth(outer);
        setMinimumHeight(outer);
        addView(ring);
        addView(halo);
        addView(core);
        setClickable(false);
    }

    private FrameLayout circle(int diameter, int color) {
        FrameLayout view = new FrameLayout(getContext());
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        view.setBackground(shape);
        view.setLayoutParams(new LayoutParams(diameter, diameter, Gravity.CENTER));
        return view;
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener listener) {
        core.setOnClickListener(listener);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        core.setEnabled(enabled);
        core.setClickable(enabled);
        setAlpha(enabled ? 1f : 0.45f);
        if (enabled) startPulse(); else stopPulse();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isEnabled()) startPulse();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopPulse();
        super.onDetachedFromWindow();
    }

    private void startPulse() {
        if (pulse != null && pulse.isRunning()) return;
        pulse = ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(PULSE_MS);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.RESTART);
        pulse.setInterpolator(new LinearInterpolator());
        pulse.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            float scale = 1f + (RING_MAX_SCALE - 1f) * t;
            ring.setScaleX(scale);
            ring.setScaleY(scale);
            ring.setAlpha(0.5f * (1f - t));
        });
        pulse.start();
    }

    private void stopPulse() {
        if (pulse != null) {
            pulse.cancel();
            pulse = null;
        }
        ring.setScaleX(1f);
        ring.setScaleY(1f);
        ring.setAlpha(0f);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
