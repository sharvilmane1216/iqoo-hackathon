# Aasra

**Team Turtle** — [iQOO City Battles 2026](https://github.com/sharvilmane1216/iqoo-hackathon)  
Voice companion for elderly users. Hindi + English. No Aasra server.

Package `com.aasra.companion`. The phone talks to Android (calls, SMS, alarms, Health Connect) and to [CallMissed](https://api.callmissed.com). **A CallMissed API key is required** — Hybrid talk, cloud STT/TTS, search, and medicine pack naming all use it. Put it in `local.properties` (not git). `./gradlew :app:assembleDebug` and Android Studio Run stop if the key is missing or still `placeholder`.

## For judges

| | |
|---|---|
| One line | Speak, and the phone answers — time, reminders, medicines, family call — on-device first, CallMissed when Hybrid is online. |
| Problem | Many older people cannot use small icons or English-only assistants. They need a large Talk button, Hindi, and family SOS they can confirm. |
| Why iQOO | Snapdragon + 12 GB RAM can run Zipformer / IndicConformer / Qwen GGUF locally. Hybrid is the fallback if the venue Wi-Fi dies. |
| No backend | Requirement: serverless. Key lives in `local.properties` → `BuildConfig` for the weekend only. |
| Languages | Hindi and English in Settings. Home copy follows that choice. |
| Safety | Call / SMS / SOS ask before they fire. SOS is the primary contact + SMS, not 112. The model prompt says not to give dosages. |

**Do not tap Confirm on Emergency assistance** during a walkthrough unless you intend a real call and SMS.

### 5-minute demo (rehearsed path)

Have Hybrid + a CallMissed key on the APK, one saved emergency contact you are willing to use, one saved medicine, and Health Connect data if you want that screen.

1. **Home** — large Talk control, Scan medicine, Reminders, red Emergency. Open Settings: name, Hindi/English, Offline vs Hybrid.
2. **Offline proof** — Settings → Offline (or airplane mode). Talk: “What time is it?” / “अभी कितने बजे हैं?” Local STT + local answer.
3. **Reminder** — “Remind me to drink water at 9 PM” or Hindi equivalent. Confirm if asked. Open Reminders and show the row.
4. **Hybrid** — Settings → Hybrid, network on. Ask a short fact or greeting (not a dosage). Point at the streamed reply and spoken TTS.
5. **Medicine** — Settings → Medicines → Save medicine pack (if not saved). Then Home → Scan medicine: brand from CallMissed, spoken “when to take”.
6. **Call (stop at confirm)** — “Call [saved name].” Show the confirm chip. Say no / cancel so the dialer does not place a live call.
7. **Emergency (stop at dialog)** — tap Emergency assistance. Show the confirm dialog. **Cancel.**
8. **Health (optional)** — Settings → Health. Steps / heart rate / SpO₂ only if Health Connect already has records.

**If venue Wi-Fi is down:** stay on steps 2–3 and 7 (Cancel). Offline time + reminder is the backup. A recorded screen video of the Hybrid turn is the second backup (not in this repo).

**If Talk is silent:** mic permission, then Hybrid key + network, or the four large model files on the APK (see How to run).

### Team

**Team Turtle.** Repo: [sharvilmane1216/iqoo-hackathon](https://github.com/sharvilmane1216/iqoo-hackathon). Add teammate names here when the roster is public.

### What we did not put in git

- Screenshots / fallback video — capture on the demo iQOO; do not commit a staged mock UI.
- Release keystore — keep it off git.
- Model files over 100 MB — copy from the build laptop (How to run §3).
- `CALLMISSED_API_KEY` — `local.properties` only.

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

## How to run

This is the path that works on a teammate checkout. It is not a complete list of every phone, OS, or Gradle failure.

### What you need

| Item | Detail |
|---|---|
| Computer | macOS, Linux, or Windows with ~8 GB free RAM (Gradle heap is 4 GB in `gradle.properties`) |
| JDK | 17. Android Studio’s bundled JBR is enough |
| Android Studio | Ladybug or newer, SDK 36, Build-Tools, NDK, Platform-Tools (`adb`) |
| Phone | **arm64-v8a**, Android 10+ (API 29). The app’s `abiFilters` is `arm64-v8a` only |
| Disk | Phone: several GB free. A debug APK with bundled weights is a few GB. Laptop: same if you copy the GGUF |
| CallMissed key | **Required.** `CALLMISSED_API_KEY=cm_...` in `local.properties` or the environment. Create one at [console.callmissed.com](https://console.callmissed.com) with `llm`, `stt`, `tts`, and `search`. |
| Network | First Gradle sync (JitPack / Google Maven). Hybrid talk and medicine naming need internet |

x86 / x86_64 emulators and 32-bit phones will not install this ABI. A stock Pixel emulator is usually x86_64.

A missing `sherpa-onnx/` folder after clone is normal. That tree is not a Gradle module. Native STT/TTS come from the JitPack AAR `com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.5`.

### 1. Clone

```bash
git clone https://github.com/sharvilmane1216/iqoo-hackathon.git
cd iqoo-hackathon
```

### 2. CallMissed key (required)

```bash
cp local.defaults.properties local.properties
```

Open `local.properties` and set both:

```
sdk.dir=/Users/YOU/Library/Android/sdk
CALLMISSED_API_KEY=cm_your_key
```

1. Sign up at [console.callmissed.com](https://console.callmissed.com).
2. Create an API key with `llm`, `stt`, `tts`, and `search`.
3. Paste the `cm_...` value. Do not leave `placeholder`.
4. Rebuild after you change the key. The value is baked into `BuildConfig` at compile time.

`local.properties` is gitignored. Do not commit a real key. You can also `export CALLMISSED_API_KEY=cm_...` instead of the file.

Typical `sdk.dir` values:

- macOS: `/Users/YOU/Library/Android/sdk`
- Linux: `/home/YOU/Android/sdk`
- Windows: `C:\\Users\\YOU\\AppData\\Local\\Android\\Sdk`

Android Studio writes `sdk.dir` when you open the repo root. The key you must add yourself.

**No key / `placeholder`:** `assembleDebug`, `installDebug`, and Run fail with `CALLMISSED_API_KEY is required`.

**Key but no network:** Hybrid falls back to the on-device path (local models).

**CallMissed free-tier limits** are small (order of tens of STT/TTS calls per month on the free plan). A long Hybrid demo can hit the quota. Check remaining usage in Settings.

### 3. Model files (two ways to run)

**A. Hybrid-only (smaller APK; key + internet still required)**  
You can skip the 2.2 GB GGUF. Talk and medicine naming use CallMissed. Offline mode and offline STT/TTS will be weak or silent if the large ONNX/GGUF files are absent.

**B. Full offline / Hybrid fallback**  
GitHub rejects files over 100 MB. Copy these onto the machine that builds the APK:

| Path under `app/src/main/assets/models/` | Use |
|---|---|
| `Qwen3.5-4B-Instruct-Q4_K_M.gguf` (~2.2 GB) | Local LLM |
| `indicconformer-hi-int8/model.onnx` (~188 MB) | Hindi STT |
| `hi-hinglish-swift/decoder.int8.onnx` (~125 MB) | Hinglish STT |
| `kokoro-int8-multi-lang-v1_0/model.int8.onnx` (~109 MB) | Local TTS |

The repo already has smaller sidecars (Zipformer, Piper, Silero, `tokens.txt`). At first launch the app copies `assets/models` into app files (`ModelPaths.stageBundledAssets`).

Do **not** expect `./scripts/download_models.sh` to fill those asset paths. That script writes a **desktop** folder named `models/` and currently fetches the **2B** GGUF (`Qwen3.5-2B-Instruct-Q4_K_M.gguf`), not the 4B file the app loads. After it finishes:

```bash
# optional: push the desktop tree onto a phone that already has the app installed
./scripts/push_models.sh models
```

The app adopts files from  
`/sdcard/Android/data/com.aasra.companion/files/models/`  
on the next launch (`ModelPaths.adoptAdbPushedFiles`). Hashes and sizes must match `ModelRegistry`.

Qwen 4B URL used by the app:  
`https://huggingface.co/TheStageAI/Qwen3.5-4B-GGUF/resolve/9e325bb0db1f4eb8a51450142227bb6a7e4d37f1/Qwen3.5-4B-M-TS-Q4_K_M.gguf`

Low-RAM phones: the registry also has `Qwen3.5-0.8B-Instruct-Q4_K_M.gguf`. A 6 GB phone may fail to load the 4B model.

### 4. Build and install

**Android Studio**

1. File → Open the repo root (`settings.gradle.kts` is there).
2. Trust the Gradle project. First sync needs network (Google Maven + JitPack).
3. USB debugging on, cable in, accept the RSA fingerprint on the phone.
4. Run configuration: `app`.
5. Run. That installs `com.aasra.companion` and starts `MainActivity`.

**macOS command line**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
adb devices
adb install -r -g --no-streaming app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.aasra.companion/.MainActivity
```

**Linux:** point `JAVA_HOME` at JDK 17 or Studio’s JBR, then the same `./gradlew` / `adb` commands.

**Windows (PowerShell):**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
adb devices
adb install -r -g --no-streaming app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.aasra.companion/.MainActivity
```

More than one device or emulator:

```bash
adb devices
adb -s DEVICE_SERIAL install -r -g --no-streaming app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE_SERIAL shell am start -n com.aasra.companion/.MainActivity
```

If `adb devices` says `unauthorized`, unlock the phone and accept the USB debugging prompt. If it says `offline`, unplug, revoking USB debugging and replugging often clears it.

`-g` asks the installer to grant runtime permissions. Some iQOO / vivo builds still show a mic, phone, or SMS dialog later.

`--no-streaming` is for a multi-GB APK. Without the large weights the APK is much smaller and a normal `adb install -r` is enough.

`INSTALL_FAILED_INSUFFICIENT_STORAGE`: free space on the phone, then install again.  
`INSTALL_FAILED_NO_MATCHING_ABIS`: the device is not arm64.

### 5. First launch

1. Allow **microphone**. Talk does nothing useful without it.
2. Onboarding: language (Hindi or English), voice, name. Contacts can be skipped; SOS and family SMS then have no number.
3. Home: **Talk to AASRA**, **Scan medicine**, **Reminders**, **Emergency assistance**.
4. Settings: speech speed, Offline vs Hybrid, medicines, Health, extra contacts, notification access, optional MCP tools.
5. Hybrid needs internet **and** the CallMissed key that was baked into that APK at build time. Changing `local.properties` after install does nothing until you rebuild.

**Permissions the OS may still ask for**

| Permission | When |
|---|---|
| Microphone | Talk, wake word |
| Camera | Scan / save medicine pack |
| Phone / SMS / contacts | Call, SMS, SOS |
| Notifications | Reminder alerts (Android 13+) |
| Exact alarms | Timed reminders |
| Location | SOS location text, if that path is used |
| Health Connect | Steps, heart rate, SpO₂ |

Health numbers stay empty unless Health Connect (and a watch companion, if you use one) already wrote data and the user allowed Aasra to read it.

**Scan medicine** needs camera, internet, and the CallMissed key (CallMissed names the brand). Missing key or network → the pack flow asks for cloud. Check does not save. Save is Settings → Medicines → Save medicine pack.

**Emergency assistance** is a confirm dialog, then a call to the primary contact and SMS to saved contacts. It does not dial 112. Do not confirm SOS on a phone that has real contacts unless you mean to place that call.

### If something fails

| What you see | What to check |
|---|---|
| `CALLMISSED_API_KEY is required` | Copy `local.defaults.properties` → `local.properties`, set a real `cm_...` key, rebuild |
| Gradle: SDK location not found | `sdk.dir` in `local.properties` |
| Gradle: invalid / wrong Java | JDK 17, `JAVA_HOME` |
| Gradle: JitPack / sherpa unresolved | Network, `maven { url = uri("https://jitpack.io") }` already in `settings.gradle.kts` |
| Sync OOM | Close other IDEs; heap is `-Xmx4g` |
| `adb devices` empty | Cable, USB debugging, OEM USB driver on Windows |
| Install ABI error | Use an arm64 phone, not an x86 emulator |
| App opens, Talk is silent | Mic permission; Offline without models; Hybrid without key/network |
| Offline Hindi unrecognised | Missing IndicConformer `model.onnx` |
| Offline English unrecognised | Zipformer files present in assets (they are in git) |
| Cloud replies stop mid-demo | CallMissed quota / rate limit |
| Medicine says need internet | Hybrid/cloud key + network |
| Reminders never fire | Exact-alarm and notification permission |
| Health screen empty | Health Connect install + permission + a writer app |
| `sherpa-onnx` folder empty | Ignore it for the app build |

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

## License

App code: [MIT](LICENSE) (Team Turtle, 2026). Third-party runtimes and model cards keep their own licenses (sherpa-onnx, Kokoro, Piper, Qwen, CallMissed terms).
