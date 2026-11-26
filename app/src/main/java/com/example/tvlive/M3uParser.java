package com.example.tvlive;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 解析标准 m3u / m3u8 播放列表，并过滤广告/推广/非频道条目。 */
public final class M3uParser {

    private static final Pattern ATTR = Pattern.compile("([a-zA-Z0-9_-]+)=\"([^\"]*)\"");
    // 纯时间戳样式，例如 2026-11-25 20:12:06
    private static final Pattern TIMESTAMP = Pattern.compile("^\\d{4}-\\d{1,2}-\\d{1,2}[\\s\\d:]*$");
    // 推广/广告关键词（出现即过滤该条目）
    private static final String[] BLOCK_WORDS = {
            "支持作者", "赞助作者", "打赏", "微信", "公众号", "QQ群", "加群",
            "推广", "广告", "捐赠", "赞赏", "本人制作", "作者网站", "赞助"
    };
    // 购物台关键词（电视购物、商城类频道一律过滤）
    private static final String[] SHOPPING_WORDS = {
            "购物", "电视购", "快乐购", "好享购", "优购", "家有购", "时尚购",
            "聚鲨", "环球购", "央广购", "东方购", "抢购", "特卖", "电视直销",
            "直销", "商城", "购物台", "乐拍", "咔哒购", "美嘉购", "星空购"
    };

    private M3uParser() {}

    /** 从输入流解析频道列表。相同名称的频道会合并为多条线路。 */
    public static List<Channel> parse(InputStream in) throws Exception {
        List<Channel> channels = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        Channel pending = null;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("#EXTINF")) {
                pending = parseExtInf(line); // 广告条目返回 null
            } else if ((line.startsWith("http://") || line.startsWith("https://")) && pending != null) {
                // 同名频道合并线路
                Channel target = null;
                for (Channel c : channels) {
                    if (c.name.equals(pending.name)) { target = c; break; }
                }
                if (target == null) {
                    target = pending;
                    channels.add(target);
                }
                target.addUrl(line);
                pending = null;
            }
        }
        reader.close();
        return channels;
    }

    private static Channel parseExtInf(String line) {
        String group = "";
        String logo = "";
        String tvgId = "";
        String name = "";

        Matcher m = ATTR.matcher(line);
        while (m.find()) {
            String key = m.group(1);
            String value = m.group(2);
            if ("group-title".equals(key)) group = value;
            else if ("tvg-logo".equals(key)) logo = value;
            else if ("tvg-id".equals(key)) tvgId = value;
        }
        int comma = line.lastIndexOf(',');
        if (comma >= 0 && comma < line.length() - 1) {
            name = line.substring(comma + 1).trim();
        }
        if (name.isEmpty()) name = "未命名";

        if (isBlocked(name, group, tvgId)) return null;
        return new Channel(name, group, logo);
    }

    /** 过滤广告/推广/时间戳等非真实频道条目。 */
    private static boolean isBlocked(String name, String group, String tvgId) {
        if ("更新时间".equals(group) || "izbds".equals(tvgId)) return true;
        if (TIMESTAMP.matcher(name).matches()) return true;
        String hay = name + " " + tvgId;
        for (String word : BLOCK_WORDS) {
            if (hay.contains(word)) return true;
        }
        for (String word : SHOPPING_WORDS) {
            if (hay.contains(word)) return true;
        }
        return false;
    }
}
