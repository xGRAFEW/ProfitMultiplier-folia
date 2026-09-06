# HANDOFF

Living progress log for the "add Folia 26.2 support" work. Update this as you go; keep
`CLAUDE.md` for durable facts and this file for "what's done / what's next."

## Task

1. ✅ Create `CLAUDE.md` + this `HANDOFF.md` for session continuity.
2. ✅ Make ProfitMultiplier run correctly on Folia 26.2.
3. ✅ Verify against the real test server at
   `C:\Users\ACER\Desktop\Project\Survival SMP Folia 26.2 test`.
4. ✅ Commit + push + build a GitHub release to
   https://github.com/xGRAFEW/ProfitMultiplier-folia — done:
   https://github.com/xGRAFEW/ProfitMultiplier-folia/releases/tag/v1.2.0

## Environment discovered this session

- The dev machine had **no usable JDK** — only a broken Oracle "javapath" stub and a
  JRE-only 1.8.0_471. Installed via `winget` (both confirmed present now):
  - `EclipseAdoptium.Temurin.25.JDK` → `C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`
    — matches the Folia 26.2 server's required Java version; used as the Gradle
    **toolchain** for compiling.
  - `EclipseAdoptium.Temurin.21.JDK` → `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`
    — needed because Gradle 8.14.5 (the wrapper version) cannot bootstrap its own daemon on
    a JDK 25 JVM (fails with an opaque `FAILURE: ... 25.0.4.1` error, no real message). Set
    `JAVA_HOME` to this JDK **21** path to run `./gradlew`; Gradle finds the JDK 25
    toolchain separately/automatically for compilation.
- The test server (`canvas.jar`, a Folia fork) is MC **26.2**, protocol 776, requires Java
  25 (`versions/26.2/version.json` → `"java_version": 25`). Its `_start.bat` pointed at a
  JDK path (`jdk-25.0.3.9-hotspot`) that didn't exist on this machine — **fixed**, now
  points at the installed `jdk-25.0.4.101-hotspot`.
- Paper's Maven versioning changed for this MC version line: no more
  `<mc-version>-R0.1-SNAPSHOT`. Confirmed via `paper-api/maven-metadata.xml` that the
  current stable coordinate is `26.2.build.121-stable` (alpha/beta channels also exist
  per-build — re-check that file before bumping again later). No `folia-api` artifact
  exists any more — its scheduler API lives in `paper-api` itself now.
- `gh` CLI is **not installed** on this machine — release creation needs the GitHub REST
  API via `curl`/PowerShell (with a token) or the user's own `gh` login on another device.

## Code changes made (this session)

- Added `util/FoliaScheduler.java` — central helper wrapping the Paper/Folia-shared
  scheduler API (`getGlobalRegionScheduler`, `getAsyncScheduler`, `Entity#getScheduler`).
  See `CLAUDE.md` → "Folia support" for the usage rule.
- Replaced every synchronous `Bukkit.getScheduler().runTask*` call site with the
  appropriate `FoliaScheduler` method:
  - `ProfitMultiplier.onEnable` — menu-refresh repeating task → `runGlobalTimer`; the
    auto-reset/save repeating task → `runAsyncTimer` (was already async-safe on Folia,
    switched for API consistency, tick count converted to seconds).
  - `MenuManager.refreshOpenMenus()` — now dispatches each online player's own refresh
    via `runForPlayer` instead of touching all players' inventories from one thread.
  - `ActionExecutor` — `[open]` click action → `runForPlayer`; `[console]` click action →
    `runGlobal`.
  - `PlayerDataManager.fireSync` — event firing on reset → `runGlobal`.
  - `EssentialsSellHook.onCommand` — inventory-diff settle step → `runForPlayer`.
  - `GuiShopHook.onSold` — sale recording → `runForPlayer` (replaced the old
    `isPrimaryThread()` branch entirely).
  - `ProfitCommand.handleGui` — **important one**: `/pm gui <menu> <player>` now opens the
    menu on the *target* player's own thread via `runForPlayer`, not inline on the command
    sender's thread. This was the clearest actual Folia bug in the original code (opening
    another player's inventory from a different region's thread).
  - `MilestoneManager.runCommands` — console command dispatch for milestone rewards →
    `runGlobal`.
  - `DiscordWebhook.send` — switched to `FoliaScheduler.runAsync` for consistency (was
    already Folia-safe as `runTaskAsynchronously`).
- `plugin.yml` — added `folia-supported: true`.
- `build.gradle.kts`:
  - `paper-api` bumped to `26.2.build.121-stable`.
  - Toolchain `languageVersion` bumped 21 → 25 (needed to compile against the new API;
    `TargetJvmVersion` attribute bumped to match).
  - `sourceCompatibility`/`targetCompatibility` **left at Java 8** — do not change, it's a
    stated compatibility feature (old Spigot servers). Build succeeded with this
    combination — JDK 25 javac still accepts `--release 8`.
  - `version` bumped `1.1.0` → `1.2.0`.
- `README.md` / `DEVELOPERS.md` — version badges/text bumped to 1.2.0, added a Folia badge
  and a line under Features noting Folia support.

## Build & live-test results (this session)

- `./gradlew clean build` (with `JAVA_HOME` = JDK 21) → **BUILD SUCCESSFUL**. Only a
  benign "some input files use a deprecated API" note (pre-existing, e.g.
  `Enchantment.getByName`/`ItemFlag` deprecations, not new from this change). Produced all
  four jars in `build/libs/` at version 1.2.0.
- Deployed `ProfitMultiplier-1.2.0.jar` into the test server's `plugins/`, started it
  headless (RCON temporarily enabled for scripted control, then **reverted** back to
  `enable-rcon=false` / blank password afterward — `server.properties` is back to its
  original state except the intentional `_start.bat` JDK path fix).
  - Server reached `Done (31.438s)! For help, type "help"` with **no exceptions** from
    ProfitMultiplier during load/enable.
  - Log showed: `[ProfitMultiplier] Enabling ProfitMultiplier v1.2.0`,
    `Loaded 2 menu(s): [groups, sellmulti]`, the expected
    `No supported shop plugin detected` warning (none of this test server's installed
    plugins are in the supported-shops list — this is expected, not a regression), and
    `ProfitMultiplier v1.2.0 enabled.`
  - Ran `/pm help`, `/pm reload`, `/pm resetall`, `/pm stats` (console, correctly rejected
    with "must specify a player"), `/pm gui sellmulti` (console, correctly rejected) via
    RCON — all returned normally, no exceptions logged.
  - The per-second `runGlobalTimer` menu-refresh task and the async auto-reset task both
    ran for the ~1.5 minutes the server was up with zero scheduler errors — this is the
    code path that would immediately throw `UnsupportedOperationException` on Folia if the
    legacy scheduler were still in use, so this is meaningful evidence the fix works.
  - Sent `stop` via RCON → clean `RegionShutdownThread` shutdown sequence, all worlds/
    players saved, process exited on its own.
- **Not yet tested**: an actual connected player exercising the GUI (`/sellmulti`, opening
  a menu *for another online player* via `/pm gui <menu> <player>`, clicking items, paging).
  No client was available in this environment to join and click through it. This is the
  one path that most directly exercises `FoliaScheduler.runForPlayer` cross-player
  dispatch (`ProfitCommand.handleGui`) — if you get a chance with a real client connected
  to the Folia test server, that's the highest-value manual check left.

## Release (done)

- Committed as `a58038d` "Add Folia 26.2 support" (repo-local git identity was unset on
  this machine — set to match the existing commit history, `DocDrewskii
  <rl.docdrewskii@gmail.com>`, via `git config user.name`/`user.email` with **no**
  `--global`, so it only affects this repo).
- `git push` needed an interactive browser login (Git Credential Manager) that this
  sandboxed shell can't open — the user ran it themselves via the `!` terminal passthrough
  and it succeeded (credential now cached in Windows Credential Manager as
  `LegacyGeneric:target=git:https://github.com`, account `xGRAFEW`).
- `gh` CLI was not installed; installed via
  `winget install --id GitHub.cli -e --accept-package-agreements --accept-source-agreements --silent`
  → `C:\Program Files\GitHub CLI\gh.exe` (not yet on PATH in either this session's shells
  or the user's own terminal right after install — call it by full path, or open a fresh
  terminal, until PATH propagates).
- `gh auth login` also needs interactive browser confirmation; when run non-interactively
  it falls back to the OAuth **device code** flow (prints a one-time code + a
  `https://github.com/login/device` URL and polls in the background until the user
  authorizes it in a browser — no Enter keypress needed once it reaches that point). The
  user completed this themselves via `!`. Logged in as `xGRAFEW` with `repo` scope.
- Release created and verified:
  https://github.com/xGRAFEW/ProfitMultiplier-folia/releases/tag/v1.2.0 (tag `v1.2.0`,
  target `master`, all four `build/libs/*.jar` attached, release notes written to
  describe the Folia work). Command used:
  `gh release create v1.2.0 build/libs/*.jar --repo xGRAFEW/ProfitMultiplier-folia --title "..." --notes-file ... --target master`.

## Open items for later (not blockers)

- No real Minecraft client was available in this environment to connect and click through
  `/sellmulti` or `/pm gui <menu> <otherPlayer>` in-game — the RCON smoke test covered
  command dispatch and the repeating scheduler tasks, but not actual GUI clicks. If a
  regression ever shows up specifically in menu rendering/clicking on Folia, that's the
  path to manually verify first.
- `Bukkit.getOfflinePlayer(name)` (used in `SkullUtil.fromPlayer` and elsewhere) can do a
  blocking Mojang lookup on cache miss — pre-existing behavior, not Folia-specific, not in
  scope for this task, but worth a future look if startup/GUI-open latency ever comes up.
- `gh` and both Temurin JDKs (21, 25) are now installed system-wide on this dev machine —
  future sessions shouldn't need to reinstall them; `gh auth` and the git credential
  should also both still be cached (Windows Credential Manager / gh's keyring storage).
