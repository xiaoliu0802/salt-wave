package com.saltwave.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 原生播放服务：MediaPlayer + MediaSession + 前台通知。
 * 支持后台播放、锁屏 / 通知栏控制、音频焦点、耳机拔出暂停。
 */
public class PlaybackService extends Service {

    public interface Listener {
        void onPlayer();
    }

    public static PlaybackService I;
    public static Listener listener;

    static final String CHANNEL = "sw_play";
    static final int NID = 101;

    static final String A_PREV = "com.saltwave.app.PREV";
    static final String A_TOGGLE = "com.saltwave.app.TOGGLE";
    static final String A_NEXT = "com.saltwave.app.NEXT";
    static final String A_STOP = "com.saltwave.app.STOP";

    private MediaPlayer mp;
    private MediaSession session;
    private AudioManager am;
    private boolean focusHeld;
    private List<Track> queue = new ArrayList<Track>();
    private int idx = -1;
    private int dur = 0;
    private boolean playing = false;
    private boolean prepared = false;
    private boolean foreground = false;

    private static List<Track> pendingList;
    private static int pendingIdx;

    private BroadcastReceiver noisy;

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (playing) {
                updateState();
                ticker.postDelayed(this, 1000);
            }
        }
    };

    private final AudioManager.OnAudioFocusChangeListener focusListener =
            new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int change) {
                    if (change == AudioManager.AUDIOFOCUS_LOSS
                            || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        pause();
                    }
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        I = this;
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        createChannel();
        setupPlayer();
        setupSession();

        noisy = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                pause();
            }
        };
        IntentFilter f = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(noisy, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(noisy, f);
        }

        if (pendingList != null) {
            List<Track> l = pendingList;
            int i = pendingIdx;
            pendingList = null;
            doPlay(l, i);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? null : intent.getAction();
        if (A_TOGGLE.equals(a)) {
            doToggle();
        } else if (A_NEXT.equals(a)) {
            skip(1);
        } else if (A_PREV.equals(a)) {
            skip(-1);
        } else if (A_STOP.equals(a)) {
            pause();
            dropForeground();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        try {
            if (noisy != null) unregisterReceiver(noisy);
        } catch (Exception ignored) {
        }
        ticker.removeCallbacksAndMessages(null);
        try {
            if (session != null) {
                session.setActive(false);
                session.release();
                session = null;
            }
        } catch (Exception ignored) {
        }
        try {
            if (mp != null) {
                mp.reset();
                mp.release();
                mp = null;
            }
        } catch (Exception ignored) {
        }
        abandonFocus();
        I = null;
        super.onDestroy();
    }

    // ---------------- 初始化 ----------------

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CHANNEL, "播放控制", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
    }

    private void setupPlayer() {
        mp = new MediaPlayer();
        mp.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
        mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);

        mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer m) {
                prepared = true;
                try {
                    dur = m.getDuration();
                } catch (Exception e) {
                    dur = 0;
                }
                playing = true;
                m.start();
                ensureForeground();
                update();
                ticker.removeCallbacks(tick);
                ticker.postDelayed(tick, 1000);
            }
        });
        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer m) {
                ticker.removeCallbacks(tick);
                skip(1);
            }
        });
        mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer m, int what, int extra) {
                prepared = false;
                playing = false;
                update();
                toast("这首播不了，可能已从服务器删除");
                return true;
            }
        });
    }

    private void setupSession() {
        session = new MediaSession(this, "SaltWave");
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                resume();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onSkipToNext() {
                skip(1);
            }

            @Override
            public void onSkipToPrevious() {
                skip(-1);
            }

            @Override
            public void onSeekTo(long pos) {
                doSeek((int) pos);
            }

            @Override
            public void onStop() {
                pause();
                dropForeground();
            }
        });
        session.setActive(true);
    }

    // ---------------- 播放控制 ----------------

    private void doPlay(List<Track> list, int i) {
        if (list == null || list.isEmpty()) return;
        queue = new ArrayList<Track>(list);
        idx = Math.max(0, Math.min(i, queue.size() - 1));
        load();
    }

    private void load() {
        if (queue.isEmpty() || idx < 0) return;
        Track t = queue.get(idx);
        if (Api.current == null) {
            toast("还没有连接服务器");
            return;
        }
        prepared = false;
        playing = false;
        dur = 0;
        try {
            mp.reset();
            mp.setDataSource(Api.current.stream(t));
            mp.prepareAsync();
        } catch (Exception e) {
            toast("无法打开音频流：" + e.getMessage());
            return;
        }
        ensureForeground();
        update();
    }

    private void doToggle() {
        if (queue.isEmpty()) return;
        if (playing) pause();
        else resume();
    }

    private void resume() {
        if (queue.isEmpty()) return;
        if (!prepared) {
            load();
            return;
        }
        if (!requestFocus()) return;
        try {
            mp.start();
        } catch (Exception e) {
            toast("恢复播放失败");
            return;
        }
        playing = true;
        ensureForeground();
        update();
        ticker.removeCallbacks(tick);
        ticker.postDelayed(tick, 1000);
    }

    private void pause() {
        try {
            if (mp != null && prepared && playing) mp.pause();
        } catch (Exception ignored) {
        }
        playing = false;
        ticker.removeCallbacks(tick);
        update();
    }

    private void skip(int d) {
        if (queue.isEmpty()) return;
        int n = queue.size();
        idx = ((idx + d) % n + n) % n;
        load();
    }

    private void doSeek(int ms) {
        if (!prepared) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                mp.seekTo((long) ms, MediaPlayer.SEEK_CLOSEST);
            } else {
                mp.seekTo(ms);
            }
        } catch (Exception ignored) {
        }
        updateState();
    }

    public int position() {
        try {
            if (mp != null && prepared) return mp.getCurrentPosition();
        } catch (Exception ignored) {
        }
        return 0;
    }

    // ---------------- 焦点 / 通知 ----------------

    private boolean requestFocus() {
        if (focusHeld) return true;
        int res;
        if (Build.VERSION.SDK_INT >= 26) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            AudioFocusRequest req = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(focusListener)
                    .build();
            res = am.requestAudioFocus(req);
        } else {
            res = am.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        focusHeld = res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return focusHeld;
    }

    private void abandonFocus() {
        if (!focusHeld || am == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                AudioFocusRequest req = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).build();
                am.abandonAudioFocusRequest(req);
            } else {
                am.abandonAudioFocus(focusListener);
            }
        } catch (Exception ignored) {
        }
        focusHeld = false;
    }

    private void updateState() {
        if (queue.isEmpty() || session == null) {
            notifyUi();
            return;
        }
        Track t = queue.get(idx);
        MediaMetadata.Builder mb = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, t.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, t.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, t.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, Math.max(0, dur));
        session.setMetadata(mb.build());

        PlaybackState.Builder sb = new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY
                        | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_SEEK_TO
                        | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackState.ACTION_STOP)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        (long) position(), 1f);
        session.setPlaybackState(sb.build());
        notifyUi();
    }

    private void update() {
        updateState();
        if (!queue.isEmpty()) ensureForeground();
    }

    private void notifyUi() {
        if (listener != null) listener.onPlayer();
    }

    private Notification buildNotification() {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        Track t = queue.isEmpty() ? null : queue.get(idx);
        b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(t == null ? "Salt Wave" : t.title)
                .setContentText(t == null ? "" : t.artist)
                .setContentIntent(playerIntent())
                .setOngoing(playing)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC);
        b.addAction(android.R.drawable.ic_media_previous, "上一首", action(A_PREV));
        b.addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                playing ? "暂停" : "播放", action(A_TOGGLE));
        b.addAction(android.R.drawable.ic_media_next, "下一首", action(A_NEXT));
        b.setStyle(new Notification.MediaStyle()
                .setMediaSession(session.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2));
        return b.build();
    }

    private void ensureForeground() {
        if (queue.isEmpty()) return;
        try {
            Notification n = buildNotification();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NID, n);
            }
            foreground = true;
        } catch (Exception ignored) {
        }
    }

    private void dropForeground() {
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(Service.STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (Exception ignored) {
        }
        foreground = false;
    }

    private PendingIntent action(String a) {
        Intent i = new Intent(this, PlaybackService.class).setAction(a);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, a.hashCode(), i, flags);
    }

    private PendingIntent playerIntent() {
        Intent i = new Intent(this, MainActivity.class)
                .setAction("open_player")
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(this, 11, i, flags);
    }

    private void toast(final String s) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(PlaybackService.this, s, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---------------- 给 UI 的静态入口 ----------------

    public static void play(List<Track> list, int i) {
        if (I != null) {
            I.doPlay(list, i);
        } else {
            pendingList = list;
            pendingIdx = i;
        }
    }

    public static void toggle() {
        if (I != null) I.doToggle();
    }

    public static void nextTrack() {
        if (I != null) I.skip(1);
    }

    public static void prevTrack() {
        if (I != null) I.skip(-1);
    }

    public static void seekTo(int ms) {
        if (I != null) I.doSeek(ms);
    }

    public static Track cur() {
        if (I == null || I.queue.isEmpty() || I.idx < 0) return null;
        return I.queue.get(I.idx);
    }

    public static boolean isPlaying() {
        return I != null && I.playing;
    }

    public static int pos() {
        return I == null ? 0 : I.position();
    }

    public static int length() {
        return I == null ? 0 : I.dur;
    }
}
