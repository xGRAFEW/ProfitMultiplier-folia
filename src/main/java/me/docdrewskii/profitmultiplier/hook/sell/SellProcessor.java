package me.docdrewskii.profitmultiplier.hook.sell;

import me.docdrewskii.profitmultiplier.ProfitMultiplier;
import me.docdrewskii.profitmultiplier.api.events.MultiplierApplyEvent;
import me.docdrewskii.profitmultiplier.api.events.ThresholdReachedEvent;
import me.docdrewskii.profitmultiplier.config.ConfigManager;
import me.docdrewskii.profitmultiplier.currency.Currency;
import me.docdrewskii.profitmultiplier.data.PlayerDataManager;
import me.docdrewskii.profitmultiplier.model.GroupStackMode;
import me.docdrewskii.profitmultiplier.model.ItemGroup;
import me.docdrewskii.profitmultiplier.model.MultiplierTier;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.List;
import java.util.UUID;

public class SellProcessor {

    private static final DecimalFormat MULT_FORMAT = new DecimalFormat("0.##");

    private final ProfitMultiplier plugin;

    public SellProcessor(ProfitMultiplier plugin) {
        this.plugin = plugin;
    }

    public double process(Player player, Material material, int amount, double originalPrice) {
        if (player == null || material == null || amount <= 0) return originalPrice;

        double finalPrice = originalPrice;
        if (originalPrice > 0) {
            double computed = computeBoostedPrice(player, material, amount, originalPrice);
            if (computed > originalPrice) {
                MultiplierApplyEvent applyEvent = new MultiplierApplyEvent(
                        player, material, amount, plugin.getDataManager().getSold(player.getUniqueId(), material),
                        originalPrice, computed);
                plugin.getServer().getPluginManager().callEvent(applyEvent);
                if (!applyEvent.isCancelled() && applyEvent.getBoostedPrice() > originalPrice) {
                    finalPrice = applyEvent.getBoostedPrice();
                }
            }
        }

        recordSale(player, material, amount, originalPrice, finalPrice);
        return finalPrice;
    }

    public double quoteBoostedPrice(Player player, Material material, int amount, double originalPrice) {
        if (player == null || material == null || amount <= 0 || originalPrice <= 0) return originalPrice;
        return computeBoostedPrice(player, material, amount, originalPrice);
    }

    /**
     * Tiers are now driven by cumulative BASE revenue earned (money), not item count — a
     * category (or a standalone item's own ladder) levels up once its players have earned
     * enough from selling it, never a single item within a category on its own.
     */
    private double computeBoostedPrice(Player player, Material material, int amount, double originalPrice) {
        ConfigManager cfg = plugin.getConfigManager();
        PlayerDataManager pdm = plugin.getDataManager();
        UUID uuid = player.getUniqueId();

        ItemGroup group = cfg.getGroupFor(material);
        double basePerUnit = originalPrice / amount;
        double scale = cfg.getThresholdScale(player);

        if (group != null) {
            double prevItemRevenue = pdm.getItemRevenue(uuid, material);
            double prevGroupRevenue = pdm.getGroupRevenue(uuid, group.getName());
            return cfg.computeUnifiedRevenueSaleValue(material, group, group.getStackMode(),
                    prevItemRevenue, prevGroupRevenue, amount, basePerUnit, scale);
        }
        List<MultiplierTier> tiers = cfg.getLadderTiers(material);
        double prevRevenue = pdm.getItemRevenue(uuid, material);
        return cfg.computeTieredRevenueSaleValue(tiers, prevRevenue, amount, basePerUnit, scale);
    }

    public void recordSale(Player player, Material material, int amount, double originalPrice, double finalPrice) {
        if (player == null || material == null || amount <= 0) return;

        ConfigManager cfg = plugin.getConfigManager();
        PlayerDataManager pdm = plugin.getDataManager();
        UUID uuid = player.getUniqueId();

        ItemGroup group = cfg.getGroupFor(material);
        long prevItemCount = pdm.getSold(uuid, material);
        long prevGroupCount = group != null ? pdm.getGroupSold(uuid, group.getMaterials()) : 0L;

        double bonus = finalPrice - originalPrice;
        if (bonus > 0 && originalPrice > 0) {
            Currency currency = plugin.getCurrencyManager().get(group != null ? group.getCurrency() : null);
            pdm.addBonus(uuid, bonus);

            if (cfg.isDebug()) {
                plugin.getLogger().info(String.format(
                        "[Debug] %s sold %dx %s%s: base=%.2f -> final=%.2f (%.3fx)",
                        player.getName(), amount, material.name(),
                        group != null ? " [" + group.getName() + "/" + group.getStackMode() + "]" : "",
                        originalPrice, finalPrice, finalPrice / originalPrice));
            }

            long totalForMsg = group != null ? prevGroupCount + amount : prevItemCount + amount;
            plugin.getLang().send(player, "multiplier-applied",
                    "{amount}", String.valueOf(amount),
                    "{item}", formatName(material.name()),
                    "{base}", currency.format(originalPrice),
                    "{final}", currency.format(finalPrice),
                    "{bonus}", currency.format(bonus),
                    "{currency}", currency.getSymbol(),
                    "{multiplier}", MULT_FORMAT.format(finalPrice / originalPrice),
                    "{total}", String.valueOf(totalForMsg));
        } else {
            pdm.setLastBonus(uuid, 0.0);
        }

        announceThresholds(player, material, amount, group, originalPrice);

        pdm.addSold(uuid, material, amount);
        if (group != null) {
            pdm.addGroupRevenue(uuid, group.getName(), originalPrice);
        } else {
            pdm.addItemRevenue(uuid, material, originalPrice);
        }
    }

    private void announceThresholds(Player player, Material material, int amount, ItemGroup group, double originalPrice) {
        ConfigManager cfg = plugin.getConfigManager();
        PlayerDataManager pdm = plugin.getDataManager();
        UUID uuid = player.getUniqueId();
        double scale = cfg.getThresholdScale(player);

        if (group != null && group.getStackMode() != GroupStackMode.ITEM) {
            double prevGroupRevenue = pdm.getGroupRevenue(uuid, group.getName());
            double newGroupRevenue = prevGroupRevenue + originalPrice;
            double oldG = cfg.revenueMultiplierAt(group.getTiers(), prevGroupRevenue, scale);
            double newG = cfg.revenueMultiplierAt(group.getTiers(), newGroupRevenue, scale);
            if (newG > oldG) {
                double threshold = cfg.revenueActiveThreshold(group.getTiers(), newGroupRevenue, scale);
                plugin.getServer().getPluginManager().callEvent(new ThresholdReachedEvent(
                        player, material, newGroupRevenue, oldG, newG, threshold));
                Currency currency = plugin.getCurrencyManager().get(group.getCurrency());
                plugin.getLang().send(player, "group-threshold-reached",
                        "{group}", formatName(group.getDisplayName() != null ? group.getDisplayName() : group.getName()),
                        "{total}", currency.format(newGroupRevenue),
                        "{multiplier}", MULT_FORMAT.format(newG),
                        "{threshold}", currency.format(threshold));
            }
            plugin.getMilestoneManager().handleCrossings(
                    player, material, group, group.getTiers(), prevGroupRevenue, newGroupRevenue, scale);
        }

        boolean itemLadderActive = (group == null) || group.getStackMode() != GroupStackMode.GROUP;
        if (itemLadderActive) {
            List<MultiplierTier> tiers = cfg.getLadderTiers(material);
            double prevItemRevenue = pdm.getItemRevenue(uuid, material);
            double newItemRevenue = prevItemRevenue + originalPrice;
            double oldI = cfg.revenueMultiplierAt(tiers, prevItemRevenue, scale);
            double newI = cfg.revenueMultiplierAt(tiers, newItemRevenue, scale);
            if (newI > oldI) {
                double threshold = cfg.revenueActiveThreshold(tiers, newItemRevenue, scale);
                if (group == null) {
                    plugin.getServer().getPluginManager().callEvent(new ThresholdReachedEvent(
                            player, material, newItemRevenue, oldI, newI, threshold));
                }
                Currency currency = plugin.getCurrencyManager().getDefault();
                plugin.getLang().send(player, "threshold-reached",
                        "{item}", formatName(material.name()),
                        "{total}", currency.format(newItemRevenue),
                        "{multiplier}", MULT_FORMAT.format(newI),
                        "{threshold}", currency.format(threshold));
            }
            plugin.getMilestoneManager().handleCrossings(
                    player, material, null, tiers, prevItemRevenue, newItemRevenue, scale);
        }
    }

    private String formatName(String raw) {
        String name = raw.replace('_', ' ').replace('-', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1).toLowerCase())
                  .append(' ');
            }
        }
        return sb.toString().trim();
    }
}
