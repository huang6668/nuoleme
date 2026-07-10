# 挪了么 Project Instructions

- This is a lightweight offline Android app.
- Do not add INTERNET permission.
- Do not add analytics, crash reporting, ad SDKs, or network libraries.
- Keep background runtime minimal: no persistent service except AlarmService while actively alarming.
- Use static SmsReceiver for SMS_RECEIVED.
- SmsReceiver must catch foreground-service start failures and fall back to urgent/full-screen notification.
- AlarmActivity is the lock-screen/full-screen fallback and must be excluded from recents.
- Use SharedPreferences, not Room/database.
- Use XML Views, not Jetpack Compose.
- Run `./gradlew test` and `./gradlew :app:assembleDebug` before reporting completion.
