package com.blackfalcon.gpstracker;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SplashActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.BLACK);

        // Line 1: Main Title
        TextView line1 = new TextView(this);
        line1.setText("GPS-Tracker");
        line1.setTextColor(Color.WHITE);
        line1.setTextSize(50);
        line1.setGravity(Gravity.CENTER);

        // Line 2: Subtitle label (Added top padding to create space from Line 1)
        TextView line2 = new TextView(this);
        line2.setText("Brought to you By:");
        line2.setTextColor(Color.WHITE);
        line2.setTextSize(22);
        line2.setGravity(Gravity.CENTER);
        line2.setPadding(0, 80, 0, 0); // <-- Adjust the '80' value to make the gap larger or smaller

        // Line 3: Creator name
        TextView line3 = new TextView(this);
        line3.setText("Black Falcon \uD83E\uDD85");
        line3.setTextColor(Color.WHITE);
        line3.setTextSize(40);
        line3.setGravity(Gravity.CENTER);
        line3.setPadding(0, 24, 0, 0);

        // Add all views to the root layout in order
        root.addView(line1);
        root.addView(line2);
        root.addView(line3);
        
        setContentView(root);

        // Delay to transition to MainActivity
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        }, 2200);
    }
}
