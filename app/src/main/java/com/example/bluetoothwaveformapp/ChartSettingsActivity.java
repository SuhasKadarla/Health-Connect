package com.example.bluetoothwaveformapp;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;

import androidx.appcompat.app.AppCompatActivity;

public class ChartSettingsActivity extends AppCompatActivity {

    private SeekBar lineWidthSeekBar;
    private CheckBox showGridLinesCheckBox;
    private CheckBox enableZoomCheckBox;
    private Button saveButton;

    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chart_settings);

        lineWidthSeekBar = findViewById(R.id.seekBarLineWidth);
        showGridLinesCheckBox = findViewById(R.id.checkBoxGridLines);
        enableZoomCheckBox = findViewById(R.id.checkBoxZoom);
        saveButton = findViewById(R.id.buttonSaveSettings);

        sharedPreferences = getSharedPreferences("ChartPrefs", MODE_PRIVATE);

        // Load saved preferences
        float savedLineWidth = sharedPreferences.getFloat("lineWidth", 2f);
        boolean savedShowGridLines = sharedPreferences.getBoolean("showGridLines", true);
        boolean savedEnableZoom = sharedPreferences.getBoolean("enableZoom", true);

        lineWidthSeekBar.setProgress((int) (savedLineWidth * 10));
        showGridLinesCheckBox.setChecked(savedShowGridLines);
        enableZoomCheckBox.setChecked(savedEnableZoom);

        saveButton.setOnClickListener(v -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putFloat("lineWidth", lineWidthSeekBar.getProgress() / 10f);
            editor.putBoolean("showGridLines", showGridLinesCheckBox.isChecked());
            editor.putBoolean("enableZoom", enableZoomCheckBox.isChecked());
            editor.apply();
            finish(); // Close settings screen
        });
    }
}
