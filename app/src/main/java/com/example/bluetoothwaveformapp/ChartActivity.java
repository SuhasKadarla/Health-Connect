package com.example.bluetoothwaveformapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class ChartActivity extends AppCompatActivity {

    private LineChart lineChart;
    private Button buttonResetZoom;
    private List<Integer> waveformPoints = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chart);

        // Enable Back Button
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // Initialize UI elements
        lineChart = findViewById(R.id.lineChart);
        buttonResetZoom = findViewById(R.id.buttonResetZoom);

        // Retrieve waveform data from Intent
        int[] waveformArray = getIntent().getIntArrayExtra("waveform_data");
        if (waveformArray != null) {
            for (int value : waveformArray) {
                waveformPoints.add(value);
            }
        }

        // Load Chart Data
        loadChartData();

        // Apply Chart Settings from SharedPreferences
        applyChartSettings();

        // Reset Zoom Button Listener
        buttonResetZoom.setOnClickListener(v -> resetChartZoom());
    }

    // Load Chart Data
    private void loadChartData() {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < waveformPoints.size(); i++) {
            entries.add(new Entry(i, waveformPoints.get(i)));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Waveform");
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);

        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);
        lineChart.invalidate();
    }

    // Apply Chart Settings from SharedPreferences
    private void applyChartSettings() {
        SharedPreferences sharedPreferences = getSharedPreferences("ChartPrefs", MODE_PRIVATE);
        float lineWidth = sharedPreferences.getFloat("lineWidth", 2f);
        boolean showGridLines = sharedPreferences.getBoolean("showGridLines", true);
        boolean enableZoom = sharedPreferences.getBoolean("enableZoom", true);

        // Apply settings
        lineChart.getXAxis().setDrawGridLines(showGridLines);
        lineChart.getAxisLeft().setDrawGridLines(showGridLines);
        lineChart.getAxisRight().setDrawGridLines(showGridLines);
        lineChart.setScaleEnabled(enableZoom);
        lineChart.setPinchZoom(enableZoom);
        lineChart.setDragEnabled(enableZoom);

        lineChart.invalidate();
    }

    // Function to Reset Zoom
    private void resetChartZoom() {
        lineChart.fitScreen();
        applyChartSettings(); // Reapply settings after reset
    }

    // Function to Update Chart Frequency
    private void updateChartFrequency(int frequency) {
        // Modify data update logic if necessary
        loadChartData();
    }

    // Handle Back Button Press
    @Override
    public boolean onSupportNavigateUp() {
        finish(); // Go back to MainActivity without erasing data
        return true;
    }
}
