package com.android.systemui.jancar;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.systemui.R;

import java.util.ArrayList;
import java.util.List;

public class PkUtils {

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static List<UserHandle> getUserProfiles(Context context) {
        UserManager mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        return mUserManager == null ? null : mUserManager.getUserProfiles();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static List<LauncherActivityInfo> getAllLauncgerActivitys(Context context) {
        List<LauncherActivityInfo> retLst = new ArrayList<>();
        LauncherApps launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        if (launcherApps == null) return retLst;
        List<UserHandle> userHandles = getUserProfiles(context);
        if (userHandles == null) return retLst;
        for (UserHandle userHandle : userHandles) {
            List<LauncherActivityInfo> addList = launcherApps.getActivityList(null, userHandle);
            if (addList != null && !addList.isEmpty()) {
                retLst.addAll(addList);
            }
        }
        return retLst;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static List<LauncherActivityInfo> getLauncgerActivitys(String packageName, Context context) {
        List<LauncherActivityInfo> retLst = new ArrayList<>();
        LauncherApps launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        if (launcherApps == null) return retLst;
        List<UserHandle> userHandles = getUserProfiles(context);
        if (userHandles == null) return retLst;
        for (UserHandle userHandle : userHandles) {
            List<LauncherActivityInfo> addList = launcherApps.getActivityList(packageName, userHandle);
            if (addList != null && !addList.isEmpty()) {
                retLst.addAll(addList);
            }
        }
        return retLst;
    }

    public static String getFocusActivityLabel(Context context) {
        String str = "";
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(context.ACTIVITY_SERVICE);
            ComponentName cn = am.getRunningTasks(1).get(0).topActivity;
            String strpackage = cn.getPackageName();
            switch (strpackage) {
                case "com.android.systemui":
                case "com.jancar.launcher":
                case "com.android.launcher3":
                    str = (context.getString(R.string.launcher));
                    break;
                default:
                    String strclass = cn.getClassName();
                    List<LauncherActivityInfo> list = PkUtils.getLauncgerActivitys(strpackage, context);
                    for (LauncherActivityInfo info : list) {
                        if (strclass.equals(info.getComponentName().getClassName())) {
                            FlyLog.d("activity info =%s", info.getName());
                            str = (String) info.getLabel();
                            break;
                        }
                    }
                    break;
            }
        } catch (Exception e) {
            FlyLog.e(e.toString());
        }
        return str;
    }

}
