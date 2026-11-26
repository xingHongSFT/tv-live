package com.example.tvlive;

/** 一个频道：名称、分组、以及一个或多个播放线路（同一频道可能有多条 m3u8）。 */
public class Channel {
    public final String name;
    public final String group;
    public final String logo;
    public final java.util.List<String> urls = new java.util.ArrayList<>();

    public Channel(String name, String group, String logo) {
        this.name = name;
        this.group = group;
        this.logo = logo;
    }

    public void addUrl(String url) {
        if (url != null && !url.trim().isEmpty()) {
            urls.add(url.trim());
        }
    }

    public String currentUrl(int lineIndex) {
        if (urls.isEmpty()) return null;
        int i = lineIndex % urls.size();
        if (i < 0) i += urls.size();
        return urls.get(i);
    }

    public int lineCount() {
        return urls.size();
    }
}
