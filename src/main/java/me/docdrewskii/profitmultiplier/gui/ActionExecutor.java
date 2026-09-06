package me.docdrewskii.profitmultiplier.gui;

import me.docdrewskii.profitmultiplier.ProfitMultiplier;
import me.docdrewskii.profitmultiplier.util.FoliaScheduler;
import me.docdrewskii.profitmultiplier.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ActionExecutor {

    private final ProfitMultiplier plugin;

    public ActionExecutor(ProfitMultiplier plugin) {
        this.plugin = plugin;
    }

    public void run(Player player, List<MenuAction> actions, Map<String, String> tokens) {
        if (actions == null || actions.isEmpty()) return;
        for (MenuAction action : actions) {
            execute(player, action, tokens);
        }
    }

    private void execute(Player player, MenuAction action, Map<String, String> tokens) {
        String arg = action.getArgument();
        switch (action.getType()) {
            case MESSAGE:
                player.sendMessage(TextUtil.render(player, arg, tokens));
                break;
            case BROADCAST:
                Bukkit.broadcastMessage(TextUtil.render(player, arg, tokens));
                break;
            case CONSOLE:
                String consoleCommand = command(player, arg, tokens);
                FoliaScheduler.runGlobal(plugin, () ->
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCommand));
                break;
            case PLAYER:
                player.performCommand(command(player, arg, tokens));
                break;
            case CLOSE:
                player.closeInventory();
                break;
            case OPEN:
                openLater(player, command(player, arg, tokens).trim());
                break;
            case SOUND:
                playSound(player, command(player, arg, tokens));
                break;
            case REFRESH:
                plugin.getMenuManager().refresh(player);
                break;
            case NEXT_PAGE:
                plugin.getMenuManager().nextPage(player);
                break;
            case PREVIOUS_PAGE:
                plugin.getMenuManager().previousPage(player);
                break;
            case PAGE:
                try {
                    plugin.getMenuManager().gotoPage(player, Integer.parseInt(command(player, arg, tokens).trim()));
                } catch (NumberFormatException ignored) {
                }
                break;
            default:
                break;
        }
    }

    private String command(Player player, String raw, Map<String, String> tokens) {
        String out = TextUtil.tokens(raw, tokens);
        out = TextUtil.papi(player, out);

        if (out.startsWith("/")) out = out.substring(1);
        return out;
    }

    private void openLater(Player player, String menuName) {
        FoliaScheduler.runForPlayer(plugin, player, () -> plugin.getMenuManager().open(player, menuName));
    }

    @SuppressWarnings({"deprecation", "removal"})
    private void playSound(Player player, String raw) {
        if (raw == null || raw.trim().isEmpty()) return;
        String[] parts = raw.trim().split("\\s+");
        float volume = 1.0f;
        float pitch = 1.0f;
        try {
            if (parts.length >= 2) volume = Float.parseFloat(parts[1]);
            if (parts.length >= 3) pitch = Float.parseFloat(parts[2]);
        } catch (NumberFormatException ignored) {
        }
        try {
            Sound sound = Sound.valueOf(parts[0].toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ex) {

            try {
                player.playSound(player.getLocation(), parts[0].toLowerCase(Locale.ROOT), volume, pitch);
            } catch (Throwable ignored) {
                plugin.getLogger().warning("Unknown sound in menu action: " + parts[0]);
            }
        }
    }
}
