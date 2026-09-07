package me.docdrewskii.profitmultiplier.shop;

import me.docdrewskii.profitmultiplier.ProfitMultiplier;
import me.docdrewskii.profitmultiplier.hook.sell.SellProcessor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Core selling logic shared by the /sell chest GUI and /sellall. Money is always credited
 * BEFORE any item is removed from the player's inventory — if the deposit fails for any
 * reason, nothing is taken and nothing is recorded, so a player can never end up paying with
 * items and receiving no money.
 */
public class SellShopService {

    private final ProfitMultiplier plugin;

    public SellShopService(ProfitMultiplier plugin) {
        this.plugin = plugin;
    }

    public boolean isSellable(Material material) {
        return plugin.getConfigManager().isSellable(material);
    }

    /**
     * Same as {@link #isSellable(Material)} but also rejects anything that isn't a plain vanilla
     * item — a custom item from MMOItems / ItemsAdder / Oraxen usually shares a vanilla Material
     * with unrelated priced items (e.g. every MMOItems sword might be DIAMOND_SWORD + a custom
     * model data id), and those plugins manage the item's identity themselves, so this shop must
     * never treat one as a plain sellable stack of that material. Same rule this shop's
     * {@code InventoryPriceLoreManager} sibling already applies for the same reason.
     */
    public boolean isSellable(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return false;
        if (!isSellable(stack.getType())) return false;
        ItemMeta meta = stack.getItemMeta();
        return meta == null || !meta.hasCustomModelData();
    }

    /**
     * Sells {@code amount} of {@code material} on the player's behalf. The caller is
     * responsible for taking the item(s) away from wherever they came from (cursor, a clicked
     * slot, inventory) and MUST NOT do so until this returns a non-null result.
     *
     * @return the amount credited to the player, or {@code null} if the sale could not happen
     *         (unsellable item, economy unavailable, or the deposit itself failed) — in which
     *         case the caller must leave the item(s) untouched.
     */
    public Double sellDetached(Player player, Material material, int amount) {
        if (player == null || material == null || amount <= 0) return null;

        Double unitPrice = plugin.getPriceRotationManager().getCurrentPrice(material);
        if (unitPrice == null || unitPrice <= 0) return null;

        if (!plugin.getEconomyManager().isAvailable()) return null;

        SellProcessor processor = plugin.getSellHookManager().getProcessor();
        double originalPrice = unitPrice * amount;
        double finalPrice = processor.quoteBoostedPrice(player, material, amount, originalPrice);

        if (!plugin.getEconomyManager().deposit(player, finalPrice)) return null;

        processor.recordSale(player, material, amount, originalPrice, finalPrice);
        return finalPrice;
    }

    /**
     * Sweeps the player's main inventory + hotbar (armor and offhand are left alone) and sells
     * every stack that has a configured price and is a plain vanilla item (see
     * {@link #isSellable(ItemStack)}). Each distinct material is sold in a single call so
     * threshold/tier crossings are computed correctly. Removal happens by the exact slots that
     * were counted — never by a generic "any stack of this material" sweep — so a custom item
     * (e.g. an MMOItems weapon) sitting in a different slot of the same material is never touched.
     */
    public SellAllResult sellAll(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();

        Map<Material, Integer> counts = new LinkedHashMap<>();
        Map<Material, List<Integer>> slots = new LinkedHashMap<>();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (!isSellable(stack)) continue;
            Material mat = stack.getType();
            counts.merge(mat, stack.getAmount(), Integer::sum);
            slots.computeIfAbsent(mat, k -> new ArrayList<>()).add(i);
        }

        SellAllResult result = new SellAllResult();
        boolean removed = false;
        for (Map.Entry<Material, Integer> entry : counts.entrySet()) {
            Double credited = sellDetached(player, entry.getKey(), entry.getValue());
            if (credited == null) continue;

            for (int slot : slots.get(entry.getKey())) {
                contents[slot] = null;
            }
            removed = true;
            result.add(entry.getKey(), entry.getValue(), credited);
        }

        if (removed) {
            player.getInventory().setStorageContents(contents);
        }
        return result;
    }
}
