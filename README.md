# ☀️ UV Index – Android home-screen widget

A home-screen widget that shows the UV index for your current location and time of day,
with an hourly UV chart, today's peak, sunrise/sunset, temperature and protection advice.

Data: [Open-Meteo](https://open-meteo.com) (free, no API key).

## Install

1. Download the newest **`UvIndex-x.y.apk`** from [Releases](../../releases/latest).
2. Open it on the phone and allow "install unknown apps" for your browser / file manager.
3. Open **UV Index**, allow location, tap **"הוסף ווידג'ט למסך הבית"**.

Updates: the app checks this repo's releases every ~6 hours (and whenever it's opened)
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

```powershell
.\release.ps1 -Version 1.8 -Notes "מה חדש"
```

Needs the signing key in `Documents\UvWidget-signing` (not in this repo – keep a backup!).
