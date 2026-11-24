# קשינג

כל הקשינג בפרויקט: מה נשמר, איפה, לכמה זמן, ומה מרענן אותו. הכול על הטלפון: `SharedPreferences` ותיקיית ה-cache של האפליקציה. אין שרת משלנו. הנתונים מגיעים מ-Open-Meteo (תחזית), מה-Geocoder של אנדרואיד (שם המקום) ומ-GitHub Releases (עדכוני גרסה).

**כל שינוי קוד שקורא או כותב נתונים שמורים צריך לעבור על הקובץ הזה.** הרשימה ב"צ'ק-ליסט לכל שינוי" למטה.

## איך זה בנוי

| שכבה | איפה בקוד | מה זה חוסך |
|---|---|---|
| קאש התחזית | `ForecastCache.kt`, נשמר ב-`UvStore` (מפתח `cache`) | קריאה לשרת בכל ציור: תחזית שעתית ליומיים מספיקה לחשב "עכשיו" בלי רשת |
| ה-snapshot שהווידג'ט מצייר | `UvData`, נשמר ב-`UvStore` (מפתח `data`) | חישוב מחדש בכל ציור של הווידג'ט או של המסך באפליקציה |
| קאש שמות מקומות | `UvStore.cachedPlace` / `savePlace` (מפתחות `place_<lat>_<lon>`) | Geocoder איטי בכל רענון |
| מיקום אחרון | `UvStore.saveLocation` (מפתחות `lat`, `lon`) | רענון ברקע בלי מיקום טרי |
| ניהול הרשת | `UvStore.netState` (מפתחות `net_*`), ההחלטה ב-`RefreshPolicy.decide` | קריאות מיותרות ל-Open-Meteo, הצפה אחרי שגיאות |
| בדיקת עדכונים | `UpdateChecker` (prefs `uv_update`) | קריאה ל-GitHub יותר מפעם בשעה |
| ה-APK שהורד | `cacheDir/updates/` | — נמחק לפני כל הורדה חדשה |

### המפתחות

| מפתח | מה נשמר | תוקף | מי כותב | מי קורא |
|---|---|---|---|---|
| `uv_store` / `cache` | `ForecastCache` כ-JSON: 48 שעות של UV, טמפרטורה, יום/לילה, מזג אוויר; זריחה ושקיעה; `fetchedAt`, `lat`, `lon`, `place` | לפי `RefreshPolicy` (למטה) | `UvRepository.update` אחרי fetch מוצלח | `UvRepository.update`, `wouldFetch`, `ClockTick.recompute` |
| `uv_store` / `data` | `UvData`: מה שהווידג'ט מצייר ברגע מסוים | עד החישוב הבא מהקאש | `UvRepository.update`, `ClockTick.recompute` | `UvWidget` (ציור), `MainActivity` (רקע השמיים) |
| `uv_store` / `place_%.2f_%.2f` | שם העיר לרשת של כ-1 ק"מ | לתמיד | `UvRepository.placeName` | `UvRepository.placeName` |
| `uv_store` / `lat`, `lon` | המיקום האחרון שנמצא | עד המיקום הבא | `UvRepository.update` | `UvRepository.update`, `wouldFetch` |
| `uv_store` / `net_last_attempt`, `net_failures`, `net_last_failure`, `net_calls_day`, `net_calls_today` | מתי ניסינו, כמה כשלונות ברצף, כמה קריאות היום | מתאפס ביום חדש / בהצלחה | `markAttempt`, `markSuccess`, `markFailure` | `RefreshPolicy.decide` |
| `uv_store` / `error` | הודעת השגיאה שמוצגת בווידג'ט | עד הרענון הבא | `UvRepository.update` | `UvWidget` |
| `uv_update` / `checkedAt` | מתי בדקנו את GitHub | שעה (`CHECK_EVERY`) | `UpdateChecker.check` | `UpdateChecker.check` |
| `uv_update` / `version`, `url`, `notes` | הגרסה האחרונה ב-GitHub וה-APK שלה | עד הבדיקה הבאה; מתעלמים ממנה כשהיא לא חדשה מהמותקנת | `UpdateChecker.check` | `UpdateChecker.cached` (הווידג'ט, בלי רשת) |
| `uv_update` / `notified` | על איזו גרסה כבר שלחנו התראה | לתמיד | `checkAndNotify` | `checkAndNotify` |

## מתי פונים לשרת

הכול מוחלט ב-`RefreshPolicy.decide`. סדר הבדיקות:

1. 150 קריאות ביום (`DAILY_CAP`): מעבר לזה לא פונים.
2. 30 שניות מהניסיון הקודם (`MIN_GAP`): לא פונים שוב.
3. אין מיקום: לא פונים.
4. רענון אוטומטי אחרי כשלון: מחכים 1, 2, 4… עד 30 דקות (`backoff`).
5. אין קאש, או שהוא לא מכסה את 3 השעות הבאות: פונים.
6. קאש מגרסה ישנה בלי נתוני מזג אוויר (`hasWeather`): פונים.
7. זזנו יותר מ-10 ק"מ (`MOVE_METERS`): פונים.
8. גיל הנתונים: אוטומטי מעל 25 דקות (`DAY_MAX_AGE` / `NIGHT_MAX_AGE`), לחיצה או פתיחת האפליקציה מעל 5 דקות (`MANUAL_MAX_AGE`).

שגיאת רשת מוצגת בווידג'ט רק כשהנתונים שמוצגים ישנים מ-2 שעות (`STALE_AFTER`). לפני זה מציגים את הקאש בשקט.

## מתי מחשבים "עכשיו" מהקאש

כל אלה קוראים ל-`ForecastCache.snapshot(now)` ושומרים את התוצאה ב-`data`, בלי רשת:

| מי | מתי |
|---|---|
| `ClockTick` | alarm מדויק בכל שעה עגולה + שנייה (12:00:01), כדי שעמודת "עכשיו" תתחלף בזמן. אם גם הגיע זמן fetch, מפעיל את `UvWorker.refreshNow` |
| `UvWorker` (תקופתי) | כל 15 דקות, דרך `UvRepository.update(AUTO)` |
| לחיצה על הווידג'ט | `RefreshAction`: אם `wouldFetch` אומר שלא צריך רשת, `update(MANUAL)` מהקאש ומציג "✓ עדכני" |
| פתיחת האפליקציה | `update(APP_OPEN)` עם מיקום טרי |

`snapshot` לא משנה את `fetchedAt`, אז "עודכן לפני X" תמיד מתייחס לקריאה האחרונה לשרת, לא לחישוב האחרון.

## תאימות לגרסאות קודמות

הקאש נשאר על הטלפון בין עדכוני גרסה. `ForecastCache.fromJson` קורא שדות חדשים עם `optJSONArray` וברירת מחדל ריקה, כך שקאש מגרסה ישנה נטען בלי לקרוס. כשהווידג'ט צריך את השדה החדש, `RefreshPolicy` מכריח fetch כשהוא חסר (כמו `hasWeather`). `UvData.fromJson` עטוף ב-`runCatching`: JSON שלא נקרא נחשב כאין snapshot.

## צ'ק-ליסט לכל שינוי

- **שדה חדש ב-`ForecastCache`:** ב-`toJson`, ב-`fromJson` עם `opt…` וברירת מחדל, וב-`fromApi`. אם הוא חובה לציור, תנאי ב-`RefreshPolicy` שמכריח fetch כשהוא ריק. ופרמטר חדש ב-`buildUrl`.
- **שדה חדש ב-`UvData`:** מחושב ב-`snapshot`, עם ברירת מחדל ב-`fromJson` לקריאת snapshot ישן.
- **מקום חדש שמציג נתונים:** קורא את `UvStore.load` או מחשב `snapshot(now)` מ-`UvStore.loadCache`. לא פונה לרשת בעצמו.
- **מקום חדש שצריך נתונים טריים:** `UvRepository.update` או `UvWorker.refreshNow`, כדי שיעבור דרך `RefreshPolicy` ויספר ב-`net_*`.
- **שינוי בתדירות או בגבולות** (`DAY_MAX_AGE`, `MANUAL_MAX_AGE`, `DAILY_CAP`, `MIN_GAP`, `CHECK_EVERY`): מעדכנים את הטבלאות כאן. Open-Meteo מגביל ל-10,000 קריאות ביום לכתובת, ו-GitHub ל-60 בשעה בלי טוקן.
- **מפתח חדש ב-`SharedPreferences`:** רק דרך `UvStore` או `UpdateChecker`, ושורה בטבלת המפתחות.
- **שינוי בתזמון "עכשיו"** (`ClockTick`, `UvWorker.schedule`): `schedule` ו-`cancel` בשני המקומות נשארים זוגיים.

## מה הקאש לא פותר

- **מיקום ברקע:** אנדרואיד לא נותן מיקום טרי לאפליקציה ברקע, אז רענון אוטומטי משתמש במיקום האחרון שנמצא. נסיעה בלי לפתוח את האפליקציה תציג את המקום הקודם עד שהמערכת תעדכן את המיקום האחרון.
- **Doze וחיסכון סוללה של סמסונג:** יכולים לעכב את ה-tick של 15 דקות בשעה ויותר. `ClockTick` מתקן את "עכשיו", אבל fetch חדש מחכה לרשת.
- **תחזית ליומיים:** בלי רשת יותר מ-48 שעות, `covers` נכשל והווידג'ט נשאר על ה-snapshot האחרון.
