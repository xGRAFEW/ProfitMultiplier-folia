# HANDOFF

Living progress log. Update this as you go; keep `CLAUDE.md` for durable facts and this file
for "what's done / what's next." Newest session at the top.

---

## Session: GUI price-change indicator + /sellall permission tightened (v1.7.0)

Two small user-requested changes, bundled into one release.

**1. Show market movement in the category-items GUI.** User wanted the per-item price shown
in the shop GUI to also say how far up/down it currently is versus a "standard" (anchor)
price — relevant now that price rotation (v1.4.0) can swing an item's live price by a
configured percentage on a schedule. The anchor was already sitting right there:
`ConfigManager.getPrice(material)` is the plain configured base price rotation swings around
(`PriceRotationManager.getCurrentPrice` reads it as the center of its ± swing), so no new
config or state was needed — just a comparison of two already-existing values.

- `NumberUtil`: added `percentChange(standard, current)`, `signedPercent(double)` (e.g.
  "+12.5%"/"-8%"/"0%"), and `priceChangeIndicator(double)` (color+arrow, e.g. "&a▲ +12.5%" /
  "&c▼ -8%" / "&7■ 0%" when within 0.05 of unchanged).
- `MenuManager.computeItemTokens`: now also resolves the base price and exposes
  `{base_price}`, `{price_change_percent}`, `{price_change_raw}`, `{price_change}` alongside
  the existing `{price}` token — available anywhere an `items:@selected`/`group-items`
  content-source is used (currently only `category-items.yml`).
- `menus/category-items.yml`: lore now shows the standard price and the market indicator
  under the live price; the click chat message includes the same info. Applied to both the
  repo's bundled default and the already-deployed copy on the real Folia test server (menu
  YAMLs aren't auto-merged — same reason prior sessions had to hand-edit both copies).
- This is purely a display feature — `SellShopService`/`SellProcessor` (the real charge path)
  were not touched, so what a player actually gets paid is unaffected.

**2. `/sellall` permission tightened to opt-in.** Was `profitmultiplier.sellall: default: true`
(everyone could use it) since it was added in v1.3.0. User asked that only players explicitly
granted the permission be able to use it. Changed `default` to `op` in `plugin.yml` — matches
the convention already used by `profitmultiplier.admin` in this same file for "restricted,
must be granted via a permission plugin or op" — server owners aren't locked out, but regular
players now need an explicit grant (e.g. via LuckPerms) instead of having it by default.
`/sell` (`profitmultiplier.sell`) was deliberately left at `default: true` — user only asked
about `/sellall`. No code change needed beyond the manifest — `SellCommand` already calls
`player.hasPermission("profitmultiplier.sellall")` before allowing the command.

Bumped to v1.7.0. Build succeeded clean (`JAVA_HOME` = JDK 21). Deployed
`ProfitMultiplier-1.7.0.jar` to the real Folia 26.2 test server (replacing the stale 1.6.0 jar
that had been sitting there since the last deploy) and did a full start/stop cycle via a
temporarily-enabled RCON connection (password set, then reverted — `server.properties` is
back to its original `enable-rcon=false` / blank password state afterward, same pattern as
past sessions). Log confirmed `Enabling ProfitMultiplier v1.7.0` → `Loaded 3 menu(s):
[category-items, groups, sellmulti]` → `Hooked into a Vault economy` → `ProfitMultiplier
v1.7.0 enabled.`, no exceptions; `/pm reload` over RCON round-tripped correctly; clean `stop`
via RCON produced a normal `RegionShutdownThread`/`MoonriseCommon` shutdown sequence.

**Note on this environment's process launching**: `Start-Process ... -RedirectStandardOutput`
(detached, backgrounded) silently died within ~10s of starting the JVM this session (only
`Starting org.bukkit.craftbukkit.Main` ever reached stdout, nothing in stderr, `logs/latest.log`
was never touched — stayed on its stale Sep-7 copy) — cause not fully diagnosed, but launching
the same `java -jar canvas.jar` command line directly as the PowerShell tool's own tracked
background command (not detached via `Start-Process`) worked reliably and stayed up for the
full test. If a future session hits an inexplicable silent-death-after-`Starting
org.bukkit.craftbukkit.Main` with a stale `logs/latest.log` that never updates, that's the
known workaround. Wrote a minimal Source-RCON client in PowerShell (raw TCP, no external
tool) for this session since neither `gh`/gradle-adjacent tooling nor Python are available
here for that; not saved into the repo (scratch-only), so a future session will need to
re-write it (it's ~40 lines — see this entry for the packet framing if useful:
length-prefixed `int32 LE` id/type/payload/`\0\0`, auth type `3`, exec type `2`).

**Not touched**: wiki pages (still describe pre-1.4.0 pricing in places, per earlier
sessions' notes — unchanged again this round).

## Session: built-in shop was selling MMOItems custom items (v1.6.1)

User report on the live Purpur 26.2 test server (`Survival SMP Purpur 26.2 test`, a *different*
box than the Folia one named in `CLAUDE.md` — MMOItems, GUIShop, CMI, Vault all installed there):
`/sellall` was sweeping up and selling MMOItems custom gear crafted via `/mi` alongside real
vanilla items.

**Root cause**: the whole built-in shop (`SellShopService`, used by both `/sell`'s chest GUI and
`/sellall`) matched sellable items purely by vanilla `Material` type — never checked whether a
stack was actually a *plain* vanilla item. MMOItems (and ItemsAdder/Oraxen) items reuse vanilla
materials under custom model data (e.g. a custom MMOItems sword is still `Material.DIAMOND_SWORD`
+ a model data id) — a well-known reuse pattern that `InventoryPriceLoreManager` (the inventory
price-lore feature from v1.5.0) already had to guard against for the exact same reason, but that
guard was never applied to the actual sell path. If the reused material happened to have a
configured price, custom items got swept in.

A second, worse bug was hiding in the same code path: `SellShopService.sellAll()`'s removal step
(`removeExact`) removed stacks purely by material match too, with no tie to which specific stack
had actually been priced/credited — so it could delete a player's custom item from a *different*
inventory slot than the one that was actually sold, not just sell it at the wrong (or missing)
price.

**Fix**: added `SellShopService.isSellable(ItemStack)` — same material-has-a-price check as
before, plus `!meta.hasCustomModelData()`, mirroring `InventoryPriceLoreManager`'s existing rule.
`sellAll()` was rewritten to record the exact inventory *slot indices* that passed this check per
material (not just a running count) and only null out those specific slots after a successful
sale — no more sweep-by-material-type removal. `SellMenuListener`'s two "is this item allowed
into the sell GUI" checks (`onClick`'s `denyIfUnsellable`, `onDrag`) were switched from
`isSellable(Material)` to `isSellable(ItemStack)` so a custom item is rejected with the normal
`sell-not-sellable` message on the way in, instead of ever reaching the GUI's close-time sell
pass at all — `onClose()` itself didn't need to change since only genuine plain-vanilla stacks can
reach it now.

Deliberately did **not** make this a config toggle — it's a correctness fix (a custom-modeled
item should never be auto-sold as if it were the plain vanilla material it happens to share),
not a new feature, and the codebase already treats "plain vanilla only" as the settled rule for
this exact class of item via `InventoryPriceLoreManager`.

Bumped to v1.6.1 (`build.gradle.kts`) for changelog traceability — real sell-logic bug fix, not
cosmetic. Build succeeded clean. Deployed to the **Purpur** test server (not the Folia one) and
did a real restart cycle; enable log showed the usual clean `Hooked into a Vault economy — /sell
and /sellall are enabled.` line, no exceptions. Could not do an in-game verification pass myself
(no MC client available to this session) — flagged to the user to re-test `/sellall` with an
MMOItems item + a vanilla item of the same underlying material in inventory and confirm only the
vanilla one gets sold.

Also diagnosed (separately, config-side, not a code bug) that `/sell` alone was being hijacked by
CMI's own `Alias.yml` (`sell: Enabled: true`, CMI enables after ProfitMultiplier in the plugin
load order on this server) — a known CMI behavior, not something this plugin can fix from its own
code. Recommended disabling that one CMI alias entry; left as a decision for the user, not applied
by this session.

## Session: per-category progress GUI + built-in shop (v1.3.0 → v1.6.0)

### Tiers switched from item-count to cumulative-revenue based (v1.6.0)

Big one — user wanted tier thresholds to represent money earned from selling, not a raw item
count (e.g. "earn $10,000 from crops" instead of "sell 10,000 crops"). Confirmed with the user
this is a real breaking behavior change before starting.

**Key design call made without a separate question** (flagged here for visibility): the
revenue counted toward a threshold is the sale's **BASE (pre-multiplier) price**, not the
boosted amount actually paid. This was a deliberate choice over the alternative (counting the
boosted/paid amount) because it avoids a compounding "rich get richer" feedback loop and keeps
progress pacing predictable regardless of a player's current multiplier — closer in spirit to
how the old item-count system felt (multiplier-agnostic progress). A nice side effect: because
base revenue accrues at an exactly constant rate per unit (`basePerUnit`, unaffected by which
tier is active), the revenue ledger itself needs no iterative stepping at all —
`pdm.addGroupRevenue(uuid, group.getName(), originalPrice)` is a single addition after the
sale. Only the *price actually charged* needs segment-stepping (since the multiplier can change
mid-sale as cumulative revenue crosses a threshold partway through the stack sold) — same
structural pattern as the old count-based math, just measuring dollars instead of items.

**Data (`PlayerDataManager`)**: added `groupRevenue: Map<UUID, Map<String, Double>>` and
`itemRevenue: Map<UUID, Map<Material, Double>>`, persisted under new `data.yml` sections
`players.<uuid>.group-revenue.<name>` / `.item-revenue.<material>`. Deliberately did **not**
touch the existing `sold` (item count) map or its persistence — that keeps working exactly as
before for `/pm stats` and anything else that wants a raw "how many has this player sold"
number; it's just no longer what drives a multiplier. Both new maps are cleared by
`resetPlayer`/`resetAll` alongside the existing ones.

**Config (`ConfigManager`)**: added a parallel revenue-based method family —
`revenueMultiplierAt`, `revenueActiveThreshold`, `revenueNextThresholdAbove` (all operate on a
plain `List<MultiplierTier>`, so the same code serves an item's own ladder or a group's ladder),
`computeTieredRevenueSaleValue` (single ladder) and `computeUnifiedRevenueSaleValue`
(`GroupStackMode`-aware, combines an item ladder + group ladder each against their own
cumulative revenue, for anyone still using `ITEM`/`STACK` mode). **Left the old count-based
methods (`multiplierAtCount`, `computeSaleValue`, `computeUnifiedSaleValue`,
`groupMultiplierAtCount`, etc.) completely untouched** — nothing in the real sell path calls
them anymore, but removing them would've broken `ProfitMultiplierAPI`'s existing (now
`@Deprecated`) count-based methods, which are still there for compile-compat for any external
consumer. `MultiplierTier.threshold` stays an `int` field — same YAML key, same number, just
now interpreted as a currency amount instead of a quantity (documented with a prominent comment
at the top of `config.yml`).

**`SellProcessor`**: `computeBoostedPrice` now fetches `pdm.getGroupRevenue`/`getItemRevenue`
instead of item counts, and calls the new `computeUnifiedRevenueSaleValue`/
`computeTieredRevenueSaleValue`. `recordSale` adds the sale's **base** price to the revenue
ledger (see design call above) in addition to the existing `addSold` item-count increment.
`announceThresholds` rewritten to diff old-vs-new revenue instead of old-vs-new count for
milestone/threshold-crossing detection, and now formats `{total}`/`{threshold}` in
`threshold-reached`/`group-threshold-reached` as currency (via the group's own `Currency` if it
has one) instead of a bare number — reworded those two default `lang.yml` messages too since
"you've sold $10,000x Wheat" doesn't parse; now "you've earned $10,000 from Wheat".

**Downstream consumers updated to match**: `MilestoneManager.handleCrossings` now takes
`double prevRevenue, double newRevenue` (was `long`) and formats `{threshold}`/`{total}`
placeholders as currency. `MenuManager.computeGroupTokens` (`/sellmulti` grid,
`category-items`, `groups` menu tokens) now reads `getGroupRevenue` and formats `sold`/
`threshold`/`remaining` through `Currency`/`NumberUtil`'s new double overloads — added
`Currency.formatAbbreviated(double)` (currency-symbol-aware K/M/B abbreviation) and double
overloads of `NumberUtil.percent`/`progressBar`/`abbreviate` (kept the `long` overloads too,
delegating to the double ones). `ProfitPlaceholders`' `multiplier_*`/`next_threshold_*`/
`remaining_*`/`group_*` placeholders rewritten to resolve through revenue instead of count.
`ProfitCommand`'s `/pm stats` now looks up each material's owning group (or its own ladder) and
shows the real revenue-based multiplier instead of the old (now wrong) count-based one — the
raw sold-count column is unchanged.

**Public API (`ProfitMultiplierAPI`)**: added `getGroupRevenue`/`getGroupMultiplier`/
`getItemRevenue`/`getItemMultiplier`. Marked `getMultiplier`, `getMultiplierAt`,
`getActiveThreshold`, `getNextThreshold`, `getRemainingToNextThreshold`, and
`calculateSaleValue` `@Deprecated` (still implemented, still compiles, just no longer reflects
real pricing) rather than removing them — this is a published API jar
(`ProfitMultiplier-API-*.jar`) per `DEVELOPERS.md`, so breaking the interface outright felt
like the wrong tradeoff versus a clearly-marked deprecation. `ThresholdReachedEvent`'s
`newTotal`/`threshold` fields changed from `long` to `double` (this one **is** a breaking
signature change — accepted because the event fundamentally represents a currency amount now,
and there's no way to keep it `long`-typed without silently truncating).

**Not touched / known follow-up**: wiki pages (`wiki/*.md`) still describe the old item-count
system in places — not updated this round, flagged for whoever touches docs next.
`config.yml`'s existing demo tier threshold *numbers* (10000/100000/1000000 for crops,
5000/50000/250000 for ores, etc.) were left exactly as-is — they now mean dollars instead of
items, which changes how fast they're reached given the per-item prices already configured
(e.g. ores' $5,000 first tier only needs ~42 diamonds at $120 each — reachable much faster than
"sell 5,000 ore items" was). Didn't rebalance the numbers since the user didn't ask for that,
only for the mechanism change — flagged clearly in the wrap-up message so they can retune
pacing if it feels too easy/hard.

**Verification**: build succeeded first try after all the type changes (long→double) propagated
correctly. No unit test infra in this project (`compileTestJava NO-SOURCE`, consistent with the
rest of the codebase), so correctness was verified by hand-tracing the segment-stepping math
for a sale that crosses a threshold partway through (confirmed: units before the crossing point
correctly get the old multiplier, units at/after get the new one, and the revenue ledger update
in `recordSale` exactly matches the ending revenue value the pricing loop itself computed —
important since those are two separate code paths that must agree). Deployed to the real Folia
test server, clean enable, no exceptions. Left the server running afterward so the user can
exercise `/sell` (Vault is hooked there) if they want to see it live.

## Session: per-category progress GUI + built-in shop (v1.3.0 → v1.5.2)

### Simplified the demo config.yml — no code changes needed (v1.5.2)

User feedback: setting an item's price required typing it in two places, and they didn't want
per-item tier ladders at all — only ever want a category/group as a whole to level up, never a
single item within it. Turned out **the engine already does exactly this** — `stack-mode:
group` (the default when the key is omitted) already means only the group's own `tiers:`
counts, and `ConfigManager.getPrice()` already prefers a group's own `prices:` entry over a
standalone `items:<mat>:price`. The actual problem was purely in the *demo* `config.yml` I'd
written earlier: I'd given `DIAMOND`/`GOLD_INGOT`/`IRON_INGOT` both a standalone `items:` entry
(price + its own per-item tier ladder) AND a `groups.ores.prices` entry, and set
`ores.stack-mode: stack` specifically to *also* demonstrate the per-item-ladder-stacks-with-
group-ladder feature — which is exactly the "typed twice, and a single item can level up on its
own" behavior the user doesn't want.

Fixed by editing config content only: removed the redundant `items: DIAMOND/GOLD_INGOT/
IRON_INGOT` blocks entirely (now `items: {}`, with a comment explaining it's only for a
material that isn't in any group), and changed `ores.stack-mode` from `stack` to `group`.
Applied to both the repo's bundled default and the test server's already-deployed config.yml
(had to fix that one by hand again — menu YAMLs auto-merge for genuinely *missing* keys, but
values that already exist on a deployed config, like `ores.stack-mode`, are never overwritten
by the merge, so a live server's own copy needs the same manual edit before it takes effect).
**Test server needs a `/pm reload` (or restart) to actually pick this up** — didn't restart it
myself since the user had it running live for their own testing at the time.

No version-worthy code change here, but bumped anyway for changelog traceability since it's a
real behavior-affecting default-config fix that future fresh installs benefit from.

### Fixed: raw hex color codes leaking into displayed prices (v1.5.1)

User screenshotted a category-items tooltip showing literally `#2bd66f2$` instead of a colored
price. Root cause: `EconomyManager.format(double)` delegated to the Vault economy's own
`Economy#format(double)` when available (falling back to our own `Currency` formatter only if
no economy was hooked) — but `zEssentials` (the economy on the test server) returns its price
text using its own `price-reductions` config, which is MiniMessage/hex-tag styled (its
`display: "#2bd66f%amount%"` config produces exactly the literal text seen — the hex tag isn't
a legacy `&`-code, so `ChatColor.translateAlternateColorCodes` never touches it and it just
prints as raw text once dropped into item lore). Different economy plugins can return
arbitrarily different markup here, none of it guaranteed legacy-safe. Fixed by having
`EconomyManager.format()` always use our own `CurrencyManager` formatter instead — it's fully
under our control and known-safe, and this only affects *display* strings; the actual
`depositPlayer` transaction is untouched and still goes through Vault/zEssentials correctly.

### Inventory price lore + /pm pricelore toggle (v1.5.0)

User clarified a prior request (I'd misread "โชว์ราคาไอเทมในตัว" as "show price in the
category-items menu" and styled that instead) — they actually meant hovering an item **in the
player's own inventory** (anywhere, not just our GUIs) should show its sell price, confirmed
with two screenshots of a vanilla item tooltip with a price line under the name.

Bukkit has no server-side "hover" event — tooltips are rendered entirely client-side from
whatever lore is currently on the ItemStack. So the only way to do this is to actually write a
lore line onto the real item and keep it in sync. New `shop/InventoryPriceLoreManager.java`:
- Scoped to **plain vanilla items only** (`!meta.hasCustomModelData()`) per explicit user
  choice — MMOItems/ItemsAdder/Oraxen (all three installed on the test server) manage their own
  item lore and regenerate it on their own triggers; touching a custom-model-data item here
  would fight them for control of the lore list and could easily tag the wrong "item" since
  custom items typically reuse a plain vanilla `Material` (e.g. every MMOItems sword might be
  `DIAMOND_SWORD` + a model-data id).
- Each managed item is tagged with a `PersistentDataContainer` marker
  (`profitmultiplier:sell_price_lore`) so a later pass knows that single lore line is ours to
  replace or clear, and it never touches lore it didn't add itself (if an eligible item already
  has non-empty lore we don't own, it's left alone).
- Refreshes a player's `getStorageContents()` (hotbar + main inv) on: `PlayerJoinEvent`,
  `EntityPickupItemEvent`, `InventoryClickEvent` (MONITOR, any inventory — covers crafting,
  moving items around, etc.), and once for every online player right after each
  `PriceRotationManager` reroll (`plugin.getInventoryPriceLoreManager().refreshAllOnline()`
  called from `PriceRotationManager.tick()`).
- `refresh(player)` is safe to call unconditionally regardless of the enabled flag — when
  disabled, price resolves to `null` for every item, which makes the same code path strip any
  previously-added tag/lore instead of adding anything. This means toggling off actually cleans
  up immediately rather than leaving stale price lines stuck on items until they're sold.
- **Fully independent from `/sellmulti`'s `{price}` and from price-rotation** — confirmed with
  the user this must stay true. `InventoryPriceLoreManager` only *reads*
  `PriceRotationManager.getCurrentPrice(...)`; nothing reads `inventory-price-lore.enabled`
  anywhere else in the codebase, so toggling this can never affect what any menu shows.
- New command `/pm pricelore [on|off]` (defaults to toggling current state if no arg,
  `profitmultiplier.admin` required) — persists to `config.yml` and immediately calls
  `refreshAllOnline()` either way, so the effect (or cleanup) is instant instead of waiting for
  a reload/restart. New config `inventory-price-lore: enabled: false` (off by default).

Also restyled `menus/category-items.yml`'s item template per an earlier (correctly-understood
this time) ask — a minimal look: plain white item name, single bold-yellow price line, no
"Price:" label prefix and no sold-count line. Applied directly to both the repo's bundled
default and the test server's already-deployed copy of the file (menu YAMLs, unlike
`config.yml`, are never auto-merged — see below — so the earlier v1.3.1 filler-removal change
had never actually reached the test server's deployed `sellmulti.yml`/`groups.yml`/
`category-items.yml` either; fixed those three deployed files by hand while in there).

Rebuilt, redeployed, clean enable/disable cycle on the real Folia test server, no exceptions;
confirmed the config-merge fix from earlier in this session is still working (`inventory-price-
lore:` section appeared in the deployed config.yml on first boot).

### Price rotation + per-item /sellall messages + config-merge bug fix (v1.4.0)

Two more follow-up requests after v1.3.1 shipped:

1. **Random/rotating prices.** New `economy/PriceRotationManager.java`: every material with a
   configured base price (`ConfigManager.getPricedMaterials()`, new method) gets rerolled
   independently within ±`variance-percent` of its base price, on a schedule
   (`config.yml` → `price-rotation:`). `schedule` accepts either `"HH:mm"` (daily, at that
   server-local time) or a duration like `"30m"`/`"6h"`/`"1d"` (repeating interval from the
   last reroll) — parsed by one regex-based method (`computeNext`) so admins get both
   modes from a single field, as requested. State (`next-rotation` epoch millis + the current
   rolled price per material) persists to `plugins/ProfitMultiplier/prices.yml` so a restart
   doesn't reset the countdown or silently revert prices to base. A `FoliaScheduler
   .runGlobalTimer` tick every 10s checks whether the next rotation is due — cheap, and more
   than precise enough since schedules are in minutes/hours/days.
   - `SellShopService.sellDetached` and `MenuManager.computeItemTokens`'s `{price}` token both
     now read `PriceRotationManager.getCurrentPrice(material)` instead of
     `ConfigManager.getPrice(material)` directly, so the rotated price is what's actually
     charged AND what's displayed — `ConfigManager.getPrice` stays the "base" price
     (unaffected by rotation, used as the anchor rotation swings around).
   - Countdown exposed as menu tokens `{price_reset_countdown}` (e.g. "1d 4h 12m", or "N/A"
     when the feature is off) and `{price_rotation_enabled}`, injected into the universal
     `pageTokens` map in `MenuManager.renderContents` so any menu item on any page can use
     them. Added a CLOCK info item to `sellmulti.yml` (slot 4) showing it by default — off by
     default (`price-rotation.enabled: false`) so this is a no-op on upgrade until an admin
     opts in.
   - `/pm reload` now also calls `PriceRotationManager.load()` (re-reads schedule/variance;
     does not force an immediate reroll — only the periodic tick does that).
2. **`/sellall` now sends one line per distinct item type sold** (e.g. "Sold 32x Wheat for
   $96." then a separate "Sold 12x Carrot for $24." line), reusing the same `sell-item-sold`
   lang key the `/sell` GUI already used, instead of only the one combined total line. Kept
   the combined `sellall-result` summary too, sent after the per-item lines — didn't seem
   like the user wanted the total removed, and it's a one-line toggle in `lang.yml` if they
   don't want it. Required widening `SellAllResult` from three plain totals into a proper
   `List<Entry(material, amount, credited)>` (`SellShopService.sellAll` now calls
   `result.add(...)` per material instead of accumulating counters itself).

Rebuilt, redeployed to the real Folia test server, clean enable/disable cycle, no exceptions.

### Bonus find while testing this: `config.yml` auto-merge was silently broken (fixed)

While verifying price-rotation would actually appear on the live test server after a restart,
noticed the deployed `config.yml` never picked up `shop:` (added in v1.3.0) either, despite
`ConfigManager.mergeMissingDefaults()` supposedly existing to do exactly that, and despite it
never once logging its "Updated config.yml with new default keys." line across every boot this
whole session (v1.2.0 → v1.4.0) — confirmed by grepping every archived log. Root cause: that
method compared `defaults.getKeys(true)` against `plugin.getConfig().contains(key)`, but
`JavaPlugin#reloadConfig()` (called immediately before it) **already** attaches the same
jar-bundled `config.yml` as a *defaults layer* on `plugin.getConfig()` — standard Bukkit
behavior — which makes `contains()` return `true` for every jar-bundled key regardless of
what's actually written to disk. So the "is anything missing" check could never fire; this bug
predates this session entirely (it's not something introduced by the recent changes) and would
have silently affected every prior config addition (`groups:` prices, `default:` blacklist
entries, etc. added to any existing server's `config.yml` after its first install). Fixed by
loading the on-disk file directly with a separate throwaway `YamlConfiguration` (no defaults
attached) and checking `.isSet(key)` against *that* instead — `LangManager`'s equivalent
`lang.yml` merge already did the right thing (it manages its own `YamlConfiguration` rather
than going through `plugin.getConfig()`), which is why lang defaults always merged fine while
config ones silently never did. Verified fixed live: fresh boot now logs "Updated config.yml
with new default keys." and the deployed file gets every missing key, including nested ones
several levels deep (`groups.crops.prices.WHEAT`, etc.).

Also live-verified the actual rotation mechanics end-to-end on the test server (temporarily set
`schedule: 20s` on the deployed config, not the repo's default): `prices.yml` got created, the
tick fired within one 10s check cycle, every priced material rerolled independently within
±20%, and `next-rotation` advanced correctly. Reverted the test server's config back to
`enabled: false` / `schedule: 6h` and deleted the test `prices.yml` afterward so the server is
left in a clean, unmodified-by-testing state.

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
