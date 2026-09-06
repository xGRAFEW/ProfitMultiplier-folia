# ProfitMultiplier — CLAUDE.md

Project context for Claude Code sessions working on this repo. See `HANDOFF.md` for
current in-flight status; this file is for durable facts that don't change week to week.

## What this is

A Paper/Folia plugin (`me.docdrewskii.profitmultiplier`) that gives players a cumulative
sell-progression multiplier: the more of an item (or item group) a player has sold over
their lifetime, the better price their next sale gets. It hooks into shop plugins
reflectively (no compile-time dependency on any of them) and layers a GUI, milestones,
Discord webhooks, and a small developer API on top.

Full feature docs live in `README.md` (user-facing) and `DEVELOPERS.md` (API consumers).
The `wiki/` folder mirrors the GitHub wiki pages.

## Build

```bash
./gradlew build
```

Produces in `build/libs/`:
- `ProfitMultiplier-<version>.jar` — the full plugin
- `ProfitMultiplier-API-<version>.jar` (+ `-sources`) — slim API-only jar for other devs

Source/target bytecode is pinned to **Java 8** (`sourceCompatibility`/`targetCompatibility`
in `build.gradle.kts`) so the same jar loads on ancient Spigot 1.8 servers as well as
current Paper/Folia — don't raise this without a strong reason, it's a stated feature
("Wide compatibility" in README.md).

### JDK requirements on this machine

This dev machine did **not** ship with a usable JDK (only a broken Oracle Java 8
"javapath" stub and a JRE-only 1.8.0_471 install — no `javac`). Two JDKs were installed
via `winget` to make the build work:

- **Temurin 21** (`EclipseAdoptium.Temurin.21.JDK`) — needed to run the **Gradle daemon
  itself**. Gradle 8.14.x (the wrapper version pinned in `gradle/wrapper/`) cannot launch
  on a JDK 25 JVM; you'll see a cryptic `FAILURE: ... 25.0.4.1` with no real explanation
  if you try. Point `JAVA_HOME` at the JDK 21 install to run `./gradlew`.
- **Temurin 25** (`EclipseAdoptium.Temurin.25.JDK`) — needed as the **compile toolchain**
  (`build.gradle.kts` sets `languageVersion.set(JavaLanguageVersion.of(25))`) because the
  Folia 26.2 test server bundles/requires Java 25 (`java_version: 25` in the server's
  `version.json`). Gradle's Java toolchain resolution will find this automatically once
  it's installed system-wide — you don't need to fight it, just make sure it's present.

If a fresh machine hits "no toolchain found" errors, install both with:
```powershell
winget install --id EclipseAdoptium.Temurin.21.JDK -e --accept-package-agreements --accept-source-agreements --silent
winget install --id EclipseAdoptium.Temurin.25.JDK -e --accept-package-agreements --accept-source-agreements --silent
```
Then run Gradle with `JAVA_HOME` pointed at the **21** install (e.g.
`C:\Program Files\Eclipse Adoptium\jdk-21.x.x.x-hotspot`) — Gradle will pick up JDK 25
separately for the toolchain via auto-detection.

### The `paper-api` coordinate looks unfamiliar — that's not a mistake

As of Minecraft **26.2** (the version this plugin now targets), Paper changed its Maven
versioning scheme. There is **no more** `<mc-version>-R0.1-SNAPSHOT` format. Versions now
look like `26.2.build.121-stable` (also `-alpha`/`-beta` channels exist). Always resolve
the current stable coordinate from
`https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml`
before bumping — don't guess the old `-R0.1-SNAPSHOT` suffix, it no longer exists for
newer versions.

There is no separate `folia-api` artifact for this version line (confirmed 404 on that
coordinate) — Paper has merged the regionized scheduler API
(`Bukkit.getGlobalRegionScheduler()`, `Bukkit.getAsyncScheduler()`,
`Bukkit.getRegionScheduler()`, `Entity#getScheduler()`) directly into `paper-api`, and
those calls work identically on plain Paper (single main thread) and Folia (per-region
threads). This is why the plugin only ever depends on `paper-api`, never a Folia-specific
artifact.

## Folia support — the threading rule

Folia has no single "main thread"; each world region and each entity is ticked by its own
thread, and the legacy `Bukkit.getScheduler()` **synchronous** methods
(`runTask`, `runTaskTimer`, `runTaskLater`, `callSyncMethod`) throw
`UnsupportedOperationException` there. The **asynchronous** variants
(`runTaskAsynchronously`, `runTaskTimerAsynchronously`, etc.) remain fully functional on
Folia — only the sync ones needed replacing.

All Folia-safe scheduling in this codebase goes through
`me.docdrewskii.profitmultiplier.util.FoliaScheduler`, which wraps the modern
Paper/Folia-shared scheduler API:

- `FoliaScheduler.runGlobal(plugin, task)` — global/console-level state (event firing,
  console command dispatch). Runs immediately if already safe, otherwise hops to the
  global region thread.
- `FoliaScheduler.runGlobalTimer(plugin, task, delayTicks, periodTicks)` — repeating
  global task (the menu-refresh loop).
- `FoliaScheduler.runForPlayer(plugin, player, task)` — anything touching a **specific
  player's** inventory/location/entity state (opening a menu, recording a sale). This is
  the one that matters most: opening a GUI for a player *other than the command sender*
  (`/pm gui <menu> <player>`) must be scheduled onto that target player's own thread, not
  run inline on the sender's thread — Folia will otherwise throw or silently corrupt state.
- `FoliaScheduler.runAsync` / `runAsyncTimer` — pure I/O (Discord webhook POSTs, the
  periodic auto-reset/save timer). No region-thread concerns here; uses
  `Bukkit.getAsyncScheduler()` directly.

**Rule of thumb when touching this code**: if a lambda calls anything on an
`org.bukkit.entity.Player` (inventory, location, `openInventory`, `updateInventory`) and
you didn't just receive that player from an event fired *for that exact player*, route it
through `FoliaScheduler.runForPlayer`. If it dispatches a console command, fires a
plugin-wide event, or touches state that isn't scoped to one player, use
`FoliaScheduler.runGlobal`.

`plugin.yml` declares `folia-supported: true` — required for Paper/Folia to enable the
plugin at all on Folia; don't remove it.

## Always test against the real Folia server

There is a real Folia 26.2 test server checked out at:

```
C:\Users\ACER\Desktop\Project\Survival SMP Folia 26.2 test
```

It runs `canvas.jar` (a Folia fork, MC 26.2, requires Java 25 — see `_start.bat`).
**Always deploy the freshly built jar into this server's `plugins/` folder and do a real
start/stop cycle before calling a change done** — compiling clean is not sufficient
evidence for Folia correctness; region-threading bugs only surface at runtime. Check the
log for the plugin's enable line and for any `UnsupportedOperationException` /
`IllegalStateException` from the scheduler or from cross-thread entity access.

The server's plugin list does **not** currently include any of ProfitMultiplier's
supported shop plugins (EconomyShopGUI, ShopGUIPlus, zShop, UltimateShop, GUIShop,
EssentialsX) — so "No supported shop plugin detected" in the log is expected there, not a
regression. It does have PlaceholderAPI, LuckPerms, and various GUI/menu plugins (KaMenu,
zMenu) that are unrelated to this plugin's own hooks.

## Repo / release

- GitHub: https://github.com/xGRAFEW/ProfitMultiplier-folia (remote `origin`, already
  configured)
- No `gh` CLI is installed on this machine — use the GitHub REST API via `curl`/PowerShell,
  or ask the user to run `gh` commands themselves, when a release/PR needs to be created.
- Versioning lives in `build.gradle.kts` (`version = "x.y.z"`) and is templated into
  `plugin.yml` at build time via `processResources`.
