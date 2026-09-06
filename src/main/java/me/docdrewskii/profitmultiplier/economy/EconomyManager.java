package me.docdrewskii.profitmultiplier.economy;

import me.docdrewskii.profitmultiplier.ProfitMultiplier;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Thin wrapper around Vault's Economy service. The registration is looked up fresh on every
 * call instead of cached at enable time — economy plugins (EssentialsX, CMI, ...) can register
 * their Vault provider after ProfitMultiplier enables depending on load order, so caching a
 * single lookup at startup would wrongly report the shop as unavailable forever on some servers.
 */
public class EconomyManager {

    private final ProfitMultiplier plugin;

    public EconomyManager(ProfitMultiplier plugin) {
        this.plugin = plugin;
    }

    /**
     * Called once on enable purely to log whether an economy is currently reachable.
     */
    public boolean setup() {
        return isAvailable();
    }

    public boolean isAvailable() {
        return economy() != null;
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        Economy economy = economy();
        if (economy == null || amount <= 0) return false;
        try {
            return economy.depositPlayer(player, amount).transactionSuccess();
        } catch (Throwable t) {
            plugin.getLogger().warning("Vault deposit failed for " + player.getName() + ": " + t.getMessage());
            return false;
        }
    }

    /**
     * Formats an amount for display in our own chat messages/menus. Deliberately does NOT call
     * the Vault economy's own {@code format(double)} — different economy plugins return wildly
     * different markup there (e.g. zEssentials returns MiniMessage-style raw hex color tags like
     * "#2bd66f2$" instead of legacy "&"-codes), which shows up as broken literal text once we
     * drop it into item lore or chat. Using our own {@link me.docdrewskii.profitmultiplier.currency.Currency}
     * formatter keeps this fully under our control and guaranteed legacy-color-safe, regardless
     * of which economy plugin is actually handling the money.
     */
    public String format(double amount) {
        return plugin.getCurrencyManager().getDefault().format(amount);
    }

    private Economy economy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return null;
        try {
            RegisteredServiceProvider<Economy> provider =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            return provider == null ? null : provider.getProvider();
        } catch (Throwable t) {
            return null;
        }
    }
}
