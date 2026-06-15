package com.example.smt;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    private static final long SPLASH_DELAY_MS = 1800L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        View brandingLayout = findViewById(R.id.splashBrandingLayout);
        brandingLayout.setAlpha(0f);
        brandingLayout.setTranslationY(24f);
        brandingLayout.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(800L)
                .setStartDelay(300L)
                .start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        }, SPLASH_DELAY_MS);
    }
}
