package me.docdrewskii.profitmultiplier.shop;

import me.docdrewskii.profitmultiplier.ProfitMultiplier;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Optional, off-by-default feature: shows the current sell price as a lore line when a player
 * hovers a sellable item in their OWN inventory (not just in our shop menus). Deliberately
 * scoped to plain vanilla items with no custom model data — a custom item from MMOItems /
 * ItemsAdder / Oraxen usually shares a vanilla Material with unrelated items (e.g. every
 * MMOItems sword might be DIAMOND_SWORD + a model data id), and those plugins manage their own
 * lore, so touching their items here would fight with them for control of the lore list and
 * could tag the wrong thing entirely. Every item this manager touches is tagged with a
 * PersistentDataContainer marker so a later refresh knows the single lore line is ours to
 * replace/clear, and it never overwrites lore it didn't add itself.
 */
public class InventoryPriceLoreManager implements Listener {

    private final ProfitMultiplier plugin;
    private final NamespacedKey priceTag;

    /**
     * Players currently viewing a custom GUI belonging to another plugin (see
     * {@link #isOwnView}) — their own inventory, our own menus, and genuine vanilla containers
     * are all exempt. Our price lore is real item-meta data, not a client-only overlay, so if we
     * don't strip it while a foreign plugin's menu is open, that menu can end up displaying our
     * line too whenever it renders/clones an item straight out of the player's inventory
     * (several "quick sell" style shops do exactly that).
     */
    private final Set<UUID> suppressed = new HashSet<>();

    public InventoryPriceLoreManager(ProfitMultiplier plugin) {
        this.plugin = plugin;
        this.priceTag = new NamespacedKey(plugin, "sell_price_lore");
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("inventory-price-lore.enabled", false);
    }

    /** Persists the toggle to config.yml and immediately applies/clears lore for everyone online. */
    public void setEnabled(boolean enabled) {
        plugin.getConfig().set("inventory-price-lore.enabled", enabled);
        plugin.saveConfig();
        refreshAllOnline();
    }

    /**
     * Re-applies (or clears) the price lore line across a player's main inventory + hotbar.
     * Safe to call regardless of {@link #isEnabled()} — when the feature is off, this only ever
     * strips lore this manager previously added, it never adds anything new.
     */
    public void refresh(Player player) {
        boolean enabled = isEnabled() && !suppressed.contains(player.getUniqueId());
        ItemStack[] contents = player.getInventory().getStorageContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack updated = applyOrClear(contents[i], enabled);
            if (updated != null) {
                contents[i] = updated;
                changed = true;
            }
        }
        if (changed) {
            player.getInventory().setStorageContents(contents);
        }
    }

    /** Refreshes every online player — used right after a /pm pricelore toggle and after each price reroll. */
    public void refreshAllOnline() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            refresh(player);
        }
    }

    /** @return the mutated stack if it changed, or {@code null} if nothing needed to change. */
    private ItemStack applyOrClear(ItemStack stack, boolean featureEnabled) {
        if (stack == null || stack.getType() == Material.AIR) return null;

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;

        boolean ours = meta.getPersistentDataContainer().has(priceTag, PersistentDataType.BYTE);
        boolean eligible = featureEnabled && !meta.hasCustomModelData();

        Double price = eligible ? plugin.getPriceRotationManager().getCurrentPrice(stack.getType()) : null;

        if (price == null) {
            if (!ours) return null;
            meta.setLore(null);
            meta.getPersistentDataContainer().remove(priceTag);
            stack.setItemMeta(meta);
            return stack;
        }

        List<String> currentLore = meta.getLore();
        if (!ours && currentLore != null && !currentLore.isEmpty()) {
            return null; // don't clobber lore we don't own
        }

        String line = ChatColor.translateAlternateColorCodes('&',
                "&e" + plugin.getEconomyManager().format(price));
        if (ours && currentLore != null && currentLore.size() == 1 && line.equals(currentLore.get(0))) {
            return null; // already up to date
        }

        meta.setLore(Collections.singletonList(line));
        meta.getPersistentDataContainer().set(priceTag, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player) {
            refresh((Player) entity);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            refresh((Player) event.getWhoClicked());
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (isOwnView(player, event.getInventory().getHolder())) return;
        suppressed.add(player.getUniqueId());
        refresh(player);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (isOwnView(player, event.getInventory().getHolder())) return;
        suppressed.remove(player.getUniqueId());
        refresh(player);
    }

    /**
     * True for the player's own inventory screen, our own menus, and genuine vanilla containers
     * (chests, furnaces, shulker boxes, horses, minecarts, ...) — none of those ever substitute
     * a different rendering for the item, they just show it as-is, which is exactly what the
     * lore is for. Only a *custom* GUI (another plugin's menu) risks re-rendering the item
     * somewhere the price line doesn't belong.
     */
    private boolean isOwnView(Player player, InventoryHolder holder) {
        return holder == player
                || holder instanceof me.docdrewskii.profitmultiplier.gui.MenuHolder
                || holder instanceof SellMenuHolder
                || isVanillaHolder(holder);
    }

    /**
     * Vanilla containers/entities are implemented by Bukkit/CraftBukkit itself, so their holder's
     * class always lives under the server's own "org.bukkit" package — checking that instead of
     * a specific interface (Container, DoubleChest, ...) avoids depending on API classes that
     * don't exist on every Spigot/Paper version this plugin supports (down to 1.8).
     */
    private boolean isVanillaHolder(InventoryHolder holder) {
        if (holder == null) return false;
        Package pkg = holder.getClass().getPackage();
        return pkg != null && pkg.getName().startsWith("org.bukkit.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        suppressed.remove(event.getPlayer().getUniqueId());
    }
}
