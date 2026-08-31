package com.kadr.longshottest;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public final class ScreenshotTriggerService extends Service {
  private static final String CHANNEL = "kadr_longshot_trigger";
  private static final int NOTIFICATION_ID = 8041;
  private final Handler main = new Handler(Looper.getMainLooper());
  private ContentObserver observer;
  private String lastUri = "";
  private long startedAtSeconds;
  private WindowManager windowManager;
  private TextView prompt;

  @Override public void onCreate() {
    super.onCreate();
    startedAtSeconds = System.currentTimeMillis() / 1000L;
    createChannel();
    startForeground(NOTIFICATION_ID, new Notification.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setContentTitle("KADR Longshot")
        .setContentText("Ожидает обычный системный скриншот")
        .setOngoing(true)
        .build());
    observer = new ContentObserver(main) {
      @Override public void onChange(boolean selfChange, Uri uri) { scheduleCheck(); }
      @Override public void onChange(boolean selfChange) { scheduleCheck(); }
    };
    getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer);
  }

  private void scheduleCheck() {
    main.removeCallbacks(checkLatest);
    main.postDelayed(checkLatest, 450L);
  }

  private final Runnable checkLatest = () -> {
    Uri uri = newestScreenshotSince(startedAtSeconds);
    if (uri == null) return;
    String value = uri.toString();
    if (value.equals(lastUri)) return;
    lastUri = value;
    showPrompt();
  };

  private Uri newestScreenshotSince(long sinceSeconds) {
    ContentResolver resolver = getContentResolver();
    String[] projection = {
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.RELATIVE_PATH,
        MediaStore.Images.Media.DATE_ADDED
    };
    String selection = MediaStore.Images.Media.DATE_ADDED + ">=?";
    String[] args = { String.valueOf(sinceSeconds) };
    try (Cursor c = resolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        args,
        MediaStore.Images.Media.DATE_ADDED + " DESC")) {
      if (c == null) return null;
      int idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
      int nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
      int pathCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH);
      while (c.moveToNext()) {
        String name = c.getString(nameCol);
        String path = c.getString(pathCol);
        if (!isScreenshot(name, path)) continue;
        long id = c.getLong(idCol);
        return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
      }
    } catch (Throwable ignored) {}
    return null;
  }

  private static boolean isScreenshot(String name, String path) {
    String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
    String p = path == null ? "" : path.toLowerCase(Locale.ROOT);
    return n.contains("screenshot") || n.contains("screen_shot") || n.contains("screencapture")
        || p.contains("screenshot") || p.contains("screenshots");
  }

  private void showPrompt() {
    if (!Settings.canDrawOverlays(this)) return;
    hidePrompt();
    windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    if (windowManager == null) return;

    TextView view = new TextView(this);
    view.setText("↧  ДЛИННЫЙ");
    view.setTextSize(17f);
    view.setTextColor(0xffffffff);
    view.setGravity(Gravity.CENTER);
    int hp = dp(18), vp = dp(11);
    view.setPadding(hp, vp, hp, vp);
    GradientDrawable background = new GradientDrawable();
    background.setColor(0xffb3261e);
    background.setCornerRadius(dp(10));
    view.setBackground(background);
    view.setElevation(dp(8));

    WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT);
    lp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
    lp.x = dp(12);

    view.setOnClickListener(v -> {
      hidePrompt();
      if (!LongScreenshotAccessibilityService.requestStart(this)) {
        Toast.makeText(this, "KADR: сервис длинного скриншота не включён", Toast.LENGTH_LONG).show();
      }
    });

    try {
      windowManager.addView(view, lp);
      prompt = view;
      main.postDelayed(this::hidePrompt, 4000L);
    } catch (Throwable ignored) {}
  }

  private void hidePrompt() {
    if (prompt != null && windowManager != null) {
      try { windowManager.removeView(prompt); } catch (Throwable ignored) {}
    }
    prompt = null;
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private void createChannel() {
    NotificationManager nm = getSystemService(NotificationManager.class);
    if (nm != null) nm.createNotificationChannel(new NotificationChannel(
        CHANNEL, "KADR Longshot", NotificationManager.IMPORTANCE_LOW));
  }

  public static void start(Context context) {
    Intent i = new Intent(context, ScreenshotTriggerService.class);
    if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(i);
    else context.startService(i);
  }

  @Override public void onDestroy() {
    hidePrompt();
    if (observer != null) getContentResolver().unregisterContentObserver(observer);
    main.removeCallbacksAndMessages(null);
    super.onDestroy();
  }

  @Override public IBinder onBind(Intent intent) { return null; }
}
