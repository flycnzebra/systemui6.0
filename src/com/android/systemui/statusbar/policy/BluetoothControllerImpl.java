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

package com.android.systemui.statusbar.policy;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.android.settingslib.bluetooth.BluetoothCallback;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class BluetoothControllerImpl implements BluetoothController, BluetoothCallback,
        CachedBluetoothDevice.Callback {
    private static final String TAG = "BluetoothController";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final ArrayList<Callback> mCallbacks = new ArrayList<Callback>();
    private final LocalBluetoothManager mLocalBluetoothManager;

    private boolean mEnabled;
    private int mConnectionState = BluetoothAdapter.STATE_DISCONNECTED;
    private CachedBluetoothDevice mLastDevice;

    private final H mHandler = new H();
	DynamicReceiver dynamicReceiver;
	private int mCurrentSignal = 0;
	private int mBatteryLevel = 0;

    public BluetoothControllerImpl(Context context, Looper bgLooper) {
        mLocalBluetoothManager = LocalBluetoothManager.getInstance(context, null);
        if (mLocalBluetoothManager != null) {
            mLocalBluetoothManager.getEventManager().setReceiverHandler(new Handler(bgLooper));
            mLocalBluetoothManager.getEventManager().registerCallback(this);
            onBluetoothStateChanged(
                    mLocalBluetoothManager.getBluetoothAdapter().getBluetoothState());
			//add by atc6068
			IntentFilter filter = new IntentFilter();
			filter.addAction("android.bluetooth.headsetclient.profile.action.AG_EVENT");
			dynamicReceiver = new DynamicReceiver();
			context.registerReceiver(dynamicReceiver,filter);
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("BluetoothController state:");
        pw.print("  mLocalBluetoothManager="); pw.println(mLocalBluetoothManager);
        if (mLocalBluetoothManager == null) {
            return;
        }
        pw.print("  mEnabled="); pw.println(mEnabled);
        pw.print("  mConnectionState="); pw.println(stateToString(mConnectionState));
        pw.print("  mLastDevice="); pw.println(mLastDevice);
        pw.print("  mCallbacks.size="); pw.println(mCallbacks.size());
        pw.println("  Bluetooth Devices:");
        for (CachedBluetoothDevice device :
                mLocalBluetoothManager.getCachedDeviceManager().getCachedDevicesCopy()) {
            pw.println("    " + getDeviceString(device));
        }
    }

    private static String stateToString(int state) {
        switch (state) {
            case BluetoothAdapter.STATE_CONNECTED:
                return "CONNECTED";
            case BluetoothAdapter.STATE_CONNECTING:
                return "CONNECTING";
            case BluetoothAdapter.STATE_DISCONNECTED:
                return "DISCONNECTED";
            case BluetoothAdapter.STATE_DISCONNECTING:
                return "DISCONNECTING";
        }
        return "UNKNOWN(" + state + ")";
    }

    private String getDeviceString(CachedBluetoothDevice device) {
        return device.getName() + " " + device.getBondState() + " " + device.isConnected();
    }

    @Override
    public void addStateChangedCallback(Callback cb) {
        mCallbacks.add(cb);
        mHandler.sendEmptyMessage(H.MSG_STATE_CHANGED);
    }

    @Override
    public void removeStateChangedCallback(Callback cb) {
        mCallbacks.remove(cb);
    }

    @Override
    public boolean isBluetoothEnabled() {
        return mEnabled;
    }

    @Override
    public boolean isBluetoothConnected() {
        return mConnectionState == BluetoothAdapter.STATE_CONNECTED;
    }

    @Override
    public boolean isBluetoothConnecting() {
        return mConnectionState == BluetoothAdapter.STATE_CONNECTING;
    }

    @Override
    public void setBluetoothEnabled(boolean enabled) {
        if (mLocalBluetoothManager != null) {
            mLocalBluetoothManager.getBluetoothAdapter().setBluetoothEnabled(enabled);
        }
    }

    @Override
    public boolean isBluetoothSupported() {
        return mLocalBluetoothManager != null;
    }

    @Override
    public void connect(final CachedBluetoothDevice device) {
        if (mLocalBluetoothManager == null || device == null) return;
        device.connect(true);
    }

    @Override
    public void disconnect(CachedBluetoothDevice device) {
        if (mLocalBluetoothManager == null || device == null) return;
        device.disconnect();
    }

    @Override
    public String getLastDeviceName() {
        return mLastDevice != null ? mLastDevice.getName() : null;
    }

    @Override
    public Collection<CachedBluetoothDevice> getDevices() {
        return mLocalBluetoothManager != null
                ? mLocalBluetoothManager.getCachedDeviceManager().getCachedDevicesCopy()
                : null;
    }

    private void updateConnected() {
        // Make sure our connection state is up to date.
        int state = mLocalBluetoothManager.getBluetoothAdapter().getConnectionState();
        if (state != mConnectionState) {
            mConnectionState = state;
            mHandler.sendEmptyMessage(H.MSG_STATE_CHANGED);
        }
        if (mLastDevice != null && mLastDevice.getDevice().isConnected()) {
            // Our current device is still valid.
            return;
        }
        mLastDevice = null;
        for (CachedBluetoothDevice device : getDevices()) {
            if (device.getDevice().isConnected()) {
                mLastDevice = device;
            }
        }
        if (mLastDevice == null && mConnectionState == BluetoothAdapter.STATE_CONNECTED) {
            // If somehow we think we are connected, but have no connected devices, we aren't
            // connected.
            mConnectionState = BluetoothAdapter.STATE_DISCONNECTED;
            mHandler.sendEmptyMessage(H.MSG_STATE_CHANGED);
        }
    }

    @Override
    public void onBluetoothStateChanged(int bluetoothState) {
        mEnabled = bluetoothState == BluetoothAdapter.STATE_ON;
		Log.v(TAG,"onBluetoothStateChanged: mEnabled = " + mEnabled);
        mHandler.sendEmptyMessage(H.MSG_STATE_CHANGED);
    }

    @Override
    public void onScanningStateChanged(boolean started) {
        // Don't care.
    }

    @Override
    public void onDeviceAdded(CachedBluetoothDevice cachedDevice) {
        cachedDevice.registerCallback(this);
        updateConnected();
        mHandler.sendEmptyMessage(H.MSG_PAIRED_DEVICES_CHANGED);
    }

    @Override
    public void onDeviceDeleted(CachedBluetoothDevice cachedDevice) {
        updateConnected();
        mHandler.sendEmptyMessage(H.MSG_PAIRED_DEVICES_CHANGED);
    }

    @Override
    public void onDeviceBondStateChanged(CachedBluetoothDevice cachedDevice, int bondState) {
        updateConnected();
        mHandler.sendEmptyMessage(H.MSG_PAIRED_DEVICES_CHANGED);
    }

    @Override
    public void onDeviceAttributesChanged() {
        updateConnected();
        mHandler.sendEmptyMessage(H.MSG_PAIRED_DEVICES_CHANGED);
    }

    @Override
    public void onConnectionStateChanged(CachedBluetoothDevice cachedDevice, int state) {
        mLastDevice = cachedDevice;
        updateConnected();
        mConnectionState = state;
        mHandler.sendEmptyMessage(H.MSG_STATE_CHANGED);
    }
	String mBatteryLevelKey = "android.bluetooth.headsetclient.extra.BATTERY_LEVEL";
	String mSignalStrengthKey = "android.bluetooth.headsetclient.extra.NETWORK_SIGNAL_STRENGTH";

	class DynamicReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent){
			Log.v(TAG, "DynamicReceiver onReceive state:");
            Bundle bundle = intent.getExtras();
            Set<String> keySet = bundle.keySet();
            for (Iterator iterator = keySet.iterator(); iterator.hasNext(); ) {
                String key = (String) iterator.next();
                Object mObject = bundle.get(key);
				int value = 0;
                Log.v(TAG,"key:" + key + " mObject:" + mObject);
				if(key.equals(mBatteryLevelKey)){
					if(mObject != null){
						value = (Integer)mObject;
					}
					mBatteryLevel = value;
					mHandler.sendEmptyMessage(H.MSG_BATTERY_CHANGED);
				}
				if(key.equals(mSignalStrengthKey)){
					if(mObject != null){
						value = (Integer)mObject;
					}
					mCurrentSignal = value;
					mHandler.sendEmptyMessage(H.MSG_SIGNAL_CHANGED);
				}
				
            }
			
		}	
	}

    private final class H extends Handler {
        private static final int MSG_PAIRED_DEVICES_CHANGED = 1;
        private static final int MSG_STATE_CHANGED = 2;
		private static final int MSG_SIGNAL_CHANGED = 3;
		private static final int MSG_BATTERY_CHANGED = 4;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_PAIRED_DEVICES_CHANGED:
                    firePairedDevicesChanged();
                    break;
                case MSG_STATE_CHANGED:
                    fireStateChange();
                    break;
				case MSG_SIGNAL_CHANGED:
                    fireSignalChanged(mCurrentSignal);
                    break;
				case MSG_BATTERY_CHANGED:
                    fireBatteryChanged(mBatteryLevel);
                    break;
            }
        }

        private void firePairedDevicesChanged() {
            for (BluetoothController.Callback cb : mCallbacks) {
                cb.onBluetoothDevicesChanged();
            }
        }

        private void fireStateChange() {
            for (BluetoothController.Callback cb : mCallbacks) {
                fireStateChange(cb);
            }
        }

        private void fireStateChange(BluetoothController.Callback cb) {
            cb.onBluetoothStateChange(mEnabled);
        }

		private void fireSignalChanged(int signal) {
            for (BluetoothController.Callback cb : mCallbacks) {
                cb.onBluetoothSignalChange(signal);
            }
        }

		private void fireBatteryChanged(int battery) {
            for (BluetoothController.Callback cb : mCallbacks) {
                cb.onBluetoothBatteryChange(battery);
            }
        }
    }
}
