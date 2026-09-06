package me.docdrewskii.profitmultiplier.shop;

import me.docdrewskii.profitmultiplier.ProfitMultiplier;
import me.docdrewskii.profitmultiplier.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class SellMenu {

    private SellMenu() {
    }

    public static void open(ProfitMultiplier plugin, Player player) {
        int rows = clamp(plugin.getConfig().getInt("shop.sell-gui-rows", 4), 1, 6);
        String title = TextUtil.color(plugin.getConfig().getString("shop.sell-gui-title", "&aSell Menu"));

        SellMenuHolder holder = new SellMenuHolder(player);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inv);
        player.openInventory(inv);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
