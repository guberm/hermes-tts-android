package com.guber.hermestts;

import android.app.Activity;
import android.content.SharedPreferences;
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
        root.setPadding(dp(18), dp(18), dp(18), dp(24));
        root.setBackgroundColor(palette.background);
        scroll.addView(root);

        TextView title = text("Settings", 26, palette.text);
        title.setGravity(Gravity.START);
        root.addView(title, matchWrap());

        TextView subtitle = text("Configure the Hermes API connection and audio defaults.", 14, palette.secondary);
        subtitle.setPadding(0, dp(4), 0, dp(16));
        root.addView(subtitle, matchWrap());

        root.addView(label("Hermes API URL"));
        apiUrlEdit = edit("https://host/v1 or https://host/v1/audio/speech", prefs.getString("api_url", ""), false);
        root.addView(apiUrlEdit, matchWrap());

        root.addView(label("API key"));
        apiKeyEdit = edit("Bearer API key", prefs.getString("api_key", ""), false);
        apiKeyEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(apiKeyEdit, matchWrap());

        root.addView(label("Voice"));
        voiceSpinner = new Spinner(this);
        ArrayAdapter<String> voiceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, AppSettings.voiceLabels());
        voiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        voiceSpinner.setAdapter(voiceAdapter);
        voiceSpinner.setSelection(AppSettings.clamp(prefs.getInt("voice_index", 0), 0, voices.size() - 1));
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
        root.addView(row, matchWrap());

        sendProviderCheck = new CheckBox(this);
        sendProviderCheck.setText("Send provider with voice");
        sendProviderCheck.setTextColor(palette.text);
        sendProviderCheck.setChecked(prefs.getBoolean("send_provider", true));
        root.addView(sendProviderCheck, matchWrapWithTop(8));

        Button saveButton = button("Save settings");
        saveButton.setOnClickListener(v -> saveAndClose());
        root.addView(saveButton, matchWrapWithTop(18));

        Button cancelButton = button("Cancel");
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
        edit.setPadding(dp(12), dp(8), dp(12), dp(8));
        edit.setTextColor(palette.text);
        edit.setHintTextColor(palette.hint);
        edit.setBackgroundColor(palette.surface);
        return edit;
    }

    private TextView label(String value) {
        TextView label = text(value, 12, palette.secondary);
        label.setPadding(0, dp(10), 0, dp(4));
        return label;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
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
}
