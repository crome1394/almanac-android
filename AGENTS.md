# AGENTS.md

Guidance for AI coding agents (and people) working on Almanac. Read
[README.md](README.md) for what the app does and
[ARCHITECTURE.md](ARCHITECTURE.md) for how it works.

## What this is

A Kotlin + Jetpack Compose calendar app meant to match the Android Google
Calendar app in features and look, on top of Android's calendar store. The
owner syncs it with their own CalDAV server through DAVx5.

**The calendar store holds the owner's real calendars, and every change syncs
to their server.** Data safety matters more than features:

- Never write a column you did not mean to change. Guests are diffed, not
  replaced. A series update always sends the full recurrence columns
  (`seriesValues`), because the store re-expands from what you pass.
- Never write exception rows for a series without `_sync_id`; use EXDATE
  (see ARCHITECTURE.md, "Repeating events").
- Deleting calendars is only for phone-only (local) calendars.
- Anything from another app (intents, `.ics` files) is untrusted: cap lengths,
  validate, never crash on bad input.
- No network code, and no `INTERNET` permission.

## Build, run, test

```sh
./gradlew assembleDebug                 # app id app.crome1394.almanac.debug, named "Almanac (debug)"
./gradlew testDebugUnitTest             # unit tests (recurrence, layouts, .ics, birthdays, EXDATE, colors, themes, call links)
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am broadcast -n app.crome1394.almanac.debug/app.crome1394.almanac.DemoSeeder
adb -s emulator-5554 shell am broadcast -n app.crome1394.almanac.debug/app.crome1394.almanac.DemoSeeder --ez soon true
adb -s emulator-5554 shell am broadcast -n app.crome1394.almanac.debug/app.crome1394.almanac.DemoSeeder --ez css true
adb -s emulator-5554 shell am start -n app.crome1394.almanac.debug/app.crome1394.almanac.ArtGallery [--ez dark true] [--ez events true]
```

`--ez css true` makes a calendar under a made-up account that offers CSS3
event colors the way DAVx5 does. The calendar store drops it when the
emulator restarts (its account doesn't exist). If the emulator crashes in the
GPU host, start it with `-gpu swiftshader_indirect`.

- **Test on an emulator, never on the owner's phone.** The phone's calendar
  store is their real, synced data. `DemoSeeder` (debug builds only) creates
  two phone-only calendars with made-up events; `--ez soon true` adds one with
  a reminder a minute away. On an emulator you may reset the store with
  `adb shell pm clear com.android.providers.calendar`. Never run that on a
  real phone.
- Inspect what was stored on a rooted emulator with
  `sqlite3 /data/data/com.android.providers.calendar/databases/calendar.db`
  (`Events`, `Instances`, `CalendarAlerts`). After any change to how events
  are written, check `Instances` for the affected series, not just the UI.
- Compose test tags double as view ids (`testTagsAsResourceId`), so
  `uiautomator dump` finds controls: `menu`, `view-schedule|day|three_day|week|month`,
  `new-event`, `today`, `search`, `edit`, `more`, `save`, `cancel`,
  `title-field`, `start-time`, `repeat`, `cell-YYYY-MM-DD`, `event`,
  `join`, `drag-ghost`, `drag-handle`, `quick-create`, `quick-<view>`,
  `quick-refresh`, `quick-today`, `search-field`, `card-handle`. The ⋮ button
  keeps the id `new-event`.
  Dialogs and menus are separate windows: find them by their text.
- Finish with unit tests passing, a clean build, and the change checked on an
  emulator (screenshots for anything visual, in light and dark).

## Conventions

- One commit per fix or feature, so any one can be reverted.
- README stays short (badges, tables, links); details go in `docs/`.
  Keep CHANGELOG.md (under "unreleased") and ARCHITECTURE.md current.
- Nothing private in the repo: no host names, accounts or keys. Examples use
  example.com.
- Text follows Google Calendar's wording where there is an equivalent.
- Dates and times go through `Fmt` (phone's language and 12/24-hour setting);
  all-day events are UTC-midnight based, timed events use their own zone.

## Releases

Releases are signed with the maintainer's release key. The key and an env
file with its settings live outside the repo, in `~/.local/share/almanac/`.
Keep a backup: phones only accept updates signed with the same key.

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`; move the
   CHANGELOG entries under the new version.
2. Commit and tag **before** building, so the APK matches the tag.
3. Build and check:

```sh
git commit -am "0.1.0: …" && git tag v0.1.0
set -a; . ~/.local/share/almanac/credentials.env; set +a
./gradlew clean :app:assembleRelease    # app/build/outputs/apk/release/app-release.apk
~/Android/Sdk/build-tools/35.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```
