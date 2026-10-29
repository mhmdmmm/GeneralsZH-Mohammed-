package com.generalsx.zerohour;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.graphics.Color;
import android.view.Gravity;

public class ModsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.BLACK);
        layout.setGravity(Gravity.CENTER);
        
        TextView text = new TextView(this);
        text.setText("Mods Manager\n\n(Place your .big mod files here)");
        text.setTextColor(Color.WHITE);
        text.setTextSize(20);
        text.setGravity(Gravity.CENTER);
        
        layout.addView(text);
        setContentView(layout);
    }
}
