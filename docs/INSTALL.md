# Installing PuzzlePop on a tablet or phone

PuzzlePop isn't on the Play Store, so you install the APK yourself. The device
needs Android 8 or newer (Settings > About tablet > Software information >
Android version).

Build the APK first (see the README). It will be in
`app/build/outputs/apk/debug/` or `app/build/outputs/apk/release/`.

## Option 1: copy the file

1. Copy the APK to the device (USB cable, cloud drive, whatever you like).
2. On the device, open the file manager and tap the APK.
3. If Android asks, allow installing from this source, then tap **Install**.

## Option 2: adb over USB

1. On the device, go to **Settings > About tablet > Software information** and
   tap **Build number** 7 times to turn on Developer options.
2. In **Settings > Developer options**, turn on **USB debugging**.
3. Plug the device in and tap **Allow** when it asks.
4. On the computer:

   ```bash
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```

## Updating

Install the new APK the same way. Photos, saved puzzles and the grown-up PIN
are kept, as long as the new APK is signed with the same key as the old one.

## Setting up kid mode

Kid mode uses Android's screen pinning ("Pin windows" on Samsung, under
Settings > Security and privacy > More security settings). The app walks you
through it the first time you start kid mode. Make sure the device has a screen
lock, otherwise a child can unpin the app without one.
