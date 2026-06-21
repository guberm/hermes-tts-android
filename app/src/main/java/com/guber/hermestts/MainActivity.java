package com.guber.hermestts;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private AppSettings.Palette palette;
    private EditText textEdit;
    private EditText speedEdit;
    private Spinner voiceSpinner;
    private Button generateButton;
    private Button playButton;
    private Button shareButton;
    private Button playerStopButton;
    private Button playerPauseButton;
    private Button playerResumeButton;
    private LinearLayout playerPanel;
    private ProgressBar progressBar;
    private TextView statusView;
    private TextView settingsSummaryView;
    private TextView playerTimeView;
    private File lastAudioFile;
    private List<AppSettings.VoiceOption> voices;
    private boolean playerReceiverRegistered = false;
    private boolean ignoreInitialVoiceSelection = false;

    private final BroadcastReceiver playerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updatePlayerUi();
        }
    };

    private final Runnable playerTicker = new Runnable() {
        @Override
        public void run() {
            updatePlayerUi();
            uiHandler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = AppSettings.prefs(this);
        palette = AppSettings.palette(this);
        voices = AppSettings.voices();
        buildUi();
        maybeRequestNotificationPermission();
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerPlayerReceiver();
        uiHandler.post(playerTicker);
        if (settingsSummaryView != null) updateSettingsSummary();
        updatePlayerUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        uiHandler.removeCallbacks(playerTicker);
        unregisterPlayerReceiver();
        saveMainAudioPrefs(false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(palette.background);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        root.setBackgroundColor(palette.background);
        scroll.addView(root);

        LinearLayout hero = card(palette.surface, 24, 0);
        hero.setBackground(gradient(new int[]{palette.accentAlt, palette.accent, palette.surface}, 24));
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = text("VOICE LAB", 12, palette.buttonText, Typeface.BOLD);
        badge.setPadding(dp(10), dp(5), dp(10), dp(5));
        badge.setBackground(rounded(0x33FFFFFF, 999, 0x44FFFFFF));
        topRow.addView(badge, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button settingsButton = ghostButton("Settings");
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        topRow.addView(settingsButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        hero.addView(topRow, matchWrap());

        TextView title = text("Hermes TTS", 34, palette.buttonText, Typeface.BOLD);
        title.setPadding(0, dp(20), 0, dp(6));
        hero.addView(title, matchWrap());

        TextView subtitle = text("Turn text into polished speech. Paste, generate, play, and share audio from one focused mobile workflow.", 15, 0xE8FFFFFF, Typeface.NORMAL);
        subtitle.setLineSpacing(dp(2), 1.0f);
        hero.addView(subtitle, matchWrap());
        root.addView(hero, matchWrap());

        settingsSummaryView = text("", 13, palette.secondary, Typeface.NORMAL);
        settingsSummaryView.setPadding(dp(14), dp(12), dp(14), dp(12));
        settingsSummaryView.setBackground(rounded(palette.surface, 18, palette.border));
        root.addView(settingsSummaryView, matchWrapWithTop(14));
        updateSettingsSummary();

        LinearLayout audioCard = card(palette.surface, 22, palette.border);
        audioCard.addView(sectionTitle("Voice and speed"), matchWrap());
        audioCard.addView(label("Voice"));
        voiceSpinner = new Spinner(this);
        ArrayAdapter<String> voiceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, AppSettings.voiceLabels());
        voiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        voiceSpinner.setAdapter(voiceAdapter);
        ignoreInitialVoiceSelection = true;
        voiceSpinner.setSelection(AppSettings.clamp(prefs.getInt("voice_index", 0), 0, voices.size() - 1));
        voiceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (ignoreInitialVoiceSelection) {
                    ignoreInitialVoiceSelection = false;
                    return;
                }
                prefs.edit().putInt("voice_index", position).apply();
                updateSettingsSummary();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        audioCard.addView(voiceSpinner, matchWrap());
        audioCard.addView(label("Speed"));
        speedEdit = edit("1.0", prefs.getString("speed", "1.0"), false);
        speedEdit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        speedEdit.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) saveMainAudioPrefs(false);
        });
        audioCard.addView(speedEdit, matchWrap());
        root.addView(audioCard, matchWrapWithTop(14));

        LinearLayout inputCard = card(palette.surface, 22, palette.border);
        LinearLayout inputTitleRow = new LinearLayout(this);
        inputTitleRow.setOrientation(LinearLayout.HORIZONTAL);
        inputTitleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView inputTitle = text("Text", 16, palette.text, Typeface.BOLD);
        inputTitleRow.addView(inputTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button cleanButton = secondaryButton("Clean");
        cleanButton.setOnClickListener(v -> clearTextWindow());
        inputTitleRow.addView(cleanButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        inputCard.addView(inputTitleRow, matchWrap());
        TextView inputHint = text("Share text here from any app or type directly below.", 13, palette.secondary, Typeface.NORMAL);
        inputHint.setPadding(0, dp(4), 0, dp(10));
        inputCard.addView(inputHint, matchWrap());
        textEdit = edit("Text to synthesize", prefs.getString("last_text", ""), true);
        textEdit.setMinLines(10);
        textEdit.setGravity(Gravity.TOP | Gravity.START);
        inputCard.addView(textEdit, matchWrap());
        root.addView(inputCard, matchWrapWithTop(14));

        generateButton = primaryButton("Generate audio");
        generateButton.setOnClickListener(v -> generateAudio());
        root.addView(generateButton, matchWrapWithTop(14));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        playButton = secondaryButton("Play");
        playButton.setEnabled(false);
        playButton.setOnClickListener(v -> togglePlayback());
        shareButton = secondaryButton("Share audio");
        shareButton.setEnabled(false);
        shareButton.setOnClickListener(v -> shareLastAudio());
        actionRow.addView(playButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        shareParams.leftMargin = dp(10);
        actionRow.addView(shareButton, shareParams);
        root.addView(actionRow, matchWrapWithTop(10));

        playerPanel = card(palette.surface, 22, palette.border);
        playerPanel.setVisibility(View.GONE);
        playerPanel.addView(sectionTitle("Player"), matchWrap());
        playerTimeView = text("00:00 / 00:00", 18, palette.text, Typeface.BOLD);
        playerTimeView.setGravity(Gravity.CENTER);
        playerTimeView.setPadding(0, dp(8), 0, dp(8));
        playerPanel.addView(playerTimeView, matchWrap());
        LinearLayout playerControls = new LinearLayout(this);
        playerControls.setOrientation(LinearLayout.HORIZONTAL);
        playerStopButton = secondaryButton("Stop");
        playerStopButton.setOnClickListener(v -> stopPlayback());
        playerPauseButton = secondaryButton("Pause");
        playerPauseButton.setOnClickListener(v -> pausePlayback());
        playerResumeButton = secondaryButton("Play");
        playerResumeButton.setOnClickListener(v -> resumePlayback());
        playerControls.addView(playerStopButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams pauseParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        pauseParams.leftMargin = dp(8);
        playerControls.addView(playerPauseButton, pauseParams);
        LinearLayout.LayoutParams resumeParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        resumeParams.leftMargin = dp(8);
        playerControls.addView(playerResumeButton, resumeParams);
        playerPanel.addView(playerControls, matchWrapWithTop(8));
        root.addView(playerPanel, matchWrapWithTop(10));

        progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, matchWrapWithTop(12));

        statusView = text("Ready", 13, palette.secondary, Typeface.NORMAL);
        statusView.setPadding(dp(2), dp(10), dp(2), 0);
        root.addView(statusView, matchWrap());

        setContentView(scroll);
        updatePlayerUi();
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        CharSequence incoming = null;
        if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(intent.getType())) {
            incoming = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        } else if (Intent.ACTION_PROCESS_TEXT.equals(action)) {
            incoming = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        }
        if (incoming != null && incoming.length() > 0) {
            textEdit.setText(incoming.toString());
            textEdit.setSelection(textEdit.getText().length());
            setStatus("Text received from Android share sheet.");
            saveTextOnly();
        }
    }

    private void generateAudio() {
        if (!saveMainAudioPrefs(true)) return;
        saveTextOnly();
        String speechUrl = normalizeSpeechUrl(prefs.getString("api_url", ""));
        String apiKey = prefs.getString("api_key", "").trim();
        String text = textEdit.getText().toString();
        if (speechUrl.isEmpty() || apiKey.isEmpty()) {
            toast("Open Settings and enter the Hermes API URL and API key first.");
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        if (text.trim().isEmpty()) {
            toast("Text is empty");
            textEdit.requestFocus();
            return;
        }

        int voiceIndex = AppSettings.clamp(prefs.getInt("voice_index", 0), 0, voices.size() - 1);
        AppSettings.VoiceOption voice = voices.get(voiceIndex);
        String format = formatFromPrefs();
        boolean sendProvider = prefs.getBoolean("send_provider", true);
        double speed;
        try {
            speed = Double.parseDouble(prefs.getString("speed", "1.0"));
            if (speed <= 0.0 || speed > 4.0) throw new NumberFormatException();
        } catch (Exception e) {
            toast("Set speed between 0 and 4.");
            speedEdit.requestFocus();
            return;
        }

        setLoading(true);
        setStatus("Generating with " + voice.label + "...");
        executor.execute(() -> {
            try {
                File audio = callTts(speechUrl, apiKey, text, voice, format, speed, sendProvider);
                runOnUiThread(() -> {
                    lastAudioFile = audio;
                    setLoading(false);
                    playButton.setEnabled(true);
                    shareButton.setEnabled(true);
                    setStatus("Saved: " + audio.getName() + " (" + audio.length() + " bytes)");
                    updatePlayerUi();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    setLoading(false);
                    setStatus("Error: " + ex.getMessage());
                    toast("TTS failed: " + ex.getMessage());
                    updatePlayerUi();
                });
            }
        });
    }

    private File callTts(String speechUrl, String apiKey, String text, AppSettings.VoiceOption voice, String format, double speed, boolean sendProvider) throws Exception {
        URL url = new URL(speechUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(20_000);
        conn.setReadTimeout(180_000);
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", acceptHeaderForFormat(format));
        String body = buildJson(text, voice, format, speed, sendProvider);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        InputStream raw = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        byte[] response = readAll(raw);
        if (code < 200 || code >= 300) {
            String err = new String(response, StandardCharsets.UTF_8);
            if (err.length() > 500) err = err.substring(0, 500);
            throw new Exception("HTTP " + code + ": " + err);
        }
        String ext = extensionForResponse(format, conn.getContentType());
        File dir = new File(getCacheDir(), "generated_audio");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Cannot create cache dir");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File file = new File(dir, "hermes_tts_" + stamp + "." + ext);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(response);
        }
        if (file.length() <= 0) throw new Exception("Empty audio response");
        return file;
    }

    private String buildJson(String text, AppSettings.VoiceOption voice, String format, double speed, boolean sendProvider) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"input\":").append(json(text));
        if (!voice.voice.isEmpty()) sb.append(",\"voice\":").append(json(voice.voice));
        if (sendProvider && !voice.provider.isEmpty()) sb.append(",\"provider\":").append(json(voice.provider));
        if (!voice.model.isEmpty()) sb.append(",\"model\":").append(json(voice.model));
        sb.append(",\"response_format\":").append(json(format));
        sb.append(",\"speed\":").append(String.format(Locale.US, "%.3f", speed));
        sb.append('}');
        return sb.toString();
    }

    private void togglePlayback() {
        if (PlayerService.isActive()) stopPlayback();
        else startPlayback();
    }

    private void startPlayback() {
        if (lastAudioFile == null || !lastAudioFile.exists()) {
            toast("No generated audio yet");
            return;
        }
        Intent intent = new Intent(this, PlayerService.class)
                .setAction(PlayerService.ACTION_PLAY)
                .putExtra(PlayerService.EXTRA_PATH, lastAudioFile.getAbsolutePath())
                .putExtra(PlayerService.EXTRA_TITLE, lastAudioFile.getName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
        else startService(intent);
        playerPanel.setVisibility(View.VISIBLE);
        playButton.setText("Stop");
        setStatus("Playing " + lastAudioFile.getName());
        uiHandler.postDelayed(this::updatePlayerUi, 300);
    }

    private void pausePlayback() {
        startService(new Intent(this, PlayerService.class).setAction(PlayerService.ACTION_PAUSE));
        uiHandler.postDelayed(this::updatePlayerUi, 150);
    }

    private void resumePlayback() {
        startService(new Intent(this, PlayerService.class).setAction(PlayerService.ACTION_RESUME));
        uiHandler.postDelayed(this::updatePlayerUi, 150);
    }

    private void stopPlayback() {
        startService(new Intent(this, PlayerService.class).setAction(PlayerService.ACTION_STOP));
        setStatus("Playback stopped.");
        uiHandler.postDelayed(this::updatePlayerUi, 150);
    }

    private void shareLastAudio() {
        if (lastAudioFile == null || !lastAudioFile.exists()) {
            toast("No generated audio yet");
            return;
        }
        Uri uri = Uri.parse("content://" + getPackageName() + ".fileprovider/" + Uri.encode(lastAudioFile.getName()));
        String type = mimeForFile(lastAudioFile);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(type);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_SUBJECT, "Hermes TTS audio");
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newUri(getContentResolver(), lastAudioFile.getName(), uri));
        startActivity(Intent.createChooser(send, "Share generated audio"));
    }

    private void updateSettingsSummary() {
        int voiceIndex = AppSettings.clamp(prefs.getInt("voice_index", 0), 0, voices.size() - 1);
        String format = labelForFormat(formatFromPrefs());
        String api = prefs.getString("api_url", "").trim().isEmpty() ? "not set" : "set";
        String key = prefs.getString("api_key", "").trim().isEmpty() ? "not set" : "set";
        String speed = prefs.getString("speed", "1.0");
        settingsSummaryView.setText("Settings  •  " + voices.get(voiceIndex).label + "  •  " + format + "  •  speed " + speed + "  •  API " + api + "  •  key " + key);
    }

    private void updatePlayerUi() {
        if (playButton == null || playerPanel == null) return;
        boolean active = PlayerService.isActive();
        boolean playing = PlayerService.isPlaying();
        boolean paused = PlayerService.isPaused();
        playButton.setText(active ? "Stop" : "Play");
        playButton.setEnabled(!progressBar.isShown() && (active || lastAudioFile != null));
        shareButton.setEnabled(!progressBar.isShown() && lastAudioFile != null);
        playerPanel.setVisibility(active ? View.VISIBLE : View.GONE);
        if (playerPauseButton != null) playerPauseButton.setEnabled(playing);
        if (playerResumeButton != null) playerResumeButton.setEnabled(paused);
        if (playerStopButton != null) playerStopButton.setEnabled(active);
        if (playerTimeView != null) {
            int pos = PlayerService.positionMs();
            int dur = PlayerService.durationMs();
            playerTimeView.setText(formatTime(pos) + " / " + formatTime(dur));
        }
        if (active && playing) setStatus("Playing audio...");
        else if (active && paused) setStatus("Playback paused.");
    }

    private void clearTextWindow() {
        textEdit.setText("");
        saveTextOnly();
        setStatus("Text cleared.");
    }

    private boolean saveMainAudioPrefs(boolean showErrors) {
        double speed;
        try {
            speed = Double.parseDouble(speedEdit.getText().toString().trim());
            if (speed <= 0.0 || speed > 4.0) throw new NumberFormatException();
        } catch (Exception e) {
            if (showErrors) {
                toast("Speed must be between 0 and 4");
                speedEdit.requestFocus();
            }
            return false;
        }
        prefs.edit()
                .putInt("voice_index", voiceSpinner.getSelectedItemPosition())
                .putString("speed", String.valueOf(speed))
                .apply();
        updateSettingsSummary();
        return true;
    }

    private String normalizeSpeechUrl(String raw) {
        String s = raw == null ? "" : raw.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty()) return "";
        if (s.endsWith("/v1/audio/speech")) return s;
        if (s.endsWith("/v1")) return s + "/audio/speech";
        return s + "/v1/audio/speech";
    }

    private String formatFromPrefs() {
        int index = AppSettings.clamp(prefs.getInt("format_index", 0), 0, 2);
        if (index == 1) return "ogg";
        if (index == 2) return "wav";
        return "mp3";
    }

    private String labelForFormat(String format) {
        if ("ogg".equals(format)) return "ogg/opus";
        if ("wav".equals(format)) return "wav";
        return "mp3";
    }

    private String acceptHeaderForFormat(String format) {
        if ("ogg".equals(format)) return "audio/ogg, audio/wav, audio/mpeg, application/json";
        if ("wav".equals(format)) return "audio/wav, audio/ogg, audio/mpeg, application/json";
        return "audio/mpeg, audio/wav, audio/ogg, application/json";
    }

    private String extensionForResponse(String requestedFormat, String contentType) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.US);
        if (ct.contains("wav") || ct.contains("wave")) return "wav";
        if (ct.contains("ogg") || ct.contains("opus")) return "ogg";
        if (ct.contains("flac")) return "flac";
        if (ct.contains("mpeg") || ct.contains("mp3")) return "mp3";
        return requestedFormat;
    }

    private String mimeForFile(File file) {
        String name = file.getName().toLowerCase(Locale.US);
        if (name.endsWith(".ogg")) return "audio/ogg";
        if (name.endsWith(".wav")) return "audio/wav";
        if (name.endsWith(".flac")) return "audio/flac";
        return "audio/mpeg";
    }

    private void saveTextOnly() {
        prefs.edit().putString("last_text", textEdit.getText().toString()).apply();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        generateButton.setEnabled(!loading);
        playButton.setEnabled(!loading && (PlayerService.isActive() || lastAudioFile != null));
        shareButton.setEnabled(!loading && lastAudioFile != null);
    }

    private void setStatus(String status) {
        statusView.setText(status);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerPlayerReceiver() {
        if (playerReceiverRegistered) return;
        IntentFilter filter = new IntentFilter(PlayerService.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(playerReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(playerReceiver, filter);
        playerReceiverRegistered = true;
    }

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1007);
        }
    }

    private void unregisterPlayerReceiver() {
        if (!playerReceiverRegistered) return;
        try {
            unregisterReceiver(playerReceiver);
        } catch (Exception ignored) { }
        playerReceiverRegistered = false;
    }

    private String formatTime(int ms) {
        if (ms < 0) ms = 0;
        int total = ms / 1000;
        int minutes = total / 60;
        int seconds = total % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private EditText edit(String hint, String value, boolean multiLine) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setText(value == null ? "" : value);
        edit.setTextSize(16);
        edit.setSingleLine(!multiLine);
        edit.setPadding(dp(14), dp(12), dp(14), dp(12));
        edit.setTextColor(palette.text);
        edit.setHintTextColor(palette.hint);
        edit.setBackground(rounded(palette.surfaceElevated, 16, palette.border));
        if (multiLine) {
            edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        }
        return edit;
    }

    private LinearLayout card(int color, int radiusDp, int strokeColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(color, radiusDp, strokeColor));
        return card;
    }

    private TextView sectionTitle(String value) {
        TextView title = text(value, 17, palette.text, Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(4));
        return title;
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private GradientDrawable gradient(int[] colors, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private TextView label(String value) {
        TextView label = text(value, 12, palette.secondary, Typeface.BOLD);
        label.setPadding(dp(2), dp(10), 0, dp(5));
        return label;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private Button primaryButton(String value) {
        Button button = button(value);
        button.setTextColor(palette.buttonText);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(gradient(new int[]{palette.accent, palette.accentAlt}, 18));
        return button;
    }

    private Button secondaryButton(String value) {
        Button button = button(value);
        button.setTextColor(palette.text);
        button.setBackground(rounded(palette.surfaceElevated, 16, palette.border));
        return button;
    }

    private Button ghostButton(String value) {
        Button button = button(value);
        button.setTextColor(palette.buttonText);
        button.setBackground(rounded(0x22FFFFFF, 999, 0x44FFFFFF));
        return button;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapWithTop(int topDp) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(topDp);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private byte[] readAll(InputStream input) throws Exception {
        if (input == null) return new byte[0];
        try (BufferedInputStream bis = new BufferedInputStream(input); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = bis.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    private String json(String value) {
        if (value == null) return "null";
        StringBuilder sb = new StringBuilder(value.length() + 16);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format(Locale.US, "\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
