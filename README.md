# ProfitMultiplier

![Version](https://img.shields.io/badge/version-1.2.0-brightgreen)
![Minecraft](https://img.shields.io/badge/Minecraft-1.8%20%E2%86%92%2026.x-blue)
![Folia](https://img.shields.io/badge/Folia-supported-success)
![Java](https://img.shields.io/badge/Java-8%20bytecode%20(built%20with%2025)-orange)
[![JitPack](https://jitpack.io/v/DevDocDrewskii/ProfitMultiplier.svg)](https://jitpack.io/#DevDocDrewskii/ProfitMultiplier)

**Reward your grinders.** ProfitMultiplier gives players a *cumulative* sell-multiplier
that climbs as they sell - the more they sell, the more each sale is worth. It hooks into
your existing shop plugin, so there's nothing to migrate: prices just get boosted at sale
time based on each player's progression.

> Sell 10K crops → **1.1x**, 100K → **1.2x**, 1M → **1.3x**. Configure any ladder you like.

---

## ✨ Features

- **Cumulative progression** - multipliers rise as players cross sold-total thresholds and
  stay unlocked. The boost is applied *per unit*: a sale that crosses a threshold only
  boosts the units at/after it.
- **Three ladder types**
  - **Per-item** ladders (`DIAMOND`, `WHEAT`, …)
  - **Groups** - many materials sharing one counter (e.g. all *crops*)
  - A **default** ladder for everything else
- **Stacking control** - per group: group multiplier wins, item multiplier wins, or
  **both stack** (item `1.5x` × group `1.2x` = `1.8x`).
- **Multi-shop support** - EconomyShopGUI (+ Premium), ShopGUI+, zShop, UltimateShop, GUIShop,
  and EssentialsX `/sell`.
- **Custom currencies** - display formatting per group; the multiplier itself is
  currency-agnostic (works with whatever your shop pays in).
- **Fully configurable GUI** (`/sellmulti`) - filler items, custom items via
  **Nexo / Oraxen / ItemsAdder / HeadDatabase**, **PlaceholderAPI** support everywhere,
  **click actions**, **pagination**, and live-updating progress bars.
- **Milestone commands** - run any console command when a player unlocks a tier (crate keys,
  titles, broadcasts), globally or per tier.
- **Threshold scaling** - permission-based discounts so donor ranks level up faster
  (e.g. VIPs need 20% fewer lifetime sales).
- **Discord webhooks** - announce milestone achievements to your community, as a classic
  embed or a Components V2 message. Fully templated.
- **PlaceholderAPI** expansion for placards, scoreboards, holograms, etc.
- **Developer API** - read progression, react to milestones, modify the boost. See
  [DEVELOPERS.md](DEVELOPERS.md).
- **Wide compatibility** - single jar, Java 8 bytecode, runs on Spigot/Paper **1.8 → 26.x**,
  including **Folia** (regionized multithreading) — all scheduling is routed through the
  Paper/Folia-shared scheduler API, so nothing needs a single "main thread".
- **Auto-reset** - optionally wipe everyone's progression on a schedule (seasons/resets).

---

## 📦 Installation

1. Install a supported shop plugin (one of: **EconomyShopGUI**, **ShopGUI+**, **zShop**,
   **UltimateShop**, **GUIShop**, **EssentialsX** `/sell`) and its economy (e.g. Vault).
2. Drop `ProfitMultiplier-x.x.x.jar` into `/plugins`.
3. *(Optional)* Install **PlaceholderAPI** and any custom-item plugin you want to use in menus.
4. Restart the server. Edit `plugins/ProfitMultiplier/config.yml`, then `/pm reload`.

Everything is auto-detected - ProfitMultiplier hooks whatever supported shops it finds. The
startup log lists which hooks activated (`Sell hooks active: [...]`).

---

## 🚀 Quick start

Out of the box you get a working **crops** group (grass/wheat/kelp tiers at 10K/100K/1M) plus
example **mob_drops** and **ores** groups. Open the menu with:

```
/sellmulti
```

Then customise `config.yml` and `menus/sellmulti.yml` to taste.

---

## 🧱 Configuration overview

`config.yml`:

| Section | Purpose |
|---------|---------|
| `hooks` | Enable/disable each shop integration |
| `currency` | Display symbols/formats; define named currencies (`gems`, `tokens`, …) |
| `items` | Per-material multiplier ladders |
| `groups` | Shared ladders across many materials (with `stack-mode`, `currency`, `icon`, `display-name`) |
| `default` | Fallback ladder for unlisted items |
| `auto-reset` | Periodic progression wipe |
| `debug` | Verbose logging |

**Example group:**
```yaml
groups:
  crops:
    icon: GRASS_BLOCK
    display-name: "Crops"
    stack-mode: group        # group | item | stack
    materials: [WHEAT, CARROT, POTATO, KELP, SUGAR_CANE, ...]
    tiers:
      - { threshold: 10000,   multiplier: 1.1, icon: GRASS_BLOCK }
      - { threshold: 100000,  multiplier: 1.2, icon: WHEAT }
      - { threshold: 1000000, multiplier: 1.3, icon: KELP }
```

Player-facing messages live in `lang.yml` (each individually toggleable). Menus live in the
`menus/` folder - drop in more `.yml` files to create more menus.

---

## 🖱️ The GUI

`menus/sellmulti.yml` is fully yours to edit. Highlights:

- **Icons**: `WHEAT`, `nexo:id`, `oraxen:id`, `itemsadder:ns:id`, `hdb:1234`,
  `player:Name`, `basehead:<texture>`
- **Per-viewer live tokens** (work even without PlaceholderAPI): `{sold}`,
  `{multiplier}`, `{threshold}`, `{remaining}`, `{progress_bar}`, `{progress_percent}`,
  `{status}`, …
- **Click actions** (`left`, `right`, `shift_left`, `middle`, `any`, …):
  `[message]`, `[broadcast]`, `[console]`, `[player]`, `[open]`, `[close]`, `[sound]`,
  `[refresh]`, `[next-page]`, `[previous-page]`, `[page] <n>`
- **Pagination**: a `content-source` (e.g. `groups` or `tiers:crops`) auto-fills
  `content-slots` across pages with `[next-page]`/`[previous-page]` navigation.

`/pm gui groups` opens a paginated, auto-generated browser of every group you've defined.

---

## ⌨️ Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/sellmulti` | Open the sell-multiplier menu | `profitmultiplier.gui` |
| `/pm gui [menu] [player]` | Open a menu (for yourself, or another player) | `profitmultiplier.gui` (`.admin` for others) |
| `/pm stats [player]` | View sell progression | `profitmultiplier.stats` |
| `/pm reload` | Reload config, lang, currencies & menus | `profitmultiplier.admin` |
| `/pm reset <player>` | Reset one player's totals | `profitmultiplier.admin` |
| `/pm resetall` | Reset every player's totals | `profitmultiplier.admin` |

Aliases: `/pm`, `/sellmulti`.

## 🔐 Permissions

| Permission | Default | Grants |
|------------|---------|--------|
| `profitmultiplier.gui` | everyone | Open the menu |
| `profitmultiplier.stats` | everyone | View own stats |
| `profitmultiplier.admin` | op | Reload, reset, open menus for others |

---

## 🔣 PlaceholderAPI

A few of the available placeholders (full list in `config.yml`):

```
%profitmultiplier_sold_total%
%profitmultiplier_bonus_total%
%profitmultiplier_sold_<MATERIAL>%
%profitmultiplier_multiplier_<MATERIAL>%
%profitmultiplier_remaining_<MATERIAL>%
%profitmultiplier_group_sold_<group>%
%profitmultiplier_group_multiplier_<group>%
%profitmultiplier_group_next_threshold_<group>%
%profitmultiplier_group_progress_<group>%
```

---

## 🛒 Supported shops

| Shop | Notes |
|------|-------|
| **EconomyShopGUI** (+ Premium) | Native `PreTransactionEvent` |
| **ShopGUI+** | `ShopPreTransactionEvent` |
| **zShop** | `ZShopSellEvent` / `ZShopSellAllEvent` |
| **UltimateShop** | `ItemPreTransactionEvent` (scales the reward) |
| **GUIShop** | `DynamicPriceProvider`; reliable for sales **through the shop GUI** |
| **EssentialsX** | `/sell` (`hand`/`inventory`/`blocks`/item); bonus paid on top of the worth payout |

Toggle any of them in `config.yml → hooks`.

---

## 🧑‍💻 Developer API

ProfitMultiplier exposes a small, stable API (`me.docdrewskii.profitmultiplier.api`) for
reading progression, reacting to milestones, and adjusting the boost. Add it via JitPack:

```kotlin
repositories { maven("https://jitpack.io") }
dependencies { compileOnly("com.github.DevDocDrewskii:ProfitMultiplier:1.1.0") }
```

Full guide, examples, and publishing instructions: **[DEVELOPERS.md](DEVELOPERS.md)**.

---

## 🔨 Building

```bash
./gradlew build
```

Outputs to `build/libs/`:

- `ProfitMultiplier-1.2.0.jar` - the plugin
- `ProfitMultiplier-API-1.2.0.jar` - the slim developer API jar (+ `-sources`)

Built with the JDK 25 toolchain but emits **Java 8 bytecode** for maximum server compatibility.

---

## 🆘 Support

- **Repository:** <https://github.com/DevDocDrewskii/ProfitMultiplier>
- **Issues / bug reports:** <https://github.com/DevDocDrewskii/ProfitMultiplier/issues>

## 📄 License & credits

Author: **DocDrewskii**.

Custom-item and shop integrations are performed reflectively - none of those plugins are
required to build or run ProfitMultiplier.
