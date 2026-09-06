# Aasra

**Team Turtle** — iQOO City Battles 2026

On-device voice companion for Hindi and English. Package `com.aasra.companion`. There is no Aasra backend. The phone talks to Android (calls, SMS, alarms, Health Connect) and, when a key is set, to [CallMissed](https://api.callmissed.com).

## What it does

- Talk: microphone → VAD → speech-to-text → device command or LLM → spoken reply
- Scan medicine (home): photograph a pack, CallMissed names the brand, match against saved medicines, speak when to take it
- Save medicine (Settings): same naming path, then pick a time and store a daily reminder
- Reminders, Health Connect (steps, heart rate, SpO₂), emergency contacts, SOS with a confirm dialog
- Offline or Hybrid (Settings). Hybrid uses the network first and falls back to this phone

Home actions: Talk to AASRA, Scan medicine, Reminders, Emergency assistance.

## Voice path

1. `VoiceService` captures 16 kHz PCM
2. Silero VAD cuts the utterance
3. STT: CallMissed `saaras:v4` when Hybrid and online; otherwise Zipformer (English) or IndicConformer (Hindi)
4. `CommandIntent` handles time, date, reminders, call, SMS, volume, torch, notifications, health, SOS
5. Other turns: `sarvam-105b-conversations` when online; Qwen GGUF on the phone when offline
6. TTS: Sonic 3.6 (Preeti) when online; Kokoro, then Piper, on the phone

Call and SMS go through a voice confirm gate. SOS calls the primary contact and SMS saved contacts. It is not a 112 / emergency-services dialer.

## Modules

| Module | Role |
|---|---|
| `app` | Compose UI, navigation, `VoiceService`, preferences |
| `core-audio` | Mic / speaker I/O |
| `core-pipeline` | VAD → STT → LLM → TTS interfaces |
| `engine-sherpa` | sherpa-onnx VAD, STT, TTS, wake word |
| `engine-llama` | llama.cpp / Qwen GGUF |
| `cloud-callmissed` | Chat, STT, TTS, search, voice-agent WebSocket |
| `tools` | Call, SMS, reminders, SOS, flashlight |
| `models` | Model registry and download paths |
| `data` | Room: contacts, reminders, medicines |

## Stack

- Android 10+ (min SDK 29, target 35), Kotlin, Jetpack Compose, Material 3
- Room, DataStore, CameraX, ML Kit text recognition (Latin + Devanagari)
- Health Connect
- sherpa-onnx, llama.cpp (Llamatik)
- OkHttp to `https://api.callmissed.com`

## Build

Needs Android Studio (JDK 17), NDK for native libs, and an arm64 phone or emulator.

```bash
cp local.defaults.properties local.properties
# add CALLMISSED_API_KEY=cm_...  (from console.callmissed.com)
# sdk.dir=... is also required for a local Android SDK

./gradlew :app:assembleDebug
```

`local.properties` is gitignored. Do not commit a real key. Without a key, cloud features stay off and the app stays on the on-device path.

## On-device models

Large weights are kept on the machine that builds the APK. GitHub rejects files over 100 MB, so these stay out of git:

| File | Use |
|---|---|
| `app/src/main/assets/models/Qwen3.5-4B-Instruct-Q4_K_M.gguf` | Local LLM |
| `app/src/main/assets/models/indicconformer-hi-int8/model.onnx` | Hindi STT |
| `app/src/main/assets/models/hi-hinglish-swift/decoder.int8.onnx` | Hinglish STT |
| `app/src/main/assets/models/kokoro-int8-multi-lang-v1_0/model.int8.onnx` | Local TTS |

Copy them into those paths on a checkout that already has the smaller sidecar files (`tokens.txt`, encoder/joiner, Piper, Silero). Desktop fetch helper: `./scripts/download_models.sh`.

## Run modes

| Mode | Behavior |
|---|---|
| Offline | STT, LLM, and TTS on this phone |
| Hybrid | Online first (CallMissed chat / STT / TTS); local engines if the network is down |

## Layout

```
app/                 UI, VoiceService, orchestrator
core-audio/
core-pipeline/
engine-sherpa/
engine-llama/
cloud-callmissed/
tools/
models/
data/
scripts/             model download helpers
```

## License notes

Third-party runtimes and model cards keep their own licenses (sherpa-onnx, Kokoro, Piper, Qwen, CallMissed terms).
