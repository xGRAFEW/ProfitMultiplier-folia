package me.docdrewskii.profitmultiplier.shop;

import me.docdrewskii.profitmultiplier.ProfitMultiplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Handles the /sell chest GUI. The sell slots are never actually used as storage — every click
 * that would place an item there is intercepted, sold, and the slot is left (or kept) empty.
 * This means there is never a tick where a sold item physically sits in the menu, which removes
 * the whole class of dupe/loss bugs that come from items resting in a shared container.
 */
public class SellMenuListener implements Listener {

    private final ProfitMultiplier plugin;

    public SellMenuListener(ProfitMultiplier plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof SellMenuHolder)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return;

        if (clicked.equals(top)) {
            handleTopClick(player, event);
        } else if (event.isShiftClick()) {
            handleShiftFromPlayer(player, event);
        }
    }

    private void handleTopClick(Player player, InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType() == Material.AIR) return;

        ClickType click = event.getClick();
        boolean sellOne = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;

        int amount = sellOne ? 1 : cursor.getAmount();
        Double credited = plugin.getSellShopService().sellDetached(player, cursor.getType(), amount);
        if (credited == null) {
            denySale(player, cursor.getType());
            return;
        }

        int remaining = cursor.getAmount() - amount;
        event.setCursor(remaining > 0 ? withAmount(cursor, remaining) : null);
        announceSale(player, cursor.getType(), amount, credited);
    }

    private void handleShiftFromPlayer(Player player, InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) return;

        int amount = current.getAmount();
        Double credited = plugin.getSellShopService().sellDetached(player, current.getType(), amount);
        if (credited == null) {
            denySale(player, current.getType());
            return;
        }

        event.setCurrentItem(null);
        announceSale(player, current.getType(), amount, credited);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof SellMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellMenuHolder)) return;

        HumanEntity who = event.getPlayer();
        if (!(who instanceof Player)) return;
        Player player = (Player) who;

        Inventory inv = event.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType() == Material.AIR) continue;

            Double credited = plugin.getSellShopService().sellDetached(player, stack.getType(), stack.getAmount());
            if (credited != null) {
                announceSale(player, stack.getType(), stack.getAmount(), credited);
            } else {
                player.getInventory().addItem(stack);
            }
            inv.setItem(i, null);
        }
    }

    private ItemStack withAmount(ItemStack template, int amount) {
        ItemStack clone = template.clone();
        clone.setAmount(amount);
        return clone;
    }

    private void announceSale(Player player, Material material, int amount, double credited) {
        plugin.getLang().send(player, "sell-item-sold",
                "{amount}", String.valueOf(amount),
                "{item}", friendly(material),
                "{price}", plugin.getEconomyManager().format(credited));
    }

    private void denySale(Player player, Material material) {
        plugin.getLang().send(player, "sell-not-sellable", "{item}", friendly(material));
    }

    private String friendly(Material mat) {
        String name = mat.name().replace('_', ' ').toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (String w : name.split(" ")) {
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }
}
