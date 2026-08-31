package com.kadr.longshottest;

import android.accessibilityservice.AccessibilityGestureEvent;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.hardware.HardwareBuffer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
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
  private static final int MAX_FRAMES = 16;
  private static final int MAX_HEIGHT = 48000;
  private static final long SETTLE_MS = 600L;

  private final Handler main = new Handler(Looper.getMainLooper());
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final List<Bitmap> frames = new ArrayList<>();
  private boolean capturing;

  @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
  @Override public void onInterrupt() {}

  @Override public boolean onGesture(AccessibilityGestureEvent event) {
    if (Build.VERSION.SDK_INT < 30) return false;
    if (event.getGestureId() != GESTURE_3_FINGER_SWIPE_DOWN) return false;
    if (capturing) {
      toast("KADR: уже снимаю");
      return true;
    }
    capturing = true;
    clearFrames();
    toast("KADR: длинный скриншот");
    capture(0);
    return true;
  }

  private void capture(int index) {
    takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
      @Override public void onSuccess(ScreenshotResult shot) {
        HardwareBuffer buffer = shot.getHardwareBuffer();
        Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, shot.getColorSpace());
        if (hardware == null) {
          buffer.close();
          fail("не удалось получить кадр");
          return;
        }
        Bitmap copy = hardware.copy(Bitmap.Config.ARGB_8888, false);
        buffer.close();
        if (copy == null) {
          fail("не удалось обработать кадр");
          return;
        }

        if (!frames.isEmpty() && nearlySame(frames.get(frames.size() - 1), copy)) {
          copy.recycle();
          finishCapture();
          return;
        }
        frames.add(copy);

        if (index + 1 >= MAX_FRAMES || !scrollForward()) {
          finishCapture();
          return;
        }
        main.postDelayed(() -> capture(index + 1), SETTLE_MS);
      }

      @Override public void onFailure(int errorCode) {
        fail("снимок недоступен: " + errorCode);
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

    int w = getResources().getDisplayMetrics().widthPixels;
    int h = getResources().getDisplayMetrics().heightPixels;
    Path p = new Path();
    p.moveTo(w * 0.5f, h * 0.78f);
    p.lineTo(w * 0.5f, h * 0.24f);
    GestureDescription g = new GestureDescription.Builder()
        .addStroke(new GestureDescription.StrokeDescription(p, 0, 320))
        .build();
    return dispatchGesture(g, null, null);
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

  private void finishCapture() {
    if (frames.isEmpty()) {
      fail("нет кадров");
      return;
    }
    worker.execute(() -> {
      try {
        Bitmap stitched = stitch();
        Uri uri = save(stitched);
        stitched.recycle();
        main.post(() -> {
          capturing = false;
          clearFrames();
          toast(uri != null ? "KADR: сохранено в Pictures/KADR" : "KADR: ошибка сохранения");
        });
      } catch (Throwable t) {
        main.post(() -> fail("ошибка склейки"));
      }
    });
  }

  private Bitmap stitch() {
    Bitmap first = frames.get(0);
    int width = first.getWidth();
    List<Integer> overlaps = new ArrayList<>();
    int total = first.getHeight();
    for (int i = 1; i < frames.size(); i++) {
      int overlap = estimateOverlap(frames.get(i - 1), frames.get(i));
      overlaps.add(overlap);
      total += Math.max(1, frames.get(i).getHeight() - overlap);
      if (total >= MAX_HEIGHT) { total = MAX_HEIGHT; break; }
    }

    Bitmap out = Bitmap.createBitmap(width, total, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(out);
    canvas.drawBitmap(first, 0, 0, null);
    int y = first.getHeight();
    for (int i = 1; i < frames.size() && i - 1 < overlaps.size() && y < total; i++) {
      Bitmap frame = frames.get(i);
      int top = Math.min(overlaps.get(i - 1), frame.getHeight() - 1);
      int remaining = Math.min(frame.getHeight() - top, total - y);
      if (remaining <= 0) break;
      android.graphics.Rect src = new android.graphics.Rect(0, top, frame.getWidth(), top + remaining);
      android.graphics.Rect dst = new android.graphics.Rect(0, y, width, y + remaining);
      canvas.drawBitmap(frame, src, dst, null);
      y += remaining;
    }
    return out;
  }

  private int estimateOverlap(Bitmap a, Bitmap b) {
    int h = Math.min(a.getHeight(), b.getHeight());
    int w = Math.min(a.getWidth(), b.getWidth());
    int min = Math.max(48, h / 10);
    int max = Math.max(min, h * 3 / 4);
    int stepY = Math.max(12, h / 140);
    int stepX = Math.max(12, w / 80);
    int best = min;
    double bestScore = Double.MAX_VALUE;

    for (int overlap = min; overlap <= max; overlap += stepY) {
      long diff = 0;
      long count = 0;
      int rows = Math.min(overlap, 220);
      for (int y = overlap - rows; y < overlap; y += stepY) {
        int ay = a.getHeight() - overlap + y;
        int by = y;
        for (int x = 0; x < w; x += stepX) {
          int ca = a.getPixel(x, ay);
          int cb = b.getPixel(x, by);
          diff += Math.abs(android.graphics.Color.red(ca) - android.graphics.Color.red(cb));
          diff += Math.abs(android.graphics.Color.green(ca) - android.graphics.Color.green(cb));
          diff += Math.abs(android.graphics.Color.blue(ca) - android.graphics.Color.blue(cb));
          count += 3;
        }
      }
      if (count > 0) {
        double score = diff / (double) count;
        if (score < bestScore) { bestScore = score; best = overlap; }
      }
    }
    return best;
  }

  private boolean nearlySame(Bitmap a, Bitmap b) {
    if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;
    int sx = Math.max(16, a.getWidth() / 60);
    int sy = Math.max(16, a.getHeight() / 90);
    long diff = 0;
    long count = 0;
    for (int y = 0; y < a.getHeight(); y += sy) {
      for (int x = 0; x < a.getWidth(); x += sx) {
        int ca = a.getPixel(x, y), cb = b.getPixel(x, y);
        diff += Math.abs(android.graphics.Color.red(ca) - android.graphics.Color.red(cb));
        diff += Math.abs(android.graphics.Color.green(ca) - android.graphics.Color.green(cb));
        diff += Math.abs(android.graphics.Color.blue(ca) - android.graphics.Color.blue(cb));
        count += 3;
      }
    }
    return count > 0 && diff / (double) count < 2.0;
  }

  private Uri save(Bitmap bitmap) throws Exception {
    ContentValues values = new ContentValues();
    values.put(MediaStore.Images.Media.DISPLAY_NAME,
        "KADR_LONG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".png");
    values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
    values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KADR");
    values.put(MediaStore.Images.Media.IS_PENDING, 1);
    Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    if (uri == null) return null;
    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
      if (out == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return null;
    }
    ContentValues done = new ContentValues();
    done.put(MediaStore.Images.Media.IS_PENDING, 0);
    getContentResolver().update(uri, done, null, null);
    return uri;
  }

  private void fail(String text) {
    capturing = false;
    clearFrames();
    toast("KADR: " + text);
  }

  private void toast(String text) {
    main.post(() -> Toast.makeText(this, text, Toast.LENGTH_SHORT).show());
  }

  private void clearFrames() {
    for (Bitmap b : frames) if (b != null && !b.isRecycled()) b.recycle();
    frames.clear();
  }

  @Override public void onDestroy() {
    clearFrames();
    worker.shutdownNow();
    main.removeCallbacksAndMessages(null);
    super.onDestroy();
  }
}
