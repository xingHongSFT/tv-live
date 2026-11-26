package com.example.tvlive;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 解析标准 m3u / m3u8 播放列表。 */
public final class M3uParser {

    private static final Pattern ATTR = Pattern.compile("([a-zA-Z0-9_-]+)=\"([^\"]*)\"");

    private M3uParser() {}

    /** 从输入流解析频道列表。相同 tvg-id 的频道会合并为多条线路。 */
    public static List<Channel> parse(InputStream in) throws Exception {
        List<Channel> channels = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        Channel pending = null;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("#EXTINF")) {
                pending = parseExtInf(line);
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
        String name = "";

        Matcher m = ATTR.matcher(line);
        while (m.find()) {
            String key = m.group(1);
            String value = m.group(2);
            if ("group-title".equals(key)) group = value;
            else if ("tvg-logo".equals(key)) logo = value;
        }
        // 名称是最后一个逗号之后的部分
        int comma = line.lastIndexOf(',');
        if (comma >= 0 && comma < line.length() - 1) {
            name = line.substring(comma + 1).trim();
        }
        if (name.isEmpty()) name = "未命名";
        return new Channel(name, group, logo);
    }
}
