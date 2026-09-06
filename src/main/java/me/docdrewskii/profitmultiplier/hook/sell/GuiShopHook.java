package me.docdrewskii.profitmultiplier.hook.sell;

import me.docdrewskii.profitmultiplier.ProfitMultiplier;
import me.docdrewskii.profitmultiplier.util.FoliaScheduler;
import me.docdrewskii.profitmultiplier.util.VersionHelper;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.ServicePriority;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GuiShopHook implements SellHook, InvocationHandler, Listener {

    private static final class Quote {
        final Material material;
        final int amount;
        final double base;
        final double boosted;

        Quote(Material material, int amount, double base, double boosted) {
            this.material = material;
            this.amount = amount;
            this.base = base;
            this.boosted = boosted;
        }
    }

    private final ProfitMultiplier plugin;
    private final SellProcessor processor;
    private final Map<UUID, Quote> quotes = new ConcurrentHashMap<>();

    private volatile UUID currentSeller;

    public GuiShopHook(ProfitMultiplier plugin, SellProcessor processor) {
        this.plugin = plugin;
        this.processor = processor;
    }

    @Override
    public String pluginName() {
        return "GUIShop";
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean register() {
        if (Bukkit.getPluginManager().getPlugin("GUIShop") == null) {
            return false;
        }
        try {
            Class<?> providerInterface = Class.forName("com.pablo67340.guishop.api.DynamicPriceProvider");
            Object proxy = Proxy.newProxyInstance(
                    providerInterface.getClassLoader(), new Class[]{providerInterface}, this);
            Bukkit.getServicesManager().register(
                    (Class<Object>) providerInterface, proxy, plugin, ServicePriority.High);
            Bukkit.getPluginManager().registerEvents(this, plugin);
            return true;
        } catch (ClassNotFoundException notFound) {
            return false;
        } catch (Throwable t) {
            plugin.getLogger().warning("[GUIShop] could not register dynamic price provider: " + t.getMessage());
            return false;
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        String name = method.getName();
        try {
            switch (name) {
                case "calculateSellPrice":
                    return calculateSellPrice(args);
                case "calculateBuyPrice":

                    return args[2];
                case "sellItem":
                    onSold(args);
                    return null;
                case "buyItem":
                    return null;
                case "toString":
                    return "ProfitMultiplierDynamicPriceProvider";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:

                    return (args != null && args.length >= 3 && args[2] instanceof BigDecimal) ? args[2] : null;
            }
        } catch (Throwable t) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().warning("[GUIShop] " + name + " error: " + t);
            }

            return (args != null && args.length >= 4 && args[3] instanceof BigDecimal) ? args[3] : null;
        }
    }

    private BigDecimal calculateSellPrice(Object[] args) {
        String item = (String) args[0];
        int quantity = ((Number) args[1]).intValue();
        BigDecimal staticSell = (BigDecimal) args[3];
        if (staticSell == null) return null;

        Player player = currentSeller();
        Material material = item == null ? null : VersionHelper.resolveMaterial(item);
        if (player == null || material == null) {
            return staticSell;
        }

        double base = staticSell.doubleValue();
        double boosted = processor.quoteBoostedPrice(player, material, quantity, base);

        quotes.put(player.getUniqueId(), new Quote(material, quantity, base, boosted));
        return boosted > base ? BigDecimal.valueOf(boosted) : staticSell;
    }

    private void onSold(Object[] args) {
        String item = (String) args[0];
        int quantity = ((Number) args[1]).intValue();
        Player player = currentSeller();
        Material material = item == null ? null : VersionHelper.resolveMaterial(item);
        if (player == null || material == null) return;

        Quote quote = quotes.remove(player.getUniqueId());
        Runnable record;
        if (quote != null && quote.material == material && quote.amount == quantity) {
            record = () -> processor.recordSale(player, material, quantity, quote.base, quote.boosted);
        } else {

            record = () -> processor.recordSale(player, material, quantity, 0.0, 0.0);
        }
        FoliaScheduler.runForPlayer(plugin, player, record);
    }

    private Player currentSeller() {
        UUID id = currentSeller;
        return id == null ? null : Bukkit.getPlayer(id);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onOpen(InventoryOpenEvent event) {
        if (isGuiShop(event.getInventory()) && event.getPlayer() instanceof Player) {
            currentSeller = event.getPlayer().getUniqueId();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (isGuiShop(event.getView().getTopInventory()) && event.getWhoClicked() instanceof Player) {
            currentSeller = event.getWhoClicked().getUniqueId();
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (id.equals(currentSeller)) {
            currentSeller = null;
        }
        quotes.remove(id);
    }

    private boolean isGuiShop(Inventory inventory) {
        if (inventory == null) return false;
        InventoryHolder holder = inventory.getHolder();
        return holder != null
                && holder.getClass().getName().toLowerCase(Locale.ROOT).contains("guishop");
    }
}
