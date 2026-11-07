# Skyglass Weather: install, widgets and alerts

## iPhone (the web app)

**Link:** https://elrichjantjies-eng.github.io/lean-strong/weather/

1. Open the link in **Safari**.
2. Tap **Share** (the square with an arrow) → **Add to Home Screen** → **Add**.
3. Open Skyglass from the Home Screen and tap **Allow** when it asks for your location.

Updates are automatic. iPhone web apps can't add home-screen widgets or send scheduled
notifications; SAWS warnings still appear at the top of the forecast.

If Lean & Strong opens instead of Skyglass, open Lean & Strong once, tap **Update available →
Reload**, then open the weather link again. (Both apps live on the same site; the update teaches
Lean & Strong to leave the weather app alone.)

## Android (the app with widgets)

**Link:** https://github.com/elrichjantjies-eng/lean-strong/releases?q=weather

1. Open the link **on your Android phone**, open the newest **Skyglass Weather** release and tap
   the file ending in **.apk**.
2. Open the download. If asked, allow **Install unknown apps** for your browser, go back and tap
   **Install**. If Play Protect warns, tap **More details → Install anyway**.
3. Open Skyglass and allow **location** (for My Location) and **notifications** (for alerts).

It installs alongside Lean & Strong. To update, install a newer .apk over the old one: your
places and settings stay.

### Adding a widget (Samsung and other Android phones)

1. Long-press an empty spot on the home screen → **Widgets**.
2. Find **Skyglass** and pick a widget: small, wide, clock, hourly or forecast.
3. Drag it to the home screen. Resize it by long-pressing it: the hourly and forecast widgets show
   more hours and days as they get bigger.

You can also add widgets from **Skyglass → Settings → Home-screen widgets**.

- **Which place:** widgets follow your first location (My Location when it's on). To pin a widget
  to a city, long-press it → **Settings** (or the pencil) and choose. On older Android versions
  this choice appears when you add the widget.
- **Look:** Skyglass → Settings → Home-screen widgets: **Glass**, **Frosted** (milky glass with dark
  text, best on busy wallpapers), **Dark** or **Sky tint** (colours follow the weather), plus
  the glass opacity. The preview there updates as you change it.
- **Refresh:** widgets update every 30 minutes, and right away when you tap ↻ on the hourly and
  forecast widgets. Tapping a widget opens Skyglass on that place.

**Samsung tip:** if widgets stop updating, open **Settings → Apps → Skyglass → Battery** and choose
**Unrestricted**, so One UI doesn't put the app to sleep.

### Notifications

Skyglass → Settings → Notifications:

- **Severe weather warnings:** a new SAWS warning covering your first location.
- **Rain starting soon:** when it's dry and rain is expected within the hour (at most every 3 hours).
- **Daily forecast:** a morning summary at the time you choose.

## Good to know

- **SAWS data on iPhone** comes from a copy refreshed every two hours by GitHub Actions. GitHub
  pauses scheduled jobs in a repository with no activity for 60 days; if the official forecast
  card disappears, open the repository's **Actions** tab, choose **Deploy web app** and enable it
  (or run it once).
- Your places and settings stay on your phone. There's no account.
