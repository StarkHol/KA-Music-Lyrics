package com.ka.music.lyrics;

import android.app.Application;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Xposed 入口：只注入 KA Music，负责把它的播放信息交给 Lyricon Provider 协议。
 */
public class XposedInit implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!KaCore.TARGET_PKG.equals(lp.packageName)) {
            return;
        }
        XposedBridge.log(KaCore.TAG + ": injected into " + lp.processName);
        try {
            XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        KaCore.attach((Application) param.thisObject);
                    } catch (Throwable t) {
                        XposedBridge.log(KaCore.TAG + ": attach failed " + t);
                    }
                }
            });

            Class<?> sessionClass = XposedHelpers.findClass("android.media.session.MediaSession", lp.classLoader);

            XposedHelpers.findAndHookMethod(sessionClass, "setMetadata", MediaMetadata.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        KaCore.onMetadata((MediaMetadata) param.args[0]);
                    } catch (Throwable t) {
                        XposedBridge.log(KaCore.TAG + ": metadata failed " + t);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(sessionClass, "setPlaybackState", PlaybackState.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        KaCore.onPlaybackState((PlaybackState) param.args[0]);
                    } catch (Throwable t) {
                        XposedBridge.log(KaCore.TAG + ": playback failed " + t);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(KaCore.TAG + ": hook setup failed " + t);
        }
    }
}