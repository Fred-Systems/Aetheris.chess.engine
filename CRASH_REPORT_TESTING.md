# Aetheris crash-report build instructions

This is a diagnostic-only APK, installed as a separate app from the regular Aetheris release.

1. Install **Aetheris-CrashReport-1.0.7.apk**. Do not uninstall the regular app.
2. Open the crash-report app and reproduce the crash once: start a game, make a move, and let Aetheris think until it crashes.
3. Reopen the crash-report app. If the crash occurs again, reopen it once more; the app saves a breadcrumb before entering native search, so it can record an unfinished search even if Android kills the process.
4. Before triggering another engine move, open **Settings → Export crash report** and send the report text back to the developer.
5. If possible, also capture Android's system crash details from the device's bug report. The report includes device/Android version, the exact FEN, search depth/time, and whether native search returned. The app will automatically prompt to export it on the next launch after an unfinished search; the prompt stays pending until you export or dismiss it. Native progress is also logged under the Android logcat tag `AetherisEngine`.

The report does not include accounts, passwords, or network data. It can include the chess position (FEN) and engine settings. This build is for diagnosis, not regular play.
