package com.example.identica;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.core.splashscreen.SplashScreen;

public class SplashActivity extends Activity {

    private static final long SPLASH_DURATION_MS = 3_000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable openMainScreen = () -> {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        handler.postDelayed(openMainScreen, SPLASH_DURATION_MS);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(openMainScreen);
        super.onDestroy();
    }
}
