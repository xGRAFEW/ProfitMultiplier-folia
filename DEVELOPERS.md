# ProfitMultiplier - Developer API

ProfitMultiplier ships a small, stable public API in the package
`me.docdrewskii.profitmultiplier.api`. Other plugins can read a player's
sell progression, react to milestones, and even adjust the boosted price
before it's applied.

The API package depends on **nothing but Bukkit/Spigot/Paper** - no Vault,
no shop plugin - so it's safe to compile against from any plugin.

---

## 1. The artifacts

`./gradlew build` produces four jars in `build/libs/`:

| File | What it is |
|------|------------|
| `ProfitMultiplier-1.2.0.jar` | the full plugin (goes in `/plugins`) |
| `ProfitMultiplier-1.2.0-sources.jar` | sources for the whole plugin |
| **`ProfitMultiplier-API-1.2.0.jar`** | **the slim API jar** (only the `api` package) |
| `ProfitMultiplier-API-1.2.0-sources.jar` | sources for the slim API jar |

The **API jar** is the "API file" you hand to other developers. It contains
only the interface, the provider, the events and `ResetCause` - never the
internal implementation.

---

## 2. Depending on the API

### Option A - JitPack (recommended)

Add the JitPack repository and the dependency:

**Gradle (Kotlin DSL)**
```kotlin
repositories {
    maven("https://jitpack.io")
}
dependencies {
    // JitPack groupId is com.github.<GitHubUser>, artifactId is the repo name,
    // version is the git tag (or commit hash).
    compileOnly("com.github.DevDocDrewskii:ProfitMultiplier:1.2.0")
}
```

**Gradle (Groovy DSL)**
```groovy
repositories { maven { url 'https://jitpack.io' } }
dependencies { compileOnly 'com.github.DevDocDrewskii:ProfitMultiplier:1.2.0' }
```

**Maven**
```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.DevDocDrewskii</groupId>
  <artifactId>ProfitMultiplier</artifactId>
  <version>1.2.0</version>
  <scope>provided</scope>
</dependency>
```

> Use `compileOnly` / `provided`: the API is supplied at runtime by the
> installed ProfitMultiplier plugin - don't shade it into your jar.

### Option B - Manual jar

Drop `ProfitMultiplier-API-1.2.0.jar` into a `libs/` folder in your project
and reference it:

```kotlin
dependencies {
    compileOnly(files("libs/ProfitMultiplier-API-1.2.0.jar"))
}
```

---

## 3. Tell the server about the dependency

In **your** plugin's `plugin.yml`, so it loads after ProfitMultiplier:

```yaml
softdepend: [ProfitMultiplier]   # or depend: [ProfitMultiplier] if you require it
```

---

## 4. Using the API

### Get the API instance
```java
import me.docdrewskii.profitmultiplier.api.ProfitMultiplierAPI;
import me.docdrewskii.profitmultiplier.api.ProfitMultiplierProvider;

if (ProfitMultiplierProvider.isAvailable()) {
    ProfitMultiplierAPI api = ProfitMultiplierProvider.get();

    long diamondsSold = api.getSold(player.getUniqueId(), Material.DIAMOND);
    double multiplier = api.getMultiplier(player.getUniqueId(), Material.DIAMOND);
    long remaining    = api.getRemainingToNextThreshold(player.getUniqueId(), Material.DIAMOND);
}
```
You can also grab it from Bukkit's services manager:
```java
ProfitMultiplierAPI api =
    Bukkit.getServicesManager().load(ProfitMultiplierAPI.class);
```

### React to / modify a boosted sale
`MultiplierApplyEvent` fires before the boosted price is committed. It's
**cancellable**, and you can override the boosted price:
```java
@EventHandler
public void onBoost(MultiplierApplyEvent event) {
    Player player = event.getPlayer();
    if (player.hasPermission("myplugin.doubleboost")) {
        // stack your own bonus on top
        event.setBoostedPrice(event.getBoostedPrice() * 2);
    }
    // event.setCancelled(true);  // veto the boost entirely
}
```
(Note: GUIShop commits the price at quote time, so its sales don't fire the
cancellable `MultiplierApplyEvent` - all other shops do.)

### React to milestones / resets
```java
@EventHandler
public void onMilestone(ThresholdReachedEvent event) {
    // event.getMaterial(), getNewTotal(), getNewMultiplier(), getThreshold()
}

@EventHandler
public void onReset(PlayerDataResetEvent event) {
    // event.getPlayerId(), event.getCause()  (COMMAND / AUTOMATIC / API / UNKNOWN)
}
```

---

## 5. API reference

**Tiers are revenue-based, not item-count-based.** A category (or a standalone item's own
ladder) levels up once enough BASE (pre-multiplier) money has been earned from selling it —
never from a raw item count, and never from a single item within a category on its own. Use
`getGroupRevenue`/`getGroupMultiplier` (or `getItemRevenue`/`getItemMultiplier` for an item
with no group) to query what actually drives pricing now.

**`ProfitMultiplierAPI`** (selected): `getSold`, `getTotalSold`, `getAllSold` (raw item counts —
still tracked, but no longer what drives a multiplier), `getGroupRevenue`,
`getGroupMultiplier`, `getItemRevenue`, `getItemMultiplier`, `getBonusTotal`, `getLastBonus`,
`addSold`, `setSold`, `addBonus`, `resetPlayer`, `resetAll`, `getLastReset`.

`getMultiplier`, `getMultiplierAt`, `getActiveThreshold`, `getNextThreshold`,
`getRemainingToNextThreshold`, and `calculateSaleValue` are still present but `@Deprecated` —
they're item-count based and no longer reflect real sale pricing; kept only so older code
compiles.

**Events** (`...api.events`): `MultiplierApplyEvent` (cancellable, settable
price), `ThresholdReachedEvent`, `PlayerDataResetEvent`, `ServerDataResetEvent`.

**`ProfitMultiplierProvider`**: `get()`, `isAvailable()`.

**PlaceholderAPI** (no API dependency needed): `%profitmultiplier_sold_<mat>%`,
`%profitmultiplier_multiplier_<mat>%`, `%profitmultiplier_group_multiplier_<group>%`,
and more - see `config.yml`.
