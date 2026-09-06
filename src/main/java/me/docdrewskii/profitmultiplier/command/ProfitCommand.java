package me.docdrewskii.profitmultiplier.command;

import me.docdrewskii.profitmultiplier.ProfitMultiplier;
import me.docdrewskii.profitmultiplier.api.ResetCause;
import me.docdrewskii.profitmultiplier.config.ConfigManager;
import me.docdrewskii.profitmultiplier.config.LangManager;
import me.docdrewskii.profitmultiplier.data.PlayerDataManager;
import me.docdrewskii.profitmultiplier.util.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProfitCommand implements TabExecutor {

    private final ProfitMultiplier plugin;

    public ProfitCommand(ProfitMultiplier plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ConfigManager config = plugin.getConfigManager();
        LangManager lang = plugin.getLang();
        PlayerDataManager data = plugin.getDataManager();

        if (args.length == 0) {
            if (label.equalsIgnoreCase("sellmulti")) {
                openOwn(sender, "sellmulti");
            } else {
                sendHelp(sender);
            }
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload":
                if (noPerm(sender, "profitmultiplier.admin")) return true;
                config.load();
                lang.load();
                plugin.getCurrencyManager().load();
                plugin.getMenuManager().loadAll();
                plugin.getMilestoneManager().load();
                lang.send(sender, "config-reloaded");
                return true;

            case "gui":
            case "menu":
                handleGui(sender, args);
                return true;

            case "reset":
                if (noPerm(sender, "profitmultiplier.admin")) return true;
                if (args.length < 2) {
                    lang.send(sender, "usage-reset");
                    return true;
                }
                handleReset(sender, args[1]);
                return true;

            case "resetall":
                if (noPerm(sender, "profitmultiplier.admin")) return true;
                int cleared = data.resetAll(ResetCause.COMMAND);
                lang.send(sender, "reset-all", "{count}", String.valueOf(cleared));
                return true;

            case "stats":
                handleStats(sender, args);
                return true;

            case "help":
                sendHelp(sender);
                return true;

            default:
                lang.send(sender, "unknown-subcommand");
                return true;
        }
    }

    private void handleGui(CommandSender sender, String[] args) {
        String menuName = args.length >= 2 ? args[1] : "sellmulti";

        if (args.length >= 3) {
            if (noPerm(sender, "profitmultiplier.admin")) return;
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                plugin.getLang().send(sender, "player-not-online", "{player}", args[2]);
                return;
            }
            FoliaScheduler.runForPlayer(plugin, target, () -> plugin.getMenuManager().open(target, menuName));
            plugin.getLang().send(sender, "menu-opened-other",
                    "{player}", target.getName(), "{menu}", menuName);
            return;
        }

        openOwn(sender, menuName);
    }

    private void openOwn(CommandSender sender, String menuName) {
        if (!(sender instanceof Player)) {
            plugin.getLang().send(sender, "console-needs-player");
            return;
        }
        if (noPerm(sender, "profitmultiplier.gui")) return;
        plugin.getMenuManager().open((Player) sender, menuName);
    }

    private void handleReset(CommandSender sender, String name) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(name);
        boolean had = plugin.getDataManager().resetPlayer(target.getUniqueId(), ResetCause.COMMAND);
        if (had) {
            plugin.getLang().send(sender, "reset-player", "{player}", name);
        } else {
            plugin.getLang().send(sender, "reset-player-none", "{player}", name);
        }
    }

    private void handleStats(CommandSender sender, String[] args) {
        UUID id;
        String label;
        if (args.length >= 2) {
            if (noPerm(sender, "profitmultiplier.admin")) return;
            id = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
            label = args[1];
        } else {
            if (!(sender instanceof Player)) {
                plugin.getLang().send(sender, "console-needs-player");
                return;
            }
            if (noPerm(sender, "profitmultiplier.stats")) return;
            Player p = (Player) sender;
            id = p.getUniqueId();
            label = p.getName();
        }

        Map<Material, Long> totals = plugin.getDataManager().getAll(id);
        if (totals.isEmpty()) {
            plugin.getLang().send(sender, "stats-none", "{player}", label);
            return;
        }
        plugin.getLang().send(sender, "stats-header", "{player}", label);
        Player online = Bukkit.getPlayer(id);
        double scale = online != null ? plugin.getConfigManager().getThresholdScale(online) : 1.0;
        for (Map.Entry<Material, Long> e : totals.entrySet()) {
            double mult = plugin.getConfigManager().multiplierAtCount(e.getKey(), e.getValue(), scale);
            String multText = mult > 1.0 ? "&b" + mult + "x" : "&7none";
            plugin.getLang().send(sender, "stats-line",
                    "{item}", friendly(e.getKey()),
                    "{sold}", String.valueOf(e.getValue()),
                    "{multiplier}", multText);
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(color("&6&lProfitMultiplier"));
        if (sender.hasPermission("profitmultiplier.gui")) {
            sender.sendMessage(color("&e/sellmulti &7- Open the sell-multiplier menu"));
            sender.sendMessage(color("&e/pm gui [menu] &7- Open a menu"));
        }
        if (sender.hasPermission("profitmultiplier.stats")) {
            sender.sendMessage(color("&e/pm stats [player] &7- View sell progression"));
        }
        if (sender.hasPermission("profitmultiplier.admin")) {
            sender.sendMessage(color("&e/pm reload &7- Reload config & lang"));
            sender.sendMessage(color("&e/pm reset <player> &7- Reset one player's totals"));
            sender.sendMessage(color("&e/pm resetall &7- Reset every player's totals"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            subs.add("help");
            if (sender.hasPermission("profitmultiplier.gui")) subs.add("gui");
            if (sender.hasPermission("profitmultiplier.stats")) subs.add("stats");
            if (sender.hasPermission("profitmultiplier.admin")) {
                subs.add("reload");
                subs.add("reset");
                subs.add("resetall");
            }
            String prefix = args[0].toLowerCase();
            for (String s : subs) {
                if (s.startsWith(prefix)) out.add(s);
            }
        } else if (args.length == 2) {
            if ((args[0].equalsIgnoreCase("gui") || args[0].equalsIgnoreCase("menu"))
                    && sender.hasPermission("profitmultiplier.gui")) {
                String prefix = args[1].toLowerCase();
                for (String menu : plugin.getMenuManager().getMenuNames()) {
                    if (menu.startsWith(prefix)) out.add(menu);
                }
            }
            boolean wantsPlayer =
                    (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("stats"))
                            && sender.hasPermission("profitmultiplier.admin");
            if (wantsPlayer) {
                String prefix = args[1].toLowerCase();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(prefix)) out.add(p.getName());
                }
            }
        } else if (args.length == 3) {
            if ((args[0].equalsIgnoreCase("gui") || args[0].equalsIgnoreCase("menu"))
                    && sender.hasPermission("profitmultiplier.admin")) {
                String prefix = args[2].toLowerCase();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(prefix)) out.add(p.getName());
                }
            }
        }

        Collections.sort(out);
        return out;
    }

    private boolean noPerm(CommandSender sender, String perm) {
        if (sender.hasPermission(perm)) return false;
        plugin.getLang().send(sender, "no-permission");
        return true;
    }

    private String friendly(Material mat) {
        String name = mat.name().replace('_', ' ').toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (String w : name.split(" ")) {
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
