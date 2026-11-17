# YukiWeb for Android

The Android version of [YukiWeb](https://github.com/katsyka/yukiweb), built on Mozilla GeckoView with a native Java UI.

**Download:** [yuki.yukichat.lol/yukiweb](https://yuki.yukichat.lol/yukiweb)

## Features

- GeckoView engine (Firefox), with tabs, a tab grid, groups and private tabs
- Tabs keep their back history across app restarts
- Address bar with inline completion, search suggestions and a choice of search engine
- Saved passwords with autofill, encrypted with the Android Keystore and gated by the screen lock
- Built-in translation, QR scanner, downloads with notifications, pull-to-refresh
- Add to home screen: sites with a web manifest open as standalone apps
- Links to other apps (banking, payments, deep links) open in those apps
- Yuki ID sign-in with sync of bookmarks, history and settings with the desktop browser
- "Send to PC" and incoming tabs from the desktop
- English, Russian and German UI
- Self-update

## Requirements

- Android SDK with compile SDK 37
- Gradle and AGP versions as set in the build files (the wrapper is included)
- `arm64-v8a` devices only

## Building

Signing uses `keystore.properties` and a `.jks` keystore, neither of which is included in this repository. Create your own (see the signing config in `app/build.gradle`), then:

```sh
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleRelease
```

`build_apk.sh "release notes"` copies the shared web assets (new tab, themes, icons, wallpapers) from the desktop project, then builds, signs and publishes the APK.

## Structure

| Path | Contents |
| --- | --- |
| `app/src/main/java/lol/yukichat/web/` | `MainActivity` (tabs, address bar, Gecko delegates), `Store` (persistence), `Sync`, `Logins`, `Prompts`, `Permissions`, `Screens` (native settings, history, bookmarks and downloads pages), `Updater` |
| `app/src/main/assets/` | Home page and bundled web assets |
| `tools/make_icons.py` | Converts the desktop icon set into Android vector drawables |

Package name: `lol.yukichat.web`
