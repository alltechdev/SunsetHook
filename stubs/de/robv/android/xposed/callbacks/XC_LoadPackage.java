package de.robv.android.xposed.callbacks;

import android.content.pm.ApplicationInfo;

public abstract class XC_LoadPackage {
    public static final class LoadPackageParam {
        public String packageName;
        public String processName;
        public ClassLoader classLoader;
        public ApplicationInfo appInfo;
    }
}
