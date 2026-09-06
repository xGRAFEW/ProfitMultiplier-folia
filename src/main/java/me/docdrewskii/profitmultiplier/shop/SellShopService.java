package me.docdrewskii.profitmultiplier.shop;

import me.docdrewskii.profitmultiplier.ProfitMultiplier;
import me.docdrewskii.profitmultiplier.hook.sell.SellProcessor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
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

        Double unitPrice = plugin.getConfigManager().getPrice(material);
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
     * every stack that has a configured price. Each distinct material is sold in a single call
     * so threshold/tier crossings are computed correctly.
     */
    public SellAllResult sellAll(Player player) {
        Map<Material, Integer> counts = new LinkedHashMap<>();
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType() == Material.AIR) continue;
            Material mat = stack.getType();
            if (!isSellable(mat)) continue;
            counts.merge(mat, stack.getAmount(), Integer::sum);
        }

        int itemsSold = 0;
        int typesSold = 0;
        double totalCredited = 0.0;

        for (Map.Entry<Material, Integer> entry : counts.entrySet()) {
            Double credited = sellDetached(player, entry.getKey(), entry.getValue());
            if (credited == null) continue;

            removeExact(player, entry.getKey(), entry.getValue());
            itemsSold += entry.getValue();
            typesSold++;
            totalCredited += credited;
        }

        return new SellAllResult(itemsSold, typesSold, totalCredited);
    }

    private void removeExact(Player player, Material material, int amount) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        int remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) continue;
            int take = Math.min(stack.getAmount(), remaining);
            remaining -= take;
            if (take >= stack.getAmount()) {
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
        }
        player.getInventory().setStorageContents(contents);
    }
}
