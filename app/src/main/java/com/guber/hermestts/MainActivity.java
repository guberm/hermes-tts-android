package com.guber.hermestts;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS = "hermes_tts_prefs";
    private static final String DEFAULT_URL = "";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private EditText apiUrlEdit;
    private EditText apiKeyEdit;
    private EditText textEdit;
    private EditText speedEdit;
    private Spinner voiceSpinner;
    private Spinner formatSpinner;
    private CheckBox sendProviderCheck;
    private Button generateButton;
    private Button playButton;
    private Button shareButton;
    private ProgressBar progressBar;
    private TextView statusView;
    private File lastAudioFile;
    private MediaPlayer mediaPlayer;
    private List<VoiceOption> voices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        voices = buildVoices();
        buildUi();
        handleIncomingIntent(getIntent());
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
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(24));
        root.setBackgroundColor(Color.rgb(247, 247, 251));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Hermes TTS");
        title.setTextSize(26);
        title.setTextColor(Color.rgb(30, 32, 45));
        title.setGravity(Gravity.START);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Вставь текст или расшарь его сюда из другого приложения. API: Hermes /v1/audio/speech.");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.rgb(90, 93, 112));
        subtitle.setPadding(0, 0, 0, dp(16));
        root.addView(subtitle, matchWrap());

        apiUrlEdit = edit("Hermes API URL (https://host/v1 or /v1/audio/speech)", prefs.getString("api_url", DEFAULT_URL), false);
        root.addView(label("API URL"));
        root.addView(apiUrlEdit, matchWrap());

        apiKeyEdit = edit("Bearer API key", prefs.getString("api_key", ""), false);
        apiKeyEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(label("API key"));
        root.addView(apiKeyEdit, matchWrap());

        textEdit = edit("Text to synthesize", prefs.getString("last_text", ""), true);
        textEdit.setMinLines(7);
        textEdit.setGravity(Gravity.TOP | Gravity.START);
        root.addView(label("Text"));
        root.addView(textEdit, matchWrap());

        root.addView(label("Voice"));
        voiceSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, voiceLabels());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        voiceSpinner.setAdapter(adapter);
        int savedVoice = clamp(prefs.getInt("voice_index", 0), 0, voices.size() - 1);
        voiceSpinner.setSelection(savedVoice);
        voiceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { saveSettingsOnly(); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        root.addView(voiceSpinner, matchWrap());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, 0);

        LinearLayout formatBox = new LinearLayout(this);
        formatBox.setOrientation(LinearLayout.VERTICAL);
        formatBox.addView(label("Format"));
        formatSpinner = new Spinner(this);
        ArrayAdapter<String> formatAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"mp3", "ogg/opus"});
        formatSpinner.setAdapter(formatAdapter);
        formatSpinner.setSelection(prefs.getInt("format_index", 0));
        formatBox.addView(formatSpinner, matchWrap());
        row.addView(formatBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout speedBox = new LinearLayout(this);
        speedBox.setOrientation(LinearLayout.VERTICAL);
        speedBox.setPadding(dp(10), 0, 0, 0);
        speedBox.addView(label("Speed"));
        speedEdit = edit("1.0", prefs.getString("speed", "1.0"), false);
        speedEdit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        speedBox.addView(speedEdit, matchWrap());
        row.addView(speedBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(row, matchWrap());

        sendProviderCheck = new CheckBox(this);
        sendProviderCheck.setText("Send provider with voice (edge/openai/etc.)");
        sendProviderCheck.setTextColor(Color.rgb(70, 73, 92));
        sendProviderCheck.setChecked(prefs.getBoolean("send_provider", true));
        root.addView(sendProviderCheck, matchWrap());

        generateButton = button("Generate audio");
        generateButton.setOnClickListener(v -> generateAudio());
        root.addView(generateButton, matchWrapWithTop(14));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        playButton = button("Play");
        playButton.setEnabled(false);
        playButton.setOnClickListener(v -> playLastAudio());
        shareButton = button("Share audio");
        shareButton.setEnabled(false);
        shareButton.setOnClickListener(v -> shareLastAudio());
        actionRow.addView(playButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        shareParams.leftMargin = dp(10);
        actionRow.addView(shareButton, shareParams);
        root.addView(actionRow, matchWrapWithTop(10));

        progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, matchWrapWithTop(10));

        statusView = new TextView(this);
        statusView.setText("Ready");
        statusView.setTextSize(13);
        statusView.setTextColor(Color.rgb(90, 93, 112));
        statusView.setPadding(0, dp(10), 0, 0);
        root.addView(statusView, matchWrap());

        setContentView(scroll);
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
            saveSettingsOnly();
        }
    }

    private void generateAudio() {
        saveSettingsOnly();
        String url = normalizeSpeechUrl(apiUrlEdit.getText().toString().trim());
        String key = apiKeyEdit.getText().toString().trim();
        String text = textEdit.getText().toString();
        if (url.isEmpty()) {
            toast("Set Hermes API URL first");
            apiUrlEdit.requestFocus();
            return;
        }
        if (key.isEmpty()) {
            toast("Set API key first");
            apiKeyEdit.requestFocus();
            return;
        }
        if (text.trim().isEmpty()) {
            toast("Text is empty");
            textEdit.requestFocus();
            return;
        }
        VoiceOption voice = voices.get(voiceSpinner.getSelectedItemPosition());
        String format = formatSpinner.getSelectedItemPosition() == 1 ? "ogg" : "mp3";
        double speed;
        try {
            speed = Double.parseDouble(speedEdit.getText().toString().trim());
            if (speed <= 0.0 || speed > 4.0) throw new NumberFormatException();
        } catch (Exception e) {
            toast("Speed must be between 0 and 4");
            speedEdit.requestFocus();
            return;
        }

        setLoading(true);
        setStatus("Generating with " + voice.label + "...");
        executor.execute(() -> {
            try {
                File audio = callTts(url, key, text, voice, format, speed, sendProviderCheck.isChecked());
                runOnUiThread(() -> {
                    lastAudioFile = audio;
                    setLoading(false);
                    playButton.setEnabled(true);
                    shareButton.setEnabled(true);
                    setStatus("Saved: " + audio.getName() + " (" + audio.length() + " bytes)");
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    setLoading(false);
                    setStatus("Error: " + ex.getMessage());
                    toast("TTS failed: " + ex.getMessage());
                });
            }
        });
    }

    private File callTts(String speechUrl, String apiKey, String text, VoiceOption voice, String format, double speed, boolean sendProvider) throws Exception {
        URL url = new URL(speechUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(20_000);
        conn.setReadTimeout(180_000);
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", format.equals("ogg") ? "audio/ogg, application/json" : "audio/mpeg, application/json");
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
        String ext = format.equals("ogg") ? "ogg" : "mp3";
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

    private String buildJson(String text, VoiceOption voice, String format, double speed, boolean sendProvider) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"input\":").append(json(text));
        if (!voice.voice.isEmpty()) {
            sb.append(",\"voice\":").append(json(voice.voice));
        }
        if (sendProvider && !voice.provider.isEmpty()) {
            sb.append(",\"provider\":").append(json(voice.provider));
        }
        if (!voice.model.isEmpty()) {
            sb.append(",\"model\":").append(json(voice.model));
        }
        sb.append(",\"response_format\":").append(json(format));
        sb.append(",\"speed\":").append(String.format(Locale.US, "%.3f", speed));
        sb.append('}');
        return sb.toString();
    }

    private void playLastAudio() {
        if (lastAudioFile == null || !lastAudioFile.exists()) {
            toast("No generated audio yet");
            return;
        }
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(lastAudioFile.getAbsolutePath());
            mediaPlayer.setOnCompletionListener(mp -> setStatus("Playback finished."));
            mediaPlayer.prepare();
            mediaPlayer.start();
            setStatus("Playing " + lastAudioFile.getName());
        } catch (Exception e) {
            toast("Cannot play: " + e.getMessage());
            setStatus("Playback error: " + e.getMessage());
        }
    }

    private void shareLastAudio() {
        if (lastAudioFile == null || !lastAudioFile.exists()) {
            toast("No generated audio yet");
            return;
        }
        Uri uri = Uri.parse("content://" + getPackageName() + ".fileprovider/" + Uri.encode(lastAudioFile.getName()));
        String type = lastAudioFile.getName().endsWith(".ogg") ? "audio/ogg" : "audio/mpeg";
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(type);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_SUBJECT, "Hermes TTS audio");
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newUri(getContentResolver(), lastAudioFile.getName(), uri));
        startActivity(Intent.createChooser(send, "Share generated audio"));
    }

    private String normalizeSpeechUrl(String raw) {
        String s = raw == null ? "" : raw.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty()) return "";
        if (s.endsWith("/v1/audio/speech")) return s;
        if (s.endsWith("/v1")) return s + "/audio/speech";
        return s + "/v1/audio/speech";
    }

    private void saveSettingsOnly() {
        if (prefs == null || apiUrlEdit == null) return;
        prefs.edit()
                .putString("api_url", apiUrlEdit.getText().toString().trim())
                .putString("api_key", apiKeyEdit.getText().toString().trim())
                .putString("last_text", textEdit.getText().toString())
                .putString("speed", speedEdit.getText().toString().trim())
                .putInt("voice_index", voiceSpinner == null ? 0 : voiceSpinner.getSelectedItemPosition())
                .putInt("format_index", formatSpinner == null ? 0 : formatSpinner.getSelectedItemPosition())
                .putBoolean("send_provider", sendProviderCheck == null || sendProviderCheck.isChecked())
                .apply();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        generateButton.setEnabled(!loading);
        playButton.setEnabled(!loading && lastAudioFile != null);
        shareButton.setEnabled(!loading && lastAudioFile != null);
    }

    private void setStatus(String status) {
        statusView.setText(status);
    }

    private EditText edit(String hint, String value, boolean multiLine) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setText(value == null ? "" : value);
        edit.setTextSize(15);
        edit.setSingleLine(!multiLine);
        edit.setPadding(dp(12), dp(8), dp(12), dp(8));
        edit.setBackgroundColor(Color.WHITE);
        if (multiLine) {
            edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        }
        return edit;
    }

    private TextView label(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(12);
        label.setTextColor(Color.rgb(80, 84, 105));
        label.setPadding(0, dp(10), 0, dp(4));
        return label;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
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

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private List<String> voiceLabels() {
        List<String> labels = new ArrayList<>();
        for (VoiceOption voice : voices) labels.add(voice.label);
        return labels;
    }

    private List<VoiceOption> buildVoices() {
        List<VoiceOption> v = new ArrayList<>();
        v.add(new VoiceOption("Edge - Guy (US male)", "edge", "en-US-GuyNeural", ""));
        v.add(new VoiceOption("Edge - Aria (US female)", "edge", "en-US-AriaNeural", ""));
        v.add(new VoiceOption("Edge - Jenny (US female)", "edge", "en-US-JennyNeural", ""));
        v.add(new VoiceOption("Edge - Andrew (US male)", "edge", "en-US-AndrewNeural", ""));
        v.add(new VoiceOption("Edge - Brian (US male)", "edge", "en-US-BrianNeural", ""));
        v.add(new VoiceOption("Edge - Sonia (UK female)", "edge", "en-GB-SoniaNeural", ""));
        v.add(new VoiceOption("Edge - Dmitry (RU male)", "edge", "ru-RU-DmitryNeural", ""));
        v.add(new VoiceOption("Edge - Svetlana (RU female)", "edge", "ru-RU-SvetlanaNeural", ""));
        v.add(new VoiceOption("OpenAI - alloy", "openai", "alloy", "gpt-4o-mini-tts"));
        v.add(new VoiceOption("OpenAI - nova", "openai", "nova", "gpt-4o-mini-tts"));
        v.add(new VoiceOption("OpenAI - onyx", "openai", "onyx", "gpt-4o-mini-tts"));
        v.add(new VoiceOption("OpenAI - shimmer", "openai", "shimmer", "gpt-4o-mini-tts"));
        v.add(new VoiceOption("xAI - eve", "xai", "eve", ""));
        v.add(new VoiceOption("Mistral - Paul", "mistral", "c69964a6-ab8b-4f8a-9465-ec0925096ec8", "voxtral-mini-tts-2603"));
        v.add(new VoiceOption("Hermes default voice", "", "", ""));
        return v;
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

    private static class VoiceOption {
        final String label;
        final String provider;
        final String voice;
        final String model;
        VoiceOption(String label, String provider, String voice, String model) {
            this.label = label;
            this.provider = provider;
            this.voice = voice;
            this.model = model;
        }
    }
}
