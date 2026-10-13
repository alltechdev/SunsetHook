package de.robv.android.xposed;

public abstract class XC_MethodHook {
    public static class MethodHookParam {
        public Object[] args;
        public Object thisObject;
        public Object getResult() { return null; }
        public void setResult(Object result) {}
        public void setThrowable(Throwable throwable) {}
    }
    public class Unhook {
        public void unhook() {}
    }
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}
}
