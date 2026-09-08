# Liftloq — workout calendar (MVP v1.1)

Android app: a fully local workout calendar with **editable workout types** (name + seed color).
No account, no backend. Material 3 + dynamic color (API 31+).

**Display name:** Liftloq (brand)
**Package:** `com.konstantyp.gymcal` (unchanged)  
**Design specs:** `../SPEC-MaterialYou-MVP-v1.0.md` + `../SPEC-MATERIAL-YOU-MVP-v1.1.md` (+ deltas)

## Requirements

- Android Studio Hedgehog / Iguana / Ladybug (or newer) with JDK 17+
- Android SDK 34, minSdk 26

## Open and run in Android Studio

1. Clone or unpack the project.
2. **File → Open** → select the `Gymcal` folder (the one with `settings.gradle.kts`).
3. Wait for Gradle sync.
4. Connect a phone with USB debugging enabled **or** use an emulator API 26+.
5. Click **Run** (▶) on the `app` configuration.

### Build APK from CLI

```bash
cd Gymcal
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

A ready debug build is also in the repo: [`releases/Gymcal-debug.apk`](releases/Gymcal-debug.apk).

## Features (MVP v1.1)

- Month calendar with ◀ ▶ navigation
- Editable workout types (max 20): add / rename / recolor / delete
- Type color picker: circular HSV wheel + brightness
- Day cells: type color + small type-name label
- Day detail: dynamic chips + manage workout types
- Home-screen widgets: week and month
- Settings: language (English default, Polish, German) + JSON export/import of the plan
- Persistence: DataStore (types JSON + day→typeId); migration from v1.0 enums

## Layout

```
app/src/main/java/com/konstantyp/gymcal/
  MainActivity.kt
  GymcalApp.kt
  data/          WorkoutType, WorkoutRepository
  ui/theme/      Color, Theme, Type (+ LocalTypeColorMap)
  ui/calendar/   DayCell, MonthGrid, Legend, CalendarScreen
  ui/detail/     DayDetailScreen
  ui/types/      TypesScreen, TypeColorPicker
  ui/settings/   Settings (language, export/import)
  widget/        Glance week + month widgets
```
