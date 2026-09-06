package me.docdrewskii.profitmultiplier.api;

import org.bukkit.Material;

import java.util.Map;
import java.util.UUID;

public interface ProfitMultiplierAPI {

    /**
     * Raw lifetime item count sold — still tracked and accurate, but as of the revenue-based
     * tier system this no longer drives any multiplier by itself. Use
     * {@link #getGroupRevenue(UUID, String)} / {@link #getItemRevenue(UUID, Material)} and
     * {@link #getGroupMultiplier(UUID, String)} for what actually determines pricing now.
     */
    long getSold(UUID playerId, Material material);

    long getTotalSold(UUID playerId);

    Map<Material, Long> getAllSold(UUID playerId);

    /**
     * @deprecated no longer reflects actual sale pricing — tiers are now driven by cumulative
     * revenue, not item count. Kept for backward compatibility only. Use
     * {@link #getGroupMultiplier(UUID, String)} (or {@link #getItemMultiplier(UUID, Material)}
     * for an item with no group) instead.
     */
    @Deprecated
    double getMultiplier(UUID playerId, Material material);

    /** @deprecated see {@link #getMultiplier(UUID, Material)}. */
    @Deprecated
    double getMultiplierAt(Material material, long soldCount);

    /** @deprecated see {@link #getMultiplier(UUID, Material)}. */
    @Deprecated
    long getActiveThreshold(UUID playerId, Material material);

    /** @deprecated see {@link #getMultiplier(UUID, Material)}. */
    @Deprecated
    long getNextThreshold(UUID playerId, Material material);

    /** @deprecated see {@link #getMultiplier(UUID, Material)}. */
    @Deprecated
    long getRemainingToNextThreshold(UUID playerId, Material material);

    /** Cumulative BASE (pre-multiplier) revenue earned toward a category's tier ladder. */
    double getGroupRevenue(UUID playerId, String groupName);

    /** Current multiplier for a category, based on {@link #getGroupRevenue(UUID, String)}. */
    double getGroupMultiplier(UUID playerId, String groupName);

    /** Cumulative BASE revenue earned toward a standalone item's own tier ladder (an item with no group). */
    double getItemRevenue(UUID playerId, Material material);

    /** Current multiplier for a standalone item (no group), based on {@link #getItemRevenue(UUID, Material)}. */
    double getItemMultiplier(UUID playerId, Material material);

    double getBonusTotal(UUID playerId);

    double getLastBonus(UUID playerId);

    long addSold(UUID playerId, Material material, int amount);

    void setSold(UUID playerId, Material material, long amount);

    void addBonus(UUID playerId, double amount);

    boolean resetPlayer(UUID playerId);

    int resetAll();

    long getLastReset();

    /**
     * @deprecated {@code previousTotal} here means item count, which no longer matches how a
     * real sale is priced (revenue-based tiers can't be queried without knowing whether the
     * material belongs to a group). Kept for backward compatibility only.
     */
    @Deprecated
    double calculateSaleValue(Material material, long previousTotal, int amount, double basePerUnit);
}
