# ASHKB · Ankylosing Spondylitis Personal Health App

[中文说明](README.md) · **English**

Ankylosing Spondylitis Health Knowledge Base — an **offline-first** personal health management app for people living with ankylosing spondylitis (AS). **No network by default, no account, all data stored locally.** The only network capability is an **optional, manually configured** encrypted WebDAV backup (see below); apart from that the app issues no network requests. It is built around the daily loop of *medication → symptoms → exercise → follow-up visits*.

> ⚠️ **Disclaimer**: This app is a personal health-record tool. It does not constitute medical advice and cannot replace a doctor's diagnosis or treatment. Always follow your physician's instructions for medication and treatment.

## Features

### 💊 Today (medication & check-in)
- Medication list: oral / injectable (including every two weeks, weekly, **twice weekly** such as Enbrel 25 mg ×2/week, and custom cycles), with planned slots for each frequency
- One-tap check-in on today's medication cards: oral (before/after meals), injectable (site rotation / batch number), PRN as-needed logging
- Exact alarms: automatically rescheduled on boot, timezone/system-time change, and permission re-grant; late tolerance does not distort adherence statistics
- Medication review checklist (R03): adding a drug automatically searches the built-in knowledge base by *generic-name key + drug class* and surfaces red/yellow interaction warnings and dietary cautions
- Automatic generic-name matching: type Chinese or English (key / generic name / brand name / alias) for live suggestions and one-tap fill

### 🩺 Health (vitals / nutrition / follow-up / emergency)
- Vitals logging: blood pressure, heart rate, temperature, blood oxygen, blood glucose
- Weight and circumference tracking, supplement records (dose / frequency / active status)
- Diet profile and food-avoidance list (pro-inflammatory / allergenic / intolerant foods)
- Follow-up management: per-item cycle configuration, visit records, lab results (reference ranges + abnormal flags; tap the high/low chip to expand that indicator's range), imaging records (MRI / CT / X-ray), vaccination records (live vaccine × biologic requires mandatory physician confirmation)
- Lab-report AI import: copy the built-in template together with report photos and send it to any AI assistant; paste the formatted result back into the app to parse it into the database (labs + imaging reports; the app itself stays offline and never uploads photos)
- Emergency card: five scenario cards (**acute uveitis flare / fever with infection (during biologics) / fall or fracture / cauda equina syndrome (neurological emergency) / corticosteroid withdrawal or adrenal crisis**, mapped one-to-one to the `EmergencyScene` enum), quick-dial 120, one-tap emergency-contact calling, and a personal emergency information card (exportable as a printable PDF)

### 📊 Reports
- 30-day medication adherence (taken / partial / skipped), exercise execution, symptom averages, BASDAI trend and activity thresholds (warning at ≥4.0)
- Hand-drawn trend charts: BASDAI / pain / morning stiffness / weight / blood pressure / heart rate
- Follow-up report PDF export (profile + medication + adherence and symptoms + labs and visits + next visit), shared via the system share sheet

### 🏃 Exercise (R27 graded matrix)
- Based on disease stage (remission / controlled / flare; unevaluated is conservatively treated as flare) and spinal-mobility assessment, it produces today's exercise prescription; during a flare, red-list L2/L3 items are suspended entirely and only gentle L1 items are issued
- Black-list movement blocking (e.g. high-risk cervical movements are blocked in all stages), post-exercise feedback logging; yesterday's pain / morning stiffness / temperature are shown as prescription context

### 📚 Knowledge base
- Built-in seed medical knowledge (guideline and consensus summaries, interactions, food avoidance, vaccines, emergency scenarios), searched locally with no network required

### 👤 Me (backup & data autonomy)
- **Lifestyle profile**: smoking / occupational sitting hours / exercise habits / sleep, driving personalized exercise-prescription hints (**hints only — it does not change the exercise red/black-list filtering**, which is driven by disease stage and spinal mobility); once smoking is recorded, the smoking-cessation knowledge entry is pinned to the top
- **Reminder reliability self-check**: five status items (notifications / exact alarms / high-priority reminders (full-screen) / battery whitelist) plus autostart guidance and a "send test reminder" end-to-end verification of the whole reminder chain
- **First-launch disclaimer**: the app is entered only after confirming "not medical advice / not a substitute for diagnosis and treatment"
- **Minimal mode**: after 3 consecutive days with no symptom record, the app asks why; if you are unwell or hospitalized it switches to minimal mode (keeping only medication and symptoms, reducing input burden)
- **Lock-screen emergency info**: optional — blood type / diagnosis / allergies / key medications / emergency contacts shown as a persistent notification **on the lock screen**, viewable without unlocking in an emergency (off by default)
- **Encrypted backup**: AES-256-GCM with PBKDF2 password derivation, per-table SHA-256 manifest verification, automatic pre-restore snapshot that can be rolled back, and a full backup ledger
- **WebDAV cloud backup**: zero-dependency implementation (probe / upload-and-read-back comparison / retention-policy rotation); backup filenames carry timestamps (multiple backups on the same day do not overwrite each other); after logging into the same account on a new device you can pull the server's backup list and choose one to restore, without generating a local backup first
- Plaintext JSON profile export / import (to set up a profile on a new device)

## Tech stack

| Item | Notes |
|---|---|
| Language / UI | Kotlin · Jetpack Compose (Material 3) |
| Storage | Room (v17, with a 1→17 chained migration) |
| Dependencies | **AndroidX / Kotlin official libraries only** — zero third-party UI, network, or charting libraries |
| Charts | Line charts drawn by hand on Canvas |
| PDF | Native export via `android.graphics.pdf` |
| Crypto | `javax.crypto` AES-256-GCM + PBKDF2 (backup container) |
| Architecture | Single-module MVVM (ViewModel + Repository); the domain layer is pure functions and unit-testable |
| SDK | compileSdk 34 · minSdk 31 (Android 12+) · targetSdk 34 |

## Building

```bash
git clone https://github.com/weante/ashkb.git
cd ashkb
gradle assembleDebug        # Debug APK
gradle assembleRelease      # Release APK (note: adjust the signing config yourself)
gradle testDebugUnitTest    # Unit tests
```

> ⚠️ This repository **does not ship the Gradle wrapper scripts** (`gradlew` / `gradlew.bat` / `gradle-wrapper.jar` are not committed; only `gradle/wrapper/gradle-wrapper.properties` is kept as a version declaration), which is why the commands above use `gradle` rather than `./gradlew`. Use **Gradle 8.7**, or simply open the project in Android Studio (which will take over the build).

Requirements: JDK 17+, Gradle 8.7, Android SDK 34.

## Data and privacy

- **No network permission required** (used only for WebDAV backup, which must be configured manually); no analytics, no telemetry, no accounts
- All health data lives solely in the on-device Room database
- WebDAV remote backup **enforces HTTPS**: plaintext `http` addresses are rejected outright — transmitting in the clear would expose the account credentials to anyone on the path
- Backup files are encrypted containers: the passphrase is never written to disk, AAD binds the container against tampering, and restore follows five steps — decrypt-and-self-verify → snapshot → transactional overwrite → row-count and SHA double-check → ledger entry

## Tests

874 JVM unit tests cover the core domain logic: schedule calculation (including the BIW twice-weekly boundary, the display convention that takes the time from an ISO timestamp, slot label = planned time, and fault-tolerant parsing of the time picker's initial value); the "cancel-then-rebuild" semantics of the reminder chain (Robolectric + `AlarmManager`, for the four sources medication / follow-up / BASDAI / exercise, including final-stage high-priority escalation); test-reminder scheduling (delay conversion / idempotency / cancellation); do-not-disturb window detection (cross-midnight / same-day / half-open boundaries); sedentary-reminder time computation (grid / window boundaries / next-day continuation / invalid windows); the morning-stiffness warm-up sequence (threshold tiers at the 15/30 boundaries / L1 only / capped at 4 items / no L1 and empty prescription); posture and sleep advice (item-id consistency / deduplicated points / prone-position avoidance / sleep profile pinned first); disclaimer wording; lifestyle-profile encoding and pinning hooks / lifestyle → prescription hints; the minimal-mode state machine (consecutive-miss cap / threshold boundaries / reason mapping / paired invariants); lock-screen emergency card text (JSON flattening / immunosuppressant flagging / capping with a total count / contact priority); profile label mapping (sacroiliitis grades 0–IV); inflammatory-marker (ESR / CRP) name normalization (no fuzzy matching) and unit conversion (`mg/dL`→`mg/L`); the three conventions for turning lab rows into series (units, windows, keeping every same-day value); shared time axes across small multiples (date-union window and window-relative coordinates); drag-to-select readings on trend charts (all same-day values taken, bubbles largest-first); the R27 exercise graded matrix (three-stage disease staging + L1 fallback during flares); lab-report AI-import parsing (labs / imaging / reference ranges / unrecognized-row hints); backup encryption (v1/v2/v3 formats read each other + recovery codes + stable keys + attachment ciphertext) / restore table-name and column-name whitelists plus SHA / WebDAV remote-list parsing and rotation retention / attachment remote-path conventions (including percent-encoding bypass); knowledge-base search conventions (single-column search text + Chinese substring semantics) and seed incremental-refresh decisions; trend-chart coordinate conventions (positioning by real date intervals / cross-year / dirty-date fallback / tick count constrained by available height / thresholds always in range / drag hit-testing consistent with drawing position / reading summary); emergency-card medication summary (immunosuppressant detection); missed-dose guidance (oral catch-up window / injectable over-window grading); weight goal-range evaluation; follow-up preparation checklist; generic-name key catalogue; medication adherence conventions (partial counts as 0.5 / no record does not count as full adherence; the report and the medication list's "medication history" share one implementation); invariants for manual correction of medication records (taken leaves no reason / partial and skipped require a reason / site is only meaningful when taken) and the stability of stored injection-site keys; credential redaction in crash logs; a static source guard for regex braces (with warnings for uncovered forms); deciding whether a discontinued medication can be deleted and the confirmation-text variants (in use cannot be deleted / any check-in records must report a count / negative-count fallback); Xiaomi-device detection and the list of switches that must be enabled manually (lock-screen display / background pop-up / autostart).

## Version

Currently `v1.2.7` (versionCode 119). Phases P0–P5 are complete; the app is in self-use validation (dogfooding).

## License

[MIT](LICENSE) © 2026 ASHKB Dev
