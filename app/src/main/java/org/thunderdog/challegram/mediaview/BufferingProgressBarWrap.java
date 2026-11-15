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
 */
package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.ProgressComponent;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.util.SingleViewProvider;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

public class BufferingProgressBarWrap extends View implements Destroyable {
  private final RectF fakeDialogFrame = new RectF();
  private final TextPaint speedTextPaint;
  private @Nullable String speedText;
  private ProgressComponent progressComponent;

  private final BoolAnimator progressVisible = new BoolAnimator(0, (id, factor, fraction, callee) -> {
    progressComponent.setAlpha(factor);
  }, AnimatorUtils.ACCELERATE_DECELERATE_INTERPOLATOR, 210L);

  private Runnable progressDelayRunnable;

  public BufferingProgressBarWrap (@NonNull Context context) {
    super(context);
    setWillNotDraw(false);
    speedTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    speedTextPaint.setColor(Color.WHITE);
    speedTextPaint.setTypeface(Fonts.getRobotoMedium());
    speedTextPaint.setTextSize(Screen.dp(12f));
    speedTextPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
    progressComponent = new ProgressComponent(UI.getContext(getContext()), Screen.dp(18f));
    progressComponent.forceColor(Color.WHITE);
    progressComponent.setUseStupidInvalidate();
    progressComponent.setUseLargerPaint(Screen.dp(4f));
    progressComponent.setViewProvider(new SingleViewProvider(this));
    progressComponent.setAlpha(0f);
    progressComponent.attachToView(this);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    int cx = getMeasuredWidth() / 2;
    int cy = getMeasuredHeight() / 2;
    int dSize = Screen.dp(42f);

    progressComponent.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
    fakeDialogFrame.set(cx - dSize, cy - dSize, cx + dSize, cy + dSize);
  }

  @Override
  public void performDestroy () {
    progressComponent.detachFromView(this);
    progressComponent.performDestroy();
  }

  public void setProgressVisibleInstant (boolean value) {
    if (progressDelayRunnable != null) {
      removeCallbacks(progressDelayRunnable);
      progressDelayRunnable = null;
    }

    progressVisible.setValue(value, false);
  }

  public void setProgressVisible (boolean value, boolean delay) {
    if (value) {
      if (!delay) {
        progressVisible.setValue(true, true);
      } else if (progressDelayRunnable == null) {
        progressDelayRunnable = () -> {
          progressVisible.setValue(true, true);
          progressDelayRunnable = null;
        };

        postDelayed(progressDelayRunnable, 350L);
      }
    } else {
      if (progressDelayRunnable != null) {
        removeCallbacks(progressDelayRunnable);
        progressDelayRunnable = null;
      }

      progressVisible.setValue(false, true);
    }
  }

  public void setSpeedText (@Nullable String text) {
    if (!java.util.Objects.equals(speedText, text)) {
      speedText = text;
      invalidate();
    }
  }

  @Override
  protected void onDraw (Canvas canvas) {
    super.onDraw(canvas);

    final int color = 0x4c000000;
    final float alpha = progressVisible.getFloatValue();
    final int bgColor = ColorUtils.color((int) ((float) Color.alpha(color) * alpha), color);

    canvas.drawRoundRect(fakeDialogFrame, Screen.dp(12f), Screen.dp(12f), Paints.getPorterDuffPaint(bgColor));
    progressComponent.draw(canvas);
    if (speedText != null && alpha > 0.1f) {
      speedTextPaint.setAlpha((int) (255f * alpha));
      final int cx = getMeasuredWidth() / 2;
      final float baseY = getMeasuredHeight() / 2f + Screen.dp(42f) + Screen.dp(20f);
      canvas.drawText(speedText, cx, baseY, speedTextPaint);
    }
  }
}
