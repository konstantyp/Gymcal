# Gymcal — kalendarz treningowy (MVP v1.1)

Aplikacja Android: lokalny kalendarz treningów z **edytowalnymi typami** (nazwa + kolor seed).
Bez konta, bez backendu. Material 3 + dynamic color (API 31+).

**Pakiet:** `com.konstantyp.gymcal`  
**Spec:** `../SPEC-MaterialYou-MVP-v1.0.md` + `../SPEC-MATERIAL-YOU-MVP-v1.1.md`

## Wymagania

- Android Studio Hedgehog / Iguana / Ladybug (lub nowsze) z JDK 17+
- Android SDK 34, minSdk 26

## Otwarcie i uruchomienie w Android Studio

1. Sklonuj / rozpakuj projekt.
2. **File → Open** → wybierz folder `Gymcal` (ten z `settings.gradle.kts`).
3. Poczekaj na sync Gradle.
4. Podłącz telefon z włączonym debugowaniem USB **lub** emulator API 26+.
5. Kliknij **Run** (▶) na konfiguracji `app`.

### Budowa APK z CLI

```bash
cd Gymcal
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Funkcje MVP v1.1

- Kalendarz miesiąca z nawigacją ◀ ▶
- Edytowalna lista typów (max 20): dodaj / edytuj nazwę / kolor / usuń
- TypeColorPicker: 12 presetów + hue + nasycenie; runtime = harmonized container
- DayCell: kolor + etykieta `labelSmall` nazwy typu (ellipsis)
- DayDetail: dynamiczne chipy + „Zarządzaj typami”
- Persistencja: DataStore (typy JSON + day→typeId); migracja z enum v1.0

## Struktura

```
app/src/main/java/com/konstantyp/gymcal/
  MainActivity.kt
  GymcalApp.kt
  data/          WorkoutType, WorkoutRepository
  ui/theme/      Color, Theme, Type (+ LocalTypeColorMap)
  ui/calendar/   DayCell, MonthGrid, Legend, CalendarScreen
  ui/detail/     DayDetailScreen
  ui/types/      TypesScreen, TypeColorPicker
```
