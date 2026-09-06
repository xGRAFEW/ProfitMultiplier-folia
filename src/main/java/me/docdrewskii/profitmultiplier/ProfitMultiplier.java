package me.docdrewskii.profitmultiplier;

import me.docdrewskii.profitmultiplier.api.ProfitMultiplierAPI;
import me.docdrewskii.profitmultiplier.api.ProfitMultiplierProvider;
import me.docdrewskii.profitmultiplier.api.impl.ProfitMultiplierAPIImpl;
import me.docdrewskii.profitmultiplier.command.ProfitCommand;
import me.docdrewskii.profitmultiplier.config.ConfigManager;
import me.docdrewskii.profitmultiplier.config.LangManager;
import me.docdrewskii.profitmultiplier.currency.CurrencyManager;
import me.docdrewskii.profitmultiplier.data.PlayerDataManager;
import me.docdrewskii.profitmultiplier.gui.MenuListener;
import me.docdrewskii.profitmultiplier.gui.MenuManager;
import me.docdrewskii.profitmultiplier.hook.sell.SellHookManager;
import me.docdrewskii.profitmultiplier.milestone.MilestoneManager;
import me.docdrewskii.profitmultiplier.placeholder.ProfitPlaceholders;
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

        getServer().getPluginManager().registerEvents(new MenuListener(this), this);

        FoliaScheduler.runGlobalTimer(this, () -> menuManager.refreshOpenMenus(),
                MENU_REFRESH_TICKS, MENU_REFRESH_TICKS);

        PluginCommand command = getCommand("profitmultiplier");
        if (command != null) {
            ProfitCommand handler = new ProfitCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

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
}
