/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import android.app.ActivityManagerNative;
import android.app.ActivityThread;
import android.app.AlarmManager;
import android.app.AlarmManager.AlarmClockInfo;
import android.app.IUserSwitchObserver;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.UserInfo;
import android.hardware.display.WifiDisplayStatus;
import android.media.AudioManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.IRemoteCallback;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings.Global;
import android.telecom.TelecomManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.systemui.R;
import com.android.systemui.jancar.FlyLog;
import com.android.systemui.jancar.PkUtils;
import com.android.systemui.qs.tiles.DndTile;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.BluetoothController.Callback;
import com.android.systemui.statusbar.policy.CastController;
import com.android.systemui.statusbar.policy.CastController.CastDevice;
import com.android.systemui.statusbar.policy.HotspotController;
import com.android.systemui.statusbar.policy.KeyButtonView;
import com.android.systemui.statusbar.policy.UserInfoController;
import com.jancar.JancarManager;
import com.jancar.state.JacState;

import java.util.List;
import java.util.Timer;

/**
 * This class contains all of the policy about which icons are installed in the status
 * bar at boot time.  It goes through the normal API for icons, even though it probably
 * strictly doesn't need to.
 */
public class PhoneStatusBarPolicy implements Callback {
    private static final String TAG = "PhoneStatusBarPolicy";
    private static final boolean DEBUG = true;//Log.isLoggable(TAG, Log.DEBUG);

    private static final String SLOT_CAST = "cast";
    private static final String SLOT_HOTSPOT = "hotspot";
    private static final String SLOT_USB = "usb";
    private static final String SLOT_BLUETOOTH = "bluetooth";
    /*add by atc6068,add icon must match
        /frameworks/base/core/res/res/values/config.xml config_statusBarIcons
        so use existing battery and phone_signal as SLOT_BT_BATTERY and SLOT_BT_SIGNAL
    */
    private static final String SLOT_BT_SIGNAL = "phone_signal";
    private static final String SLOT_BT_BATTERY = "battery";
    private static final String SLOT_TTY = "tty";
    private static final String SLOT_ZEN = "zen";
    private static final String SLOT_VOLUME = "volume";
    private static final String SLOT_ALARM_CLOCK = "alarm_clock";
    private static final String SLOT_MANAGED_PROFILE = "managed_profile";
    /// M: Add for [Headset Icon] @ {
    private static final String SLOT_HEADSET = "headset";
    /// @ }
    private final AudioManager mAudio;

    private final Context mContext;
    private final View mView;
    private final StatusBarManager mService;
    private final Handler mHandler = new Handler();
    private final Handler mUIHandler; //= new PhoneStatusBar.UIHandler();
    private final CastController mCast;
    private final HotspotController mHotspot;
    private final AlarmManager mAlarmManager;
    private final UserInfoController mUserInfoController;

    // Assume it's all good unless we hear otherwise.  We don't always seem
    // to get broadcasts that it *is* there.
    IccCardConstants.State mSimState = IccCardConstants.State.READY;

    private boolean mZenVisible;
    private boolean mVolumeVisible;
    private boolean mCurrentUserSetup;

    private int mZen;

    private boolean mManagedProfileFocused = false;
    private boolean mManagedProfileIconVisible = true;

    private boolean mKeyguardVisible = true;
    private BluetoothController mBluetooth;
    ///M: Add for bug fix ALPS02302321
    private boolean mIsPluginWithMic;
    private boolean mIsPluginWithoutMic;
    private JacState jacState = null;
    private WindowManager floatWindowManager;
    private View floatview;
    PopupWindow mPopupWindow;
    TextView apptitle;
    private ImageView btn_back;
    private KeyButtonView mRecentView;
    private KeyButtonView btn_close_screen;
    private ImageView btn_home;
    Button btBtn;
    Button fmBtn;
    Button musicBtn;
    Button videoBtn;


    Timer timer = null;
    private boolean mDiglogIsShow = false;
    private boolean mIsLauncher = false;
    boolean mIsCloseDisplay = false;
    int mKeyTime = 0;
    int mCurrentSource = 0;
    int mCurrentValue = 0;
    final static int MAX_LOOP_TIME = 15;
    final static int SWITCH_SOURCE = 1;
    final static int DIALOG_UI_SYNC = 2;
    final static int DIALOG_UI_START = 3;
    int mLooptime = MAX_LOOP_TIME;
    //CustomDialog customDialog;
    private final CustomSourceTabDialog mSourceTabDialog;
    public static int mCurrentApp = 0;

    private JancarManager jancarManager;

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "atc6068 action = " + action);
            if (action.equals(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)) {
                updateAlarm();
            } else if (action.equals(AudioManager.RINGER_MODE_CHANGED_ACTION) ||
                    action.equals(AudioManager.INTERNAL_RINGER_MODE_CHANGED_ACTION)) {
                //updateVolumeZen();
            } else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                updateSimState(intent);
            } else if (action.equals(TelecomManager.ACTION_CURRENT_TTY_MODE_CHANGED)) {
                updateTTY(intent);
            } else if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
                updateHeadset(intent);
            }
            /// M: [Multi-User] Register Alarm intent by user @{
            else if (action.equals(Intent.ACTION_USER_SWITCHED)) {
                updateAlarm();
                int newUserId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);
                registerAlarmClockChanged(newUserId, true);
            }
//            else if (intent.getAction().equals(Intent.ACTION_MEDIA_MOUNTED)
//                    || intent.getAction().equals(Intent.ACTION_MEDIA_CHECKING)) {
////                updateUsb(true);
//            }
//            else if (intent.getAction().equals(Intent.ACTION_MEDIA_REMOVED)
//                    || intent.getAction().equals(Intent.ACTION_MEDIA_EJECT)) {
////                updateUsb(false);
//            }
            else if (intent.getAction().equals(ActivityThread.ACTION_ACTIVITY_STATE_CHANGED)) {
                try {
                    Bundle bundle = intent.getExtras();
                    if (bundle != null) {
                        String strpackage = bundle.getString("package");
                        String strclass = bundle.getString("class");
                        String strstate = bundle.getString("state");
                        FlyLog.d("Activity change intent=%s", intent.toUri(0));
                        if (strstate.equals("foreground")) {
                            /**
                             * 隐藏返回图标
                             */

                            if ("com.jancar.launcher".equals(strpackage)) {
                                btn_back.setVisibility(View.GONE);
                            } else {
                                btn_back.setVisibility(View.VISIBLE);
                            }

                            /**
                             * 显示标题
                             */
                            switch (strpackage) {
                                case "com.android.systemui":
                                    break;
                                case "com.jancar.launcher":
                                case "com.android.launcher3":
                                    apptitle.setText(context.getString(R.string.launcher));
                                    break;
                                default:
                                    List<LauncherActivityInfo> list = PkUtils.getLauncgerActivitys(strpackage, context);
                                    for (LauncherActivityInfo info : list) {
                                        if (strclass.equals(info.getComponentName().getClassName())) {
                                            FlyLog.d("activity info =%s", info.getName());
                                            apptitle.setText(info.getLabel());
                                            break;
                                        }
                                    }
                                    break;
                            }

                        }
                    }
                } catch (Exception e) {
                    FlyLog.e(e.toString());
                }
            }
            /// M: [Multi-User] Register Alarm intent by user @}
//            else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
//				updateUsb(true);
//			}
//			else if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
//				updateUsb(false);
//			}
        }
    };

    private Runnable mRemoveCastIconRunnable = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) Log.v(TAG, "updateCast: hiding icon NOW");
            mService.setIconVisibility(SLOT_CAST, false);
        }
    };

    public PhoneStatusBarPolicy(View view, final Context context, CastController cast, HotspotController hotspot,
                                UserInfoController userInfoController, BluetoothController bluetooth, Handler mHandler) {
        mView = view;
        mContext = context;
        mCast = cast;
        mHotspot = hotspot;
        mBluetooth = bluetooth;
        mUIHandler = mHandler;
        mAudio = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        jacState = new SystemStates();
        jancarManager = (JancarManager) context.getSystemService("jancar_manager");
        jancarManager.registerJacStateListener(jacState.asBinder());
        FlyLog.d("jancarManager.registerJacStateListener jancarManager=" + jancarManager);

        mBluetooth.addStateChangedCallback(this);
        mService = (StatusBarManager) context.getSystemService(Context.STATUS_BAR_SERVICE);
        mAlarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        mUserInfoController = userInfoController;
        // listen for broadcasts
        IntentFilter filter = new IntentFilter();
        /// M: [Multi-User] Will register this action using special receiver.
        //filter.addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED);
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        filter.addAction(AudioManager.INTERNAL_RINGER_MODE_CHANGED_ACTION);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.addAction(TelecomManager.ACTION_CURRENT_TTY_MODE_CHANGED);
        ///M: Add to show [Headset Icon] @ {
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        /// @ }
        /// M: [Multi-User] Add user switched action for updating possible alarm icon.
        filter.addAction(Intent.ACTION_USER_SWITCHED);

        //filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        //filter.addAction(Intent.ACTION_MEDIA_EJECT);
        /**
         * 监听Activity变化
         */
        filter.addAction(ActivityThread.ACTION_ACTIVITY_STATE_CHANGED);

        mContext.registerReceiver(mIntentReceiver, filter, null, mHandler);
        /// M: [Multi-User] Register Alarm intent by user
        registerAlarmClockChanged(UserHandle.USER_OWNER, false);

        // listen for user / profile change.
        try {
            ActivityManagerNative.getDefault().registerUserSwitchObserver(mUserSwitchListener);
        } catch (RemoteException e) {
            // Ignore
        }

        // TTY status
        mService.setIcon(SLOT_TTY, R.drawable.stat_sys_tty_mode, 0, null);
        mService.setIconVisibility(SLOT_TTY, false);

        // bluetooth status
        updateBluetooth();

        // Alarm clock
        mService.setIcon(SLOT_ALARM_CLOCK, R.drawable.stat_sys_alarm, 0, null);
        mService.setIconVisibility(SLOT_ALARM_CLOCK, false);

        // zen
        mService.setIcon(SLOT_ZEN, R.drawable.stat_sys_zen_important, 0, null);
        mService.setIconVisibility(SLOT_ZEN, false);

        // volume
        mService.setIcon(SLOT_VOLUME, R.drawable.stat_sys_ringer_vibrate, 0, null);
        mService.setIconVisibility(SLOT_VOLUME, false);
        //updateVolumeZen();

        // cast
        // M: Remove CastTile when WFD is not support in quicksetting
        if (mCast != null) {
            mService.setIcon(SLOT_CAST, R.drawable.stat_sys_cast, 0, null);
            mService.setIconVisibility(SLOT_CAST, false);
            mCast.addCallback(mCastCallback);
        }

        // hotspot
        mService.setIcon(SLOT_HOTSPOT, R.drawable.stat_sys_hotspot, 0,
                mContext.getString(R.string.accessibility_status_bar_hotspot));
        mService.setIconVisibility(SLOT_HOTSPOT, mHotspot.isHotspotEnabled());
        mHotspot.addCallback(mHotspotCallback);

        // managed profile
        mService.setIcon(SLOT_MANAGED_PROFILE, R.drawable.stat_sys_managed_profile_status, 0,
                mContext.getString(R.string.accessibility_managed_profile));
        mService.setIconVisibility(SLOT_MANAGED_PROFILE, false);

        //customDialog = new CustomDialog(mView);

        floatview = LayoutInflater.from(mContext).inflate(R.layout.floatlayout, null);
        //mPopupWindow = new PopupWindow(floatview, 1000, 300, true);
        mSourceTabDialog = new CustomSourceTabDialog(mContext);
        mSourceTabDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mSourceTabDialog.setContentView(floatview);
        apptitle = (TextView) mView.findViewById(R.id.app_title);
        btn_back = (ImageView) mView.findViewById(R.id.back);
        mRecentView = (KeyButtonView) mView.findViewById(R.id.recent_apps007);
        btn_close_screen = (KeyButtonView) mView.findViewById(R.id.close_screen);
        //btn_back has two function ,back and close screen
        btn_close_screen.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (DEBUG) Log.v(TAG, "btn_close_screen: onClick");
                jancarManager.requestDisplay(false);
            }
        });
        btn_home = (ImageView) mView.findViewById(R.id.home);
        btBtn = (Button) floatview.findViewById(R.id.bt);
        fmBtn = (Button) floatview.findViewById(R.id.fm);
        videoBtn = (Button) floatview.findViewById(R.id.video);
        musicBtn = (Button) floatview.findViewById(R.id.music);
        mSourceTabDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        Window dialogWindow = mSourceTabDialog.getWindow();
        //
        WindowManager.LayoutParams lp = dialogWindow.getAttributes();
        dialogWindow.setGravity(Gravity.TOP);
        //lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        //lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        //lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        //lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
        //lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.width = 616;//565*1.5 ? ;
        lp.y = 20;//status bar height
        lp.height = 200;// 238;//159*1.5;
        lp.alpha = 0.7f;
        dialogWindow.setAttributes(lp);

    }

    public void setDefaultLastVolume() {

        SharedPreferences sharedPreferences = mContext.getSharedPreferences("last_volume", Context.MODE_PRIVATE);
        int level;
        int lastVolume;

        level = mAudio.getStreamVolume(AudioManager.STREAM_BLUETOOTH_SCO);
        if (level == 0) {
            lastVolume = sharedPreferences.getInt("" + AudioManager.STREAM_BLUETOOTH_SCO, 15);
            mAudio.setStreamVolume(AudioManager.STREAM_BLUETOOTH_SCO, lastVolume, 0);
            level = mAudio.getStreamVolume(AudioManager.STREAM_BLUETOOTH_SCO);
            if (level != lastVolume) {
                Log.d(TAG, " STREAM_BLUETOOTH_SCO lastVolume set fail :" + "level: " + level + " lastVolume: " + lastVolume);
            }
        }

        level = mAudio.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
        if (level == 0) {
            lastVolume = sharedPreferences.getInt("" + AudioManager.STREAM_VOICE_CALL, 15);
            mAudio.setStreamVolume(AudioManager.STREAM_VOICE_CALL, lastVolume, 0);
            level = mAudio.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
            if (level != lastVolume) {
                Log.d(TAG, " STREAM_VOICE_CALL lastVolume set fail :" + "level: " + level + " lastVolume: " + lastVolume);
            }
        }
        level = mAudio.getStreamVolume(AudioManager.STREAM_GIS);
        if (level == 0) {
            lastVolume = sharedPreferences.getInt("" + AudioManager.STREAM_GIS, 15);
            mAudio.setStreamVolume(AudioManager.STREAM_GIS, lastVolume, 0);
            level = mAudio.getStreamVolume(AudioManager.STREAM_GIS);
            if (level != lastVolume) {
                Log.d(TAG, " STREAM_GIS lastVolume set fail :" + "level: " + level + " lastVolume: " + lastVolume);
            }
        }
        level = mAudio.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (level == 0) {
            lastVolume = sharedPreferences.getInt("" + AudioManager.STREAM_MUSIC, 15);
            mAudio.setStreamVolume(AudioManager.STREAM_MUSIC, lastVolume, 0);
            level = mAudio.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (level != lastVolume) {
                Log.d(TAG, " STREAM_MUSIC lastVolume set fail :" + "level: " + level + " lastVolume: " + lastVolume);
            }
        }
        level = mAudio.getStreamVolume(AudioManager.STREAM_AUXIN);
        if (level == 0) {
            lastVolume = sharedPreferences.getInt("" + AudioManager.STREAM_AUXIN, 15);
            mAudio.setStreamVolume(AudioManager.STREAM_AUXIN, lastVolume, 0);
            level = mAudio.getStreamVolume(AudioManager.STREAM_AUXIN);
            if (level != lastVolume) {
                Log.d(TAG, " STREAM_AUXIN lastVolume set fail :" + "level: " + level + " lastVolume: " + lastVolume);
            }
        }
        //level = mAudio.getStreamVolume(AudioManager.STREAM_RING);


    }

    private void saveCurrentSource(int value) {
        if (DEBUG) Log.d(TAG, "saveCurrentSource value=" + value);
        SharedPreferences sharedPreferences = mContext.getSharedPreferences("current_source", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("source", value);
        editor.commit();
    }

    private int getCurrentSource() {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences("current_source", Context.MODE_PRIVATE);
        int currentSource;
        currentSource = sharedPreferences.getInt("source", 0);
        if (DEBUG) Log.d(TAG, "currentSource =" + currentSource);
        return currentSource;
    }

    public boolean getIsLauncher() {

        return mIsLauncher;
    }

    public boolean getIsCloseDisplay() {

        return mIsCloseDisplay;
    }

    public void setZenMode(int zen) {
        mZen = zen;
        //updateVolumeZen();
    }

    private void updateAlarm() {
        final AlarmClockInfo alarm = mAlarmManager.getNextAlarmClock(UserHandle.USER_CURRENT);
        final boolean hasAlarm = alarm != null && alarm.getTriggerTime() > 0;
        final boolean zenNone = mZen == Global.ZEN_MODE_NO_INTERRUPTIONS;
        mService.setIcon(SLOT_ALARM_CLOCK, zenNone ? R.drawable.stat_sys_alarm_dim
                : R.drawable.stat_sys_alarm, 0, null);
        mService.setIconVisibility(SLOT_ALARM_CLOCK, mCurrentUserSetup && hasAlarm);
    }

    private final void updateSimState(Intent intent) {
        String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
        if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
            mSimState = IccCardConstants.State.ABSENT;
        } else if (IccCardConstants.INTENT_VALUE_ICC_CARD_IO_ERROR.equals(stateExtra)) {
            mSimState = IccCardConstants.State.CARD_IO_ERROR;
        } else if (IccCardConstants.INTENT_VALUE_ICC_READY.equals(stateExtra)) {
            mSimState = IccCardConstants.State.READY;
        } else if (IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(stateExtra)) {
            final String lockedReason =
                    intent.getStringExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON);
            if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PIN.equals(lockedReason)) {
                mSimState = IccCardConstants.State.PIN_REQUIRED;
            } else if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PUK.equals(lockedReason)) {
                mSimState = IccCardConstants.State.PUK_REQUIRED;
            } else {
                mSimState = IccCardConstants.State.NETWORK_LOCKED;
            }
        } else {
            mSimState = IccCardConstants.State.UNKNOWN;
        }
    }

    private final void updateVolumeZen() {
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        boolean zenVisible = false;
        int zenIconId = 0;
        String zenDescription = null;

        boolean volumeVisible = false;
        int volumeIconId = 0;
        String volumeDescription = null;

        if (DndTile.isVisible(mContext) || DndTile.isCombinedIcon(mContext)) {
            zenVisible = mZen != Global.ZEN_MODE_OFF;
            zenIconId = mZen == Global.ZEN_MODE_NO_INTERRUPTIONS
                    ? R.drawable.stat_sys_dnd_total_silence : R.drawable.stat_sys_dnd;
            zenDescription = mContext.getString(R.string.quick_settings_dnd_label);
        } else if (mZen == Global.ZEN_MODE_NO_INTERRUPTIONS) {
            zenVisible = true;
            zenIconId = R.drawable.stat_sys_zen_none;
            zenDescription = mContext.getString(R.string.interruption_level_none);
        } else if (mZen == Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS) {
            zenVisible = true;
            zenIconId = R.drawable.stat_sys_zen_important;
            zenDescription = mContext.getString(R.string.interruption_level_priority);
        }

        if (DndTile.isVisible(mContext) && !DndTile.isCombinedIcon(mContext)
                && audioManager.getRingerModeInternal() == AudioManager.RINGER_MODE_SILENT) {
            volumeVisible = true;
            volumeIconId = R.drawable.stat_sys_ringer_silent;
            volumeDescription = mContext.getString(R.string.accessibility_ringer_silent);
        } else if (mZen != Global.ZEN_MODE_NO_INTERRUPTIONS && mZen != Global.ZEN_MODE_ALARMS &&
                audioManager.getRingerModeInternal() == AudioManager.RINGER_MODE_VIBRATE) {
            volumeVisible = true;
            volumeIconId = R.drawable.stat_sys_ringer_vibrate;
            volumeDescription = mContext.getString(R.string.accessibility_ringer_vibrate);
        }

        if (zenVisible) {
            mService.setIcon(SLOT_ZEN, zenIconId, 0, zenDescription);
        }
        if (zenVisible != mZenVisible) {
            mService.setIconVisibility(SLOT_ZEN, zenVisible);
            mZenVisible = zenVisible;
        }

        if (volumeVisible) {
            mService.setIcon(SLOT_VOLUME, volumeIconId, 0, volumeDescription);
        }
        if (volumeVisible != mVolumeVisible) {
            mService.setIconVisibility(SLOT_VOLUME, volumeVisible);
            mVolumeVisible = volumeVisible;
        }
        updateAlarm();
    }

    @Override
    public void onBluetoothDevicesChanged() {
        updateBluetooth();
    }

    @Override
    public void onBluetoothStateChange(boolean enabled) {
        updateBluetooth();
    }

    @Override
    public void onBluetoothSignalChange(int signal) {
        updateBluetoothSignal(signal);
    }

    @Override
    public void onBluetoothBatteryChange(int battery) {
        updateBluetoothBattery(battery);
    }

    private final void updateBluetooth() {
        int iconId = R.drawable.statusbar_icon_bt_noconnect;
        Message msg1 = mUIHandler.obtainMessage();
        String contentDescription =
                mContext.getString(R.string.accessibility_quick_settings_bluetooth_on);
        boolean bluetoothEnabled = false;
        if (mBluetooth != null) {
            bluetoothEnabled = mBluetooth.isBluetoothEnabled();
            if (mBluetooth.isBluetoothConnected()) {
                //mView.updateQsbt(true);
                msg1.what = PhoneStatusBar.MSG_UPDATE_QS_BT;
                msg1.arg1 = 1;
                mUIHandler.sendMessage(msg1);
                iconId = R.drawable.statusbar_icon_bt_connected;
                contentDescription = mContext.getString(R.string.accessibility_bluetooth_connected);
            } else {
                msg1.what = PhoneStatusBar.MSG_UPDATE_QS_BT;
                msg1.arg1 = 0;
                //mService.setIconVisibility(SLOT_BT_SIGNAL, false);
                //mView.updateQsbt(false);
                mUIHandler.sendMessage(msg1);
                mService.setIconVisibility(SLOT_BT_BATTERY, false);
            }
        }
        mService.setIcon(SLOT_BLUETOOTH, iconId, 0, contentDescription);
        mService.setIconVisibility(SLOT_BLUETOOTH, bluetoothEnabled);


    }

    private final void updateUsb(boolean mounted) {
        int iconId = R.drawable.home_icon_status_usb_d;
        String contentDescription = "";
        mService.setIcon(SLOT_USB, iconId, 0, contentDescription);
        mService.setIconVisibility(SLOT_USB, mounted);
    }

    private final void updateBluetoothSignal(int signal) {
        int iconId = R.drawable.statusbar_icon_signal_4;
        String contentDescription = "";
        boolean bluetoothEnabled = false;
        if (mBluetooth != null) {
            bluetoothEnabled = mBluetooth.isBluetoothEnabled();
            if (mBluetooth.isBluetoothConnected()) {
                iconId = R.drawable.statusbar_icon_bt_connect;
            }else{
                iconId = R.drawable.statusbar_icon_bt_noconnect;
            }
        }
//        if (bluetoothEnabled == true) {
//            switch (signal) {
//                case 0:
//                    iconId = R.drawable.statusbar_icon_signal_0;
//                    break;
//                case 1:
//                    iconId = R.drawable.statusbar_icon_signal_1;
//                    break;
//                case 2:
//                    iconId = R.drawable.statusbar_icon_signal_2;
//                    break;
//                case 3:
//                case 4:
//                    iconId = R.drawable.statusbar_icon_signal_3;
//                    break;
//                case 5:
//                    iconId = R.drawable.statusbar_icon_signal_4;
//                    break;
//            }
//
//        }

        mService.setIcon(SLOT_BT_SIGNAL, iconId, 0, contentDescription);
        mService.setIconVisibility(SLOT_BT_SIGNAL, bluetoothEnabled);

    }

    private final void updateBluetoothBattery(int battery) {
        int iconId = R.drawable.statusbar_icon_battery_3;
        String contentDescription = "";
        boolean bluetoothEnabled = false;
        if (mBluetooth != null) {
            bluetoothEnabled = mBluetooth.isBluetoothEnabled();
            if (mBluetooth.isBluetoothConnected()) {
                iconId = R.drawable.stat_sys_data_bluetooth_connected;
            }
        }
        if (bluetoothEnabled == true) {
            switch (battery) {
                case 0:
                    iconId = R.drawable.statusbar_icon_battery_0;
                    break;
                case 1:
                case 2:
                    iconId = R.drawable.statusbar_icon_battery_1;
                    break;
                case 3:
                case 4:
                    iconId = R.drawable.statusbar_icon_battery_2;
                    break;
                case 5:
                    iconId = R.drawable.statusbar_icon_battery_3;
                    break;
            }

        }
        mService.setIcon(SLOT_BT_BATTERY, iconId, 0, contentDescription);
        mService.setIconVisibility(SLOT_BT_BATTERY, bluetoothEnabled);
    }

    private final void updateTTY(Intent intent) {
        int currentTtyMode = intent.getIntExtra(TelecomManager.EXTRA_CURRENT_TTY_MODE,
                TelecomManager.TTY_MODE_OFF);
        boolean enabled = currentTtyMode != TelecomManager.TTY_MODE_OFF;

        if (DEBUG) Log.v(TAG, "updateTTY: enabled: " + enabled);

        if (enabled) {
            // TTY is on
            if (DEBUG) Log.v(TAG, "updateTTY: set TTY on");
            mService.setIcon(SLOT_TTY, R.drawable.stat_sys_tty_mode, 0,
                    mContext.getString(R.string.accessibility_tty_enabled));
            mService.setIconVisibility(SLOT_TTY, true);
        } else {
            // TTY is off
            if (DEBUG) Log.v(TAG, "updateTTY: set TTY off");
            mService.setIconVisibility(SLOT_TTY, false);
        }
    }

    private void updateCast() {
        boolean isCasting = false;
        for (CastDevice device : mCast.getCastDevices()) {
            if (device.state == CastDevice.STATE_CONNECTING
                    || device.state == CastDevice.STATE_CONNECTED) {
                isCasting = true;
                break;
            }
        }
        if (DEBUG) Log.v(TAG, "updateCast: isCasting: " + isCasting);
        mHandler.removeCallbacks(mRemoveCastIconRunnable);
        if (isCasting) {
            mService.setIcon(SLOT_CAST, R.drawable.stat_sys_cast, 0,
                    mContext.getString(R.string.accessibility_casting));
            mService.setIconVisibility(SLOT_CAST, true);
        } else {
            // don't turn off the screen-record icon for a few seconds, just to make sure the user
            // has seen it
            if (DEBUG) Log.v(TAG, "updateCast: hiding icon in 3 sec...");
            mHandler.postDelayed(mRemoveCastIconRunnable, 3000);
        }
    }

    private void profileChanged(int userId) {
        UserManager userManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        UserInfo user = null;
        if (userId == UserHandle.USER_CURRENT) {
            try {
                user = ActivityManagerNative.getDefault().getCurrentUser();
            } catch (RemoteException e) {
                // Ignore
            }
        } else {
            user = userManager.getUserInfo(userId);
        }

        mManagedProfileFocused = user != null && user.isManagedProfile();
        if (DEBUG) Log.v(TAG, "profileChanged: mManagedProfileFocused: " + mManagedProfileFocused);
        // Actually update the icon later when transition starts.
    }

    private void updateManagedProfile() {
        if (DEBUG) Log.v(TAG, "updateManagedProfile: mManagedProfileFocused: "
                + mManagedProfileFocused
                + " mKeyguardVisible: " + mKeyguardVisible);
        boolean showIcon = mManagedProfileFocused && !mKeyguardVisible;
        if (mManagedProfileIconVisible != showIcon) {
            mService.setIconVisibility(SLOT_MANAGED_PROFILE, showIcon);
            mManagedProfileIconVisible = showIcon;
        }
    }

    private final IUserSwitchObserver.Stub mUserSwitchListener =
            new IUserSwitchObserver.Stub() {
                @Override
                public void onUserSwitching(int newUserId, IRemoteCallback reply) {
                    mUserInfoController.reloadUserInfo();
                }

                @Override
                public void onUserSwitchComplete(int newUserId) throws RemoteException {
                    updateAlarm();
                    profileChanged(newUserId);
                }

                @Override
                public void onForegroundProfileSwitch(int newProfileId) {
                    profileChanged(newProfileId);
                }
            };

    private final HotspotController.Callback mHotspotCallback = new HotspotController.Callback() {
        @Override
        public void onHotspotChanged(boolean enabled) {
            mService.setIconVisibility(SLOT_HOTSPOT, enabled);
        }
    };

    private final CastController.Callback mCastCallback = new CastController.Callback() {
        @Override
        public void onCastDevicesChanged() {
            updateCast();
        }

        /// M: WFD sink support {@
        @Override
        public void onWfdStatusChanged(WifiDisplayStatus status,
                                       boolean sinkMode) {

        }

        @Override
        public void onWifiP2pDeviceChanged(WifiP2pDevice device) {

        }
        /// @}
    };

    public void appTransitionStarting(long startTime, long duration) {
        updateManagedProfile();
    }

    public void setKeyguardShowing(boolean visible) {
        mKeyguardVisible = visible;
        updateManagedProfile();
    }

    public void setCurrentUserSetup(boolean userSetup) {
        if (mCurrentUserSetup == userSetup) return;
        mCurrentUserSetup = userSetup;
        updateAlarm();
    }

    /// M: Add for [Headset Icon] @ {
    protected void updateHeadset(Intent intent) {
        int state = intent.getIntExtra("state", -1);
        int mic = intent.getIntExtra("microphone", -1);
        int iconId = 0;
        Log.d(TAG, "updateHeadSet, state = " + state + ", mic = " + mic);
        if (state == 1) {
            if (mic == 1) {
                mIsPluginWithMic = true;
            } else {
                mIsPluginWithoutMic = true;
            }
            iconId = mic == 1 ? R.drawable.stat_sys_headset_with_mic :
                    R.drawable.stat_sys_headset_without_mic;
            mService.setIcon(SLOT_HEADSET, iconId, 0, null);
            mService.setIconVisibility(SLOT_HEADSET, true);
            Log.d(TAG, "updateHeadSet mIsPluginWithMic = " + mIsPluginWithMic +
                    ", mIsPluginWithoutMic = " + mIsPluginWithoutMic);
        } else {
            if ((mic == 0) && (mIsPluginWithMic) && (mIsPluginWithoutMic)) {
                mIsPluginWithMic = false;
                mIsPluginWithoutMic = false;
                // For handle case ALPS02302321, when receive two connected broadcast with
                // plug in and no mic and mic, then receive another plug out, it actually
                // still plugged in.
                Log.d(TAG, "Reset the flag, and do not hide the icons");
            } else {
                mIsPluginWithMic = false;
                mIsPluginWithoutMic = false;
                mService.setIconVisibility(SLOT_HEADSET, false);
            }
        }
    }
    /// @ }

    /// M: [Multi-User] Register Alarm intent by user @{
    private BroadcastReceiver mAlarmIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "onReceive:" + action);
            if (action.equals(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)) {
                updateAlarm();
            }
        }
    };

    private void registerAlarmClockChanged(int newUserId, boolean userSwitch) {
        if (userSwitch) {
            mContext.unregisterReceiver(mAlarmIntentReceiver);
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED);

        Log.d(TAG, "registerAlarmClockChanged:" + newUserId);
        UserHandle newUserHandle = new UserHandle(newUserId);
        mContext.registerReceiverAsUser(mAlarmIntentReceiver, newUserHandle, filter,
                null /* permission */, mHandler /* scheduler */);
    }

    /*
    private void backgroundAlpha(float bgAlpha) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.alpha = bgAlpha; //0.0-1.0
        getWindow().setAttributes(lp);
    }
    */
    private void initPopupWindow(View view, View contentView) {
        mPopupWindow.setContentView(contentView);
        //mPopupWindow.setTouchable(true);
        //mPopupWindow.setBackgroundDrawable(getResources().getDrawable(R.drawable.bg_list));
        //backgroundAlpha(0.7f);
        //mPopupWindow.showAtLocation(view, Gravity.CENTER, 0, 0);
        mPopupWindow.showAsDropDown(view);
    }


    private void closePopupWindow() {
        if (null != mPopupWindow && mPopupWindow.isShowing()) {
            mPopupWindow.dismiss();
        }
    }


    public class SystemStates extends JacState {
        @Override
        public void OnBackCar(boolean bState) {
            super.OnBackCar(bState);
        }

        @Override
        public void OnStorage(StorageState state) {
            FlyLog.d("usb state:" + state.isUsbMounted());
            updateUsb(state.isUsbMounted());
            super.OnStorage(state);
        }
    }
}
