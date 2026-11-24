"""PostToolUse hook (.claude/settings.json): after an edit to code that reads or writes cached data, remind Claude to
check docs/CACHING.md. It never blocks the edit."""
import json
import re
import sys

DATA_CODE = re.compile(
    r"(^|/)app/src/main/java/com/shai/uvwidget/"
    r"(UvStore|ForecastCache|RefreshPolicy|UvRepository|UpdateChecker|UvWorker|ClockTick|UvWidget|UvData)\.kt$"
)

try:
    event = json.load(sys.stdin)
except ValueError:
    sys.exit(0)
path = str((event.get("tool_input") or {}).get("file_path", "")).replace("\\", "/")
if DATA_CODE.search(path):
    print(json.dumps({"hookSpecificOutput": {
        "hookEventName": "PostToolUse",
        "additionalContext": (
            "Caching check (docs/CACHING.md): does this change read or write data that a cache holds? "
            "UvStore keys (cache, data, place_*, net_*, error), UpdateChecker prefs (checkedAt, version, notified), "
            "RefreshPolicy limits, ForecastCache.fromJson back-compat for new fields. "
            "Update docs/CACHING.md with any change."
        ),
    }}))
