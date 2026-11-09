# SA Radio 2.0: review and changes in 3.0

SA Radio 2.0 had a solid core. It decoded streams itself, read song titles from the stream, and
had a sleep timer, favourites and a news reader. But some things were broken or would quietly
fail, and the design looked dated. Below: what I found, how I checked it, and what 3.0 changes.

## How this was checked

- I decompiled the 2.0 APK (10 Java classes, no libraries) and read all of it.
- I tested every news feed URL and the station directory over the network.
- I rebuilt the app and wrote 13 unit tests. One test simulates live streaming to measure the
  buffering bug below.
- A live test connects the real stream engine to the 14 most popular stations. Only the audio
  decoder is a stand-in. 11 of 14 connected, synced and produced audio frames, with real song titles
  (e.g. *Stargazing – Myles Smith* on Jacaranda FM). The other 3 use ports 8000 and 3690, which my
  test environment blocks; phones aren't affected.
- I rendered every screen at a typical size (393×852 dp) and a small one (360×640 dp), and fixed
  what looked wrong.
- **Not yet tested on a real phone.** No Android emulator could run where I worked. Actual sound
  output, the equalizer's effect on real hardware, and the lock-screen controls still need a quick
  check on a device.

## What wasn't working

| Problem in 2.0 | Effect | Fix in 3.0 |
| --- | --- | --- |
| After playback started, the decoder paused to refill whenever the *network* buffer was briefly empty, even with seconds of audio still waiting in its own buffer. | Recurring "Buffering…" and gaps. In a 12-second simulation at 128 kbps the old rule tripped 14–15 times. | It now pauses only when the audio has really run out. Same simulation: 0 stalls. |
| 4 of 14 news feeds had moved or been switched off: TimesLIVE, Sowetan, Mail & Guardian (all now 404) and EWN (no feed at all). | Those publishers never appeared. | Feeds fixed for TimesLIVE, Sowetan and M&G. EWN removed. eNCA, GroundUp and BusinessLIVE added. Live check: 353 stories from 14 publishers. |
| The date parser read two-digit years ("04 Nov 26") as the year 26 AD. | Any feed using that format (eNCA does) would have lost every story as "too old". | Fixed and covered by a test. |
| When a station was listed more than once (for example Jacaranda FM at 48 kbps AAC+ and 128 kbps MP3), only the first listing was kept. | The lighter streams were lost, and so was a backup when the main stream was down. | The listings are merged into one station with several streams. 16 stations now offer a choice. If a stream fails, the app tries the station's other streams. |
| The province filter only used the listing's "state" field, which is blank for most stations. | Most stations never appeared under any province. | The province is also worked out from city names in the name and tags, so 10 more stations now appear under their province. |
| The "equalizer" was a decoration: four bars moving on a timer, unconnected to the sound. | No way to shape the sound. | A real equalizer with presets and effects (below). The visualiser now follows the actual audio. |
| A logo that failed to load (for example when opened offline) was never retried until restart. The picture cache grew forever. | Blank logos. Storage creep. | Failed pictures are retried when the network returns. The cache is capped at about 40 MB. |
| The station list was downloaded again on every launch. | Wasted data. | It refreshes after 12 hours (3 days in Data saver), or on demand in Settings. |
| The sleep timer cut the sound off mid-sentence. | Jarring. | It fades out over the last 30 seconds. |
| Some stations send their own name as the "song". Every interruption said "Paused for call". The search box promised to search "shows". | Misleading text. | Station names are no longer shown as songs. The text now just says "Paused". The search box says "stations, genres, cities". |
| The app targeted Android 13 (SDK 33) and lacked the media-playback service type. | Newer Android versions and Play Protect treat it as outdated. | It now targets SDK 35 with the correct service type, draws edge to edge, and uses version checks for newer APIs. |

## Design

2.0 used glossy "bubble" logos (a 2010-era look), plain system dialogs, toasts and a basic tab
bar. 3.0:

- **Look:** a near-black base with soft glows in the South African flag's gold, green and blue. A
  small flag stripe sits beside the greeting. Text is set in Plus Jakarta Sans throughout.
- **Station tiles:** rounded artwork cards, colourful initials when there's no logo, a gold ring
  and pulse on the station that's playing, and a gold star on favourites.
- **Now Playing:** large artwork with a glow in the station's own colour, which also tints the
  background. It has a visualiser, a LIVE badge, the song, sleep and favourite buttons, and the
  sound and data controls. Swipe down to close.
- **Navigation:** icon-only, as asked. The selected tab sits on a gold pill that glides between
  icons. Screen readers still hear "Radio", "Favourites" and "News", and a long press shows a
  tooltip.
- **Sheets, empty states and messages** in the app's own style, replacing system dialogs and
  toasts.
- **Small phones:** the Now Playing artwork shrinks so the play button stays on screen, and the
  home screen tightens so stations show without scrolling.

## Sound: the equalizer

There are 12 presets made for radio listening:

- Normal
- Talk & News
- Bass boost
- Amapiano
- Pop & Hits
- Rock
- Jazz & Soul
- Gospel & Choir
- Classical
- Phone speaker
- In the car
- Late night

You can also drag the curve to make your own. Bass boost, surround (best with headphones) and
loudness are separate sliders. Presets are defined as frequency curves and fitted to whatever bands
the phone's equalizer offers. If the phone doesn't allow apps to change the sound, the app says so
instead of failing quietly. Quick preset chips are also right on the Now Playing screen.

## Data & quality

There are four settings: **Auto**, **Saver**, **Balanced** and **Best**. Auto uses Best on Wi-Fi
and Balanced on mobile data, and switches when you change networks. You can change it on the Now
Playing screen or in Settings.

A radio stream's bitrate is set by the station, so the setting is honest about what it can and
can't do:

- **It changes:**
  - which stream plays, when the station has more than one (Jacaranda FM: 48 kbps ≈ 22 MB an hour
    vs 128 kbps ≈ 58 MB an hour);
  - how much audio is buffered (more in Best, for fewer drop-outs);
  - logo sizes, preview pictures for news, and pictures inside articles (off in Saver).
- **It shows:** the bitrate playing now, about how many MB an hour that uses, and the radio data
  used this session and this month. The data figure is counted from bytes actually downloaded.
- **It tells you** when a station has only one stream, so the sound won't change.

## Journalism notes

- Every story shows its publisher and time, and opens on the publisher's own site.
- "Top stories" mixes all publishers newest first. The chips filter by publisher.
- News24 and MyBroadband sit behind Cloudflare bot protection, which blocked my test requests. They
  will likely load on phones, but may not on every network.
- The station directory is run by volunteers, so some stations are off air at times. The app now
  says so in Settings and falls back to a station's other streams where it can.

## Installing over 2.0

The original signing key wasn't available, so 3.0 is signed with a new key. Uninstall 2.0 before
installing 3.0. Favourites from 2.0 can't be carried over. Keep the new key: future updates must
use it.

## 3.1: ad-free news reader

In 2.0 and 3.0, tapping a headline loaded the publisher's whole web page: ads, pop-ups, cookie
banners and dozens of trackers.

Now the app downloads only the page's HTML. It pulls the story out with Mozilla Readability, the
engine behind Firefox's Reader View, inside a hidden WebView with network access switched off, so
none of the page's scripts or ads run. The app then draws the story itself:

- headline, byline and read time
- the lead picture, then paragraphs, sub-headings, quotes, lists, tables and pictures with captions
- links to embedded videos and posts

**Tested on 28 live stories from 14 publishers.** Moneyweb blocked my test server, and News24 and
MyBroadband blocked their feeds. All 26 stories that loaded came out cleanly in under 0.1 s each.
I tuned the clean-up rules on those stories so they drop:

- "Story audio is generated using AI" notices
- "Also read" link lists
- "Share your thoughts in the comments" prompts
- AI-summary widgets
- site names glued onto headlines

Run together with names, bylines are separated again ("Thando Maeko, Political Correspondent").

**Data:** for 8 of those stories, the full web pages used **36 MB**. TimesLIVE alone made 993
requests and used 12.8 MB for one story, 3 MB of it from ad and tracker networks. The reader used
**2 MB** with pictures (6%), or **0.6 MB** as text only (2%). In Data saver, pictures load only
when tapped.

**What it doesn't do:**

- It doesn't get around paywalls. When a publisher marks a story as subscriber-only and serves
  only the opening, the reader shows that opening and a "Continue on [publisher]" button.
- When a page isn't a normal article (a video, live blog or gallery), the reader says so and offers
  the original page. On the original page, about 90 ad, recommendation and tracker networks are
  blocked and empty ad slots are hidden.
- Every story ends with a note that it belongs to the publisher, and a button to open the original.
  Ads pay for much of this journalism.

Not yet checked on a real phone: the hidden-WebView step. The extraction itself was tested in
Chromium, which is the engine Android's WebView uses, and the reader screens were rendered from
real stories.

## 3.2: Sport

The News tab now has a **Headlines | Sport** switch. Sport stays inside the News tab and never
touches the radio.

**What it shows:**

- **Results** from the past week and **fixtures** for the next two, grouped by day and competition.
  Live matches sit at the top and refresh every minute while the screen is open.
- **Filters:** SA focus, Rugby, Soccer, Cricket, F1, Golf and UFC. SA focus shows matches with South
  African teams or players, plus F1. South African sides are marked in gold.
- **Details when you tap a match:**
  - **Soccer:** goals and cards with minutes, line-ups by formation with substitutes, and match
    stats (possession, shots, corners and more)
  - **Rugby:** the scoring timeline (tries, conversions and penalties, with the running score), the
    starting XV and replacements
  - **Cricket:** full batting and bowling scorecards and both XIs
  - **F1:** the race classification, qualifying and the drivers' championship; for upcoming races,
    the weekend's session times in your time zone
  - **Golf:** the leaderboard with round scores, South Africans listed first
  - **UFC:** the full fight card with results

**Competitions:**

- **Rugby:** Nations Championship and other Springbok tests, URC, Currie Cup, Champions and
  Challenge Cup, the Rugby Championship, Six Nations, Premiership and Top 14.
- **Soccer:** Betway Premiership, Nedbank Cup, MTN8, Motsepe Foundation Championship, Bafana Bafana
  (qualifiers, AFCON and friendlies), CAF Champions League and Confederation Cup. The Premier
  League, Champions League, La Liga, Serie A, Bundesliga and Europa League are fetched only when
  you open the Soccer filter, to save data.
- **Cricket:** the Proteas (men and women), South African domestic competitions, SA20, and
  internationals between the major nations.
- **F1, golf and UFC:** F1, the DP World Tour and PGA Tour, and UFC events with South African
  fighters highlighted. American sports are left out, as asked.

**Where the data comes from:**

- **ESPN:** rugby, soccer, cricket, golf and UFC come from ESPN's public score feeds, the ones its
  website uses. They need no key, but they're unofficial and undocumented, so ESPN could change
  them; if that happens, Sport shows an error and the rest of the app is unaffected.
- **Jolpica:** F1 comes from Jolpica, the open-data successor to the Ergast F1 database.
- **Not SuperSport:** SuperSport has no public data feed. Its site uses a private internal API, and
  building on that would break its terms and could stop working without notice.

**Data use:** a full refresh is about 30 small requests, fetched only when Sport is opened and
cached for 15 minutes. Live updates refresh only the competitions with live matches. Team logos
are fetched at 80 px, about 12 times smaller than ESPN's full-size files.

**Checked against live data on 9 November 2026:**

- 67 fixtures and results came back from 29 requests, with none failing.
- Italy 7–66 South Africa with its full scoring timeline, and Arsenal's line-up in its 4-2-3-1.
- Western Province v Lions with the full scorecard.
- The Brazilian Grand Prix classification. Cars a lap down now read "+1 lap": the feed's raw
  "+4.427" for those cars wasn't a gap to the winner.
- The Abu Dhabi leaderboard with all nine South Africans.

Two feed quirks are handled:

- ESPN's cricket "end dates" are placeholders, so cricket results are filed under the day they
  were played.
- Its "starts at 13:00 local time" notes give the venue's time, so they're dropped in favour of
  the app's own times.
