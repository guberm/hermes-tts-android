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
        final int surfaceElevated;
        final int text;
        final int secondary;
        final int hint;
        final int accent;
        final int accentAlt;
        final int border;
        final int success;
        final int buttonText;

        private Palette(int background, int surface, int surfaceElevated, int text, int secondary,
                        int hint, int accent, int accentAlt, int border, int success, int buttonText) {
            this.background = background;
            this.surface = surface;
            this.surfaceElevated = surfaceElevated;
            this.text = text;
            this.secondary = secondary;
            this.hint = hint;
            this.accent = accent;
            this.accentAlt = accentAlt;
            this.border = border;
            this.success = success;
            this.buttonText = buttonText;
        }

        static Palette light() {
            return new Palette(
                    Color.rgb(245, 247, 252),
                    Color.WHITE,
                    Color.rgb(238, 241, 249),
                    Color.rgb(22, 25, 38),
                    Color.rgb(82, 88, 110),
                    Color.rgb(126, 134, 158),
                    Color.rgb(91, 108, 255),
                    Color.rgb(111, 72, 255),
                    Color.rgb(220, 225, 238),
                    Color.rgb(19, 170, 108),
                    Color.WHITE
            );
        }

        static Palette dark() {
            return new Palette(
                    Color.rgb(8, 9, 14),
                    Color.rgb(25, 27, 36),
                    Color.rgb(35, 38, 50),
                    Color.rgb(247, 248, 252),
                    Color.rgb(200, 206, 222),
                    Color.rgb(138, 145, 166),
                    Color.rgb(126, 141, 255),
                    Color.rgb(179, 121, 255),
                    Color.rgb(58, 63, 82),
                    Color.rgb(29, 214, 139),
                    Color.WHITE
            );
        }
    }
}
