package com.guber.hermestts;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class SettingsActivity extends Activity {
    private SharedPreferences prefs;
    private AppSettings.Palette palette;
    private EditText apiUrlEdit;
    private EditText apiKeyEdit;
    private EditText speedEdit;
    private Spinner voiceSpinner;
    private Spinner formatSpinner;
    private CheckBox sendProviderCheck;
    private List<AppSettings.VoiceOption> voices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = AppSettings.prefs(this);
        palette = AppSettings.palette(this);
        voices = AppSettings.voices();
        buildUi();
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
        hero.setBackground(gradient(new int[]{palette.surfaceElevated, palette.surface, palette.background}, 24));
        TextView badge = text("SETTINGS", 12, palette.accent, Typeface.BOLD);
        badge.setPadding(dp(10), dp(5), dp(10), dp(5));
        badge.setBackground(rounded(palette.surfaceElevated, 999, palette.border));
        hero.addView(badge, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView title = text("Voice setup", 31, palette.text, Typeface.BOLD);
        title.setPadding(0, dp(18), 0, dp(4));
        hero.addView(title, matchWrap());
        TextView subtitle = text("Connection, voice, and audio defaults live here. The main screen stays clean for writing and sharing.", 14, palette.secondary, Typeface.NORMAL);
        subtitle.setLineSpacing(dp(2), 1.0f);
        hero.addView(subtitle, matchWrap());
        root.addView(hero, matchWrap());

        LinearLayout connectionCard = card(palette.surface, 22, palette.border);
        connectionCard.addView(sectionTitle("Connection"), matchWrap());
        connectionCard.addView(label("Hermes API URL"));
        apiUrlEdit = edit("https://host/v1 or https://host/v1/audio/speech", prefs.getString("api_url", ""), false);
        connectionCard.addView(apiUrlEdit, matchWrap());
        connectionCard.addView(label("API key"));
        apiKeyEdit = edit("Bearer API key", prefs.getString("api_key", ""), false);
        apiKeyEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        connectionCard.addView(apiKeyEdit, matchWrap());
        root.addView(connectionCard, matchWrapWithTop(14));

        LinearLayout audioCard = card(palette.surface, 22, palette.border);
        audioCard.addView(sectionTitle("Audio defaults"), matchWrap());
        audioCard.addView(label("Voice"));
        voiceSpinner = new Spinner(this);
        ArrayAdapter<String> voiceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, AppSettings.voiceLabels());
        voiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        voiceSpinner.setAdapter(voiceAdapter);
        voiceSpinner.setSelection(AppSettings.clamp(prefs.getInt("voice_index", 0), 0, voices.size() - 1));
        audioCard.addView(voiceSpinner, matchWrap());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, 0);

        LinearLayout formatBox = new LinearLayout(this);
        formatBox.setOrientation(LinearLayout.VERTICAL);
        formatBox.addView(label("Format"));
        formatSpinner = new Spinner(this);
        ArrayAdapter<String> formatAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"mp3", "ogg/opus"});
        formatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        formatSpinner.setAdapter(formatAdapter);
        formatSpinner.setSelection(AppSettings.clamp(prefs.getInt("format_index", 0), 0, 1));
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
        audioCard.addView(row, matchWrap());

        sendProviderCheck = new CheckBox(this);
        sendProviderCheck.setText("Send provider with voice");
        sendProviderCheck.setTextColor(palette.text);
        sendProviderCheck.setChecked(prefs.getBoolean("send_provider", true));
        audioCard.addView(sendProviderCheck, matchWrapWithTop(8));
        root.addView(audioCard, matchWrapWithTop(14));

        Button saveButton = primaryButton("Save settings");
        saveButton.setOnClickListener(v -> saveAndClose());
        root.addView(saveButton, matchWrapWithTop(18));

        Button cancelButton = secondaryButton("Cancel");
        cancelButton.setOnClickListener(v -> finish());
        root.addView(cancelButton, matchWrapWithTop(8));

        setContentView(scroll);
    }

    private void saveAndClose() {
        double speed;
        try {
            speed = Double.parseDouble(speedEdit.getText().toString().trim());
            if (speed <= 0.0 || speed > 4.0) throw new NumberFormatException();
        } catch (Exception e) {
            Toast.makeText(this, "Speed must be between 0 and 4", Toast.LENGTH_LONG).show();
            speedEdit.requestFocus();
            return;
        }

        prefs.edit()
                .putString("api_url", apiUrlEdit.getText().toString().trim())
                .putString("api_key", apiKeyEdit.getText().toString().trim())
                .putString("speed", String.valueOf(speed))
                .putInt("voice_index", voiceSpinner.getSelectedItemPosition())
                .putInt("format_index", formatSpinner.getSelectedItemPosition())
                .putBoolean("send_provider", sendProviderCheck.isChecked())
                .apply();
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        finish();
    }

    private EditText edit(String hint, String value, boolean multiLine) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setText(value == null ? "" : value);
        edit.setTextSize(15);
        edit.setSingleLine(!multiLine);
        edit.setPadding(dp(14), dp(11), dp(14), dp(11));
        edit.setTextColor(palette.text);
        edit.setHintTextColor(palette.hint);
        edit.setBackground(rounded(palette.surfaceElevated, 16, palette.border));
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

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        return button;
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
}
