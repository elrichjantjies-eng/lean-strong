# Lean & Strong: install, update and back up

Everything stays on your own phone. There's no account and no cloud, so the backup steps at
the end matter.

## iPhone (the web app)

**Link:** https://elrichjantjies-eng.github.io/lean-strong/

1. Open the link in **Safari** (not Chrome), on Wi-Fi. Wait about a minute: the app downloads
   everything it needs to work offline.
2. Tap the **Share** button (the square with an arrow, at the bottom of Safari) and choose
   **Add to Home Screen**.
3. Tap **Add**. From now on, always open Lean & Strong from its Home Screen icon.

The app only keeps your data reliably once it's on the Home Screen, so do step 2 before you start
logging.

**Reminders on iPhone:** web apps on iPhone can't send notifications on a schedule. Instead go to
**More → Settings → Add workouts to my calendar**, open the file and tap **Add All**. Your workout
days, weigh-ins, measurement days and check-ins then appear in the Calendar app with alerts.

**Updates:** automatic. When a new version is ready, the app shows **Update available → Reload**.
Tap Reload; your data stays.

## Android (the APK)

**Link:** https://github.com/elrichjantjies-eng/lean-strong/releases/latest

1. Open the link **on your Android phone** and tap the file that ends in **.apk** to download it.
2. Open the download. If Android asks, allow **Install unknown apps** for your browser, then go back
   and tap **Install**.
3. If Google Play Protect warns about an unknown developer, tap **More details → Install anyway**.
   (The developer is you: the app is built from your own GitHub repository.)

When the app first asks to send notifications, tap **Allow** so reminders work. You can change
which reminders you get in **More → Settings → Reminders**.

**Updates:** open the same link again, download the newer .apk and install it over the old app.
Your data stays, because every version is signed with the same key (see below).

## Backup and restore (both phones)

Your history lives only on the phone. Back up every couple of weeks (the app reminds you) and
always before changing phones or reinstalling.

- **Back up:** More → Backup & restore → **Back up now**. On Android choose where to send it
  (Google Drive, WhatsApp to yourself, email). On iPhone choose **Save to Files** or send it to
  yourself.
- **Restore:** More → Backup & restore → **Choose a backup file**. You'll see what's in the file
  first, then choose **Merge** (add it to what's on the phone) or **Replace everything**.

A backup contains every profile on that phone.

## The Android signing key

The key that signs the Android app is in `C:\Users\elric\Documents\LeanStrong-signing-key\`, with
its passwords in the note next to it. **Keep a copy somewhere safe** (a USB stick, your password
manager or a private cloud folder). Updates only install over the old app if they're signed with
this same key. Without it you'd have to uninstall the app to update, which deletes its data. The
same key is stored as encrypted secrets in the GitHub repository, which is how the automatic
builds sign the app.

## Two people, one phone

Each person normally uses their own phone. If you share one, add a second profile in
**More → Profiles → Add a profile** and switch between them there.
