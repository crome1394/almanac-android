# Building

| Need | Version |
|---|---|
| JDK | 21 |
| Android SDK | platform 36, build tools 35 |
| Gradle | wrapper (8.14) |

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit tests
./gradlew assembleRelease      # needs the signing settings below
```

The debug build has its own app id (`app.crome1394.almanac.debug`), so it
installs beside a release build without touching it.

## Testing

Test against made-up data on an emulator, never on a phone with real
calendars. The debug build includes a seeder:

```sh
adb -s emulator-5554 shell am broadcast -n app.crome1394.almanac.debug/app.crome1394.almanac.DemoSeeder
# an event with a reminder one minute away:
adb -s emulator-5554 shell am broadcast -n app.crome1394.almanac.debug/app.crome1394.almanac.DemoSeeder --ez soon true
```

## Signing

Release builds are signed when these environment variables are set:
`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
`RELEASE_KEY_PASSWORD`. Keep the keystore and its passwords outside the
repository, and keep a backup: Android only installs an update over an app
signed with the same key. The release steps are in [AGENTS.md](../AGENTS.md).
