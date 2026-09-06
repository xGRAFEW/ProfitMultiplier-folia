# HANDOFF

Living progress log. Update this as you go; keep `CLAUDE.md` for durable facts and this file
for "what's done / what's next." Newest session at the top.

---

## Session: per-category progress GUI + built-in shop (v1.3.0 → v1.3.1)

### Follow-up fixes (v1.3.1, after live client testing)

Live-tested on the real Folia test server once a working Vault economy was available (see
"Open items" below — turned out to be `plugins/zEssentials/modules/economy/config.yml` having
`enable: false`; flipping it to `true` registered zEssentials' economy with Vault and fixed
`/sell`/`/sellall` immediately with **zero ProfitMultiplier changes needed** — confirmed via
the boot log: `[zEssentials] Register Vault Economy.` → `[ProfitMultiplier] Hooked into a
Vault economy`). After that, user feedback from actually clicking through the GUI:

1. **`/sell` mechanic changed**: was "sell the instant an item lands in a slot, slot always
   stays empty" (zero-dupe-window by construction). User wants the opposite UX: items sit in
   the sell-chest slots for real (like a normal container) while dragging more in, and the
   sale only finalizes when the player **closes** the GUI — everything left inside at that
   point is sold in one pass (or handed back if a sale fails, e.g. economy vanished
   mid-session). Rewrote `SellMenuListener` around this:
   - `InventoryClickEvent`: switches on `event.getAction()` instead of cancelling
     everything — only cancels the specific click if it would introduce an unsellable item
     into a sell slot (checked for `PLACE_ALL/SOME/ONE`, `SWAP_WITH_CURSOR` via
     `event.getCursor()`, `MOVE_TO_OTHER_INVENTORY` shift-click-in via
     `event.getCurrentItem()`, `HOTBAR_SWAP`/`HOTBAR_MOVE_AND_READD` via the hotbar slot
     item). Every other action only ever *removes* items from the sell inventory, so it's
     left alone — normal chest-like play.
   - `InventoryDragEvent`: now allowed (previously blanket-cancelled) unless the dragged
     item type isn't sellable.
   - `InventoryCloseEvent`: aggregates everything left in the inventory by material, sells
     each via `SellShopService.sellDetached` (still pay-before-take at that layer), and
     gives back anything that couldn't be sold via `Player#getInventory().addItem` +
     drop-on-overflow, then clears the inventory.
   - Trade-off accepted knowingly: items now sit in a real (if non-persistent) `Inventory`
     for the duration the GUI is open, so a mid-session server crash would **lose** those
     items (they're never written to disk — a plain custom `Inventory` isn't tied to a
     block/container). This is a loss risk, not a dupe risk, and only fires on an actual
     crash (not something a player can trigger deliberately) — accepted per explicit user
     request to change the UX this way.
2. **Removed the gray glass-pane filler/background from every menu** (`sellmulti.yml`,
   `category-items.yml`, `groups.yml`) — set `filler.enabled: false` in all three (left the
   block in place, just toggled off, so it's still there to flip back on or restyle). User
   is doing their own GUI art/decoration from here.
3. **Sell chat message** (item name + price, sent only to the seller) — this was already
   exactly what `SellMenuListener.announceSale` did (`plugin.getLang().send(player, ...)`,
   never broadcast); no change needed there, just re-verified it still fires correctly under
   the new close-to-finalize flow (one line per distinct material sold).

Rebuilt, redeployed to the real Folia test server, did another enable/disable cycle — clean,
no exceptions, `Hooked into a Vault economy` still confirmed present in the log.

### Task (original v1.3.0 scope)

1. ✅ Restructure `/sellmulti` so every category (`ItemGroup`) shows its own progress,
   multiplier, and threshold ladder — never a combined/global counter. This was mostly
   already true at the engine level (`SellProcessor`/`PlayerDataManager` already resolved
   and recorded per-category); the actual gap was the GUI, which was hardcoded to one demo
   category instead of showing all of them.
2. ✅ Add a native built-in shop: `/sell` (drag-to-sell chest GUI) and `/sellall` (sweep the
   whole inventory), backed by Vault, reusing the exact same `SellProcessor` pricing path as
   every other shop hook.
3. ✅ Build, deploy to the real Folia 26.2 test server, verify a clean enable with no
   exceptions.
4. ✅ Commit + push + build a GitHub release.

### Design decisions (confirmed with the user before implementing)

- **Naming**: kept the internal class name `ItemGroup` and config key `groups:` as-is — only
  user-facing text now says "Category". A full rename would touch ~10 files and break every
  existing server's `config.yml` for zero functional gain.
- **Item price source**: a new admin-set `price` (per item) / `prices:` (per group, keyed by
  material) field in `config.yml` — **not** a live price pulled from the connected shop
  plugin. None of the existing shop hooks expose a "what does this cost" query (they only
  react to an actual sale event), and several shops have floating supply/demand prices, so a
  reflective price-lookup per shop plugin would be both a lot of extra hook code and fragile
  across shop-plugin updates. This same `price` value now doubles as the real `/sell`
  transaction price (confirmed with the user — one source of truth instead of two).
- **Economy backend**: Vault (`VaultAPI` was already a `compileOnly` dependency and already
  in `softdepend` from a previous session — unused until now). `EconomyManager` looks up the
  `Economy` service **fresh on every call** rather than caching it at `onEnable` — economy
  plugins can register their Vault provider after ProfitMultiplier enables depending on load
  order, so a one-time lookup at boot would wrongly report the shop as permanently
  unavailable on some servers.
- **`/sell` GUI mechanics (the dupe-risk-sensitive part)**: the sell-chest inventory's slots
  are **never actually used as storage**. Every click/shift-click that would place an item
  into a sell slot is intercepted (`SellMenuListener`, cancels the event first like the
  existing `MenuListener` pattern), sold immediately via `SellShopService.sellDetached`, and
  the slot stays empty. There is no tick where a sold item physically sits in the GUI, which
  removes the whole class of "item got stuck when the window closed at the wrong moment"
  dupe/loss bugs. Money is always credited **before** anything is removed from the player's
  real inventory/cursor — if the Vault deposit fails, nothing is taken and nothing is
  recorded. `InventoryDragEvent` is cancelled outright over this GUI (not supported) to avoid
  the more complex multi-slot-split dupe surface. An `InventoryCloseEvent` handler is still
  present as a belt-and-braces safety net (sell-or-return anything unexpectedly found in the
  GUI on close), even though normal operation should never leave anything there.
  `/sellall` batches all matching items per material into one `SellProcessor` call each
  (correct threshold-crossing math) and only removes them from the player's
  `getStorageContents()` (hotbar + main inventory — armor/offhand deliberately excluded)
  after that material's money has already landed.
- **Category GUI navigation**: added a small `Map<UUID,String>` session field in
  `MenuManager` (`selectedGroup`) plus a new content-source keyword `items:@selected` /
  `tiers:@selected`. When a dynamic `content-source: groups` tile is clicked, its `{group}`
  token is captured in `ActionExecutor`'s `OPEN` case before the follow-up menu opens, so a
  *statically defined* YAML menu (`category-items.yml`) can render whichever category the
  player *just clicked*, dynamically. This is purely a GUI navigation aid — `SellProcessor`
  never reads it, so it can't affect what multiplier a real sale gets.

### Files changed

- `model/ItemGroup.java` — added `Map<Material, Double> prices` + `getPrice(Material)`.
- `config/ConfigManager.java` — parses `items.<mat>.price` and `groups.<name>.prices`, new
  `getPrice(Material)` / `isSellable(Material)` (group price wins over the standalone item
  price), and now warns (instead of silently keeping the first match) when a material is
  listed in two groups.
- `economy/EconomyManager.java` (new) — thin Vault `Economy` wrapper, lazy lookup, graceful
  "no economy" fallback (uses the existing `Currency` formatter for `format()` in that case).
- `shop/SellShopService.java`, `shop/SellAllResult.java` (new) — the pay-then-take selling
  core shared by the GUI and `/sellall`.
- `shop/SellMenu.java`, `shop/SellMenuHolder.java`, `shop/SellMenuListener.java` (new) — the
  `/sell` chest GUI and its click/drag/close handling described above.
- `command/SellCommand.java` (new) — handles both `sell` and `sellall` commands.
- `gui/MenuManager.java` — `selectedGroup` session map, `items:@selected` content-source,
  `{selected_group}` menu-title token.
- `gui/ActionExecutor.java` — `[open]` now captures `{group}` into the session map first.
- `menus/sellmulti.yml` — rewritten from a hardcoded single-category demo into a dynamic
  grid of every configured category (paginated).
- `menus/category-items.yml` (new) — read-only per-category item + price list, opened from
  either `sellmulti.yml` or `groups.yml`.
- `menus/groups.yml` — its entry click now also opens `category-items` instead of just a
  chat message (kept as a working alias menu; `/sellmulti` is the primary hub now).
- `plugin.yml` — `sell`/`sellall` commands, `profitmultiplier.sell` /
  `profitmultiplier.sellall` permissions (both default `true`, matching `.gui`/`.stats`).
- `config.yml` — new `shop:` section (`sell-gui-title`, `sell-gui-rows`), example
  `price`/`prices` values added to the `items:`/`crops`/`ores` sections so the shop actually
  has something sellable out of the box.
- `lang.yml` — `shop-unavailable`, `sell-item-sold`, `sell-not-sellable`, `sellall-result`,
  `sellall-empty`.
- `ProfitMultiplier.java` — wires up `EconomyManager`/`SellShopService`, registers
  `SellMenuListener`, registers the two new commands.
- `README.md`, `build.gradle.kts` (version → 1.3.0).

### Build & live-test results (this session)

- `./gradlew build` (`JAVA_HOME` = JDK 21) → **BUILD SUCCESSFUL** after fixing one import
  (`HumanEntity` is `org.bukkit.entity`, not `org.bukkit.inventory`).
- Deployed `ProfitMultiplier-1.3.0.jar` to the real Folia 26.2 test server. Reached
  `Done (29.730s)!` with **no exceptions** from ProfitMultiplier. Log confirmed:
  `Loaded 3 menu(s): [category-items, groups, sellmulti]`, and the expected graceful
  degradation warnings (`No supported shop plugin detected`, `No Vault economy found`) — this
  test server's "Vault" (actually `VaultUnlocked`) has **no** economy provider registered at
  all right now (confirmed system-wide: MMOItems, EnesOrder, and SmartSpawner all logged the
  identical "Vault found but no economy provider registered!" at the same boot), so this is a
  server-config gap, not a ProfitMultiplier bug — the graceful-fallback path is exactly what
  fired, as designed.
- Could not send a clean `stop` console command — the server was launched headlessly via a
  redirected-stdin PowerShell `Process` object whose parent exited after `Start()`, so there
  was no live handle left to write "stop" into by the time boot finished. Confirmed via
  `taskkill` (non-forceful) that Windows refuses to close a console-mode Java process that
  way (exit code 1, "can only be terminated forcefully"); used `Stop-Process -Force` instead.
  **Not a regression risk** — `onDisable()` here is trivial (just `dataManager.save()` +
  a log line, no Folia scheduler calls) — but if this project ever needs a real graceful
  shutdown test again, launch the jar with RCON temporarily enabled (as the previous Folia
  session did) instead of trying to pipe stdin through a detached process.
- **Not tested**: an actual connected client clicking through the new `/sellmulti` category
  grid, the `category-items` drill-down, or dragging items into the `/sell` chest GUI — no
  Vault economy was available on the test server to exercise `/sell`/`/sellall` end-to-end
  even if a client had been available. If a regression ever shows up specifically in these
  new menus/GUI, that's the manual check to do first, ideally on a server with a working
  Vault economy provider.

### Open items for later (not blockers)

- The test server's Vault economy gap (zEssentials creates its own "money"/"coins" economies
  and has a `vault.yml`/`vault-configuration.yml` module, but doesn't appear to actually
  register a Vault `Economy` service on this install) means `/sell`/`/sellall` have never
  been exercised against a live deposit. Worth fixing that server-side config (or installing
  a plain EssentialsX alongside for a known-good Vault provider) before the next real GUI
  click-test pass.
- `/sell`'s chest GUI only supports whole-stack left-click and single-item right-click (plus
  shift-click-from-inventory for a full stack). No partial-drag support (drag events over the
  GUI are cancelled outright) — intentional scope cut for dupe-safety, not a bug, but flag it
  if a future request wants finer-grained selling.

---

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
