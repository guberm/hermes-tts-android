package com.guber.hermestts;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.io.File;

public class PlayerService extends Service {
    public static final String ACTION_PLAY = "com.guber.hermestts.action.PLAY";
    public static final String ACTION_PAUSE = "com.guber.hermestts.action.PAUSE";
    public static final String ACTION_RESUME = "com.guber.hermestts.action.RESUME";
    public static final String ACTION_STOP = "com.guber.hermestts.action.STOP";
    public static final String ACTION_STATE_CHANGED = "com.guber.hermestts.action.STATE_CHANGED";
    public static final String EXTRA_PATH = "path";
    public static final String EXTRA_TITLE = "title";

    private static final int NOTIFICATION_ID = 2307;
    private static final String CHANNEL_ID = "hermes_tts_playback";
    private static final int STATE_IDLE = 0;
    private static final int STATE_PLAYING = 1;
    private static final int STATE_PAUSED = 2;
    private static final Object LOCK = new Object();

    private static MediaPlayer player;
    private static int state = STATE_IDLE;
    private static String currentTitle = "Hermes TTS audio";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            broadcastState();
            if (isActive()) handler.postDelayed(this, 1000);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_PLAY.equals(action)) {
            play(intent.getStringExtra(EXTRA_PATH), intent.getStringExtra(EXTRA_TITLE));
        } else if (ACTION_PAUSE.equals(action)) {
            pause();
        } else if (ACTION_RESUME.equals(action)) {
            resume();
        } else if (ACTION_STOP.equals(action)) {
            stopPlayback(true);
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(progressTick);
        stopPlayback(false);
        super.onDestroy();
    }

    private void play(String path, String title) {
        if (path == null || path.trim().isEmpty() || !new File(path).exists()) {
            stopPlayback(true);
            return;
        }
        currentTitle = title == null || title.trim().isEmpty() ? new File(path).getName() : title;
        try {
            synchronized (LOCK) {
                releaseLocked();
                player = new MediaPlayer();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    player.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build());
                }
                player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
                player.setDataSource(path);
                player.setOnCompletionListener(mp -> stopPlayback(true));
                player.prepare();
                player.start();
                state = STATE_PLAYING;
            }
            startForeground(NOTIFICATION_ID, buildNotification());
            startProgressTicker();
            broadcastState();
        } catch (Exception e) {
            stopPlayback(true);
        }
    }

    private void pause() {
        synchronized (LOCK) {
            if (player == null || state != STATE_PLAYING) return;
            player.pause();
            state = STATE_PAUSED;
        }
        refreshNotification();
        broadcastState();
    }

    private void resume() {
        synchronized (LOCK) {
            if (player == null || state != STATE_PAUSED) return;
            player.start();
            state = STATE_PLAYING;
        }
        refreshNotification();
        startProgressTicker();
        broadcastState();
    }

    private void stopPlayback(boolean stopSelf) {
        synchronized (LOCK) {
            releaseLocked();
            state = STATE_IDLE;
        }
        handler.removeCallbacks(progressTick);
        stopForeground(true);
        broadcastState();
        if (stopSelf) stopSelf();
    }

    private static void releaseLocked() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) { }
            try {
                player.release();
            } catch (Exception ignored) { }
            player = null;
        }
    }

    private void startProgressTicker() {
        handler.removeCallbacks(progressTick);
        handler.post(progressTick);
    }

    private void refreshNotification() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, buildNotification());
    }

    private Notification buildNotification() {
        createNotificationChannel();
        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(this, 0, openIntent, pendingFlags());

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(getApplicationInfo().icon)
                .setContentTitle("Hermes TTS")
                .setContentText(isPlaying() ? "Playing: " + currentTitle : "Paused: " + currentTitle)
                .setContentIntent(openPending)
                .setOngoing(isPlaying())
                .setShowWhen(false)
                .setOnlyAlertOnce(true);

        if (isPlaying()) builder.addAction(0, "Pause", servicePending(ACTION_PAUSE, 1));
        else builder.addAction(0, "Play", servicePending(ACTION_RESUME, 2));
        builder.addAction(0, "Stop", servicePending(ACTION_STOP, 3));
        return builder.build();
    }

    private PendingIntent servicePending(String action, int requestCode) {
        Intent intent = new Intent(this, PlayerService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, intent, pendingFlags());
    }

    private int pendingFlags() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel existing = manager.getNotificationChannel(CHANNEL_ID);
        if (existing != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Hermes TTS playback", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Background playback controls for generated TTS audio");
        manager.createNotificationChannel(channel);
    }

    private void broadcastState() {
        Intent intent = new Intent(ACTION_STATE_CHANGED).setPackage(getPackageName());
        sendBroadcast(intent);
    }

    public static boolean isActive() {
        synchronized (LOCK) {
            return player != null && state != STATE_IDLE;
        }
    }

    public static boolean isPlaying() {
        synchronized (LOCK) {
            return player != null && state == STATE_PLAYING;
        }
    }

    public static boolean isPaused() {
        synchronized (LOCK) {
            return player != null && state == STATE_PAUSED;
        }
    }

    public static int positionMs() {
        synchronized (LOCK) {
            if (player == null) return 0;
            try {
                return player.getCurrentPosition();
            } catch (Exception ignored) {
                return 0;
            }
        }
    }

    public static int durationMs() {
        synchronized (LOCK) {
            if (player == null) return 0;
            try {
                return player.getDuration();
            } catch (Exception ignored) {
                return 0;
            }
        }
    }
}
