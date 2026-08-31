package com.kadr.longshottest;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
  @Override protected void onCreate(Bundle state) {
    super.onCreate(state);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setGravity(Gravity.CENTER);
    root.setPadding(48, 48, 48, 48);

    TextView title = new TextView(this);
    title.setText("KADR — тест длинного скриншота");
    title.setTextSize(22f);
    title.setGravity(Gravity.CENTER);
    root.addView(title);

    TextView body = new TextView(this);
    body.setText("1. Включи KADR Longshot Test в специальных возможностях.\n\n2. Открой приложение с длинной лентой.\n\n3. Проведи тремя пальцами вниз.\n\nKADR сам прокрутит экран, склеит кадры и сохранит PNG в Pictures/KADR.");
    body.setTextSize(17f);
    body.setPadding(0, 36, 0, 36);
    root.addView(body);

    Button open = new Button(this);
    open.setText("ОТКРЫТЬ СПЕЦ. ВОЗМОЖНОСТИ");
    open.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    root.addView(open);

    setContentView(root);
  }
}
