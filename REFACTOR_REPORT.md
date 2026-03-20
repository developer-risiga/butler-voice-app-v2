# Butler Voice App — Refactor & Fix Report

**Date:** 2026-03-20
**Scope:** `my_testing/butler-voice-first-app`

---

## 1. Missing Classes Created

| File | What | Why |
|---|---|---|
| `ai/Order.kt` | New `data class Order(product: String, quantity: Int)` | Referenced everywhere but never existed — caused compile failure |
| `ai/OrderParser.kt` | New class that parses spoken text (e.g. "two burgers") into an `Order` | Referenced in `MainActivity` but never implemented |

---

## 2. Supabase SDK Upgraded (v1.4.0 → v3.4.1)

The entire codebase was written for Supabase Kotlin SDK v2/v3 API but the declared dependencies were v1.4.0, causing every Supabase call to fail to compile.

| Change | Detail |
|---|---|
| `gotrue-kt:1.4.0` removed | Replaced with `auth-kt:3.4.1` (module was renamed in v2+) |
| `postgrest-kt:1.4.0` → `postgrest-kt:3.4.1` | Updated to match the code's API usage |

---

## 3. Import Fixes (Supabase API Changes)

| File | Old Import | New Import |
|---|---|---|
| `SupabaseClient.kt` | `io.github.jan.supabase.gotrue.Auth` | `io.github.jan.supabase.auth.Auth` |
| `AuthManager.kt` | `io.github.jan.supabase.gotrue.providers.builtin.Email` | `io.github.jan.supabase.auth.providers.builtin.Email` |
| `AuthManager.kt` | _(missing)_ | Added `io.github.jan.supabase.auth.auth` (required extension property in v3) |
| `ApiClient.kt` | `io.github.jan.supabase.postgrest.query.filter` (invalid) | Removed — `filter` is a DSL method, not an import |
| `ApiClient.kt` | `io.github.jan.supabase.postgrest.decodeList/decodeSingle` (invalid) | Removed — these are member functions on `PostgrestResult` |

---

## 4. ApiClient.kt — API Call Fixes

| Issue | Fix |
|---|---|
| `client.rpc(...)` — wrong receiver type | Changed to `client.postgrest.rpc(...)` (v3 requires calling on `Postgrest` plugin) |
| `rpc` received `Map<String, Any>` | v3 requires `JsonObject` — added `@Serializable` data classes (`OrderItem`, `CreateOrderParams`) and serialize via `Json.encodeToJsonElement().jsonObject` |
| `decodeSingle<T>()` doesn't exist in v3 | Replaced with `decodeList<T>().first()` |

---

## 5. Kotlin Upgraded (1.9.24 → 2.3.0)

Supabase-kt 3.4.1 transitively pulls in `kotlin-stdlib:2.3.10` which requires Kotlin compiler 2.3.x. Upgraded the full Kotlin stack:

| Item | Old | New |
|---|---|---|
| Kotlin compiler | 1.9.24 | 2.3.0 |
| Serialization plugin | `kotlin("plugin.serialization") version "1.9.0"` | `id("org.jetbrains.kotlin.plugin.serialization")` via root |
| Compose compiler | `composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }` | Replaced with `id("org.jetbrains.kotlin.plugin.compose")` plugin (Kotlin 2.x integrates it) |
| `kotlinOptions { jvmTarget / freeCompilerArgs }` | Deprecated in 2.3 | Migrated to `kotlin { compilerOptions { ... } }` top-level DSL |
| kotlinx-coroutines | 1.7.3 | 1.9.0 |
| ktor-client-android | 2.3.7 | 3.0.3 |
| kotlinx-serialization-json | 1.6.0 | 1.7.3 |

---

## 6. Android SDK Version

| Item | Old | New | Reason |
|---|---|---|---|
| `compileSdk` | 34 | 36 | `androidx.browser:browser:1.9.0` (transitive from Supabase) requires compileSdk ≥ 36 |

---

## 7. Build Script Fixes (`app/build.gradle.kts`)

| Issue | Fix |
|---|---|
| Elvis `?: ""` on non-nullable `String` (error in Kotlin 2.3) | Removed the redundant `?: ""` |
| `kotlinOptions` block (error in Kotlin 2.3) | Replaced with top-level `kotlin { compilerOptions { } }` |

---

## 8. Documentation

- Created `CLAUDE.md` at project root — documents build commands, architecture overview, key files, and known incomplete items for future developers.

---

## Outstanding Items (Not Code Issues)

| Item | Status |
|---|---|
| `PORCUPINE_ACCESS_KEY` missing at build time | Needs to be set in `local.properties` or CI secrets |
| Login credentials in `MainActivity` (`testuser@gmail.com` / `123456`) | User must be created in Supabase Auth dashboard, or credentials updated in code |
| `GptService.kt` | Still has placeholder `"YOUR_OPENAI_API_KEY"` and is not wired into the main flow |
