# Hermes TTS Android

Private Android client for the Hermes Agent API Server TTS endpoint.

## Features

- English-only UI.
- Manual text input for text-to-speech.
- Accepts text from Android Share (`ACTION_SEND`) and selected text menu (`PROCESS_TEXT`).
- Dedicated Settings screen for API URL, API key, voice, format, speed, and provider behavior.
- Voice picker with common Hermes TTS provider/voice combinations.
- MP3 or Opus/Ogg output.
- Local playback and share generated audio to other apps.
- Light and dark mode support using the Android system theme.

## API

The app calls Hermes API Server:

```http
POST /v1/audio/speech
Authorization header: configured API key
Content-Type: application/json

{
  "input": "Text to synthesize",
  "provider": "edge",
  "voice": "en-US-GuyNeural",
  "response_format": "mp3",
  "speed": 1.0
}
```

The app stores the API URL/key only in Android SharedPreferences on the device. The repository does not contain secrets.

## Build

```bash
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```
