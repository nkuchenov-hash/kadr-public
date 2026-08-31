package com.kadr.longshottest;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
  private static final int REQ_MEDIA = 71;

  @Override protected void onCreate(Bundle state) {
    super.onCreate(state);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setGravity(Gravity.CENTER_HORIZONTAL);
    root.setPadding(48, 64, 48, 48);

    TextView title = new TextView(this);
    title.setText("KADR — безопасный тест longshot");
    title.setTextSize(22f);
    root.addView(title);

    TextView body = new TextView(this);
    body.setText(
        "Этот вариант НЕ перехватывает жесты и НЕ включает Touch Exploration.\n\n"
        + "1. Дай доступ к изображениям.\n"
        + "2. Разреши KADR показывать кнопку поверх приложений.\n"
        + "3. Включи сервис KADR в специальных возможностях — он используется только для screenshot/scroll.\n"
        + "4. Нажми «Запустить ожидание».\n\n"
        + "После этого сделай обычный системный скриншот тремя пальцами. Справа появится «↧ ДЛИННЫЙ» на 4 секунды.");
    body.setTextSize(16f);
    body.setPadding(0, 32, 0, 28);
    root.addView(body);

    Button media = new Button(this);
    media.setText("1 · ДОСТУП К ИЗОБРАЖЕНИЯМ");
    media.setOnClickListener(v -> requestMedia());
    root.addView(media);

    Button overlay = new Button(this);
    overlay.setText("2 · КНОПКА ПОВЕРХ ПРИЛОЖЕНИЙ");
    overlay.setOnClickListener(v -> startActivity(new Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:" + getPackageName()))));
    root.addView(overlay);

    Button accessibility = new Button(this);
    accessibility.setText("3 · ДОСТУП ДЛЯ SCROLL / SCREENSHOT");
    accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    root.addView(accessibility);

    Button start = new Button(this);
    start.setText("4 · ЗАПУСТИТЬ ОЖИДАНИЕ");
    start.setOnClickListener(v -> ScreenshotTriggerService.start(this));
    root.addView(start);

    setContentView(root);
  }

  private void requestMedia() {
    if (Build.VERSION.SDK_INT >= 33) {
      requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.POST_NOTIFICATIONS}, REQ_MEDIA);
    } else {
      requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_MEDIA);
    }
  }
}
