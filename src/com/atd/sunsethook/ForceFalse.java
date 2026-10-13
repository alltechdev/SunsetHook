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
        // Only override an actual boolean flag read. Since the hook is gated by
        // symbol presence rather than a version number, this guards against the
        // unlikely case of a different build exposing an unrelated hcvl.L(hbhk)
        // with a non-boolean return.
        if (!(param.getResult() instanceof Boolean)) {
            return;
        }
        if (!logged && Boolean.TRUE.equals(param.getResult())) {
            logged = true;
            XposedBridge.log("SunsetHook: forcing 45798734 false in " + processName);
        }
        param.setResult(Boolean.FALSE);
    }
}
