package me.docdrewskii.profitmultiplier;

import me.docdrewskii.profitmultiplier.api.ProfitMultiplierAPI;
import me.docdrewskii.profitmultiplier.api.ProfitMultiplierProvider;
import me.docdrewskii.profitmultiplier.api.impl.ProfitMultiplierAPIImpl;
import me.docdrewskii.profitmultiplier.command.ProfitCommand;
import me.docdrewskii.profitmultiplier.config.ConfigManager;
import me.docdrewskii.profitmultiplier.config.LangManager;
import me.docdrewskii.profitmultiplier.currency.CurrencyManager;
import me.docdrewskii.profitmultiplier.data.PlayerDataManager;
import me.docdrewskii.profitmultiplier.economy.EconomyManager;
import me.docdrewskii.profitmultiplier.gui.MenuListener;
import me.docdrewskii.profitmultiplier.gui.MenuManager;
import me.docdrewskii.profitmultiplier.hook.sell.SellHookManager;
import me.docdrewskii.profitmultiplier.milestone.MilestoneManager;
import me.docdrewskii.profitmultiplier.placeholder.ProfitPlaceholders;
import me.docdrewskii.profitmultiplier.command.SellCommand;
import me.docdrewskii.profitmultiplier.shop.SellMenuListener;
import me.docdrewskii.profitmultiplier.shop.SellShopService;
import me.docdrewskii.profitmultiplier.util.FoliaScheduler;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class ProfitMultiplier extends JavaPlugin {

    private static final long PERIODIC_SECONDS = 60L * 5L;
    private static final long MENU_REFRESH_TICKS = 20L;

    private ConfigManager configManager;
    private LangManager langManager;
    private CurrencyManager currencyManager;
    private PlayerDataManager dataManager;
    private MenuManager menuManager;
    private MilestoneManager milestoneManager;
    private SellHookManager sellHookManager;
    private EconomyManager economyManager;
    private SellShopService sellShopService;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.load();

        langManager = new LangManager(this);
        langManager.load();

        currencyManager = new CurrencyManager(this);
        currencyManager.load();

        dataManager = new PlayerDataManager(this);
        dataManager.load();
        dataManager.checkAutoReset();

        menuManager = new MenuManager(this);
        menuManager.loadAll();

        milestoneManager = new MilestoneManager(this);
        milestoneManager.load();

        ProfitMultiplierAPI api = new ProfitMultiplierAPIImpl(this);
        ProfitMultiplierProvider.register(api);
        getServer().getServicesManager().register(ProfitMultiplierAPI.class, api, this, ServicePriority.Normal);

        sellHookManager = new SellHookManager(this);
        sellHookManager.registerAll();

        economyManager = new EconomyManager(this);
        sellShopService = new SellShopService(this);
        if (economyManager.setup()) {
            getLogger().info("Hooked into a Vault economy — /sell and /sellall are enabled.");
        } else {
            getLogger().warning("No Vault economy found — /sell and /sellall will be unavailable "
                    + "until one is installed (e.g. EssentialsX). Everything else still works.");
        }

        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new SellMenuListener(this), this);

        FoliaScheduler.runGlobalTimer(this, () -> menuManager.refreshOpenMenus(),
                MENU_REFRESH_TICKS, MENU_REFRESH_TICKS);

        PluginCommand command = getCommand("profitmultiplier");
        if (command != null) {
            ProfitCommand handler = new ProfitCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        SellCommand sellHandler = new SellCommand(this);
        registerSellCommand("sell", sellHandler);
        registerSellCommand("sellall", sellHandler);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ProfitPlaceholders(this).register();
            getLogger().info("Hooked into PlaceholderAPI.");
        }

        FoliaScheduler.runAsyncTimer(this, () -> {
            dataManager.checkAutoReset();
            dataManager.saveIfDirty();
        }, PERIODIC_SECONDS, PERIODIC_SECONDS);

        getLogger().info("ProfitMultiplier v" + getDescription().getVersion() + " enabled.");
    }

    private void registerSellCommand(String name, SellCommand handler) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command '" + name + "' is missing from plugin.yml — skipping.");
            return;
        }
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }

    @Override
    public void onDisable() {
        ProfitMultiplierProvider.unregister();
        if (dataManager != null) dataManager.save();
        getLogger().info("ProfitMultiplier disabled.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LangManager getLang() {
        return langManager;
    }

    public CurrencyManager getCurrencyManager() {
        return currencyManager;
    }

    public PlayerDataManager getDataManager() {
        return dataManager;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public MilestoneManager getMilestoneManager() {
        return milestoneManager;
    }

    public SellHookManager getSellHookManager() {
        return sellHookManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public SellShopService getSellShopService() {
        return sellShopService;
    }
}
