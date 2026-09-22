package com.pinghome.app.ui.fakecall;

import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.pinghome.app.R;
import com.pinghome.app.data.SessionManager;

public class FakeCallSettingsActivity extends AppCompatActivity {

    private static final int MIN_DELAY_SECONDS = 5;

    private EditText callerNameInput;
    private SeekBar delaySeekBar;
    private TextView delayValueText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fake_call_settings);
        setTitle("Fake incoming call");

        SessionManager session = SessionManager.getInstance(this);

        callerNameInput = findViewById(R.id.callerNameInput);
        callerNameInput.setText(session.getFakeCallCallerName());

        delaySeekBar = findViewById(R.id.delaySeekBar);
        delayValueText = findViewById(R.id.delayValueText);
        int currentDelay = session.getFakeCallDelaySeconds();
        delaySeekBar.setProgress(Math.max(0, currentDelay - MIN_DELAY_SECONDS));
        updateDelayLabel(currentDelay);

        delaySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateDelayLabel(progress + MIN_DELAY_SECONDS);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        findViewById(R.id.saveButton).setOnClickListener(v -> save());
        findViewById(R.id.fullScreenPermissionButton).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                        .setData(Uri.parse("package:" + getPackageName()))));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Android 14+ grants full-screen notifications only on request; without them the fake call
        // still works while the app is open (FakeCallService launches the screen directly) but
        // can't take over a locked screen.
        boolean needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && !getSystemService(NotificationManager.class).canUseFullScreenIntent();
        int visibility = needsPermission ? View.VISIBLE : View.GONE;
        findViewById(R.id.fullScreenHintText).setVisibility(visibility);
        findViewById(R.id.fullScreenPermissionButton).setVisibility(visibility);
    }

    private void updateDelayLabel(int seconds) {
        delayValueText.setText(seconds + "s");
    }

    private void save() {
        String name = callerNameInput.getText().toString().trim();
        if (name.isEmpty()) name = "Mom";
        int delaySeconds = delaySeekBar.getProgress() + MIN_DELAY_SECONDS;

        SessionManager.getInstance(this).setFakeCallSettings(name, delaySeconds);
        Toast.makeText(this, "Saved.", Toast.LENGTH_SHORT).show();
        finish();
    }
}
