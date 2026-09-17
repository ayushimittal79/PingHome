package com.pinghome.app.ui.fakecall;

import android.os.Bundle;
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
