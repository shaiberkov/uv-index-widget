# ☀️ UV Index – Android home-screen widget

A home-screen widget that shows the UV index for your current location and time of day,
with an hourly UV chart, today's peak, sunrise/sunset, temperature and protection advice.

Data: [Open-Meteo](https://open-meteo.com) (free, no API key).

## Install

1. Download the newest **`UvIndex-x.y.apk`** from [Releases](../../releases/latest).
2. Open it on the phone and allow "install unknown apps" for your browser / file manager.
3. Open **UV Index**, allow location, tap **"הוסף ווידג'ט למסך הבית"**.

Updates: the app checks this repo's releases every hour (and whenever it's opened)
and offers a one-tap update.

## How it works

| File | What it does |
|---|---|
| `UvWidget.kt` | The Glance widget UI; tap = refresh |
| `UvRepository.kt` | Location + Open-Meteo calls |
| `ForecastCache.kt` | 2-day hourly forecast kept on the phone; "UV now" is interpolated locally |
| `RefreshPolicy.kt` | Smart rate limiter: auto refresh ~30 min, tap ≥5 min, move >10 km, 30 s gap, backoff, 150/day cap |
| `UvWorker.kt` | 15-min background tick (mostly offline), expedited job for taps |
| `UpdateChecker.kt` | Self-update from GitHub Releases |

## Releasing a new version

Fully automatic – **merge to `main`** and GitHub Actions does the rest
([`.github/workflows/release.yml`](.github/workflows/release.yml)):

1. Bumps the version from the last tag (`v1.7` → `v1.8` …; `#major` in a commit → `v2.0`).
2. Builds an APK signed with the release key (stored as repository secrets).
3. Publishes a GitHub Release with notes made from the commit messages.
4. Installed apps notice it within ~1 h (notification + "⬆ עדכון זמין" on the widget) (instantly when opened) and offer a one-tap update.

- Changes that don't touch the app (README, docs) don't release.
- `[skip release]` in the merge message → merge without releasing.
- Pull requests are build-checked first ([`build.yml`](.github/workflows/build.yml)).
- Manual run: **Actions → Release → Run workflow**.

Signing secrets: `UVWIDGET_KEYSTORE_BASE64`, `UVWIDGET_KEYSTORE_PASSWORD`,
`UVWIDGET_KEY_ALIAS`, `UVWIDGET_KEY_PASSWORD`. The original key file is kept
outside the repo (`Documents\UvWidget-signing`) – keep a backup!
