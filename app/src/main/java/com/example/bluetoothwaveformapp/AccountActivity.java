package com.example.bluetoothwaveformapp;

import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import java.util.Calendar;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

public class AccountActivity extends AppCompatActivity {

    private EditText editTextName, editTextEmail, editTextPhone, editTextDob;
    private Button btnEditProfile;
    private boolean isEditing = false;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.accounts);

        // Enable Back Button
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        sharedPreferences = getSharedPreferences("UserProfile", MODE_PRIVATE);

        editTextName = findViewById(R.id.editTextName);
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPhone = findViewById(R.id.editTextPhone);
        editTextDob = findViewById(R.id.editTextDob);
        btnEditProfile = findViewById(R.id.btnEditProfile);

        // Load saved data
        loadProfileData();

        // Disable fields initially
        setEditable(false);

        btnEditProfile.setOnClickListener(v -> {
            if (isEditing) {
                saveProfile();
            } else {
                setEditable(true);
            }
        });

        // Open Date Picker when clicking on DOB field
        editTextDob.setOnClickListener(view -> {
            if (isEditing) {
                showDatePicker();
            }
        });
    }

    private void setEditable(boolean enabled) {
        editTextName.setEnabled(enabled);
        editTextEmail.setEnabled(enabled);
        editTextPhone.setEnabled(enabled);
        editTextDob.setEnabled(enabled);
        btnEditProfile.setText(enabled ? "Save" : "Edit Profile");
        isEditing = enabled;
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                android.R.style.Theme_Holo_Light_Dialog, // Light-themed DatePicker
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    String selectedDate = selectedDay + "/" + (selectedMonth + 1) + "/" + selectedYear;
                    editTextDob.setText(selectedDate);
                },
                year, month, day
        );
        datePickerDialog.getWindow().setBackgroundDrawableResource(android.R.color.white); // Ensure white background
        datePickerDialog.show();
    }

    private void saveProfile() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("Name", editTextName.getText().toString());
        editor.putString("Email", editTextEmail.getText().toString());
        editor.putString("Phone", editTextPhone.getText().toString());
        editor.putString("DOB", editTextDob.getText().toString());
        editor.apply(); // Save changes

        Toast.makeText(this, "Profile Updated!", Toast.LENGTH_SHORT).show();
        setEditable(false);
    }

    private void loadProfileData() {
        editTextName.setText(sharedPreferences.getString("Name", ""));
        editTextEmail.setText(sharedPreferences.getString("Email", ""));
        editTextPhone.setText(sharedPreferences.getString("Phone", ""));
        editTextDob.setText(sharedPreferences.getString("DOB", ""));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish(); // Go back to previous screen without erasing data
        return true;
    }
}
