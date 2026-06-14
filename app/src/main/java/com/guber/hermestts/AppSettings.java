package com.guber.hermestts;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

final class AppSettings {
    static final String PREFS = "hermes_tts_prefs";

    private AppSettings() { }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isDark(Context context) {
        int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    static Palette palette(Context context) {
        return isDark(context) ? Palette.dark() : Palette.light();
    }

    static List<VoiceOption> voices() {
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

    static List<String> voiceLabels() {
        List<String> labels = new ArrayList<>();
        for (VoiceOption voice : voices()) labels.add(voice.label);
        return labels;
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static final class VoiceOption {
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

    static final class Palette {
        final int background;
        final int surface;
        final int text;
        final int secondary;
        final int hint;
        final int accent;

        private Palette(int background, int surface, int text, int secondary, int hint, int accent) {
            this.background = background;
            this.surface = surface;
            this.text = text;
            this.secondary = secondary;
            this.hint = hint;
            this.accent = accent;
        }

        static Palette light() {
            return new Palette(
                    Color.rgb(247, 247, 251),
                    Color.WHITE,
                    Color.rgb(30, 32, 45),
                    Color.rgb(86, 90, 112),
                    Color.rgb(120, 124, 145),
                    Color.rgb(91, 108, 255)
            );
        }

        static Palette dark() {
            return new Palette(
                    Color.rgb(16, 18, 24),
                    Color.rgb(31, 34, 43),
                    Color.rgb(241, 243, 248),
                    Color.rgb(181, 186, 203),
                    Color.rgb(139, 145, 165),
                    Color.rgb(126, 141, 255)
            );
        }
    }
}
