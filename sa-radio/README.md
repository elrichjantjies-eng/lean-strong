# SA Radio 4.0

South African radio and news for Android. This is the source of the SA Radio app, rebuilt from the
2.0 APK and redesigned. See [REVIEW.md](REVIEW.md) for what was wrong with 2.0 and what changed.

4.0 has the "Number Plate" design: taxi-rank yellow, black outlines, minibus livery stripes and a
bottom bar of number plates on a road. Stations are painted signs in each station's own colour
with its logo.

| Stations | Favourites | Now Playing | Sound & data gear |
| --- | --- | --- | --- |
| ![Stations](docs/screenshots/home.jpg) | ![Favourites](docs/screenshots/favourites.jpg) | ![Now Playing](docs/screenshots/now-playing.jpg) | ![Sound and data](docs/screenshots/now-playing-sound-and-data.jpg) |

| News | Sport | Ad-free reader | Equaliser |
| --- | --- | --- | --- |
| ![News](docs/screenshots/news.jpg) | ![Sport](docs/screenshots/sport.jpg) | ![Reader](docs/screenshots/reader.jpg) | ![Equaliser](docs/screenshots/equaliser.jpg) |

| Settings | Match details | Small phone (360×640 dp) |
| --- | --- | --- |
| ![Settings](docs/screenshots/settings.jpg) | ![Match details](docs/screenshots/match.jpg) | ![Small phone](docs/screenshots/small-phone-now-playing.jpg) |

## What it does

- **Radio:** about 190 South African stations from the community directory at radio-browser.info,
  with search, provinces, favourites, recently played, and your own stream links.
- **Sound:** a real equaliser (12 presets such as Talk & News, Amapiano, Phone speaker and Late
  night, plus a curve you can drag), bass boost, surround and loudness. Uses the phone's own audio
  effects, so a few phones that reserve them for their own sound settings can't use it.
- **Data & quality:** Auto, Saver, Balanced or Best. Picks the lighter or better stream when a
  station broadcasts more than one, sets the buffer depth, and trims picture downloads. Shows the
  stream's bitrate, roughly how many MB an hour it uses, and the radio data used this session and
  this month.
- **News without the clutter:** headlines from 16 South African publishers. Stories open in an
  ad-free reader that downloads only the article's text and pictures and draws them in the app's
  own design, with adjustable text size. If a page isn't a normal article, the original page opens
  with ad networks and trackers blocked.
- **Sport** (News tab → Sport): results from the past week and fixtures for the next two, with live
  scores. Covers rugby (Springbok tests, URC, Currie Cup and European competitions), soccer
  (Betway Premiership, cups, Bafana Bafana, CAF and, on request, the big European leagues),
  cricket (Proteas and South African domestic), F1, golf and UFC. "SA focus" shows South African
  teams and players. Tap a match for line-ups, scorers, scorecards, race classifications or
  leaderboards. Data comes from ESPN's public score feeds and Jolpica (F1).
- **Visualiser** driven by the actual audio, a sleep timer that fades out, and lock-screen and
  notification controls.

## Install

Download the latest `SA-Radio-4.x.apk`, open it on the phone and allow installing from that source.
4.0 installs straight over any 3.x version, keeping favourites and settings.

The app is signed with a new key, so **uninstall SA Radio 2.0 first** (Android refuses to install
an app over one signed with a different key). Favourites from 2.0 can't be carried over.

## Build

Plain Java, no AndroidX or other libraries. Needs JDK 17+ and the Android SDK (platform 36).

```sh
./gradlew assembleDebug                 # debug APK
./gradlew testDebugUnitTest             # unit tests (Robolectric)
./gradlew testDebugUnitTest -Plive=true # also: real stations, live feeds, screenshots in build/screenshots
./gradlew assembleRelease               # signed release, needs keystore.properties (below)
```

Release signing reads `sa-radio/keystore.properties`, which is git-ignored:

```properties
storeFile=/absolute/path/to/saradio-release.jks
storePassword=...
keyAlias=saradio
keyPassword=...
```

Keep the `.jks` file and its password safe: future updates must be signed with the same key, or
users will have to uninstall again.

## Code map

| File | What it does |
| --- | --- |
| `StreamPlayer` | Downloads MP3/AAC streams, reads ICY song titles, splits frames, decodes and plays |
| `RadioService` | Background playback, stream choice and fallback, data meter, notification, sleep timer |
| `DataMode` | The Auto / Saver / Balanced / Best rules |
| `AudioFx`, `EqPresets` | Equaliser and effects on the shared audio session; presets as frequency curves |
| `Spectrum`, `BarsView` | Audio analysis for the visualiser, kept in step with the speaker |
| `StationRepo`, `NewsRepo` | Station directory and news feeds |
| `Extractor`, `Article`, `ArticleView`, `assets/reader/` | The ad-free reader: download a story's HTML, extract it with Readability, draw it |
| `AdBlock` | Ad and tracker blocking for the original-page fallback |
| `Sports`, `SportView`, `MatchSheet` | Fixtures, results and live scores; the Sport list; match details |
| `MainActivity`, `NowPlaying`, `Panels`, `Sheet` | Screens and bottom sheets |
| `Ui`, `Livery`, `LogoView`, `NavBar`, `Segmented`, `EqCurveView` | Design system and custom views: colours and fonts, livery stripes, logo roundels and brand colours, the number-plate bar |

`tools/reader-check/` re-tests the reader against live stories from every feed.

Third-party: the fonts Anton, Bungee, Caveat Brush and Space Grotesk (all SIL Open Font License
1.1); Mozilla Readability 0.6.0
(Apache License 2.0, `assets/reader/readability.js`).
