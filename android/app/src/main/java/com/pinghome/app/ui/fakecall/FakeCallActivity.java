package com.pinghome.app.ui.fakecall;

import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;

import com.pinghome.app.R;

/**
 * The simulated incoming-call screen itself. Deliberately does nothing beyond local UI/sound/
 * vibration - no network call, no trip/alert interaction - per SPEC.md: "must not itself
 * create a safety alert." Accept/decline both simply end the simulation.
 */
public class FakeCallActivity extends AppCompatActivity {

    public static final String EXTRA_CALLER_NAME = "caller_name";
    private static final int NOTIFICATION_ID = 77;

    private Ringtone ringtone;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        setContentView(R.layout.activity_fake_call);

        String callerName = getIntent().getStringExtra(EXTRA_CALLER_NAME);
        ((TextView) findViewById(R.id.callerNameText)).setText(callerName != null ? callerName : "Unknown caller");

        findViewById(R.id.acceptButton).setOnClickListener(v -> endSimulation());
        findViewById(R.id.declineButton).setOnClickListener(v -> endSimulation());

        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
        startRinging();
    }

    private void startRinging() {
        try {
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ringtone = RingtoneManager.getRingtone(this, ringtoneUri);
            if (ringtone != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ringtone.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .build());
                }
                ringtone.play();
            }
        } catch (Exception ignored) {
            // Ringtone playback is a nicety, not core to the simulation - fail silently.
        }

        vibrator = getSystemService(Vibrator.class);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = {0, 800, 600};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        }
    }

    private void endSimulation() {
        if (ringtone != null && ringtone.isPlaying()) ringtone.stop();
        if (vibrator != null) vibrator.cancel();
        finish();
    }

    @Override
    protected void onDestroy() {
        if (ringtone != null && ringtone.isPlaying()) ringtone.stop();
        if (vibrator != null) vibrator.cancel();
        super.onDestroy();
    }
}
