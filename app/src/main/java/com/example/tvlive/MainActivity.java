package com.example.tvlive;

import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** 主界面：全屏播放 + 左侧频道列表，遥控器上下换台、左右切线路、菜单键开关列表。 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "TVLive";

    private ExoPlayer player;
    private PlayerView playerView;
    private View channelPanel;
    private RecyclerView channelList;
    private TextView channelTitle;
    private TextView lineInfo;
    private TextView bufferingText;
    private ChannelAdapter adapter;

    private List<Channel> channels = new ArrayList<>();
    private int currentIndex = 0;
    private int currentLine = 0;
    private boolean listVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        playerView = findViewById(R.id.player_view);
        channelPanel = findViewById(R.id.channel_panel);
        channelList = findViewById(R.id.channel_list);
        channelTitle = findViewById(R.id.channel_title);
        lineInfo = findViewById(R.id.line_info);
        bufferingText = findViewById(R.id.buffering_text);

        initPlayer();
        initList();
        loadChannels();
    }

    private void initPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    bufferingText.setVisibility(View.VISIBLE);
                } else {
                    bufferingText.setVisibility(View.GONE);
                }
            }
            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                Log.e(TAG, "playback error", error);
                Toast.makeText(MainActivity.this, "播放失败，可用左右键切换线路", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initList() {
        adapter = new ChannelAdapter(this, channels, position -> {
            currentIndex = position;
            currentLine = 0;
            playCurrent();
            hideList();
        });
        channelList.setLayoutManager(new LinearLayoutManager(this));
        channelList.setAdapter(adapter);
        channelPanel.setVisibility(View.GONE);
    }

    private void loadChannels() {
        try {
            InputStream in = getAssets().open("channels.m3u");
            channels = M3uParser.parse(in);
            adapter.setChannels(channels);
            if (!channels.isEmpty()) {
                playCurrent();
            } else {
                channelTitle.setText("没有可用频道");
            }
        } catch (Exception e) {
            Log.e(TAG, "load channels failed", e);
            Toast.makeText(this, "读取频道列表失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void playCurrent() {
        if (channels.isEmpty()) return;
        Channel ch = channels.get(currentIndex);
        String url = ch.currentUrl(currentLine);
        if (url == null) {
            Toast.makeText(this, "该频道没有播放地址", Toast.LENGTH_SHORT).show();
            return;
        }
        channelTitle.setText(ch.name);
        updateLineInfo();
        player.setMediaItem(MediaItem.fromUri(url));
        player.prepare();
        player.play();
        // 列表滚动到当前频道
        channelList.scrollToPosition(currentIndex);
        adapter.setSelected(currentIndex);
    }

    private void updateLineInfo() {
        Channel ch = channels.get(currentIndex);
        if (ch.lineCount() > 1) {
            lineInfo.setText("线路 " + (currentLine % ch.lineCount() + 1) + "/" + ch.lineCount()
                    + "  ·  左右键切换");
        } else {
            lineInfo.setText("");
        }
    }

    private void switchChannel(int delta) {
        if (channels.isEmpty()) return;
        currentIndex = (currentIndex + delta + channels.size()) % channels.size();
        currentLine = 0;
        playCurrent();
    }

    private void switchLine(int delta) {
        if (channels.isEmpty()) return;
        Channel ch = channels.get(currentIndex);
        if (ch.lineCount() <= 1) {
            Toast.makeText(this, "该频道只有一条线路", Toast.LENGTH_SHORT).show();
            return;
        }
        currentLine = (currentLine + delta + ch.lineCount()) % ch.lineCount();
        playCurrent();
    }

    private void showList() {
        listVisible = true;
        channelPanel.setVisibility(View.VISIBLE);
        adapter.setSelected(currentIndex);
        channelList.scrollToPosition(currentIndex);
    }

    private void hideList() {
        listVisible = false;
        channelPanel.setVisibility(View.GONE);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                switchChannel(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                switchChannel(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                switchLine(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                switchLine(1);
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_HELP:
            case KeyEvent.KEYCODE_INFO:
                if (listVisible) hideList(); else showList();
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (listVisible) { hideList(); return true; }
                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) player.release();
    }
}
