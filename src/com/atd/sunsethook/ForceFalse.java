package com.atd.sunsethook;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Replaces a boolean flag read with false, logging the first override per process. */
public class ForceFalse extends XC_MethodHook {
    private final String processName;
    private boolean logged;

    public ForceFalse(String processName) {
        this.processName = processName;
    }

    @Override
    protected void afterHookedMethod(MethodHookParam param) {
        if (!logged && Boolean.TRUE.equals(param.getResult())) {
            logged = true;
            XposedBridge.log("SunsetHook: forcing 45798734 false in " + processName);
        }
        param.setResult(Boolean.FALSE);
    }
}
