/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.philoniare.sunshinewear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
  private static final Typeface NORMAL_TYPEFACE =
          Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

  /**
   * Update rate in milliseconds for interactive mode. We update once a second since seconds are
   * displayed in interactive mode.
   */
  private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

  /**
   * Handler message id for updating the time periodically in interactive mode.
   */
  private static final int MSG_UPDATE_TIME = 0;

  private static final String LOG_TAG = SunshineWatchFace.class.getSimpleName();
  @Override
  public Engine onCreateEngine() {
    return new Engine();
  }

  private static class EngineHandler extends Handler {
    private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

    public EngineHandler(SunshineWatchFace.Engine reference) {
      mWeakReference = new WeakReference<>(reference);
    }

    @Override
    public void handleMessage(Message msg) {
      SunshineWatchFace.Engine engine = mWeakReference.get();
      if (engine != null) {
        switch (msg.what) {
          case MSG_UPDATE_TIME:
            engine.handleUpdateTimeMessage();
            break;
        }
      }
    }
  }

  private class Engine extends CanvasWatchFaceService.Engine {
    final Handler mUpdateTimeHandler = new EngineHandler(this);
    boolean mRegisteredTimeZoneReceiver = false;
    Paint mBackgroundPaint;
    Paint mTextPaintTime;
    Paint mTextPaintDate;
    boolean mAmbient;
    Calendar mCalendar;
    final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        mCalendar.setTimeZone(TimeZone.getDefault());
        invalidate();
      }
    };
    float mXOffset;
    float mYOffsetTime;
    float mYOffsetDate;

    /**
     * Whether the display supports fewer bits for each color in ambient mode. When true, we
     * disable anti-aliasing in ambient mode.
     */
    boolean mLowBitAmbient;

    @Override
    public void onCreate(SurfaceHolder holder) {
      super.onCreate(holder);

      setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
              .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
              .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
              .setShowSystemUiTime(false)
              .setAcceptsTapEvents(true)
              .build());
      Resources resources = SunshineWatchFace.this.getResources();
      mYOffsetTime = resources.getDimension(R.dimen.digital_y_offset_time);
      mYOffsetDate = resources.getDimension(R.dimen.digital_y_offset_date);

      mBackgroundPaint = new Paint();
      mBackgroundPaint.setColor(resources.getColor(R.color.background));

      mTextPaintTime = new Paint();
      mTextPaintTime = createTextPaint(resources.getColor(R.color.digital_text_time));
      mTextPaintDate = new Paint();
      mTextPaintDate = createTextPaint(resources.getColor(R.color.digital_text_date));
      mCalendar = Calendar.getInstance();
    }

    @Override
    public void onDestroy() {
      mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
      super.onDestroy();
    }

    private Paint createTextPaint(int textColor) {
      Paint paint = new Paint();
      paint.setColor(textColor);
      paint.setTypeface(NORMAL_TYPEFACE);
      paint.setAntiAlias(true);
      return paint;
    }

    @Override
    public void onVisibilityChanged(boolean visible) {
      super.onVisibilityChanged(visible);

      if (visible) {
        registerReceiver();

        // Update time zone in case it changed while we weren't visible.
        mCalendar.setTimeZone(TimeZone.getDefault());
        invalidate();
      } else {
        unregisterReceiver();
      }

      // Whether the timer should be running depends on whether we're visible (as well as
      // whether we're in ambient mode), so we may need to start or stop the timer.
      updateTimer();
    }

    private void registerReceiver() {
      if (mRegisteredTimeZoneReceiver) {
        return;
      }
      mRegisteredTimeZoneReceiver = true;
      IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
      SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
    }

    private void unregisterReceiver() {
      if (!mRegisteredTimeZoneReceiver) {
        return;
      }
      mRegisteredTimeZoneReceiver = false;
      SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
    }

    @Override
    public void onApplyWindowInsets(WindowInsets insets) {
      super.onApplyWindowInsets(insets);

      // Load resources that have alternate values for round watches.
      Resources resources = SunshineWatchFace.this.getResources();
      boolean isRound = insets.isRound();
      mXOffset = resources.getDimension(isRound
              ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
      float textSizeTime = resources.getDimension(isRound
              ? R.dimen.digital_text_size_time : R.dimen.digital_text_size_round_time);
      float textSizeDate = resources.getDimension(isRound
              ? R.dimen.digital_text_size_date : R.dimen.digital_text_size_round_date);
      mTextPaintTime.setTextSize(textSizeTime);
      mTextPaintDate.setTextSize(textSizeDate);
    }

    @Override
    public void onPropertiesChanged(Bundle properties) {
      super.onPropertiesChanged(properties);
      mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
    }

    @Override
    public void onTimeTick() {
      super.onTimeTick();
      invalidate();
    }

    @Override
    public void onAmbientModeChanged(boolean inAmbientMode) {
      super.onAmbientModeChanged(inAmbientMode);
      if (mAmbient != inAmbientMode) {
        mAmbient = inAmbientMode;
        if (mLowBitAmbient) {
          mTextPaintTime.setAntiAlias(!inAmbientMode);
          mTextPaintDate.setAntiAlias(!inAmbientMode);
        }
        invalidate();
      }

      // Whether the timer should be running depends on whether we're visible (as well as
      // whether we're in ambient mode), so we may need to start or stop the timer.
      updateTimer();
    }

    /**
     * Captures tap event (and tap type) and toggles the background color if the user finishes
     * a tap.
     */
    @Override
    public void onTapCommand(int tapType, int x, int y, long eventTime) {
      switch (tapType) {
        case TAP_TYPE_TOUCH:
          // The user has started touching the screen.
          break;
        case TAP_TYPE_TOUCH_CANCEL:
          // The user has started a different gesture or otherwise cancelled the tap.
          break;
        case TAP_TYPE_TAP:
          // The user has completed the tap gesture.
          // TODO: Add code to handle the tap gesture.
          Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                  .show();
          break;
      }
      invalidate();
    }

    @Override
    public void onDraw(Canvas canvas, Rect bounds) {
      // Draw the background.
      if (isInAmbientMode()) {
        canvas.drawColor(Color.BLACK);
      } else {
        canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
      }

      // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
      long now = System.currentTimeMillis();
      mCalendar.setTimeInMillis(now);

      // Set time and date
      String textTime = String.format("%d:%02d", mCalendar.get(Calendar.HOUR),
              mCalendar.get(Calendar.MINUTE));
      String textDate= new SimpleDateFormat("EEE, MMM d yyyy").format(mCalendar.getTime());
      canvas.drawText(textTime, mXOffset, mYOffsetTime, mTextPaintTime);
      canvas.drawText(textDate, mXOffset, mYOffsetDate, mTextPaintDate);

      // Fetch the weather
    }

    /**
     * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
     * or stops it if it shouldn't be running but currently is.
     */
    private void updateTimer() {
      mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
      if (shouldTimerBeRunning()) {
        mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
      }
    }

    /**
     * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
     * only run when we're visible and in interactive mode.
     */
    private boolean shouldTimerBeRunning() {
      return isVisible() && !isInAmbientMode();
    }

    /**
     * Handle updating the time periodically in interactive mode.
     */
    private void handleUpdateTimeMessage() {
      invalidate();
      if (shouldTimerBeRunning()) {
        long timeMs = System.currentTimeMillis();
        long delayMs = INTERACTIVE_UPDATE_RATE_MS
                - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
        mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
      }
    }
  }
}
