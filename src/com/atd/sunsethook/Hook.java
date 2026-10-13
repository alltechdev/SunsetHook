package com.atd.sunsethook;

import android.os.Binder;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Keeps classic Google Assistant working on Google app 17.59.18.
 *
 * Scope "com.google.android.googlequicksearchbox": forces the Assistant
 * "Sunset" flag (com.google.android.libraries.search.device / 45798734, read
 * via the static helper hcvl.L(hbhk)) to false, so opted-out users are no
 * longer routed into Gemini onboarding. Obfuscated names are specific to
 * versionCode 301808679, so the hook is skipped on any other build.
 *
 * Scope "android" (system_server): refuses install sessions for the Google
 * app unless they come from root or shell, so the Play Store cannot replace
 * the pinned build. Manual installs via su/adb still work.
 */
public class Hook implements IXposedHookLoadPackage {
    private static final String GSA = "com.google.android.googlequicksearchbox";
    private static final long GSA_PINNED_VERSION = 301808679L;
    private static final int ROOT_UID = 0;
    private static final int SHELL_UID = 2000;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (GSA.equals(lpparam.packageName)) {
            hookSunset(lpparam);
        } else if ("android".equals(lpparam.packageName)) {
            hookInstallBlock(lpparam);
        }
    }

    private static void hookSunset(XC_LoadPackage.LoadPackageParam lpparam) {
        long version = versionOf(lpparam);
        if (version != GSA_PINNED_VERSION) {
            XposedBridge.log("SunsetHook: skipping " + lpparam.processName + ", versionCode " + version
                    + " != " + GSA_PINNED_VERSION);
            return;
        }
        try {
            XposedHelpers.findAndHookMethod("hcvl", lpparam.classLoader, "L", "hbhk",
                    new ForceFalse(lpparam.processName));
            XposedBridge.log("SunsetHook: hooked hcvl.L in " + lpparam.processName);
        } catch (Throwable t) {
            XposedBridge.log("SunsetHook: hook failed in " + lpparam.processName + ": " + t);
        }
    }

    private static long versionOf(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Object v = XposedHelpers.getObjectField(lpparam.appInfo, "longVersionCode");
            return ((Number) v).longValue();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static void hookInstallBlock(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> pis = XposedHelpers.findClass("com.android.server.pm.PackageInstallerService",
                    lpparam.classLoader);
            XposedBridge.hookAllMethods(pis, "createSessionInternal", new BlockGsaInstall());
            XposedBridge.log("SunsetHook: install block active in system_server");
        } catch (Throwable t) {
            XposedBridge.log("SunsetHook: install block failed: " + t);
        }
    }

    /** Rejects new install sessions for the Google app from anything but root or shell. */
    static class BlockGsaInstall extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            for (Object arg : param.args) {
                if (arg == null || !"android.content.pm.PackageInstaller$SessionParams".equals(arg.getClass().getName())) {
                    continue;
                }
                Object pkg = XposedHelpers.getObjectField(arg, "appPackageName");
                if (!GSA.equals(pkg)) {
                    return;
                }
                int uid = Binder.getCallingUid();
                if (uid == ROOT_UID || uid == SHELL_UID) {
                    return;
                }
                XposedBridge.log("SunsetHook: blocked Google app install session from uid " + uid);
                param.setThrowable(new SecurityException("Google app updates are blocked by SunsetHook"));
                return;
            }
        }
    }
}
