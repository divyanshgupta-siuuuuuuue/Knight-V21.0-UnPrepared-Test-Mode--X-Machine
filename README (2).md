# Knight for Android

A website can't control a phone. This app can, once you allow it. It is the Knight website in an app, plus:
- **Hey Knight, in the background.** A listening notification keeps an offline speech engine running (your voice never leaves the phone).
- **Phone control, only after you switch it on** in Knight setup: open apps, call and text (you press send), alarms and timers,
  flashlight, volume, media, Wi-Fi/Bluetooth/display settings, plus Back/Home/Recents, scrolling, tapping by name, reading the screen,
  screenshots and locking (these need Android's Accessibility access, which you grant yourself in Settings).
- Anything it does not recognise goes to Knight AI in the app, which turns it into one of those fixed jobs.

## Build it
You need Android Studio (or the Android SDK + Gradle 8.9) and JDK 17.
1. Open `gradle.properties` and set `knightUrl` to your Knight website, or build with `-PknightUrl=https://your-site.workers.dev`.
2. Android Studio: **Open** the `android` folder, then Run on a phone. Or: `gradle assembleDebug` and install `app/build/outputs/apk/debug/app-debug.apk`.
3. Or push to GitHub: `.github/workflows/android.yml` builds the APK for you (set the repo variable `KNIGHT_URL`).

## First run (Knight App, Hey Knight tab)
Switch on "Hey Knight over my whole device", allow the microphone, then press **Allow permissions**, **Accessibility settings**
(turn Knight on) and **Display over other apps**. The last one lets Knight open apps while it is listening in the background.
The first start downloads a ~40 MB speech model, then works offline.

## Honest limits
- Android only. Apple does not let any app listen in the background or control other apps; on iPhone the best you can get is Siri Shortcuts.
- Android 14+ will not let an app start the microphone from a reboot, so open Knight once after restarting your phone.
- Some phone makers (Xiaomi, Huawei, Samsung "sleeping apps") kill background services: set Knight to "Unrestricted" battery use.
- Calls and texts need your contacts and phone permissions. Texts open ready to send; you always press send.
- Not tested on a device yet: build it and try it before you rely on it.
