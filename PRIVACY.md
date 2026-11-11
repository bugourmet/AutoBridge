# Privacy

- AutoBridge does not request the `INTERNET` permission. The manifest removes it even if a library asks for it, and CI and the release workflow fail if an APK requests it. You can check yourself: `aapt2 dump permissions AutoBridge.apk`.
- Message content stays in memory only, for as long as the notification is shown. It is never written to disk. App backups are disabled.
- On disk, AutoBridge stores only its settings: switches, channel names and keywords you enter, quick replies, and the names of workspaces seen in Slack's notifications.
- No analytics, crash reporting or accounts. No Slack API access, tokens or OAuth.
- Replies are handed to the Slack app on your phone through the reply action Slack put on its own notification; the Slack app sends them.
- Debug builds (not releases) log notification contents to logcat to help capture test fixtures.
