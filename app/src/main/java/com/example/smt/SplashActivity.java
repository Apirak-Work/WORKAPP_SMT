package com.example.smt;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    private static final long SPLASH_DELAY_MS = 1800L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        TextView tvAppVersion = findViewById(R.id.tv_app_version);
        tvAppVersion.setText("v" + BuildConfig.VERSION_NAME);

        View brandingGroup = findViewById(R.id.splashBrandingGroup);
        brandingGroup.setAlpha(0f);
        brandingGroup.setTranslationY(24f);
        brandingGroup.animate()
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
