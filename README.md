# Hermes TTS Android

Private Android client for the Hermes Agent API Server TTS endpoint.

## Features

- English-only UI.
- Custom Hermes TTS app icon.
- Modern card-based UI with gradient hero, rounded surfaces, and polished action buttons.
- Manual text input for text-to-speech.
- Text card includes a **Clean** button for quickly clearing the current input.
- Accepts text from Android Share (`ACTION_SEND`) and selected text menu (`PROCESS_TEXT`).
- Voice picker and speed control are available directly on the main screen.
- Dedicated Settings screen for API URL, API key, audio format, and provider behavior.
- Voice picker with common Hermes TTS provider/voice combinations, including local Supertonic.
- MP3, Opus/Ogg, or WAV output.
- Local playback with a visible player panel: Stop, Pause, Play, and elapsed/total time.
- Main **Play** button changes to **Stop** while audio is playing.
- Foreground media playback service keeps generated audio playing when the app goes to the background.
- Android notification playback controls for background audio.
- Light and dark mode support using the Android system theme.

## API

The app calls Hermes API Server:

```http
POST /v1/audio/speech
Authorization header: configured API key
Content-Type: application/json

{
  "input": "Text to synthesize",
  "provider": "supertonic-local",
  "voice": "M1",
  "response_format": "wav",
  "speed": 1.0
}
```

The app stores the API URL/key only in Android SharedPreferences on the device. The repository does not contain secrets.

## Playback behavior

Generated audio is played by `PlayerService`, a foreground service with `mediaPlayback` type. This lets playback continue after the activity is backgrounded, while the main screen stays synchronized through local state broadcasts and a lightweight timer.

On Android 13+ the app requests notification permission so the foreground playback notification and controls can be shown.

## Build

```bash
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Release

Current release: `v1.0.4`

Release APK naming convention:

```text
release/hermes-tts-v<version>-debug.apk
```
