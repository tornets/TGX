/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 14/11/2026
 */
package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;

import androidx.annotation.Nullable;
import me.vkryl.core.lambda.CancellableRunnable;

public class VideoGestureOverlayView extends View {
  private static final int MODE_NONE = 0;
  private static final int MODE_SEEK_STEP = 1;
  private static final int MODE_PLAY_PAUSE = 2;
  private static final int MODE_SPEED = 3;
  private static final int MODE_SEEK_PREVIEW = 4;

  private int mode = MODE_NONE;
  private boolean seekForward;
  private long seekDeltaMs;
  private boolean playing;
  private long previewNowMs, previewTotalMs;
  private @Nullable Bitmap previewBitmap;

  private final Paint fillingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final Path iconPath = new Path();
  private final RectF pillRect = new RectF();
  private final RectF previewRect = new RectF();

  private CancellableRunnable hideRunnable;

  public VideoGestureOverlayView (Context context) {
    super(context);
    setWillNotDraw(false);
    setAlpha(0f);
    setClickable(false);
    setFocusable(false);

    fillingPaint.setColor(0xB3000000);
    textPaint.setColor(0xFFFFFFFF);
    textPaint.setTypeface(Fonts.getRobotoMedium());
    textPaint.setTextSize(Screen.dp(14));
    textPaint.setTextAlign(Paint.Align.CENTER);
  }

  private void cancelHide () {
    if (hideRunnable != null) {
      hideRunnable.cancel();
      hideRunnable = null;
    }
  }

  private void scheduleHide (long delay) {
    cancelHide();
    UI.post(hideRunnable = new CancellableRunnable() {
      @Override
      public void act () {
        hideRunnable = null;
        hide();
      }
    }, delay);
  }

  private void show () {
    cancelHide();
    animate().cancel();
    setAlpha(1f);
    invalidate();
  }

  public void hide () {
    cancelHide();
    animate().alpha(0f).setDuration(180).start();
  }

  public void showSeekStep (boolean forward, long deltaMs) {
    mode = MODE_SEEK_STEP;
    seekForward = forward;
    seekDeltaMs = deltaMs;
    show();
    scheduleHide(700);
  }

  public void showPlayPause (boolean playing) {
    mode = MODE_PLAY_PAUSE;
    this.playing = playing;
    show();
    scheduleHide(500);
  }

  public void showSpeedBoost () {
    mode = MODE_SPEED;
    show();
  }

  public void showSeekPreview (long nowMs, long totalMs) {
    mode = MODE_SEEK_PREVIEW;
    previewNowMs = nowMs;
    previewTotalMs = totalMs;
    show();
  }

  public void setPreviewBitmap (@Nullable Bitmap bitmap) {
    previewBitmap = bitmap;
    if (mode == MODE_SEEK_PREVIEW) {
      invalidate();
    }
  }

  @Override
  protected void onDraw (Canvas canvas) {
    if (mode == MODE_NONE || getAlpha() == 0f) {
      return;
    }
    final int width = getWidth();
    final int height = getHeight();
    switch (mode) {
      case MODE_SEEK_STEP: {
        drawSeekStep(canvas, width, height);
        break;
      }
      case MODE_PLAY_PAUSE: {
        drawPlayPause(canvas, width, height);
        break;
      }
      case MODE_SPEED: {
        drawSpeed(canvas, width);
        break;
      }
      case MODE_SEEK_PREVIEW: {
        drawSeekPreview(canvas, width, height);
        break;
      }
    }
  }

  private void drawSeekStep (Canvas canvas, int width, int height) {
    final float pillWidth = Screen.dp(110);
    final float pillHeight = Screen.dp(44);
    final float centerY = height / 2f;
    final float centerX = seekForward ? width - Screen.dp(24) - pillWidth / 2f : Screen.dp(24) + pillWidth / 2f;

    pillRect.set(centerX - pillWidth / 2f, centerY - pillHeight / 2f, centerX + pillWidth / 2f, centerY + pillHeight / 2f);
    canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, fillingPaint);

    drawSeekIcon(canvas, centerX - Screen.dp(26), centerY, seekForward);
    drawCenteredText(canvas, (seekDeltaMs / 1000) + "s", centerX + Screen.dp(16), centerY);
  }

  private void drawPlayPause (Canvas canvas, int width, int height) {
    final float centerX = width / 2f;
    final float centerY = height / 2f;
    final float radius = Screen.dp(30);

    canvas.drawCircle(centerX, centerY, radius, fillingPaint);

    iconPath.reset();
    if (playing) {
      final float barWidth = Screen.dp(5);
      final float barHeight = Screen.dp(16);
      final float gap = Screen.dp(4);
      iconPath.addRect(centerX - gap - barWidth, centerY - barHeight / 2f, centerX - gap, centerY + barHeight / 2f, Path.Direction.CW);
      iconPath.addRect(centerX + gap, centerY - barHeight / 2f, centerX + gap + barWidth, centerY + barHeight / 2f, Path.Direction.CW);
    } else {
      final float triangleWidth = Screen.dp(14);
      final float triangleHeight = Screen.dp(18);
      iconPath.moveTo(centerX - triangleWidth / 2f + Screen.dp(2), centerY - triangleHeight / 2f);
      iconPath.lineTo(centerX - triangleWidth / 2f + Screen.dp(2), centerY + triangleHeight / 2f);
      iconPath.lineTo(centerX + triangleWidth / 2f + Screen.dp(2), centerY);
      iconPath.close();
    }
    textPaint.setStyle(Paint.Style.FILL);
    canvas.drawPath(iconPath, textPaint);
  }

  private void drawSpeed (Canvas canvas, int width) {
    final String text = getContext().getString(R.string.VideoGestureSpeed);
    final float pillHeight = Screen.dp(36);
    final float pillWidth = textPaint.measureText(text) + Screen.dp(32);
    final float centerX = width / 2f;
    final float top = Screen.dp(72);

    pillRect.set(centerX - pillWidth / 2f, top, centerX + pillWidth / 2f, top + pillHeight);
    canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, fillingPaint);
    drawCenteredText(canvas, text, centerX, top + pillHeight / 2f);
  }

  private void drawSeekPreview (Canvas canvas, int width, int height) {
    final String text = Strings.buildDuration(previewNowMs / 1000) + " / " + Strings.buildDuration(previewTotalMs / 1000);
    final float pillHeight = Screen.dp(40);
    final float pillWidth = textPaint.measureText(text) + Screen.dp(32);
    final float centerX = width / 2f;
    final float centerY = height / 2f;

    final Bitmap bitmap = previewBitmap;
    float pillCenterY = centerY;
    if (bitmap != null && !bitmap.isRecycled()) {
      final float maxWidth = width * 2f / 3f;
      final float maxHeight = height * 2f / 3f;
      float drawScale = Math.min(maxWidth / (float) bitmap.getWidth(), maxHeight / (float) bitmap.getHeight());
      final float drawWidth = bitmap.getWidth() * drawScale;
      final float drawHeight = bitmap.getHeight() * drawScale;
      final float left = centerX - drawWidth / 2f;
      final float top = centerY - drawHeight / 2f;
      previewRect.set(left, top, left + drawWidth, top + drawHeight);
      final int saveCount = canvas.save();
      iconPath.reset();
      iconPath.addRoundRect(previewRect, Screen.dp(8), Screen.dp(8), Path.Direction.CW);
      canvas.clipPath(iconPath);
      canvas.drawBitmap(bitmap, null, previewRect, null);
      canvas.restoreToCount(saveCount);
      pillCenterY = Math.min(height - pillHeight, top + drawHeight + Screen.dp(12) + pillHeight / 2f);
    }

    pillRect.set(centerX - pillWidth / 2f, pillCenterY - pillHeight / 2f, centerX + pillWidth / 2f, pillCenterY + pillHeight / 2f);
    canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, fillingPaint);
    drawCenteredText(canvas, text, centerX, pillCenterY);
  }

  private void drawSeekIcon (Canvas canvas, float centerX, float centerY, boolean forward) {
    final float triangleWidth = Screen.dp(9);
    final float triangleHeight = Screen.dp(12);
    iconPath.reset();
    for (int i = 0; i < 2; i++) {
      final float offset = (i - .5f) * triangleWidth;
      final float x = centerX + (forward ? offset : -offset);
      if (forward) {
        iconPath.moveTo(x - triangleWidth / 2f, centerY - triangleHeight / 2f);
        iconPath.lineTo(x - triangleWidth / 2f, centerY + triangleHeight / 2f);
        iconPath.lineTo(x + triangleWidth / 2f, centerY);
      } else {
        iconPath.moveTo(x + triangleWidth / 2f, centerY - triangleHeight / 2f);
        iconPath.lineTo(x + triangleWidth / 2f, centerY + triangleHeight / 2f);
        iconPath.lineTo(x - triangleWidth / 2f, centerY);
      }
      iconPath.close();
    }
    canvas.drawPath(iconPath, textPaint);
  }

  private void drawCenteredText (Canvas canvas, String text, float centerX, float centerY) {
    final Paint.FontMetrics metrics = textPaint.getFontMetrics();
    canvas.drawText(text, centerX, centerY - (metrics.ascent + metrics.descent) / 2f, textPaint);
  }

}