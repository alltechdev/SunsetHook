package com.atd.sunsethook;

import android.content.Context;
import android.content.pm.PackageInfo;
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
 * longer routed into Gemini onboarding. The obfuscated names are specific to
 * version 17.59.18, which ships the same classes.dex across every architecture.
 *
 * The arm64-v8a standalone build (versionCode 301808679) is matched directly
 * and hooked immediately at load time, exactly as the original release did, so
 * that proven path is unchanged. Any other build is confirmed to be a 17.59.18
 * build by its versionName (which needs an app Context, so it is read at
 * Application.attach) before the same hook is installed; this adds armeabi-v7a
 * and x86_64 without touching the arm64 path. Any other version is skipped
 * rather than risk hooking the wrong method.
 *
 * Scope "android" (system_server): refuses install sessions for the Google
 * app unless they come from root or shell, so the Play Store cannot replace
 * the pinned build. Manual installs via su/adb still work.
 */
public class Hook implements IXposedHookLoadPackage {
    private static final String GSA = "com.google.android.googlequicksearchbox";
    private static final long ARM64_PINNED_VERSION = 301808679L;
    private static final String TARGET_VERSION = "17.59.18";
    private static final int ROOT_UID = 0;
    private static final int SHELL_UID = 2000;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (GSA.equals(lpparam.packageName)) {
            if (versionCodeOf(lpparam) == ARM64_PINNED_VERSION) {
                // Original, device-proven arm64 path: hook immediately at load time.
                hookSunset(lpparam, "versionCode " + ARM64_PINNED_VERSION);
            } else {
                // Other architectures of the same version: confirm by versionName first.
                hookSunsetWhenReady(lpparam);
            }
        } else if ("android".equals(lpparam.packageName)) {
            hookInstallBlock(lpparam);
        }
    }

    /** Installs the flag hook now. Used for the arm64 build, unchanged from the first release. */
    private static void hookSunset(XC_LoadPackage.LoadPackageParam lpparam, String why) {
        try {
            XposedHelpers.findAndHookMethod("hcvl", lpparam.classLoader, "L", "hbhk",
                    new ForceFalse(lpparam.processName));
            XposedBridge.log("SunsetHook: hooked hcvl.L in " + lpparam.processName + " (" + why + ")");
        } catch (Throwable t) {
            XposedBridge.log("SunsetHook: hook failed in " + lpparam.processName + ": " + t);
        }
    }

    /**
     * ApplicationInfo does not carry the versionName, so for non-arm64 builds we
     * wait for Application.attach(Context), read the versionName from the app's
     * own PackageManager, and install the same hook only if it is a 17.59.18
     * build. The hcvl class is already on the app class loader by this point.
     */
    private static void hookSunsetWhenReady(final XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "attach",
                Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Context ctx = (Context) param.args[0];
                String version = versionNameOf(ctx);
                if (version == null || !version.startsWith(TARGET_VERSION)) {
                    XposedBridge.log("SunsetHook: skipping " + lpparam.processName + ", versionName "
                            + version + " is not a " + TARGET_VERSION + " build");
                    return;
                }
                hookSunset(lpparam, "versionName " + version);
            }
        });
    }

    private static long versionCodeOf(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Object v = XposedHelpers.getObjectField(lpparam.appInfo, "longVersionCode");
            return ((Number) v).longValue();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static String versionNameOf(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName;
        } catch (Throwable t) {
            return null;
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
