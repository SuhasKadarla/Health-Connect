package com.example.bluetoothwaveformapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Get Started button
        Button getStartedButton = findViewById(R.id.getStartedButton);

        // Set OnClickListener
        getStartedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Apply the scale animation
                Animation scaleAnimation = AnimationUtils.loadAnimation(SplashActivity.this, R.anim.button_click_animation);
                v.startAnimation(scaleAnimation);

                // Delay navigation slightly to allow animation to complete
                v.postDelayed(() -> {
                    Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish(); // Finish SplashActivity to prevent returning to it
                }, 150); // Delay slightly longer than animation duration (100ms)
            }
        });
    }
}
