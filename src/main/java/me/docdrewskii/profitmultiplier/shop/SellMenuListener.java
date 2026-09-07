package me.docdrewskii.profitmultiplier.shop;

import me.docdrewskii.profitmultiplier.ProfitMultiplier;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles the /sell chest GUI. Items you drag in sit there for real (like a normal chest) —
 * only unsellable items are refused on the way in. The sale only finalizes when the GUI is
 * closed: everything left in it at that point is sold (or, if that fails, handed back) in one
 * pass, and the inventory is cleared.
 */
public class SellMenuListener implements Listener {

    private final ProfitMultiplier plugin;

    public SellMenuListener(ProfitMultiplier plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof SellMenuHolder)) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        boolean clickedTop = clicked != null && clicked.equals(top);

        switch (event.getAction()) {
            case PLACE_ALL:
            case PLACE_SOME:
            case PLACE_ONE:
            case SWAP_WITH_CURSOR: {
                if (!clickedTop) break;
                ItemStack incoming = event.getCursor();
                denyIfUnsellable(event, player, incoming);
                break;
            }
            case MOVE_TO_OTHER_INVENTORY: {
                if (clickedTop) break; // taking an item back out is always fine
                ItemStack incoming = event.getCurrentItem();
                denyIfUnsellable(event, player, incoming);
                break;
            }
            case HOTBAR_SWAP:
            case HOTBAR_MOVE_AND_READD: {
                if (!clickedTop) break;
                ItemStack incoming = event.getHotbarButton() >= 0
                        ? player.getInventory().getItem(event.getHotbarButton()) : null;
                denyIfUnsellable(event, player, incoming);
                break;
            }
            default:
                break; // every other action only removes items from the sell slots, never adds
        }
    }

    private void denyIfUnsellable(InventoryClickEvent event, Player player, ItemStack incoming) {
        if (incoming == null || incoming.getType() == Material.AIR) return;
        if (!plugin.getSellShopService().isSellable(incoming)) {
            event.setCancelled(true);
            denySale(player, incoming.getType());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof SellMenuHolder)) return;

        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (!touchesTop) return;

        ItemStack oldCursor = event.getOldCursor();
        Material type = oldCursor.getType();
        if (type != Material.AIR && !plugin.getSellShopService().isSellable(oldCursor)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player) {
                denySale((Player) event.getWhoClicked(), type);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellMenuHolder)) return;

        HumanEntity who = event.getPlayer();
        if (!(who instanceof Player)) return;
        Player player = (Player) who;

        Inventory inv = event.getInventory();
        Map<Material, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType() == Material.AIR) continue;
            counts.merge(stack.getType(), stack.getAmount(), Integer::sum);
        }

        for (Map.Entry<Material, Integer> entry : counts.entrySet()) {
            Double credited = plugin.getSellShopService().sellDetached(player, entry.getKey(), entry.getValue());
            if (credited != null) {
                announceSale(player, entry.getKey(), entry.getValue(), credited);
            } else {
                giveOrDrop(player, entry.getKey(), entry.getValue());
            }
        }
        inv.clear();
    }

    private void giveOrDrop(Player player, Material material, int amount) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(material, amount));
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
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
