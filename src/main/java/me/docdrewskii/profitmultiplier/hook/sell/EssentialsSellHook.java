package me.docdrewskii.profitmultiplier.hook.sell;

import me.docdrewskii.profitmultiplier.ProfitMultiplier;
import me.docdrewskii.profitmultiplier.util.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Essentials exposes no pre-sell price event, so this hook snapshots the seller's
 * inventory when /sell runs, diffs it one tick later to find what was sold, recomputes
 * the base payout from Essentials' worth registry and deposits the bonus on top.
 */
public class EssentialsSellHook implements SellHook, Listener {

    private final ProfitMultiplier plugin;
    private final SellProcessor processor;
    private final Map<String, Method> methodCache = new HashMap<>();

    private Plugin essentials;

    public EssentialsSellHook(ProfitMultiplier plugin, SellProcessor processor) {
        this.plugin = plugin;
        this.processor = processor;
    }

    @Override
    public String pluginName() {
        return "Essentials";
    }

    @Override
    public boolean register() {
        essentials = Bukkit.getPluginManager().getPlugin("Essentials");
        if (essentials == null) {
            return false;
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isEssentialsSell(event.getMessage())) {
            return;
        }
        Player player = event.getPlayer();
        Map<Material, Integer> before = snapshot(player);
        FoliaScheduler.runForPlayer(plugin, player, () -> settle(player, before));
    }

    private boolean isEssentialsSell(String message) {
        String label = message.startsWith("/") ? message.substring(1) : message;
        int space = label.indexOf(' ');
        if (space >= 0) {
            label = label.substring(0, space);
        }
        PluginCommand command = Bukkit.getPluginCommand(label.toLowerCase(Locale.ROOT));
        return command != null
                && "sell".equals(command.getName())
                && command.getPlugin() == essentials;
    }

    private Map<Material, Integer> snapshot(Player player) {
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getAmount() > 0) {
                counts.merge(stack.getType(), stack.getAmount(), Integer::sum);
            }
        }
        return counts;
    }

    private void settle(Player player, Map<Material, Integer> before) {
        if (!player.isOnline() || player.isDead()) {
            return;
        }
        Map<Material, Integer> after = snapshot(player);
        for (Map.Entry<Material, Integer> entry : before.entrySet()) {
            Integer remaining = after.get(entry.getKey());
            int sold = entry.getValue() - (remaining == null ? 0 : remaining);
            if (sold > 0) {
                handleSold(player, entry.getKey(), sold);
            }
        }
    }

    private void handleSold(Player player, Material material, int amount) {
        double basePrice;
        try {
            basePrice = basePayout(player, material, amount);
        } catch (Throwable t) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().warning("[Essentials] worth lookup failed for " + material + ": " + t);
            }
            return;
        }

        if (basePrice <= 0) {
            return;
        }

        double boosted = processor.process(player, material, amount, basePrice);
        double bonus = boosted - basePrice;
        if (bonus > 0) {
            deposit(player, bonus);
        }
    }

    private double basePayout(Player player, Material material, int amount) throws Exception {
        Object worth = call(essentials, "getWorth");
        if (worth == null) {
            return 0.0;
        }
        ItemStack probe = new ItemStack(material);
        Object price = invokeFirst(worth, "getPrice",
                new Object[]{essentials, probe}, new Object[]{probe});
        if (!(price instanceof BigDecimal)) {
            return 0.0;
        }
        return ((BigDecimal) price)
                .multiply(essentialsMultiplier(player))
                .multiply(BigDecimal.valueOf(amount))
                .doubleValue();
    }

    private BigDecimal essentialsMultiplier(Player player) {
        try {
            Object settings = call(essentials, "getSettings");
            Object user = call(essentials, "getUser", player);
            Object multiplier = call(settings, "getMultiplier", user);
            if (multiplier instanceof BigDecimal && ((BigDecimal) multiplier).signum() > 0) {
                return (BigDecimal) multiplier;
            }
        } catch (Throwable ignored) {
        }
        return BigDecimal.ONE;
    }

    private void deposit(Player player, double bonus) {
        try {
            Object user = call(essentials, "getUser", player);
            Object balance = call(user, "getMoney");
            call(user, "setMoney", ((BigDecimal) balance).add(BigDecimal.valueOf(bonus)));
        } catch (Throwable t) {
            plugin.getLogger().warning("[Essentials] could not deposit bonus for "
                    + player.getName() + ": " + t.getMessage());
        }
    }

    private Object call(Object target, String name, Object... args) throws Exception {
        Method method = findMethod(target.getClass(), name, args);
        if (method == null) {
            throw new NoSuchMethodException(target.getClass().getName() + "#" + name + "/" + args.length);
        }
        return method.invoke(target, args);
    }

    private Object invokeFirst(Object target, String name, Object[]... attempts) throws Exception {
        for (Object[] args : attempts) {
            Method method = findMethod(target.getClass(), name, args);
            if (method != null) {
                return method.invoke(target, args);
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + name);
    }

    private Method findMethod(Class<?> type, String name, Object[] args) {
        StringBuilder keyBuilder = new StringBuilder(type.getName()).append('#').append(name);
        for (Object arg : args) {
            keyBuilder.append('/').append(arg == null ? "null" : arg.getClass().getName());
        }
        String key = keyBuilder.toString();
        Method cached = methodCache.get(key);
        if (cached != null) {
            return cached;
        }
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name)) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length != args.length) continue;
            boolean compatible = true;
            for (int i = 0; i < params.length; i++) {
                if (args[i] != null && !params[i].isInstance(args[i])) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                method.setAccessible(true);
                methodCache.put(key, method);
                return method;
            }
        }
        return null;
    }
}
