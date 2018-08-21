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

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Window;
import android.view.View;
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
import android.widget.Button;
import com.android.internal.logging.MetricsLogger;
import com.android.systemui.R;
import ICManager.ICManager;
import ICommon.ICMessage;
import ICommon.Actions;
import ICommon.Keys;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/** A dialog that provides controls for adjusting the screen brightness. */
public class FullScreen extends Activity implements View.OnClickListener {
	private static final String TAG = "FullScreen";
	private ICManager mManager = null;
	private Button btn;
	
	private Handler mHander = new Handler(){
		public void handleMessage(Message m) {
			Log.d(TAG, " handleMessage:what :" + m.what);
			super.handleMessage(m);
			switch (m.what) {
				case 1:
				//dismiss = false;
					break;
				case 2:
					finish();
					break;
			}
		}
	};
	private Runnable r = new Runnable(){
			public void run(){
				//Log.d(TAG, " run:dismiss " + dismiss);
				Log.d(TAG, " run:" );
				finish();
			}
		};
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		mManager = ICManager.getICManager();
        final Window window = getWindow();

        IntentFilter filter = new IntentFilter();
        filter.addAction("ICService.BACKLIGHT_STATE");
        registerReceiver(broadcastReceiver, filter);

        window.setGravity(Gravity.CENTER);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.requestFeature(Window.FEATURE_NO_TITLE);
		window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
		window.addFlags( WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
				
				| WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
				| WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
				| WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
				
        setContentView(R.layout.full_screen_layout);
		btn = (Button)findViewById(R.id.screen_btn);
		btn.setOnClickListener(this);
		
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
    @Override
    protected void onPause() {
        super.onPause();
		ICMessage msg = ICMessage.obtain().setCMD(ICMessage.CMD_REGISTER_APP).setInteger(ICMessage.APP_SystemUI);
	 	msg = ICMessage.obtain();
	 	msg.setCMD(ICMessage.CMD_REQUEST_CLOSE_SCREEN)
			.getData()
			.putBoolean(ICMessage.KEY_DATA_a, false);
	 	ICMessage ret = mManager.talkWithService(msg);
		finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
		Log.d(TAG, " onKeyDown:keyCode :" + keyCode);
		ICMessage msg = ICMessage.obtain().setCMD(ICMessage.CMD_REGISTER_APP).setInteger(ICMessage.APP_SystemUI);
						 msg = ICMessage.obtain();
						 msg.setCMD(ICMessage.CMD_REQUEST_CLOSE_SCREEN)
								.getData()
								.putBoolean(ICMessage.KEY_DATA_a, false);
						 ICMessage ret = mManager.talkWithService(msg);
		
		mHander.postDelayed(r,50);
        return super.onKeyDown(keyCode, event);
    }
	
	@Override
	public void onClick(View v){
		switch (v.getId()){
			case R.id.screen_btn:
				Log.d(TAG, " onClick:screen_btn :");
				ICMessage msg = ICMessage.obtain().setCMD(ICMessage.CMD_REGISTER_APP).setInteger(ICMessage.APP_SystemUI);
					 msg = ICMessage.obtain();
					 msg.setCMD(ICMessage.CMD_REQUEST_CLOSE_SCREEN)
							.getData()
							.putBoolean(ICMessage.KEY_DATA_a, false);
					 ICMessage ret = mManager.talkWithService(msg);
				finish();
				break;
		}
	}

    BroadcastReceiver broadcastReceiver = new BroadcastReceiver(){

	    @Override
	    public void onReceive(Context context, Intent intent) {
	        if (intent.getAction().equals("ICService.BACKLIGHT_STATE")){
	            Log.d(TAG, "BacklightReceiver :" + intent.getAction());
	            Log.d(TAG,"getBooleanExtra :" + intent.getBooleanExtra(ICMessage.KEY_DATA_a, true));
	            if (!intent.getBooleanExtra(ICMessage.KEY_DATA_a,true)){
	                Message msg = new Message();
	                msg.what = 2;
	                mHander.sendMessage(msg);
	            }
	        }
	    }
    };
}
