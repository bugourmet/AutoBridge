# Contributing

## Guidelines

- Branch from `main` and open a pull request. CI runs `./gradlew lint test assembleDebug`.
- Keep Slack-specific logic in `source/SlackParser.kt`.
- Add a fixture in `SlackFixtures.kt` and a test for parser changes.
- Never add the `INTERNET` permission or anything that sends data off the device.
- Don't use the Slack name or logo in the app name, icon, or store assets.

## Build from source

You need JDK 17 or later and Android SDK 36.

```bash
./gradlew lint test assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Project structure

```text
app/src/main/java/io/github/autobridge/
├── listener/   RelayListenerService
├── source/     SlackParser, RelayDiagnostics, NotificationDumper
├── model/      Message, Conversation, ConversationKey
├── store/      ConversationStore
├── relay/      NotificationRelay, AutoNotifier, RelayState, TestMessage
├── filter/     FilterEngine
├── car/        CarStateMonitor
├── actions/    ReplyReceiver, MarkReadReceiver, ReplyForwarder
└── settings/   SettingsRepository and the Compose settings UI
```

## Test with the Desktop Head Unit

1. In Android Studio, open **SDK Manager → SDK Tools** and install **Android Auto Desktop Head Unit Emulator**.
2. Enable Android Auto developer mode, then choose **⋮ → Start head unit server**.
3. Forward the port, then run `desktop-head-unit` from `$ANDROID_HOME/extras/google/auto/`:

   ```bash
   adb forward tcp:5277 tcp:5277
   ```

## Capture notification data

Debug builds log the extras and actions of each Slack notification:

```bash
adb logcat -s AutoBridgeDump
```

This output includes message text, so remove names and content before sharing it.

## Releases

Pushing a `v*` tag runs the [release workflow](.github/workflows/release.yml), which attaches a signed APK and its SHA-256 checksum to a GitHub Release. It needs these repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`.
