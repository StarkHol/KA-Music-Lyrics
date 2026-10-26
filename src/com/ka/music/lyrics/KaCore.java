package com.ka.music.lyrics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;

import io.github.proify.lyricon.lyric.model.Song;
import io.github.proify.lyricon.provider.LyriconFactory;
import io.github.proify.lyricon.provider.LyriconProvider;
import io.github.proify.lyricon.provider.ProviderLogo;
import io.github.proify.lyricon.provider.ProviderMetadata;

/**
 * 核心：注册 Lyricon Provider（HyperLyrics Enhanced 能发现它），并把 KA Music 的播放信息喂进去。
 */
public final class KaCore {

    public static final String TAG = "KAMLyrics";
    public static final String TARGET_PKG = "com.hoilai.mm.music";
    public static final String SELF_PKG = "com.ka.music.lyrics";

    private static final String ICON_B64 =
            "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";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static LyriconProvider sProvider;
    private static String sLastKey;
    private static volatile int sRequestGen;
    private static final java.util.concurrent.ExecutorService WORK =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    // 所有与 SDK 的交互都放到这个线程，KA Music 自己的线程一律不碰
    private static HandlerThread sWorker;
    private static Handler sWorkerHandler;
    private static final Object LOCK = new Object();
    private static MediaMetadata sPendingMetadata;
    private static PlaybackState sPendingState;
    private static boolean sDraining;
    private static volatile PlaybackState sLastState;
    private static HandlerThread sBeatThread;
    private static Handler sBeatHandler;

    private KaCore() {
    }

    public static synchronized void attach(Context context) {
        if (sProvider != null) {
            return;
        }
        final Context app = context.getApplicationContext();
        try {
            String b64 = logoBase64(app);
            ProviderLogo logo = null;
            if (b64 != null) {
                try {
                    logo = ProviderLogo.Companion.fromBase64(b64);
                } catch (Throwable ignored) {
                }
            }
            HashMap<String, String> map = new HashMap<>();
            map.put("author", "柑橘の洛");
            map.put("description", "为 KA Music 提供云端歌词");
            ProviderMetadata metadata = new ProviderMetadata(map);

            String process = null;

            // 用反射调用 Kotlin 的默认参数版本，跳过 providerService 与末位参数
            java.lang.reflect.Method factory = LyriconFactory.class.getDeclaredMethod(
                    "createProvider$default",
                    LyriconFactory.class,
                    Context.class,
                    String.class,
                    String.class,
                    ProviderLogo.class,
                    ProviderMetadata.class,
                    String.class,
                    io.github.proify.lyricon.provider.ProviderService.class,
                    String.class,
                    int.class,
                    Object.class);
            factory.setAccessible(true);
            sProvider = (LyriconProvider) factory.invoke(null, LyriconFactory.INSTANCE, app,
                    SELF_PKG, TARGET_PKG, logo, metadata, null, null, "com.android.systemui", 96, null);

            sWorker = new HandlerThread("KAMLyrics-io");
            sWorker.start();
            sWorkerHandler = new Handler(sWorker.getLooper());

            // 每秒把最近一次的播放状态再推一遍。HyperLyrics 靠这个算当前进度，断供歌词就不滚了
            sBeatThread = new HandlerThread("KAMLyrics-beat");
            sBeatThread.start();
            sBeatHandler = new Handler(sBeatThread.getLooper());
            sBeatHandler.post(new Runnable() {
                @Override
                public void run() {
                    PlaybackState st = sLastState;
                    if (st != null && sProvider != null) {
                        try {
                            sProvider.getPlayer().setPlaybackState(st);
                        } catch (Throwable ignored) {
                        }
                    }
                    Handler h = sBeatHandler;
                    if (h != null) {
                        h.postDelayed(this, 1000L);
                    }
                }
            });

            sProvider.setAutoSync(true);
            boolean ok = sProvider.register();
            de.robv.android.xposed.XposedBridge.log(TAG + ": provider registered=" + ok);
        } catch (Throwable t) {
            Throwable cause = t;
            while (cause.getCause() != null && cause.getCause() != cause) {
                cause = cause.getCause();
            }
            de.robv.android.xposed.XposedBridge.log(TAG + ": provider init failed: " + cause);
            de.robv.android.xposed.XposedBridge.log(TAG + ": stack " + android.util.Log.getStackTraceString(cause));
        }
    }

    private static String logoBase64(Context ctx) {
        return ICON_B64;
    }

    public static void onMetadata(MediaMetadata md) {
        if (sProvider == null || md == null) {
            return;
        }
        synchronized (LOCK) {
            sPendingMetadata = md;
            kickLocked();
        }
    }

    private static void kickLocked() {
        if (sDraining || sWorkerHandler == null) {
            return;
        }
        sDraining = true;
        sWorkerHandler.post(DRAIN);
    }

    private static final Runnable DRAIN = new Runnable() {
        @Override
        public void run() {
            MediaMetadata md;
            PlaybackState st;
            synchronized (LOCK) {
                md = sPendingMetadata;
                st = sPendingState;
                sPendingMetadata = null;
                sPendingState = null;
                sDraining = false;
            }
            if (st != null) {
                try {
                    sProvider.getPlayer().setPlaybackState(st);
                } catch (Throwable t) {
                    de.robv.android.xposed.XposedBridge.log(TAG + ": push state failed " + t);
                }
            }
            final MediaMetadata meta = md;
            if (meta != null) {
                // 搜索和 setSong 都可能慢，扔到另外一条线程，别堵住上面这条播放状态的推送
                WORK.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            handleMetadata(meta);
                        } catch (Throwable t) {
                            de.robv.android.xposed.XposedBridge.log(TAG + ": metadata failed " + t);
                        }
                    }
                });
            }
        }
    };

    private static void handleMetadata(MediaMetadata md) {
        final String title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
        final String artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        final long duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if (title == null || title.isEmpty()) {
            return;
        }
        final String key = title + "|" + artist + "|" + duration;
        if (key.equals(sLastKey)) {
            return;
        }
        sLastKey = key;
        final int gen = ++sRequestGen;

        // 先把歌名/歌手推给播放器，界面上可立即显示
        try {
            Song stub = new Song();
            stub.setName(title);
            stub.setArtist(artist);
            stub.setDuration(duration);
            sProvider.getPlayer().setSong(stub);
        } catch (Throwable t) {
            de.robv.android.xposed.XposedBridge.log(TAG + ": push stub failed " + t);
        }

        WORK.execute(new Runnable() {
            @Override
            public void run() {
                // 前面还有更新的歌时，这次请求直接跳过，省得白跑
                if (gen != sRequestGen) {
                    return;
                }
                Song song = null;
                try {
                    song = LyricRepo.fetch(title, artist, duration);
                } catch (Throwable t) {
                    de.robv.android.xposed.XposedBridge.log(TAG + ": lyric fetch failed " + t);
                }
                final Song result = song;
                // 抓取期间又切歌了，结果作废，别把上一首的歌词塞给当前这首
                if (gen != sRequestGen) {
                    de.robv.android.xposed.XposedBridge.log(TAG + ": stale result dropped " + title);
                    return;
                }
                if (result == null) {
                    de.robv.android.xposed.XposedBridge.log(TAG + ": no lyric for " + title);
                    return;
                }
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != sRequestGen) {
                            return;
                        }
                        try {
                            boolean ok = sProvider.getPlayer().setSong(result);
                            de.robv.android.xposed.XposedBridge.log(TAG + ": lyric pushed=" + ok
                                    + " lines=" + (result.getLyrics() == null ? 0 : result.getLyrics().size()));
                        } catch (Throwable t) {
                            de.robv.android.xposed.XposedBridge.log(TAG + ": push lyric failed " + t);
                        }
                    }
                });
            }
        });
    }

    public static void onPlaybackState(PlaybackState state) {
        if (sProvider == null || state == null) {
            return;
        }
        sLastState = state;
        synchronized (LOCK) {
            sPendingState = state;
            kickLocked();
        }
    }
}