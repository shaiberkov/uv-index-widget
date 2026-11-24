# CLAUDE.md

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

## Caching

All caching is mapped in `docs/CACHING.md`: the forecast cache and the snapshot the widget draws, the place-name cache, the network bookkeeping that limits calls, the update check, and the downloaded APK. Rules:
- Any change that reads or writes cached data checks `docs/CACHING.md` first, and updates it in the same change. The checklist at its end lists what to look at. A PostToolUse hook (`.claude/hooks/cache_reminder.py`) reminds you after edits to data code.
- Everything is stored on the phone in `SharedPreferences`, only through `UvStore` (`uv_store`) and `UpdateChecker` (`uv_update`). No other file opens those prefs.
- Whether to call Open-Meteo is decided only in `RefreshPolicy.decide`. Code that wants fresh data calls `UvRepository.update` or `UvWorker.refreshNow`, never the network directly.
- "Now" is always computed offline from the cache: `ForecastCache.snapshot(now)` → `UvStore.save`. Nothing draws an old snapshot when the cache can produce a newer one.
- A new field in `ForecastCache` must be optional in `fromJson` (`optJSONArray` with a default), so caches saved by older versions still load. If the widget needs it, `RefreshPolicy` forces a refetch when it is missing, the way `hasWeather` does.
- Never cache: the location in place of a fresh fix while the app is open (`APP_OPEN` asks for one), the update check beyond once an hour (GitHub allows 60 calls an hour), or a network error once the data is older than `STALE_AFTER` (it must show).

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- When graphify-out/graph.json exists, run `graphify query "<question>"` before your first grep, search or source-file read for a codebase question, and put this rule in every subagent prompt that explores code. Read raw files only for lines the graph or an earlier read already located. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
- A committed graphify-out/graph.json is rewritten whole on every build, so a large diff to it in a pull request is expected. Do not flag it as churn or suggest skipping the graph rebuild or its hooks.
