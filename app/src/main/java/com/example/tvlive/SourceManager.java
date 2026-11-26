package com.example.tvlive;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 直播源管理：
 * 1) 本地缓存优先，保证断网也能开；
 * 2) 后台从 GitHub 的 CDN 镜像拉取最新 m3u（源仓库每 6 小时更新），过滤后写回缓存；
 * 3) 因此源在 GitHub 更新后，APK 无需重装即可跟上。
 */
public final class SourceManager {

    private static final String TAG = "TVLive";
    private static final String CACHE_FILE = "channels_cache.m3u";
    private static final String PREF_NAME = "source_pref";
    private static final String PREF_UPDATE = "last_update";
    private static final int MIN_CHANNELS = 30; // 少于该数量视为异常源，不覆盖

    // 走 CDN 镜像，避免小米盒子直连 raw.githubusercontent 失败
    private static final String[] REMOTE_URLS = {
            "https://fastly.jsdelivr.net/gh/vbskycn/iptv@master/tv/iptv4.m3u",
            "https://cdn.jsdelivr.net/gh/vbskycn/iptv@master/tv/iptv4.m3u"
    };

    public interface Callback {
        /** channels 为 null 表示拉取失败/无需更新。 */
        void onResult(List<Channel> channels, boolean fromNetwork, String message);
    }

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();

    private SourceManager() {}

    /** 读取本地源：优先缓存，其次内置 assets。 */
    public static List<Channel> loadLocal(Context ctx) {
        File cache = new File(ctx.getFilesDir(), CACHE_FILE);
        InputStream in = null;
        try {
            if (cache.exists()) {
                in = new FileInputStream(cache);
            } else {
                in = ctx.getAssets().open("channels.m3u");
            }
            List<Channel> list = M3uParser.parse(in);
            if (list != null && !list.isEmpty()) return list;
        } catch (Exception e) {
            Log.e(TAG, "load local source failed", e);
        } finally {
            closeQuietly(in);
        }
        // 缓存损坏时回退内置源
        try {
            in = ctx.getAssets().open("channels.m3u");
            return M3uParser.parse(in);
        } catch (Exception e) {
            Log.e(TAG, "load builtin source failed", e);
            return null;
        } finally {
            closeQuietly(in);
        }
    }

    /** 后台刷新：每次启动都检查 GitHub 源是否有更新。 */
    public static void refreshAsync(final Context ctx, final boolean force, final Callback cb) {
        final SharedPreferences pref = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                String text = null;
                for (String url : REMOTE_URLS) {
                    try {
                        String candidate = download(url);
                        if (candidate != null && candidate.contains("#EXTINF")) {
                            text = candidate;
                            break;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "fetch failed: " + url + " -> " + e.getMessage());
                    }
                }

                List<Channel> result = null;
                if (text != null) {
                    try {
                        result = M3uParser.parse(
                                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
                    } catch (Exception e) {
                        Log.e(TAG, "parse remote source failed", e);
                    }
                    if (result != null && result.size() >= MIN_CHANNELS) {
                        saveCache(ctx, text);
                        pref.edit().putLong(PREF_UPDATE, System.currentTimeMillis()).apply();
                    } else {
                        result = null;
                    }
                }

                final List<Channel> finalResult = result;
                final boolean fromNetwork = result != null;
                final String message = fromNetwork ? "直播源已自动更新" : "使用本地直播源";
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (cb != null) cb.onResult(finalResult, fromNetwork, message);
                    }
                });
            }
        });
    }

    private static String download(String urlString) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code);
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            reader.close();
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private static void saveCache(Context ctx, String text) {
        File dir = ctx.getFilesDir();
        File tmp = new File(dir, CACHE_FILE + ".tmp");
        File dst = new File(dir, CACHE_FILE);
        try {
            Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8);
            try {
                w.write(text);
            } finally {
                w.close();
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(dst);
        } catch (IOException e) {
            Log.e(TAG, "save cache failed", e);
        }
    }

    private static void closeQuietly(InputStream in) {
        if (in == null) return;
        try {
            in.close();
        } catch (IOException ignored) {
        }
    }
}
