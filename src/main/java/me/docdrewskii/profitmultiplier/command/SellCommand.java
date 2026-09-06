package me.docdrewskii.profitmultiplier.command;

import me.docdrewskii.profitmultiplier.ProfitMultiplier;
import me.docdrewskii.profitmultiplier.shop.SellAllResult;
import me.docdrewskii.profitmultiplier.shop.SellMenu;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class SellCommand implements TabExecutor {

    private final ProfitMultiplier plugin;

    public SellCommand(ProfitMultiplier plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getLang().send(sender, "console-needs-player");
            return true;
        }
        Player player = (Player) sender;

        if (!plugin.getEconomyManager().isAvailable()) {
            plugin.getLang().send(player, "shop-unavailable");
            return true;
        }

        if (command.getName().equalsIgnoreCase("sellall")) {
            if (noPerm(player, "profitmultiplier.sellall")) return true;
            handleSellAll(player);
        } else {
            if (noPerm(player, "profitmultiplier.sell")) return true;
            SellMenu.open(plugin, player);
        }
        return true;
    }

    private void handleSellAll(Player player) {
        SellAllResult result = plugin.getSellShopService().sellAll(player);
        if (result.isEmpty()) {
            plugin.getLang().send(player, "sellall-empty");
            return;
        }
        for (SellAllResult.Entry entry : result.getEntries()) {
            plugin.getLang().send(player, "sell-item-sold",
                    "{amount}", String.valueOf(entry.getAmount()),
                    "{item}", friendly(entry.getMaterial()),
                    "{price}", plugin.getEconomyManager().format(entry.getCredited()));
        }
        plugin.getLang().send(player, "sellall-result",
                "{items}", String.valueOf(result.getItemsSold()),
                "{types}", String.valueOf(result.getDistinctTypes()),
                "{total}", plugin.getEconomyManager().format(result.getTotalCredited()));
    }

    private String friendly(Material mat) {
        String name = mat.name().replace('_', ' ').toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (String w : name.split(" ")) {
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    private boolean noPerm(Player player, String perm) {
        if (player.hasPermission(perm)) return false;
        plugin.getLang().send(player, "no-permission");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
