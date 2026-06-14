# Hermes TTS Android

Private Android client for the Hermes Agent API Server TTS endpoint.

## Features

- Manual text input for text-to-speech.
- Accepts text from Android Share (`ACTION_SEND`) and selected text menu (`PROCESS_TEXT`).
- Voice picker with common Hermes TTS provider/voice combinations.
- Configurable Hermes API URL and API key.
- MP3 or Opus/Ogg output.
- Local playback and share generated audio to other apps.

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
