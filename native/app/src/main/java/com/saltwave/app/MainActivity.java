package com.saltwave.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/** 全部界面在代码里搭建：首页网格 / 专辑·歌单详情 / 播放页 / 登录 / 迷你条。 */
public class MainActivity extends Activity implements PlaybackService.Listener {

    private static final int BG = 0xFFF5F6F8;
    private static final int TEXT = 0xFF14161A;
    private static final int SUB = 0xFF6E7480;
    private static final int ACCENT = 0xFF1B4FE0;
    private static final int DARK = 0xFF0B0E14;
    private static final int DSUB = 0xFF98A1B2;

    private static final int T_HOME = 0;
    private static final int T_DETAIL = 1;
    private static final int T_PLAYER = 2;
    private static final int T_LOGIN = 3;

    private static class Screen {
        int type = T_HOME;
        int tab = 0;
        String id = "";
        String title = "";
        String artist = "";
        String cover = null;
        int count = 0;
        boolean playlist = false;
    }

    private final ArrayDeque<Screen> stack = new ArrayDeque<Screen>();
    private Screen cur;

    private SharedPreferences prefs;
    private Api api;
    private boolean demo = false;
    private boolean loaded = false;
    private boolean loading = false;

    private ArrayList<Album> albums = new ArrayList<Album>();
    private ArrayList<Playlist> playlists = new ArrayList<Playlist>();
    private ArrayList<Album> starred = new ArrayList<Album>();

    private final HashMap<String, ArrayList<Track>> songCache = new HashMap<String, ArrayList<Track>>();
    private final HashSet<String> loadingKeys = new HashSet<String>();

    private FrameLayout root;
    private FrameLayout content;

    private LinearLayout mini;
    private Cover mCover;
    private TextView mTitle;
    private TextView mSub;
    private TextView mPlayGlyph;
    private ProgressBar mProg;

    private Cover pCover;
    private TextView pTitle;
    private TextView pArtist;
    private TextView pPos;
    private TextView pDur;
    private TextView pPlayGlyph;
    private SeekBar pSeek;
    private boolean seeking = false;

    private int screenW = 0;
    private int colW = 0;

    private final Handler ui = new Handler();
    private final Runnable uiTick = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            ui.postDelayed(this, 500);
        }
    };

    // ---------------- 生命周期 ----------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Art.init(this);
        prefs = getSharedPreferences("sw", MODE_PRIVATE);
        screenW = getResources().getDisplayMetrics().widthPixels;

        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        buildMini();
        setContentView(root);

        PlaybackService.listener = this;

        restore();

        stack.push(screen(T_HOME));
        if (api == null) stack.push(screen(T_LOGIN));
        render();
        if (api != null) startLoad();
        askNotifications();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && "open_player".equals(intent.getAction())) {
            openPlayer();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        PlaybackService.listener = this;
        ui.removeCallbacks(uiTick);
        ui.post(uiTick);
        updateMini();
        updatePlayerViews();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(uiTick);
    }

    @Override
    public void onBackPressed() {
        if (stack.size() > 1) {
            stack.pop();
            render();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onPlayer() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateMini();
                updatePlayerViews();
            }
        });
    }

    private void restore() {
        String server = prefs.getString("server", "");
        demo = prefs.getBoolean("demo", false);
        if (server.length() > 0 && !demo) {
            try {
                api = new Api(server, prefs.getString("user", ""), prefs.getString("pass", ""));
                Api.current = api;
            } catch (Exception ignored) {
                api = null;
            }
        }
        if (demo) fillDemo();
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
        }
    }

    // ---------------- 渲染 ----------------

    private Screen screen(int type) {
        Screen s = new Screen();
        s.type = type;
        return s;
    }

    private void render() {
        cur = stack.peek();
        if (cur == null) {
            cur = screen(T_HOME);
            stack.push(cur);
        }
        content.removeAllViews();
        View v;
        switch (cur.type) {
            case T_DETAIL:
                v = buildDetail();
                break;
            case T_PLAYER:
                v = buildPlayer();
                break;
            case T_LOGIN:
                v = buildLogin();
                break;
            default:
                v = buildHome();
        }
        content.addView(v, new FrameLayout.LayoutParams(-1, -1));
        applyChrome(cur.type == T_PLAYER);
        updateMini();
        updatePlayerViews();
    }

    private void applyChrome(boolean dark) {
        try {
            getWindow().setStatusBarColor(dark ? DARK : BG);
            getWindow().setNavigationBarColor(dark ? DARK : BG);
        } catch (Exception ignored) {
        }
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (!dark) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void pop() {
        if (stack.size() > 1) {
            stack.pop();
            render();
        }
    }

    private void openLogin() {
        if (cur != null && cur.type == T_LOGIN) return;
        stack.push(screen(T_LOGIN));
        render();
    }

    private void openPlayer() {
        if (PlaybackService.cur() == null) {
            toast("还没有正在播放的歌曲");
            return;
        }
        if (cur != null && cur.type == T_PLAYER) return;
        stack.push(screen(T_PLAYER));
        render();
    }

    // ---------------- 首页 ----------------

    private View buildHome() {
        LinearLayout v = vbox();
        int pad = dp(16);
        v.setPadding(pad, dp(16), pad, 0);

        LinearLayout head = hbox();
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("Salt Wave", 25, true, TEXT);
        title.setLetterSpacing(0.02f);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView srv = label(api != null ? "服务器" : "未连接", 12, true, ACCENT);
        srv.setPadding(dp(10), dp(5), dp(10), dp(5));
        srv.setBackground(pill(0x141B4FE0, 16));
        srv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                openLogin();
            }
        });
        head.addView(srv);
        v.addView(head);

        LinearLayout tabs = hbox();
        tabs.setPadding(0, dp(16), 0, 0);
        String[] names = {"专辑", "歌单", "收藏"};
        for (int i = 0; i < names.length; i++) {
            final int t = i;
            TextView tv = label(names[i], 13, true, cur.tab == i ? Color.WHITE : SUB);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(dp(16), dp(7), dp(16), dp(7));
            tv.setBackground(pill(cur.tab == i ? ACCENT : 0x11000000, 18));
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    cur.tab = t;
                    render();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            tabs.addView(tv, lp);
        }
        v.addView(tabs);

        if (api == null && !demo) {
            v.addView(fillView(status("还没有连接服务器\n点右上角“服务器”填入地址后就能看到你的曲库", true)));
            return v;
        }
        if (loading) {
            v.addView(fillView(status("正在从服务器读取…", false)));
            return v;
        }

        final ArrayList<Object> items = new ArrayList<Object>();
        String empty;
        if (cur.tab == 0) {
            items.addAll(albums);
            empty = "服务器上还没有专辑";
        } else if (cur.tab == 1) {
            items.addAll(playlists);
            empty = "服务器上还没有歌单";
        } else {
            items.addAll(starred);
            empty = "还没有收藏的专辑";
        }

        if (items.isEmpty()) {
            v.addView(fillView(status(empty, false)));
            return v;
        }

        GridView g = new GridView(this);
        g.setNumColumns(2);
        g.setVerticalSpacing(dp(18));
        g.setHorizontalSpacing(dp(14));
        g.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        g.setClipToPadding(false);
        g.setPadding(0, dp(14), 0, dp(90));
        g.setSelector(new ColorDrawable(0));
        colW = (screenW - dp(32) - dp(14)) / 2;
        g.setColumnWidth(colW);
        g.setAdapter(new CardAdapter(items));
        g.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                Object o = items.get(pos);
                Screen s = screen(T_DETAIL);
                if (o instanceof Album) {
                    Album a = (Album) o;
                    s.id = a.id;
                    s.title = a.name;
                    s.artist = a.artist;
                    s.cover = a.cover;
                    s.count = a.count;
                    s.playlist = false;
                } else if (o instanceof Playlist) {
                    Playlist p = (Playlist) o;
                    s.id = p.id;
                    s.title = p.name;
                    s.artist = "";
                    s.cover = p.cover;
                    s.count = p.count;
                    s.playlist = true;
                }
                stack.push(s);
                render();
            }
        });
        v.addView(g, new LinearLayout.LayoutParams(-1, 0, 1));
        return v;
    }

    private class CardAdapter extends BaseAdapter {

        private final ArrayList<Object> items;

        CardAdapter(ArrayList<Object> items) {
            this.items = items;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Object o = items.get(position);
            String name;
            String sub;
            String cover;
            String badge;
            if (o instanceof Album) {
                Album a = (Album) o;
                name = a.name;
                cover = a.cover;
                badge = "专辑";
                if (a.artist == null || a.artist.length() == 0) {
                    sub = a.count + " 首";
                } else {
                    sub = a.artist + " · " + a.count + " 首";
                }
            } else {
                Playlist p = (Playlist) o;
                name = p.name;
                cover = p.cover;
                badge = "歌单";
                sub = p.count + " 首";
            }

            LinearLayout card = vbox();

            FrameLayout frame = new FrameLayout(MainActivity.this);
            Cover cv = new Cover(MainActivity.this);
            Art.into(cv, cover, 14);
            frame.addView(cv, new FrameLayout.LayoutParams(colW, colW));

            TextView b = label(badge, 10, false, Color.WHITE);
            b.setPadding(dp(6), dp(2), dp(6), dp(3));
            b.setBackground(pill(0x66000000, 7));
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
            blp.leftMargin = dp(8);
            blp.bottomMargin = dp(8);
            frame.addView(b, blp);

            card.addView(frame);

            TextView t1 = label(name, 14, true, TEXT);
            t1.setSingleLine(true);
            t1.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(-1, -2);
            lp1.topMargin = dp(9);
            card.addView(t1, lp1);

            TextView t2 = label(sub, 11, false, SUB);
            t2.setSingleLine(true);
            t2.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(-1, -2);
            lp2.topMargin = dp(2);
            card.addView(t2, lp2);

            return card;
        }
    }

    // ---------------- 专辑 / 歌单详情 ----------------

    private String songKey(Screen s) {
        return (s.playlist ? "p:" : "a:") + s.id;
    }

    private ArrayList<Track> songsFor(Screen s) {
        if (demo) return demoSongs(s);
        return songCache.get(songKey(s));
    }

    private View buildDetail() {
        final Screen sc = cur;
        ArrayList<Track> songs = songsFor(sc);

        LinearLayout v = vbox();
        int pad = dp(16);
        v.setPadding(pad, dp(6), pad, 0);

        LinearLayout top = hbox();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = label("‹", 26, false, TEXT);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                pop();
            }
        });
        top.addView(back, new LinearLayout.LayoutParams(dp(34), dp(40)));
        TextView tt = label(sc.title, 15, true, TEXT);
        tt.setSingleLine(true);
        tt.setEllipsize(TextUtils.TruncateAt.END);
        tt.setGravity(Gravity.CENTER);
        top.addView(tt, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(new View(this), new LinearLayout.LayoutParams(dp(34), dp(1)));
        v.addView(top);

        LinearLayout sum = hbox();
        sum.setGravity(Gravity.CENTER_VERTICAL);
        sum.setPadding(0, dp(14), 0, dp(4));
        Cover cv = new Cover(this);
        Art.into(cv, sc.cover, 12);
        sum.addView(cv, new LinearLayout.LayoutParams(dp(84), dp(84)));

        LinearLayout col = vbox();
        col.setPadding(dp(14), 0, 0, 0);
        TextView name = label(sc.title, 18, true, TEXT);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(name);
        if (sc.artist != null && sc.artist.length() > 0) {
            TextView ar = label(sc.artist, 12, false, SUB);
            ar.setSingleLine(true);
            ar.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
            alp.topMargin = dp(4);
            col.addView(ar, alp);
        }
        TextView cnt = label((songs == null ? sc.count : songs.size()) + " 首", 11, false, SUB);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = dp(4);
        col.addView(cnt, clp);
        sum.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        v.addView(sum);

        TextView play = label("播放全部", 14, true, Color.WHITE);
        play.setGravity(Gravity.CENTER);
        play.setBackground(pill(ACCENT, 23));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, dp(46));
        plp.topMargin = dp(8);
        plp.bottomMargin = dp(6);
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                ArrayList<Track> list = songsFor(sc);
                if (list == null || list.isEmpty()) {
                    toast("曲目还没加载出来");
                    return;
                }
                playAll(list, 0);
            }
        });
        v.addView(play, plp);

        if (songs == null) {
            v.addView(fillView(status("正在加载曲目…", false)));
            loadSongs(sc);
            return v;
        }
        if (songs.isEmpty()) {
            v.addView(fillView(status("这个列表里没有歌曲", false)));
            return v;
        }

        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setDividerHeight(0);
        lv.setSelector(new ColorDrawable(0));
        lv.setClipToPadding(false);
        lv.setPadding(0, 0, 0, dp(90));
        lv.setAdapter(new TrackAdapter(songs));
        final ArrayList<Track> fs = songs;
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                playAll(fs, pos);
            }
        });
        v.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1));
        return v;
    }

    private void loadSongs(final Screen s) {
        final String k = songKey(s);
        if (loadingKeys.contains(k)) return;
        loadingKeys.add(k);
        final Api a = api;
        new Thread(new Runnable() {
            @Override
            public void run() {
                ArrayList<Track> r = null;
                String err = null;
                try {
                    if (a == null) err = "还没有连接服务器";
                    else r = new ArrayList<Track>(s.playlist ? a.playlistSongs(s.id) : a.albumSongs(s.id));
                } catch (Exception e) {
                    err = e.getMessage();
                }
                final ArrayList<Track> fr = r;
                final String fe = err;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loadingKeys.remove(k);
                        if (fr != null) {
                            songCache.put(k, fr);
                            if (cur != null && cur.type == T_DETAIL && k.equals(songKey(cur))) render();
                        } else if (fe != null) {
                            toast("读取曲目失败：" + fe);
                        }
                    }
                });
            }
        }).start();
    }

    private class TrackAdapter extends BaseAdapter {

        private final ArrayList<Track> songs;

        TrackAdapter(ArrayList<Track> songs) {
            this.songs = songs;
        }

        @Override
        public int getCount() {
            return songs.size();
        }

        @Override
        public Object getItem(int position) {
            return songs.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Track t = songs.get(position);
            LinearLayout row = hbox();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(9), 0, dp(9));

            Cover cv = new Cover(MainActivity.this);
            Art.into(cv, t.cover, 8);
            row.addView(cv, new LinearLayout.LayoutParams(dp(44), dp(44)));

            LinearLayout col = vbox();
            col.setPadding(dp(11), 0, dp(8), 0);
            TextView t1 = label(t.title, 13.5f, true, TEXT);
            t1.setSingleLine(true);
            t1.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(t1);
            TextView t2 = label(t.artist, 11, false, SUB);
            t2.setSingleLine(true);
            t2.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(2);
            col.addView(t2, lp);
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

            TextView dur = label(mmss(t.duration * 1000), 11, false, SUB);
            row.addView(dur);
            return row;
        }
    }

    // ---------------- 播放页 ----------------

    private View buildPlayer() {
        LinearLayout col = vbox();
        col.setBackgroundColor(DARK);
        int pad = dp(18);
        col.setPadding(pad, dp(12), pad, dp(18));

        LinearLayout top = hbox();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = label("‹", 26, false, 0xFFE6EAF2);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                pop();
            }
        });
        top.addView(back, new LinearLayout.LayoutParams(dp(34), dp(40)));
        TextView mid = label("正在播放", 12, false, DSUB);
        mid.setGravity(Gravity.CENTER);
        top.addView(mid, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(new View(this), new LinearLayout.LayoutParams(dp(34), dp(1)));
        col.addView(top);

        pCover = new Cover(this);
        int w = (int) (screenW * 0.76f);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(w, w);
        clp.topMargin = dp(16);
        clp.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(pCover, clp);

        pTitle = label("", 18, true, 0xFFFFFFFF);
        pTitle.setGravity(Gravity.CENTER);
        pTitle.setSingleLine(true);
        pTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = dp(20);
        col.addView(pTitle, tlp);

        pArtist = label("", 12, false, DSUB);
        pArtist.setGravity(Gravity.CENTER);
        pArtist.setSingleLine(true);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.topMargin = dp(6);
        col.addView(pArtist, alp);

        LinearLayout times = hbox();
        times.setPadding(0, dp(26), 0, 0);
        pPos = label("0:00", 10, false, DSUB);
        pDur = label("0:00", 10, false, DSUB);
        pDur.setGravity(Gravity.END);
        times.addView(pPos, new LinearLayout.LayoutParams(0, -2, 1));
        times.addView(pDur, new LinearLayout.LayoutParams(0, -2, 1));
        col.addView(times);

        pSeek = new SeekBar(this);
        pSeek.setProgressTintList(ColorStateList.valueOf(ACCENT));
        pSeek.setThumbTintList(ColorStateList.valueOf(0xFFFFFFFF));
        pSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser) pPos.setText(mmss(p));
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                seeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                seeking = false;
                PlaybackService.seekTo(sb.getProgress());
            }
        });
        col.addView(pSeek, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout ctrl = hbox();
        ctrl.setGravity(Gravity.CENTER);
        ctrl.setPadding(0, dp(16), 0, 0);
        TextView prev = label("⏮", 20, false, 0xFFCBD3E0);
        prev.setGravity(Gravity.CENTER);
        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                PlaybackService.prevTrack();
            }
        });
        ctrl.addView(prev, new LinearLayout.LayoutParams(dp(64), dp(56)));

        pPlayGlyph = label("▶", 17, true, Color.WHITE);
        pPlayGlyph.setGravity(Gravity.CENTER);
        pPlayGlyph.setBackground(circle(ACCENT, 33));
        pPlayGlyph.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                toggle();
            }
        });
        ctrl.addView(pPlayGlyph, new LinearLayout.LayoutParams(dp(66), dp(66)));

        TextView next = label("⏭", 20, false, 0xFFCBD3E0);
        next.setGravity(Gravity.CENTER);
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                PlaybackService.nextTrack();
            }
        });
        ctrl.addView(next, new LinearLayout.LayoutParams(dp(64), dp(56)));
        col.addView(ctrl);

        return col;
    }

    private void toggle() {
        if (demo) {
            toast("演示模式不能播放，先连接你的 Navidrome");
            return;
        }
        startService(new Intent(this, PlaybackService.class));
        PlaybackService.toggle();
    }

    private void playAll(ArrayList<Track> list, int index) {
        if (demo) {
            toast("演示模式不能播放，先连接你的 Navidrome");
            return;
        }
        startService(new Intent(this, PlaybackService.class));
        PlaybackService.play(list, index);
        if (cur == null || cur.type != T_PLAYER) {
            stack.push(screen(T_PLAYER));
        }
        render();
    }

    private void updatePlayerViews() {
        if (pTitle == null || pPlayGlyph == null) return;
        Track t = PlaybackService.cur();
        if (t == null) {
            pTitle.setText("还没有正在播放的歌");
            pArtist.setText("");
            pPlayGlyph.setText("▶");
            return;
        }
        Art.into(pCover, t.cover, 22);
        pTitle.setText(t.title);
        pArtist.setText(t.artist);
        pPlayGlyph.setText(PlaybackService.isPlaying() ? "❚❚" : "▶");
        int dur = PlaybackService.length();
        pDur.setText(mmss(dur));
        if (pSeek.getMax() != Math.max(dur, 1)) pSeek.setMax(Math.max(dur, 1));
    }

    private void updateProgress() {
        int pos = PlaybackService.pos();
        int dur = PlaybackService.length();
        if (cur != null && cur.type == T_PLAYER && pSeek != null && !seeking && dur > 0) {
            pSeek.setProgress(pos);
            pPos.setText(mmss(pos));
            pDur.setText(mmss(dur));
        }
        if (mProg != null && dur > 0) {
            int pct = (int) (pos * 1000L / dur);
            mProg.setProgress(Math.max(0, Math.min(1000, pct)));
        }
    }

    // ---------------- 迷你播放条 ----------------

    private void buildMini() {
        mini = vbox();
        mini.setBackgroundColor(Color.WHITE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(68), Gravity.BOTTOM);
        mini.setLayoutParams(lp);
        if (Build.VERSION.SDK_INT >= 21) mini.setElevation(dp(10));

        mProg = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        mProg.setMax(1000);
        mProg.setProgress(0);
        try {
            mProg.setProgressTintList(ColorStateList.valueOf(ACCENT));
            mProg.setProgressBackgroundTintList(ColorStateList.valueOf(0xFFE6EAF1));
        } catch (Exception ignored) {
        }
        mini.addView(mProg, new LinearLayout.LayoutParams(-1, dp(2)));

        LinearLayout row = hbox();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(6), dp(10), dp(8));
        mCover = new Cover(this);
        row.addView(mCover, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout col = vbox();
        col.setPadding(dp(10), 0, dp(6), 0);
        mTitle = label("", 13, true, TEXT);
        mTitle.setSingleLine(true);
        mTitle.setEllipsize(TextUtils.TruncateAt.END);
        mSub = label("", 11, false, SUB);
        mSub.setSingleLine(true);
        mSub.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(mTitle);
        col.addView(mSub);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

        mPlayGlyph = label("▶", 14, true, ACCENT);
        mPlayGlyph.setGravity(Gravity.CENTER);
        mPlayGlyph.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                toggle();
            }
        });
        row.addView(mPlayGlyph, new LinearLayout.LayoutParams(dp(44), dp(44)));

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                openPlayer();
            }
        });
        mini.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
        mini.setVisibility(View.GONE);
        root.addView(mini);
    }

    private void updateMini() {
        if (mini == null) return;
        Track t = PlaybackService.cur();
        if (t == null || (cur != null && cur.type == T_PLAYER)) {
            mini.setVisibility(View.GONE);
            return;
        }
        mini.setVisibility(View.VISIBLE);
        Art.into(mCover, t.cover, 10);
        mTitle.setText(t.title);
        mSub.setText(t.artist);
        mPlayGlyph.setText(PlaybackService.isPlaying() ? "❚❚" : "▶");
    }

    // ---------------- 登录 ----------------

    private View buildLogin() {
        LinearLayout v = vbox();
        v.setPadding(dp(24), dp(44), dp(24), dp(24));

        TextView title = label("Salt Wave", 26, true, TEXT);
        title.setLetterSpacing(0.02f);
        v.addView(title);
        TextView sub = label("连接你的 Navidrome", 13, false, SUB);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(6);
        v.addView(sub, slp);

        final EditText e1 = field("服务器地址，如 192.168.1.10:4533", false);
        e1.setText(prefs.getString("server", ""));
        final EditText e2 = field("用户名", false);
        e2.setText(prefs.getString("user", ""));
        final EditText e3 = field("密码", true);
        e3.setText(prefs.getString("pass", ""));

        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, dp(50));
        flp.topMargin = dp(24);
        v.addView(e1, flp);
        flp = new LinearLayout.LayoutParams(-1, dp(50));
        flp.topMargin = dp(10);
        v.addView(e2, flp);
        flp = new LinearLayout.LayoutParams(-1, dp(50));
        flp.topMargin = dp(10);
        v.addView(e3, flp);

        final TextView btn = label("连接", 14, true, Color.WHITE);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(pill(ACCENT, 24));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(48));
        blp.topMargin = dp(22);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                final String srv = e1.getText().toString().trim();
                final String usr = e2.getText().toString().trim();
                final String pwd = e3.getText().toString();
                if (srv.length() == 0 || usr.length() == 0) {
                    toast("地址和用户名都要填");
                    return;
                }
                btn.setText("连接中…");
                btn.setEnabled(false);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String err = null;
                        final Api a;
                        Api tmp = null;
                        try {
                            tmp = new Api(srv, usr, pwd);
                            tmp.ping();
                        } catch (Exception e) {
                            err = e.getMessage() == null ? "连接失败" : e.getMessage();
                        }
                        a = tmp;
                        final String fe = err;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                btn.setText("连接");
                                btn.setEnabled(true);
                                if (fe != null) {
                                    toast("连接失败：" + fe);
                                    return;
                                }
                                api = a;
                                Api.current = a;
                                demo = false;
                                loaded = false;
                                loading = false;
                                albums.clear();
                                playlists.clear();
                                starred.clear();
                                songCache.clear();
                                prefs.edit()
                                        .putString("server", srv)
                                        .putString("user", usr)
                                        .putString("pass", pwd)
                                        .putBoolean("demo", false)
                                        .apply();
                                stack.clear();
                                stack.push(screen(T_HOME));
                                render();
                                startLoad();
                            }
                        });
                    }
                }).start();
            }
        });
        v.addView(btn, blp);

        TextView demoBtn = label("先看看界面（演示模式）", 13, true, ACCENT);
        demoBtn.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, dp(46));
        dlp.topMargin = dp(10);
        demoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                api = null;
                Api.current = null;
                demo = true;
                loaded = true;
                albums.clear();
                playlists.clear();
                starred.clear();
                songCache.clear();
                fillDemo();
                prefs.edit().putBoolean("demo", true).apply();
                stack.clear();
                stack.push(screen(T_HOME));
                render();
                toast("演示数据只是壳，连上服务器才有你的音乐");
            }
        });
        v.addView(demoBtn, dlp);

        TextView tip = label("支持 Navidrome / Airsonic 等 Subsonic 服务。\n"
                + "地址可以带端口，例如 http://192.168.1.10:4533。\n"
                + "HTTP 明文已放行，局域网可直接用。", 11, false, SUB);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = dp(22);
        v.addView(tip, tlp);
        return v;
    }

    private EditText field(String hint, boolean password) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0xFF9AA3B2);
        e.setTextColor(TEXT);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        e.setSingleLine(true);
        e.setBackground(roundRect(Color.WHITE, 12));
        e.setPadding(dp(14), 0, dp(14), 0);
        if (password) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT);
        }
        return e;
    }

    // ---------------- 数据 ----------------

    private void startLoad() {
        if (api == null || loading) return;
        loading = true;
        final Api a = api;
        new Thread(new Runnable() {
            @Override
            public void run() {
                ArrayList<Album> al = new ArrayList<Album>();
                ArrayList<Playlist> pl = new ArrayList<Playlist>();
                ArrayList<Album> st = new ArrayList<Album>();
                String err = null;
                try {
                    al.addAll(a.albums(100));
                    pl.addAll(a.playlists());
                    st.addAll(a.starred());
                } catch (Exception e) {
                    err = e.getMessage();
                }
                final ArrayList<Album> fal = al;
                final ArrayList<Playlist> fpl = pl;
                final ArrayList<Album> fst = st;
                final String fe = err;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loading = false;
                        loaded = true;
                        if (fe == null) {
                            albums = fal;
                            playlists = fpl;
                            starred = fst;
                            if (cur != null && cur.type == T_HOME) render();
                        } else {
                            toast("读取曲库失败：" + fe);
                            if (cur != null && cur.type == T_HOME) render();
                        }
                    }
                });
            }
        }).start();
    }

    private void fillDemo() {
        String[] names = {"夜航", "墨", "黄昏以后", "潮汐线", "黑点", "雾中来信"};
        String[] artists = {"北岸乐队", "林砚", "黄昏以后", "潮汐线", "黑点乐队", "雾中来信"};
        for (int i = 0; i < names.length; i++) {
            Album a = new Album();
            a.id = "demo-a" + (i + 1);
            a.name = names[i];
            a.artist = artists[i];
            a.count = 10 + i;
            a.cover = "asset:cover" + (i + 1) + ".jpg";
            albums.add(a);
        }
        String[] plNames = {"深夜循环", "通勤 30 分钟", "最近常听"};
        int[] plCovers = {2, 3, 6};
        int[] plCounts = {18, 12, 9};
        for (int i = 0; i < plNames.length; i++) {
            Playlist p = new Playlist();
            p.id = "demo-p" + (i + 1);
            p.name = plNames[i];
            p.count = plCounts[i];
            p.cover = "asset:cover" + plCovers[i] + ".jpg";
            playlists.add(p);
        }
        starred.add(albums.get(0));
        starred.add(albums.get(3));
        loaded = true;
    }

    private ArrayList<Track> demoSongs(Screen s) {
        String[] titles = {"Intro", "城市边缘", "旧磁带", "午夜电台", "回声", "Outro"};
        int[] durs = {186, 241, 205, 268, 233, 178};
        ArrayList<Track> out = new ArrayList<Track>();
        String cover = s.cover;
        for (int i = 0; i < titles.length; i++) {
            Track t = new Track();
            t.id = s.id + "-" + i;
            t.title = titles[i];
            t.artist = s.artist == null || s.artist.length() == 0 ? "演示数据" : s.artist;
            t.album = s.title;
            t.duration = durs[i];
            t.cover = cover;
            out.add(t);
        }
        return out;
    }

    // ---------------- 小工具 ----------------

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private LinearLayout vbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout hbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private TextView label(String text, float sp, boolean medium, int color) {
        TextView t = new TextView(this);
        t.setText(text == null ? "" : text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (medium) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private Drawable pill(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private Drawable circle(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private Drawable roundRect(int color, float radiusDp) {
        return pill(color, radiusDp);
    }

    private View status(String text, boolean big) {
        LinearLayout box = vbox();
        box.setGravity(Gravity.CENTER);
        TextView t = label(text, big ? 14 : 13, false, SUB);
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(dp(4), 1f);
        box.addView(t);
        return box;
    }

    private View fillView(View inner) {
        LinearLayout wrap = vbox();
        wrap.setGravity(Gravity.CENTER);
        wrap.addView(inner);
        wrap.setPadding(0, dp(60), 0, dp(60));
        return wrap;
    }

    private String mmss(int ms) {
        if (ms < 0) ms = 0;
        int total = ms / 1000;
        int m = total / 60;
        int s = total % 60;
        return m + ":" + (s < 10 ? "0" + s : String.valueOf(s));
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
