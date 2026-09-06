package me.docdrewskii.profitmultiplier.api.impl;

import me.docdrewskii.profitmultiplier.ProfitMultiplier;
import me.docdrewskii.profitmultiplier.api.ProfitMultiplierAPI;
import me.docdrewskii.profitmultiplier.api.ResetCause;
import me.docdrewskii.profitmultiplier.config.ConfigManager;
import me.docdrewskii.profitmultiplier.data.PlayerDataManager;
import org.bukkit.Material;

import java.util.Map;
import java.util.UUID;

public class ProfitMultiplierAPIImpl implements ProfitMultiplierAPI {

    private final ProfitMultiplier plugin;

    public ProfitMultiplierAPIImpl(ProfitMultiplier plugin) {
        this.plugin = plugin;
    }

    private ConfigManager config() {
        return plugin.getConfigManager();
    }

    private PlayerDataManager data() {
        return plugin.getDataManager();
    }

    @Override
    public long getSold(UUID playerId, Material material) {
        return data().getSold(playerId, material);
    }

    @Override
    public long getTotalSold(UUID playerId) {
        return data().getTotalSold(playerId);
    }

    @Override
    public Map<Material, Long> getAllSold(UUID playerId) {
        return data().getAll(playerId);
    }

    @Override
    public double getMultiplier(UUID playerId, Material material) {
        return config().multiplierAtCount(material, data().getSold(playerId, material));
    }

    @Override
    public double getMultiplierAt(Material material, long soldCount) {
        return config().multiplierAtCount(material, soldCount);
    }

    @Override
    public long getActiveThreshold(UUID playerId, Material material) {
        return config().activeThreshold(material, data().getSold(playerId, material));
    }

    @Override
    public long getNextThreshold(UUID playerId, Material material) {
        long next = config().nextThresholdAbove(material, data().getSold(playerId, material));
        return next == Long.MAX_VALUE ? -1L : next;
    }

    @Override
    public long getRemainingToNextThreshold(UUID playerId, Material material) {
        long sold = data().getSold(playerId, material);
        long next = config().nextThresholdAbove(material, sold);
        return next == Long.MAX_VALUE ? -1L : next - sold;
    }

    @Override
    public double getGroupRevenue(UUID playerId, String groupName) {
        return data().getGroupRevenue(playerId, groupName);
    }

    @Override
    public double getGroupMultiplier(UUID playerId, String groupName) {
        me.docdrewskii.profitmultiplier.model.ItemGroup group = config().getGroup(groupName);
        if (group == null) return 1.0;
        double revenue = data().getGroupRevenue(playerId, groupName);
        return config().revenueMultiplierAt(group.getTiers(), revenue, 1.0);
    }

    @Override
    public double getItemRevenue(UUID playerId, Material material) {
        return data().getItemRevenue(playerId, material);
    }

    @Override
    public double getItemMultiplier(UUID playerId, Material material) {
        double revenue = data().getItemRevenue(playerId, material);
        return config().revenueMultiplierAt(config().getLadderTiers(material), revenue, 1.0);
    }

    @Override
    public double getBonusTotal(UUID playerId) {
        return data().getBonusTotal(playerId);
    }

    @Override
    public double getLastBonus(UUID playerId) {
        return data().getLastBonus(playerId);
    }

    @Override
    public long addSold(UUID playerId, Material material, int amount) {
        return data().addSold(playerId, material, amount);
    }

    @Override
    public void setSold(UUID playerId, Material material, long amount) {
        data().setSold(playerId, material, amount);
    }

    @Override
    public void addBonus(UUID playerId, double amount) {
        data().addBonus(playerId, amount);
    }

    @Override
    public boolean resetPlayer(UUID playerId) {
        return data().resetPlayer(playerId, ResetCause.API);
    }

    @Override
    public int resetAll() {
        return data().resetAll(ResetCause.API);
    }

    @Override
    public long getLastReset() {
        return data().getLastReset();
    }

    @Override
    public double calculateSaleValue(Material material, long previousTotal, int amount, double basePerUnit) {
        return config().computeSaleValue(material, previousTotal, amount, basePerUnit);
    }
}
