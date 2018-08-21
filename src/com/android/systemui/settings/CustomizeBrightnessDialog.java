/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.systemui.settings;

import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.util.Log;
import android.os.Message;
import android.app.Dialog;
import android.content.Context;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.R;

/** A dialog that provides controls for adjusting the screen brightness. */
public class CustomizeBrightnessDialog extends Dialog {
	private static final String TAG = "CustomizeBrightnessDialog";
    private BrightnessController mBrightnessController;
	private Context mContext;

	private boolean dismiss = false;
	private boolean isDismiss = false;
	
	private Handler mHander = new Handler(){
		public void handleMessage(Message m) {
			Log.d(TAG, " handleMessage:what :" + m.what);
			super.handleMessage(m);
			switch (m.what) {
				case 1:
				dismiss = false;
				break;
			}
		}
	};
	private Runnable r = new Runnable(){
			public void run(){
				Log.d(TAG, " run:dismiss " + dismiss);
				if(dismiss){
					dismiss();
					isDismiss = true;
				}else{
					isDismiss = false;
				}
				dismiss = true;	
				//msg1.what = 1;
				//mHander.sendMessage(msg1);
				if(!isDismiss){
					mHander.postDelayed(r,3000);
				}
			}
		};
	public CustomizeBrightnessDialog(Context context) {
        super(context);
		mContext = context;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate ");

        final Window window = getWindow();

        window.setGravity(Gravity.CENTER);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.requestFeature(Window.FEATURE_NO_TITLE);
		window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
		/*
		window.addFlags( WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
				
				| WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
				| WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
				| WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
		*/

		
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);

        final WindowManager.LayoutParams lp = window.getAttributes();
        lp.type = WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY;
        lp.format = PixelFormat.TRANSLUCENT;
        lp.gravity = Gravity.CENTER;
        lp.windowAnimations = -1;
        window.setAttributes(lp);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
		
        setContentView(R.layout.quick_settings_brightness_dialog);
		//setContentView(R.layout.volume_dialog);

        final ImageView icon = (ImageView) findViewById(R.id.brightness_icon);
        final ToggleSlider slider = (ToggleSlider) findViewById(R.id.brightness_slider);
        mBrightnessController = new BrightnessController(mContext, icon, slider,mHander);

		
    }

    @Override
    protected void onStart() {
        super.onStart();
		Log.d(TAG, "onStart ");
        mBrightnessController.registerCallbacks();
		dismiss = false;
		isDismiss = false;
		mHander.postDelayed(r,50);
        //MetricsLogger.visible(this, MetricsLogger.BRIGHTNESS_DIALOG);
    }

    @Override
    protected void onStop() {
        super.onStop();
        //MetricsLogger.hidden(this, MetricsLogger.BRIGHTNESS_DIALOG);
        mBrightnessController.unregisterCallbacks();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
        }
		Log.d(TAG, "onKeyDown ");
		//dismiss();

        return super.onKeyDown(keyCode, event);
    }

	@Override
    public boolean onTouchEvent(MotionEvent event) {
		Log.d(TAG, "onTouchEvent event:" +event.getAction());
		if(event.getAction() == MotionEvent.ACTION_OUTSIDE){
			mHander.removeCallbacks(r);
			dismiss();
			dismiss = false;
			isDismiss = false;
		}
        return super.onTouchEvent(event);
    }
}
