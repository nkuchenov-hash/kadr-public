package com.kadr.longshottest;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.hardware.HardwareBuffer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.content.ContentValues;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LongScreenshotAccessibilityService extends AccessibilityService {
  private static final int MAX_FRAMES = 24;
  private static final int MAX_OUTPUT_HEIGHT = 60_000;
  private static final long SCROLL_SETTLE_MS = 650L;
  private static volatile LongScreenshotAccessibilityService activeInstance;

  private final Handler main = new Handler(Looper.getMainLooper());
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final List<Bitmap> frames = new ArrayList<>();
  private boolean capturing;

  @Override protected void onServiceConnected() {
    super.onServiceConnected();
    activeInstance = this;
  }

  @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
  @Override public void onInterrupt() {}

  public static boolean requestStart(Context context) {
    LongScreenshotAccessibilityService service = activeInstance;
    if (service == null) return false;
    service.main.post(service::startLongCapture);
    return true;
  }

  private void startLongCapture() {
    if (capturing) {
      toast("KADR: длинный скриншот уже создаётся");
      return;
    }
    capturing = true;
    clearFrames();
    toast("KADR: длинный скриншот");
    captureFrame(0);
  }

  private void captureFrame(int index) {
    if (Build.VERSION.SDK_INT < 30) {
      finishWithError("Нужен Android 11 или новее");
      return;
    }
    takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
      @Override public void onSuccess(ScreenshotResult screenshot) {
        HardwareBuffer buffer = screenshot.getHardwareBuffer();
        Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.getColorSpace());
        if (hardware == null) {
          buffer.close();
          finishWithError("Не удалось получить изображение");
          return;
        }
        Bitmap copy = hardware.copy(Bitmap.Config.ARGB_8888, false);
        buffer.close();
        if (copy == null) {
          finishWithError("Не удалось обработать изображение");
          return;
        }
        if (!frames.isEmpty() && isNearlySame(frames.get(frames.size() - 1), copy)) {
          copy.recycle();
          stitchAndSave();
          return;
        }
        frames.add(copy);
        if (index + 1 >= MAX_FRAMES) {
          stitchAndSave();
          return;
        }
        if (!scrollForward()) {
          stitchAndSave();
          return;
        }
        main.postDelayed(() -> captureFrame(index + 1), SCROLL_SETTLE_MS);
      }
      @Override public void onFailure(int errorCode) {
        finishWithError("Снимок экрана недоступен (" + errorCode + ")");
      }
    });
  }

  private boolean scrollForward() {
    AccessibilityNodeInfo root = getRootInActiveWindow();
    AccessibilityNodeInfo scrollable = findScrollable(root);
    if (scrollable != null) {
      boolean ok = scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
      scrollable.recycle();
      if (root != null) root.recycle();
      if (ok) return true;
    } else if (root != null) {
      root.recycle();
    }
    int width = getResources().getDisplayMetrics().widthPixels;
    int height = getResources().getDisplayMetrics().heightPixels;
    Path path = new Path();
    path.moveTo(width * 0.5f, height * 0.78f);
    path.lineTo(width * 0.5f, height * 0.24f);
    GestureDescription gesture = new GestureDescription.Builder()
        .addStroke(new GestureDescription.StrokeDescription(path, 0, 320))
        .build();
    return dispatchGesture(gesture, null, null);
  }

  private AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo node) {
    if (node == null) return null;
    if (node.isScrollable()) return AccessibilityNodeInfo.obtain(node);
    for (int i = 0; i < node.getChildCount(); i++) {
      AccessibilityNodeInfo child = node.getChild(i);
      AccessibilityNodeInfo found = findScrollable(child);
      if (child != null) child.recycle();
      if (found != null) return found;
    }
    return null;
  }

  private void stitchAndSave() {
    if (frames.isEmpty()) {
      finishWithError("Нет кадров для сохранения");
      return;
    }
    worker.execute(() -> {
      try {
        Bitmap result = stitch(frames);
        Uri uri = savePng(result);
        result.recycle();
        main.post(() -> {
          capturing = false;
          clearFrames();
          toast(uri != null ? "KADR: длинный скриншот сохранён" : "KADR: ошибка сохранения");
        });
      } catch (Throwable t) {
        main.post(() -> finishWithError("Не удалось склеить скриншот"));
      }
    });
  }

  private Bitmap stitch(List<Bitmap> input) {
    Bitmap first = input.get(0);
    int width = first.getWidth();
    List<Integer> overlaps = new ArrayList<>();
    int totalHeight = first.getHeight();
    for (int i = 1; i < input.size(); i++) {
      Bitmap next = input.get(i);
      int overlap = estimateOverlap(input.get(i - 1), next);
      overlaps.add(overlap);
      totalHeight += Math.max(1, next.getHeight() - overlap);
      if (totalHeight >= MAX_OUTPUT_HEIGHT) {
        totalHeight = MAX_OUTPUT_HEIGHT;
        break;
      }
    }
    Bitmap out = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(out);
    canvas.drawBitmap(first, 0, 0, null);
    int y = first.getHeight();
    for (int i = 1; i < input.size() && i - 1 < overlaps.size() && y < totalHeight; i++) {
      Bitmap frame = input.get(i);
      int srcTop = Math.min(overlaps.get(i - 1), frame.getHeight() - 1);
      int remaining = Math.min(frame.getHeight() - srcTop, totalHeight - y);
      if (remaining <= 0) break;
      android.graphics.Rect src = new android.graphics.Rect(0, srcTop, frame.getWidth(), srcTop + remaining);
      android.graphics.Rect dst = new android.graphics.Rect(0, y, width, y + remaining);
      canvas.drawBitmap(frame, src, dst, null);
      y += remaining;
    }
    return out;
  }

  private int estimateOverlap(Bitmap a, Bitmap b) {
    int h = Math.min(a.getHeight(), b.getHeight());
    int w = Math.min(a.getWidth(), b.getWidth());
    int min = Math.max(32, h / 12);
    int max = Math.max(min, (h * 3) / 4);
    int best = min;
    double bestScore = Double.MAX_VALUE;
    int sample = Math.max(8, w / 120);
    int yStep = Math.max(8, h / 180);
    for (int overlap = min; overlap <= max; overlap += yStep) {
      long diff = 0;
      long count = 0;
      int rows = Math.min(overlap, 240);
      int rowStart = Math.max(0, overlap - rows);
      for (int y = rowStart; y < overlap; y += yStep) {
        int ay = a.getHeight() - overlap + y;
        for (int x = 0; x < w; x += sample) {
          int ca = a.getPixel(x, ay);
          int cb = b.getPixel(x, y);
          diff += Math.abs(android.graphics.Color.red(ca) - android.graphics.Color.red(cb));
          diff += Math.abs(android.graphics.Color.green(ca) - android.graphics.Color.green(cb));
          diff += Math.abs(android.graphics.Color.blue(ca) - android.graphics.Color.blue(cb));
          count += 3;
        }
      }
      if (count > 0) {
        double score = diff / (double) count;
        if (score < bestScore) {
          bestScore = score;
          best = overlap;
        }
      }
    }
    return best;
  }

  private boolean isNearlySame(Bitmap a, Bitmap b) {
    if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;
    int stepX = Math.max(12, a.getWidth() / 80);
    int stepY = Math.max(12, a.getHeight() / 120);
    long diff = 0;
    long count = 0;
    for (int y = 0; y < a.getHeight(); y += stepY) {
      for (int x = 0; x < a.getWidth(); x += stepX) {
        int ca = a.getPixel(x, y);
        int cb = b.getPixel(x, y);
        diff += Math.abs(android.graphics.Color.red(ca) - android.graphics.Color.red(cb));
        diff += Math.abs(android.graphics.Color.green(ca) - android.graphics.Color.green(cb));
        diff += Math.abs(android.graphics.Color.blue(ca) - android.graphics.Color.blue(cb));
        count += 3;
      }
    }
    return count > 0 && diff / (double) count < 2.0;
  }

  private Uri savePng(Bitmap bitmap) throws Exception {
    ContentValues values = new ContentValues();
    String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    values.put(MediaStore.Images.Media.DISPLAY_NAME, "KADR_LONG_" + stamp + ".png");
    values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
    if (Build.VERSION.SDK_INT >= 29) {
      values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KADR");
      values.put(MediaStore.Images.Media.IS_PENDING, 1);
    }
    Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    if (uri == null) return null;
    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
      if (out == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
        getContentResolver().delete(uri, null, null);
        return null;
      }
    }
    if (Build.VERSION.SDK_INT >= 29) {
      ContentValues done = new ContentValues();
      done.put(MediaStore.Images.Media.IS_PENDING, 0);
      getContentResolver().update(uri, done, null, null);
    }
    return uri;
  }

  private void finishWithError(String text) {
    capturing = false;
    clearFrames();
    toast("KADR: " + text);
  }

  private void clearFrames() {
    for (Bitmap frame : frames) if (frame != null && !frame.isRecycled()) frame.recycle();
    frames.clear();
  }

  private void toast(String text) {
    main.post(() -> Toast.makeText(this, text, Toast.LENGTH_SHORT).show());
  }

  @Override public void onDestroy() {
    if (activeInstance == this) activeInstance = null;
    clearFrames();
    worker.shutdownNow();
    main.removeCallbacksAndMessages(null);
    super.onDestroy();
  }
}
