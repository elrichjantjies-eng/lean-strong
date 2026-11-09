package za.saradio.app;

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
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONObject;

/** Keeps the radio playing in the background, with a media notification and lock-screen controls. */
public class RadioService extends Service implements AudioManager.OnAudioFocusChangeListener {
    public static final String ACTION_PLAY = "za.saradio.app.PLAY";
    public static final String ACTION_TOGGLE = "za.saradio.app.TOGGLE";
    public static final String ACTION_STOP = "za.saradio.app.STOP";
    public static final String ACTION_CLOSE = "za.saradio.app.CLOSE";
    public static final String ACTION_SLEEP = "za.saradio.app.SLEEP";
    /** The data &amp; quality setting changed: pick the stream again. */
    public static final String ACTION_QUALITY = "za.saradio.app.QUALITY";
    public static final String EXTRA_STATION = "station";
    public static final String EXTRA_MINUTES = "minutes";

    public static final int IDLE = 0;
    public static final int BUFFERING = 1;
    public static final int PLAYING = 2;
    public static final int ERROR = 3;

    private static final String CHANNEL = "playback";
    private static final int NOTIF_ID = 7;
    private static final long FADE_MS = 30_000;

    public interface Listener {
        void onRadioState(Station s, int state, String message);
    }

    // State shared with the screen (same process).
    public static volatile Station current;
    public static volatile int state = IDLE;
    public static volatile String message = "";
    public static volatile String songTitle = "";
    public static volatile long sleepAt;
    /** The stream being played and how many the station has. */
    public static volatile Station.Stream nowStream;
    public static volatile int streamCount;
    /** Audio bitrate measured from the stream itself, or 0. */
    public static volatile int measuredKbps;
    /** Data downloaded since the app started playing, in bytes. */
    public static volatile long sessionBytes;
    /** True when the data figure is an estimate (HLS streams via the system player). */
    public static volatile boolean bytesEstimated;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Listener listener;

    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private NotificationManager nm;
    private MediaSession session;
    private WifiManager.WifiLock wifiLock;
    private PowerManager.WakeLock wakeLock;
    private ConnectivityManager.NetworkCallback netCallback;
    private boolean inForeground;
    private boolean noisyRegistered;
    private boolean resumeAfterFocus;
    private boolean wantPlaying;
    private boolean useFallback;
    private int retries;
    private List<Station.Stream> plan = new ArrayList<>();
    private int planIdx;
    private StreamPlayer stream;
    private MediaPlayer player;
    private long lastBytes;
    private long pendingMonthBytes;
    private long meterAt;
    private float duckVolume = 1f;
    private float fadeVolume = 1f;

    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(i.getAction())) stopPlayback("Headphones unplugged");
        }
    };

    private final Runnable retry = () -> {
        if (wantPlaying && current != null) startStream();
    };

    private final Runnable meter = new Runnable() {
        @Override
        public void run() {
            meterTick();
            if (wantPlaying) MAIN.postDelayed(this, 5000);
        }
    };

    private final Runnable sleepFade = new Runnable() {
        @Override
        public void run() {
            long left = sleepAt - System.currentTimeMillis();
            if (sleepAt == 0) return;
            if (left <= 0) {
                sleepAt = 0;
                fadeVolume = 1f;
                stopPlayback("Sleep timer ended");
                notifyListener();
                return;
            }
            fadeVolume = Math.max(0f, Math.min(1f, left / (float) FADE_MS));
            applyVolume();
            MAIN.postDelayed(this, 500);
        }
    };

    private final Runnable requality = this::applyQuality;

    public static void setListener(Listener l) {
        listener = l;
        if (l != null) l.onRadioState(current, state, message);
    }

    public static boolean isActive() {
        return state == PLAYING || state == BUFFERING;
    }

    public static void send(Context c, String action, Station s, int minutes) {
        Intent i = new Intent(c, RadioService.class).setAction(action);
        if (s != null) i.putExtra(EXTRA_STATION, s.toJson().toString());
        i.putExtra(EXTRA_MINUTES, minutes);
        try {
            c.startForegroundService(i);
        } catch (Exception e) {
            try {
                c.startService(i);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ImageLoader.init(this);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Now playing", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        ch.setSound(null, null);
        nm.createNotificationChannel(ch);
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wm != null) {
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "saradio:stream");
            wifiLock.setReferenceCounted(false);
        }
        wakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "saradio:play");
        wakeLock.setReferenceCounted(false);
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setOnAudioFocusChangeListener(this, MAIN)
                .setWillPauseWhenDucked(false)
                .build();
        session = new MediaSession(this, "SARadio");
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                if (current != null) play(current, false);
            }

            @Override
            public void onPause() {
                stopPlayback("Stopped");
            }

            @Override
            public void onStop() {
                stopPlayback("Stopped");
            }
        });
        session.setSessionActivity(PendingIntent.getActivity(this, 1,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        if (current == null) current = Prefs.lastStation(this);
        watchNetwork();
    }

    private void watchNetwork() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            netCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network n) {
                    ImageLoader.clearFailures();
                }

                @Override
                public void onCapabilitiesChanged(Network n, NetworkCapabilities caps) {
                    // Auto mode: Wi-Fi <-> mobile data changes which stream is right.
                    MAIN.removeCallbacks(requality);
                    MAIN.postDelayed(requality, 3000);
                }
            };
            cm.registerDefaultNetworkCallback(netCallback, MAIN);
        } catch (Exception e) {
            netCallback = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        goForeground();
        String action = intent == null || intent.getAction() == null ? "" : intent.getAction();
        switch (action) {
            case ACTION_PLAY: {
                Station s = null;
                try {
                    s = Station.fromJson(new JSONObject(intent.getStringExtra(EXTRA_STATION)));
                } catch (Exception ignored) {
                }
                if (s == null) leaveForegroundIfIdle();
                else play(s, true);
                break;
            }
            case ACTION_TOGGLE:
                if (isActive()) stopPlayback("Stopped");
                else if (current != null) play(current, false);
                else leaveForegroundIfIdle();
                break;
            case ACTION_STOP:
                stopPlayback("Stopped");
                break;
            case ACTION_CLOSE:
                close();
                break;
            case ACTION_SLEEP:
                setSleep(intent.getIntExtra(EXTRA_MINUTES, 0));
                leaveForegroundIfIdle();
                break;
            case ACTION_QUALITY:
                applyQuality();
                leaveForegroundIfIdle();
                break;
            default:
                leaveForegroundIfIdle();
        }
        return START_NOT_STICKY;
    }

    private void play(Station s, boolean userChoice) {
        boolean same = current != null && current.key().equals(s.key());
        if (!same) songTitle = "";
        current = s;
        Prefs.saveLastStation(this, s);
        if (userChoice) Prefs.addRecent(this, s);
        plan = DataMode.rank(s, DataMode.effective(this));
        planIdx = 0;
        streamCount = plan.size();
        retries = 0;
        resumeAfterFocus = false;
        MAIN.removeCallbacks(retry);
        if (plan.isEmpty()) {
            wantPlaying = false;
            setState(ERROR, "This station has no stream we can play.");
            leaveForegroundIfIdle();
            return;
        }
        if (audio.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            wantPlaying = false;
            setState(ERROR, "Audio is busy (is a call in progress?)");
            leaveForegroundIfIdle();
            return;
        }
        wantPlaying = true;
        if (!noisyRegistered) {
            IntentFilter f = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, f, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(noisy, f);
            noisyRegistered = true;
        }
        if (wifiLock != null && !wifiLock.isHeld()) wifiLock.acquire();
        if (!wakeLock.isHeld()) wakeLock.acquire(12 * 3600_000L);
        session.setActive(true);
        AudioFx.apply(this);
        meterAt = SystemClock.elapsedRealtime();
        MAIN.removeCallbacks(meter);
        MAIN.postDelayed(meter, 5000);
        startStream();
        if (userChoice && !same) StationRepo.countClick(s.uuid);
    }

    private static boolean isHls(Station.Stream s) {
        String u = s.url.toLowerCase(Locale.ROOT);
        return u.contains(".m3u8") || s.codec.toUpperCase(Locale.ROOT).contains("HLS");
    }

    private void startStream() {
        releasePlayers();
        final Station.Stream s = plan.get(planIdx);
        if (retries == 0) useFallback = isHls(s);
        nowStream = s;
        measuredKbps = 0;
        String msg = retries == 0
                ? (planIdx == 0 ? "Connecting…" : "Trying another stream…")
                : "Reconnecting (" + retries + "/" + maxRetries() + ")…";
        setState(BUFFERING, msg);
        goForeground();
        if (useFallback) {
            startMediaPlayer(s);
            return;
        }
        int eff = DataMode.effective(this);
        stream = new StreamPlayer(s.url, s.bitrate, AudioFx.session(this), DataMode.startBufferSeconds(eff),
                DataMode.rebufferSeconds(eff), new StreamPlayer.Callback() {
                    @Override
                    public void onPlaying(final StreamPlayer p) {
                        MAIN.post(() -> {
                            if (p == stream && wantPlaying) {
                                retries = 0;
                                AudioFx.apply(RadioService.this);
                                setState(PLAYING, "Live");
                            }
                        });
                    }

                    @Override
                    public void onBuffering(final StreamPlayer p) {
                        MAIN.post(() -> {
                            if (p == stream && wantPlaying) setState(BUFFERING, "Buffering…");
                        });
                    }

                    @Override
                    public void onError(final StreamPlayer p, Exception e, final boolean unsupported) {
                        MAIN.post(() -> {
                            if (p != stream || !wantPlaying) return;
                            if (unsupported) {
                                useFallback = true;
                                releasePlayers();
                                startMediaPlayer(s);
                            } else {
                                handleFailure();
                            }
                        });
                    }

                    @Override
                    public void onTitle(final StreamPlayer p, final String title) {
                        MAIN.post(() -> {
                            if (p != stream) return;
                            String t = cleanTitle(title);
                            if (!t.equals(songTitle)) {
                                songTitle = t;
                                setState(state, message);
                            }
                        });
                    }
                });
        stream.setVolume(duckVolume * fadeVolume);
        stream.start();
    }

    static String cleanTitle(String s) {
        if (s == null) return "";
        String t = s.trim();
        String l = t.toLowerCase(Locale.ROOT);
        if (l.length() < 3 || l.startsWith("http") || l.equals("unknown") || l.contains("advert")) return "";
        // Many stations send "Station name - Station name" or just their own name; that isn't a song.
        if (current != null && StationRepo.normName(t).equals(StationRepo.normName(current.name))) return "";
        return t;
    }

    private void startMediaPlayer(Station.Stream s) {
        bytesEstimated = true;
        try {
            MediaPlayer mp = new MediaPlayer();
            player = mp;
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            int sid = AudioFx.session(this);
            if (sid > 0) mp.setAudioSessionId(sid);
            mp.setOnPreparedListener(m -> {
                if (m != player || !wantPlaying) return;
                try {
                    m.setVolume(duckVolume * fadeVolume, duckVolume * fadeVolume);
                    m.start();
                    retries = 0;
                    AudioFx.apply(this);
                    setState(PLAYING, "Live");
                } catch (Exception e) {
                    handleFailure();
                }
            });
            mp.setOnErrorListener((m, what, extra) -> {
                if (m == player) handleFailure();
                return true;
            });
            mp.setOnCompletionListener(m -> {
                if (m == player && wantPlaying) handleFailure();
            });
            mp.setOnInfoListener((m, what, extra) -> {
                if (m == player) {
                    if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) setState(BUFFERING, "Buffering…");
                    else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) setState(PLAYING, "Live");
                }
                return false;
            });
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", StationRepo.USER_AGENT);
            mp.setDataSource(this, Uri.parse(s.url), headers);
            mp.prepareAsync();
        } catch (Exception e) {
            handleFailure();
        }
    }

    private int maxRetries() {
        return plan.size() > 1 ? 2 : 4;
    }

    private void handleFailure() {
        releasePlayers();
        if (!wantPlaying) return;
        if (retries < maxRetries()) {
            retries++;
            if (retries == 2 && !useFallback) useFallback = true; // give the system player a go
            else if (retries == 3) useFallback = isHls(plan.get(planIdx));
            setState(BUFFERING, "Reconnecting (" + retries + "/" + maxRetries() + ")…");
            MAIN.removeCallbacks(retry);
            MAIN.postDelayed(retry, 1000L * retries);
            return;
        }
        if (planIdx + 1 < plan.size()) {
            // This stream is down; the station has others.
            planIdx++;
            retries = 0;
            MAIN.removeCallbacks(retry);
            MAIN.postDelayed(retry, 400);
            return;
        }
        wantPlaying = false;
        releaseLocks();
        audio.abandonAudioFocusRequest(focusRequest);
        setState(ERROR, "Can't reach this station right now. It may be off air.");
        leaveForegroundIfIdle();
    }

    /** Re-picks the stream after the data setting or network type changed. */
    private void applyQuality() {
        if (!wantPlaying || current == null || plan.isEmpty()) return;
        List<Station.Stream> next = DataMode.rank(current, DataMode.effective(this));
        if (next.isEmpty()) return;
        Station.Stream now = plan.get(planIdx);
        plan = next;
        planIdx = 0;
        if (next.get(0).url.equals(now.url)) {
            planIdx = 0;
            notifyListener();
            return;
        }
        retries = 0;
        startStream();
    }

    private void stopPlayback(String why) {
        wantPlaying = false;
        resumeAfterFocus = false;
        MAIN.removeCallbacks(retry);
        meterTick();
        flushMonth();
        MAIN.removeCallbacks(meter);
        releasePlayers();
        releaseLocks();
        audio.abandonAudioFocusRequest(focusRequest);
        setState(IDLE, why);
        leaveForegroundIfIdle();
    }

    private void close() {
        stopPlayback("Stopped");
        setSleep(0);
        session.setActive(false);
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (Exception ignored) {
        }
        inForeground = false;
        nm.cancel(NOTIF_ID);
        stopSelf();
    }

    private void releasePlayers() {
        meterTick();
        if (stream != null) {
            stream.stop();
            stream = null;
        }
        lastBytes = 0;
        if (player != null) {
            MediaPlayer mp = player;
            player = null;
            try {
                mp.reset();
            } catch (Exception ignored) {
            }
            try {
                mp.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void releaseLocks() {
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (noisyRegistered) {
            try {
                unregisterReceiver(noisy);
            } catch (Exception ignored) {
            }
            noisyRegistered = false;
        }
    }

    // ---- Data meter -------------------------------------------------------------------------

    private void meterTick() {
        long now = SystemClock.elapsedRealtime();
        long delta = 0;
        if (stream != null) {
            bytesEstimated = false;
            long b = stream.bytesRead();
            delta = b - lastBytes;
            lastBytes = b;
            int k = stream.measuredKbps();
            if (k > 0) measuredKbps = k;
        } else if (player != null && state == PLAYING && meterAt > 0) {
            Station.Stream s = nowStream;
            int kbps = s != null && s.bitrate > 0 ? s.bitrate : 64;
            delta = (long) kbps * 125 * (now - meterAt) / 1000;
        }
        meterAt = now;
        if (delta > 0) {
            sessionBytes += delta;
            pendingMonthBytes += delta;
            if (pendingMonthBytes > (512 << 10)) flushMonth();
            notifyListener();
        }
    }

    private void flushMonth() {
        if (pendingMonthBytes > 0) {
            Prefs.addMonthBytes(this, pendingMonthBytes);
            pendingMonthBytes = 0;
        }
    }

    // ---- Volume, focus, sleep ---------------------------------------------------------------

    private void applyVolume() {
        float v = duckVolume * fadeVolume;
        if (stream != null) stream.setVolume(v);
        if (player != null) {
            try {
                player.setVolume(v, v);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onAudioFocusChange(int change) {
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                duckVolume = 0.25f;
                applyVolume();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (isActive()) {
                    wantPlaying = false;
                    MAIN.removeCallbacks(retry);
                    releasePlayers();
                    resumeAfterFocus = true;
                    setState(IDLE, "Paused");
                    leaveForegroundIfIdle();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                stopPlayback("Paused — another app is playing");
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                duckVolume = 1f;
                applyVolume();
                if (resumeAfterFocus && current != null) {
                    resumeAfterFocus = false;
                    play(current, false);
                }
                break;
            default:
        }
    }

    private void setSleep(int minutes) {
        MAIN.removeCallbacks(sleepFade);
        fadeVolume = 1f;
        applyVolume();
        if (minutes > 0) {
            sleepAt = System.currentTimeMillis() + minutes * 60_000L;
            // Fade out gently over the last 30 seconds rather than cutting off mid-sentence.
            MAIN.postDelayed(sleepFade, Math.max(0, minutes * 60_000L - FADE_MS));
        } else {
            sleepAt = 0;
        }
        notifyListener();
    }

    // ---- State, session, notification -------------------------------------------------------

    private void setState(int s, String msg) {
        state = s;
        message = msg;
        int ps;
        switch (s) {
            case BUFFERING: ps = PlaybackState.STATE_BUFFERING; break;
            case PLAYING: ps = PlaybackState.STATE_PLAYING; break;
            case ERROR: ps = PlaybackState.STATE_ERROR; break;
            default: ps = PlaybackState.STATE_STOPPED;
        }
        PlaybackState.Builder b = new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP
                        | PlaybackState.ACTION_PLAY_PAUSE)
                .setState(ps, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f);
        if (s == ERROR) b.setErrorMessage(msg);
        session.setPlaybackState(b.build());
        updateMetadata();
        if (inForeground || s != IDLE) {
            try {
                nm.notify(NOTIF_ID, buildNotification());
            } catch (Exception ignored) {
            }
        }
        notifyListener();
    }

    private static void notifyListener() {
        MAIN.post(() -> {
            Listener l = listener;
            if (l != null) l.onRadioState(current, state, message);
        });
    }

    private String subtitle() {
        if (state == PLAYING && songTitle.length() > 0) return songTitle;
        if (state == PLAYING) return "Live";
        return message == null || message.length() == 0 ? "Tap to choose a station" : message;
    }

    private void updateMetadata() {
        Station c = current;
        if (c == null) return;
        MediaMetadata.Builder b = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, c.name)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, subtitle())
                .putLong(MediaMetadata.METADATA_KEY_DURATION, -1);
        Bitmap art = ImageLoader.cachedAny(c.favicon);
        if (art != null) {
            b.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, art);
            b.putBitmap(MediaMetadata.METADATA_KEY_ART, art);
        }
        session.setMetadata(b.build());
    }

    private PendingIntent servicePi(String action, int code) {
        return PendingIntent.getForegroundService(this, code, new Intent(this, RadioService.class).setAction(action),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification buildNotification() {
        boolean active = isActive();
        Station c = current;
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(c != null ? c.name : getString(R.string.app_name))
                .setContentText(subtitle())
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setOngoing(active)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setColor(Ui.ACCENT)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, active ? R.drawable.ic_stop : R.drawable.ic_play),
                        active ? "Stop" : "Play", servicePi(ACTION_TOGGLE, 10)).build())
                .addAction(new Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_close), "Close",
                        servicePi(ACTION_CLOSE, 11)).build())
                .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1));
        Bitmap art = c != null ? ImageLoader.cachedAny(c.favicon) : null;
        if (art != null) b.setLargeIcon(art);
        return b.build();
    }

    private void goForeground() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIF_ID, buildNotification());
            }
            inForeground = true;
        } catch (Exception ignored) {
        }
    }

    private void leaveForegroundIfIdle() {
        if (isActive()) return;
        try {
            stopForeground(STOP_FOREGROUND_DETACH);
        } catch (Exception ignored) {
        }
        inForeground = false;
        try {
            nm.notify(NOTIF_ID, buildNotification());
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (!isActive()) close();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        wantPlaying = false;
        MAIN.removeCallbacks(retry);
        MAIN.removeCallbacks(sleepFade);
        MAIN.removeCallbacks(meter);
        MAIN.removeCallbacks(requality);
        meterTick();
        flushMonth();
        sleepAt = 0;
        releasePlayers();
        releaseLocks();
        try {
            audio.abandonAudioFocusRequest(focusRequest);
        } catch (Exception ignored) {
        }
        if (netCallback != null) {
            try {
                ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).unregisterNetworkCallback(netCallback);
            } catch (Exception ignored) {
            }
        }
        AudioFx.release();
        state = IDLE;
        message = "Stopped";
        notifyListener();
        session.release();
        super.onDestroy();
    }
}
