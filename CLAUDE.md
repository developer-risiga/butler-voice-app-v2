# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew assembleDebug      # Build debug APK
./gradlew build              # Full build (debug + release)
./gradlew clean              # Clean build artifacts
./gradlew installDebug       # Build and install on connected device
./gradlew test               # Run unit tests
./gradlew lint               # Run lint checks
```

API keys must be available as environment variables or in `local.properties` before building:
- `SARVAM_API_KEY` — Sarvam AI speech-to-text
- `PORCUPINE_ACCESS_KEY` — Picovoice wake-word detection
- `ELEVENLABS_API_KEY` and `ELEVENLABS_VOICE_ID` — ElevenLabs TTS
- `SUPABASE_URL` and `SUPABASE_KEY` — Supabase backend

The build script reads these via `getEnvOrLocal()` in `app/build.gradle.kts` and injects them as `BuildConfig` fields.

## Architecture

Single-Activity Android app (`MainActivity`) using Jetpack Compose UI, targeting API 26+. The user flow is linear:

1. **Wake word** — `WakeWordManager` listens continuously using Picovoice Porcupine with the bundled model `assets/Hey-Butler_en_android_v4_0_0.ppn` (sensitivity 0.7). Triggers `onWakeWordDetected()`.
2. **Speech-to-text** — `SarvamSTTManager` captures PCM audio at 16kHz, detects silence (25 silent frames ≈ 312ms after speech onset), builds a WAV and POSTs it to Sarvam AI. Auto-cancels if no speech in 4s, max 8s recording.
3. **Order parsing** — `OrderParser` (referenced in `MainActivity` but not yet implemented) converts spoken text into an `Order` object.
4. **Product lookup** — `ApiClient.searchProduct()` does an ILIKE query on the `products` table's `searchable_text` column via Supabase Postgrest.
5. **Order creation** — `ApiClient.createOrder()` calls the Supabase RPC function `create_order` with item list.
6. **TTS feedback** — `TTSManager` calls ElevenLabs (`eleven_turbo_v2_5` model) for natural speech; falls back to the system eSpeak engine if ElevenLabs fails.

## Key Files

| File | Purpose |
|------|---------|
| `app/src/main/java/.../MainActivity.kt` | Orchestrates the full voice loop |
| `app/src/main/java/.../WakeWordManager.kt` | Porcupine wake-word integration |
| `app/src/main/java/.../voice/SarvamSTTManager.kt` | Sarvam AI STT with silence detection |
| `app/src/main/java/.../TTSManager.kt` | ElevenLabs TTS + eSpeak fallback |
| `app/src/main/java/.../api/ApiClient.kt` | Product search and order creation (Supabase) |
| `app/src/main/java/.../api/SupabaseClient.kt` | Supabase client singleton |
| `app/src/main/java/.../api/AuthManager.kt` | Supabase email/password auth |
| `app/src/main/java/.../ai/GptService.kt` | OpenAI GPT integration (currently unused) |
| `app/build.gradle.kts` | Secrets injection, SDK versions, dependencies |

## Incomplete / Known Issues

- **`OrderParser` is missing** — `MainActivity` instantiates `OrderParser()` and calls `parser.parse(spokenText)` but the class does not exist. This will cause a compile error until implemented.
- **`GptService.kt`** has a placeholder `"YOUR_OPENAI_API_KEY"` and is not wired into the main flow.
- `local.properties` must not be committed — it may contain secrets.

## CI/CD

`.github/workflows/android-build.yml` builds a debug APK on every push to `main` using JDK 17, uploads the APK as artifact `butler-voice-apk`. GitHub Actions secrets must mirror the environment variables listed above.
