package com.example.tvlive;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 直播源管理：
 * 1) 每次拉取前先清理旧缓存；
 * 2) 拉取结果只在内存中覆盖，不写入任何本地文件；
 * 3) 无论拉取成功与否，finally 中删除缓存文件与临时文件，不占用存储；
 * 4) 拉取失败时回退到 APK 内置源（assets），不依赖任何本地缓存。
 */
public final class SourceManager {

    private static final String TAG = "TVLive";
    private static final String CACHE_FILE = "channels_cache.m3u";
    private static final String PREF_NAME = "source_pref";
    private static final int MIN_CHANNELS = 30; // 少于该数量视为异常源，不覆盖

    // 走 CDN 镜像，避免小米盒子直连 raw.githubusercontent 失败
    private static final String[] REMOTE_URLS = {
            "https://fastly.jsdelivr.net/gh/vbskycn/iptv@master/tv/iptv4.m3u",
            "https://cdn.jsdelivr.net/gh/vbskycn/iptv@master/tv/iptv4.m3u"
    };

    public interface Callback {
        /** channels 为 null 表示拉取失败，调用方应保留内置源。 */
        void onResult(List<Channel> channels, boolean fromNetwork, String message);
    }

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();

    private SourceManager() {}

    /** 读取内置源（assets）。同时清理历史遗留的缓存/临时文件。 */
    public static List<Channel> loadLocal(Context ctx) {
        cleanup(ctx);
        InputStream in = null;
        try {
            in = ctx.getAssets().open("channels.m3u");
            List<Channel> list = M3uParser.parse(in);
            if (list != null && !list.isEmpty()) return list;
        } catch (Exception e) {
            Log.e(TAG, "load builtin source failed", e);
        } finally {
            closeQuietly(in);
        }
        return null;
    }

    /** 后台刷新：每次启动都拉取最新源，结果只覆盖内存，结束后清理所有本地文件。 */
    public static void refreshAsync(final Context ctx, final boolean force, final Callback cb) {
        // 拉取前先清掉上一次可能残留的缓存
        cleanup(ctx);
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                List<Channel> result = null;
                try {
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

                    if (text != null) {
                        try {
                            result = M3uParser.parse(
                                    new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
                        } catch (Exception e) {
                            Log.e(TAG, "parse remote source failed", e);
                        }
                        if (result != null && result.size() < MIN_CHANNELS) {
                            result = null; // 异常源不覆盖
                        }
                    }
                } finally {
                    // 不管成功与否，都删除缓存与临时文件，不在本地保留任何拉取产物
                    cleanup(ctx);
                }

                final List<Channel> finalResult = result;
                final boolean fromNetwork = result != null;
                final String message = fromNetwork ? "直播源已更新" : "使用内置直播源";
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (cb != null) cb.onResult(finalResult, fromNetwork, message);
                    }
                });
            }
        });
    }

    /** 删除缓存文件、临时文件以及旧的更新时间记录。 */
    private static void cleanup(Context ctx) {
        File dir = ctx.getFilesDir();
        deleteQuietly(new File(dir, CACHE_FILE));
        deleteQuietly(new File(dir, CACHE_FILE + ".tmp"));
        try {
            SharedPreferences pref = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            pref.edit().clear().apply();
        } catch (Exception e) {
            Log.w(TAG, "clear pref failed", e);
        }
    }

    private static void deleteQuietly(File f) {
        if (f != null && f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
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

    private static void closeQuietly(InputStream in) {
        if (in == null) return;
        try {
            in.close();
        } catch (IOException ignored) {
        }
    }
}
