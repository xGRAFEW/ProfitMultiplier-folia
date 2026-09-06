package me.docdrewskii.profitmultiplier.config;

import me.docdrewskii.profitmultiplier.ProfitMultiplier;
import me.docdrewskii.profitmultiplier.model.GroupStackMode;
import me.docdrewskii.profitmultiplier.model.ItemGroup;
import me.docdrewskii.profitmultiplier.model.MilestoneCommands;
import me.docdrewskii.profitmultiplier.model.MultiplierTier;
import me.docdrewskii.profitmultiplier.util.VersionHelper;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.permissions.Permissible;

import java.util.*;

public class ConfigManager {

    private final ProfitMultiplier plugin;

    private final Map<Material, List<MultiplierTier>> itemTiers = new HashMap<>();

    private final Map<Material, MilestoneCommands> itemMilestones = new HashMap<>();

    private final Map<Material, Double> itemPrices = new HashMap<>();

    private final Map<String, ItemGroup> groups = new LinkedHashMap<>();

    private final Map<Material, ItemGroup> materialGroup = new HashMap<>();

    private boolean defaultEnabled;
    private int defaultThreshold;
    private double defaultMultiplier;
    private List<MultiplierTier> defaultLadder;
    private final Set<Material> blacklist = new HashSet<>();

    private boolean thresholdScalingEnabled;
    private final Map<String, Double> thresholdScaleRules = new LinkedHashMap<>();

    private boolean autoResetEnabled;
    private long autoResetIntervalMillis;

    private boolean debug;

    public ConfigManager(ProfitMultiplier plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        mergeMissingDefaults();

        itemTiers.clear();
        itemMilestones.clear();
        itemPrices.clear();
        groups.clear();
        materialGroup.clear();
        blacklist.clear();

        ConfigurationSection itemsSection = plugin.getConfig().getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                Material mat = VersionHelper.resolveMaterial(key);
                if (mat == null) {
                    plugin.getLogger().warning("Unknown material in config items section: " + key);
                    continue;
                }
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                if (itemSection == null) continue;

                List<MultiplierTier> tiers = parseTiers(itemSection.getMapList("tiers"), "item " + key);
                tiers.sort(Comparator.comparingInt(MultiplierTier::getThreshold));
                if (!tiers.isEmpty()) {
                    itemTiers.put(mat, tiers);
                    MilestoneCommands milestones = parseMilestones(itemSection.getConfigurationSection("milestones"));
                    if (milestones != null) {
                        itemMilestones.put(mat, milestones);
                    }
                }

                if (itemSection.contains("price")) {
                    itemPrices.put(mat, itemSection.getDouble("price"));
                }
            }
        }

        ConfigurationSection groupsSection = plugin.getConfig().getConfigurationSection("groups");
        if (groupsSection != null) {
            for (String key : groupsSection.getKeys(false)) {
                ConfigurationSection groupSection = groupsSection.getConfigurationSection(key);
                if (groupSection == null) continue;

                Set<Material> members = new LinkedHashSet<>();
                for (String matName : groupSection.getStringList("materials")) {
                    Material mat = VersionHelper.resolveMaterial(matName);
                    if (mat == null) {
                        plugin.getLogger().warning("Unknown material '" + matName + "' in group '" + key + "'.");
                        continue;
                    }
                    members.add(mat);
                }

                List<MultiplierTier> tiers = parseTiers(groupSection.getMapList("tiers"), "group " + key);
                tiers.sort(Comparator.comparingInt(MultiplierTier::getThreshold));

                if (members.isEmpty() || tiers.isEmpty()) {
                    plugin.getLogger().warning("Group '" + key + "' has no materials or no tiers — skipped.");
                    continue;
                }

                String icon = groupSection.getString("icon", null);
                String displayName = groupSection.getString("display-name", null);
                GroupStackMode stackMode = GroupStackMode.fromString(
                        groupSection.getString("stack-mode", groupSection.getString("stacking", null)));
                String currency = groupSection.getString("currency", null);
                MilestoneCommands milestones = parseMilestones(groupSection.getConfigurationSection("milestones"));

                Map<Material, Double> prices = new HashMap<>();
                ConfigurationSection pricesSection = groupSection.getConfigurationSection("prices");
                if (pricesSection != null) {
                    for (String matName : pricesSection.getKeys(false)) {
                        Material mat = VersionHelper.resolveMaterial(matName);
                        if (mat == null) {
                            plugin.getLogger().warning("Unknown material '" + matName + "' in prices of group '" + key + "'.");
                            continue;
                        }
                        prices.put(mat, pricesSection.getDouble(matName));
                    }
                }

                ItemGroup group = new ItemGroup(key.toLowerCase(Locale.ROOT), members, tiers,
                        icon, displayName, stackMode, currency, milestones, prices);
                groups.put(group.getName(), group);
                for (Material mat : members) {
                    ItemGroup existing = materialGroup.putIfAbsent(mat, group);
                    if (existing != null && existing != group) {
                        plugin.getLogger().warning("Material '" + mat.name() + "' is listed in both group '"
                                + existing.getName() + "' and group '" + group.getName()
                                + "' — it will only count toward '" + existing.getName() + "'.");
                    }
                }
            }
        }

        ConfigurationSection def = plugin.getConfig().getConfigurationSection("default");
        if (def != null) {
            defaultEnabled = def.getBoolean("enabled", true);
            defaultThreshold = def.getInt("threshold", 1000);
            defaultMultiplier = def.getDouble("multiplier", 1.1);

            for (String key : def.getStringList("blacklist")) {
                Material mat = VersionHelper.resolveMaterial(key);
                if (mat != null) {
                    blacklist.add(mat);
                } else {
                    plugin.getLogger().warning("Unknown material in blacklist: " + key);
                }
            }
        } else {
            defaultEnabled = false;
        }
        defaultLadder = Collections.singletonList(new MultiplierTier(defaultThreshold, defaultMultiplier));

        thresholdScalingEnabled = false;
        thresholdScaleRules.clear();
        ConfigurationSection scaling = plugin.getConfig().getConfigurationSection("threshold-scaling");
        if (scaling != null && scaling.getBoolean("enabled", false)) {
            ConfigurationSection ranks = scaling.getConfigurationSection("ranks");
            if (ranks != null) {
                for (String key : ranks.getKeys(false)) {
                    ConfigurationSection rank = ranks.getConfigurationSection(key);
                    if (rank == null) continue;
                    String permission = rank.getString("permission");
                    double scale = rank.getDouble("scale", 1.0);
                    if (permission == null || permission.isEmpty()) {
                        plugin.getLogger().warning("threshold-scaling rank '" + key + "' has no permission — skipped.");
                        continue;
                    }
                    if (scale <= 0) {
                        plugin.getLogger().warning("threshold-scaling rank '" + key + "' has invalid scale " + scale + " — skipped.");
                        continue;
                    }
                    thresholdScaleRules.put(permission, scale);
                }
            }
            thresholdScalingEnabled = !thresholdScaleRules.isEmpty();
        }

        ConfigurationSection ar = plugin.getConfig().getConfigurationSection("auto-reset");
        if (ar != null) {
            autoResetEnabled = ar.getBoolean("enabled", false);
            autoResetIntervalMillis = parseInterval(ar.getString("interval", "WEEKLY"));
        } else {
            autoResetEnabled = false;
            autoResetIntervalMillis = 0L;
        }

        debug = plugin.getConfig().getBoolean("debug", false);
    }

    public static long scaledThreshold(int threshold, double scale) {
        if (scale == 1.0) return threshold;
        return Math.max(1L, (long) Math.ceil(threshold * scale));
    }

    public double getThresholdScale(Permissible player) {
        if (!thresholdScalingEnabled || player == null) return 1.0;
        double best = Double.NaN;
        for (Map.Entry<String, Double> rule : thresholdScaleRules.entrySet()) {
            if (player.hasPermission(rule.getKey())) {
                double scale = rule.getValue();
                if (Double.isNaN(best) || scale < best) best = scale;
            }
        }
        return Double.isNaN(best) ? 1.0 : best;
    }

    public double computeSaleValue(Material material, long prevTotal, int amount, double basePerUnit) {
        return computeSaleValue(material, prevTotal, amount, basePerUnit, 1.0);
    }

    public double computeSaleValue(Material material, long prevTotal, int amount, double basePerUnit, double scale) {
        long start = prevTotal + 1;
        long end = prevTotal + amount;
        double total = 0.0;
        long pos = start;
        while (pos <= end) {
            double m = multiplierAtCount(material, pos, scale);
            long next = nextThresholdAbove(material, pos, scale);
            long segEnd = (next == Long.MAX_VALUE) ? end : Math.min(end, next - 1);
            if (segEnd < pos) segEnd = pos;
            long count = segEnd - pos + 1;
            total += count * basePerUnit * m;
            pos = segEnd + 1;
        }
        return total;
    }

    public double multiplierAtCount(Material material, long count) {
        return multiplierAtCount(material, count, 1.0);
    }

    public double multiplierAtCount(Material material, long count, double scale) {
        List<MultiplierTier> tiers = itemTiers.get(material);
        if (tiers != null) {
            double best = 1.0;
            for (MultiplierTier t : tiers) {
                if (count >= scaledThreshold(t.getThreshold(), scale)) best = t.getMultiplier();
            }
            return best;
        }
        if (defaultEnabled && !blacklist.contains(material)
                && count >= scaledThreshold(defaultThreshold, scale)) {
            return defaultMultiplier;
        }
        return 1.0;
    }

    public long activeThreshold(Material material, long count) {
        return activeThreshold(material, count, 1.0);
    }

    public long activeThreshold(Material material, long count, double scale) {
        List<MultiplierTier> tiers = itemTiers.get(material);
        if (tiers != null) {
            long best = 0L;
            for (MultiplierTier t : tiers) {
                long threshold = scaledThreshold(t.getThreshold(), scale);
                if (count >= threshold) best = threshold;
            }
            return best;
        }
        long threshold = scaledThreshold(defaultThreshold, scale);
        if (defaultEnabled && !blacklist.contains(material) && count >= threshold) {
            return threshold;
        }
        return 0L;
    }

    public long nextThresholdAbove(Material material, long count) {
        return nextThresholdAbove(material, count, 1.0);
    }

    public long nextThresholdAbove(Material material, long count, double scale) {
        List<MultiplierTier> tiers = itemTiers.get(material);
        if (tiers != null) {
            for (MultiplierTier t : tiers) {
                long threshold = scaledThreshold(t.getThreshold(), scale);
                if (threshold > count) return threshold;
            }
            return Long.MAX_VALUE;
        }
        long threshold = scaledThreshold(defaultThreshold, scale);
        if (defaultEnabled && !blacklist.contains(material) && threshold > count) {
            return threshold;
        }
        return Long.MAX_VALUE;
    }

    public List<MultiplierTier> getLadderTiers(Material material) {
        List<MultiplierTier> tiers = itemTiers.get(material);
        if (tiers != null) return tiers;
        if (defaultEnabled && !blacklist.contains(material)) return defaultLadder;
        return null;
    }

    public boolean isDefaultLadder(List<MultiplierTier> tiers) {
        return tiers == defaultLadder;
    }

    public MilestoneCommands getItemMilestones(Material material) {
        return itemMilestones.get(material);
    }

    private MilestoneCommands parseMilestones(ConfigurationSection section) {
        if (section == null) return null;
        List<String> onTier = cleanCommands(section.getStringList("on-tier"));
        List<String> onMaxTier = cleanCommands(section.getStringList("on-max-tier"));
        if (onTier.isEmpty() && onMaxTier.isEmpty()) return null;
        return new MilestoneCommands(onTier, onMaxTier);
    }

    private List<String> cleanCommands(List<String> raw) {
        List<String> commands = new ArrayList<>();
        for (String command : raw) {
            if (command != null && !command.trim().isEmpty()) {
                commands.add(command.trim());
            }
        }
        return commands;
    }

    public boolean hasItemTiers(Material material) {
        return itemTiers.containsKey(material);
    }

    public ItemGroup getGroupFor(Material material) {
        return materialGroup.get(material);
    }

    /**
     * Admin-configured sell price for one unit of the material, or {@code null} if none is
     * set (meaning the item is not sellable through the built-in shop / price-aware menus).
     * Checked in order: the item's own group price, then a standalone per-item price.
     */
    public Double getPrice(Material material) {
        ItemGroup group = materialGroup.get(material);
        if (group != null) {
            Double groupPrice = group.getPrice(material);
            if (groupPrice != null) return groupPrice;
        }
        return itemPrices.get(material);
    }

    public boolean isSellable(Material material) {
        return getPrice(material) != null;
    }

    public ItemGroup getGroup(String name) {
        return name == null ? null : groups.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<ItemGroup> getGroups() {
        return groups.values();
    }

    public double groupMultiplierAtCount(ItemGroup group, long count) {
        return groupMultiplierAtCount(group, count, 1.0);
    }

    public double groupMultiplierAtCount(ItemGroup group, long count, double scale) {
        double best = 1.0;
        for (MultiplierTier t : group.getTiers()) {
            if (count >= scaledThreshold(t.getThreshold(), scale)) best = t.getMultiplier();
        }
        return best;
    }

    public long groupActiveThreshold(ItemGroup group, long count) {
        return groupActiveThreshold(group, count, 1.0);
    }

    public long groupActiveThreshold(ItemGroup group, long count, double scale) {
        long best = 0L;
        for (MultiplierTier t : group.getTiers()) {
            long threshold = scaledThreshold(t.getThreshold(), scale);
            if (count >= threshold) best = threshold;
        }
        return best;
    }

    public long groupNextThresholdAbove(ItemGroup group, long count) {
        return groupNextThresholdAbove(group, count, 1.0);
    }

    public long groupNextThresholdAbove(ItemGroup group, long count, double scale) {
        for (MultiplierTier t : group.getTiers()) {
            long threshold = scaledThreshold(t.getThreshold(), scale);
            if (threshold > count) return threshold;
        }
        return Long.MAX_VALUE;
    }

    public double computeUnifiedSaleValue(Material material, ItemGroup group, GroupStackMode mode,
                                          long prevItem, long prevGroup, int amount, double basePerUnit) {
        return computeUnifiedSaleValue(material, group, mode, prevItem, prevGroup, amount, basePerUnit, 1.0);
    }

    public double computeUnifiedSaleValue(Material material, ItemGroup group, GroupStackMode mode,
                                          long prevItem, long prevGroup, int amount, double basePerUnit,
                                          double scale) {
        double total = 0.0;
        int k = 0;
        while (k < amount) {
            long itemCount = prevItem + k + 1;
            long groupCount = prevGroup + k + 1;

            double materialMult = (mode == GroupStackMode.GROUP) ? 1.0 : multiplierAtCount(material, itemCount, scale);
            double groupMult = (mode == GroupStackMode.ITEM) ? 1.0 : groupMultiplierAtCount(group, groupCount, scale);

            double eff;
            switch (mode) {
                case ITEM:  eff = materialMult; break;
                case GROUP: eff = groupMult; break;
                default:    eff = materialMult * groupMult; break;
            }

            long remaining = amount - k;
            long itemSpan = (mode == GroupStackMode.GROUP) ? remaining
                    : spanTo(nextThresholdAbove(material, itemCount, scale), itemCount, remaining);
            long groupSpan = (mode == GroupStackMode.ITEM) ? remaining
                    : spanTo(groupNextThresholdAbove(group, groupCount, scale), groupCount, remaining);

            long span = Math.min(itemSpan, groupSpan);
            if (span < 1) span = 1;
            total += span * basePerUnit * eff;
            k += (int) span;
        }
        return total;
    }

    private long spanTo(long nextThreshold, long currentCount, long remaining) {
        if (nextThreshold == Long.MAX_VALUE) return remaining;
        long span = nextThreshold - currentCount;
        if (span < 1) span = 1;
        return Math.min(span, remaining);
    }

    public double computeGroupSaleValue(ItemGroup group, long prevTotal, int amount, double basePerUnit) {
        return computeGroupSaleValue(group, prevTotal, amount, basePerUnit, 1.0);
    }

    public double computeGroupSaleValue(ItemGroup group, long prevTotal, int amount, double basePerUnit, double scale) {
        long start = prevTotal + 1;
        long end = prevTotal + amount;
        double total = 0.0;
        long pos = start;
        while (pos <= end) {
            double m = groupMultiplierAtCount(group, pos, scale);
            long next = groupNextThresholdAbove(group, pos, scale);
            long segEnd = (next == Long.MAX_VALUE) ? end : Math.min(end, next - 1);
            if (segEnd < pos) segEnd = pos;
            long count = segEnd - pos + 1;
            total += count * basePerUnit * m;
            pos = segEnd + 1;
        }
        return total;
    }

    private List<MultiplierTier> parseTiers(List<Map<?, ?>> tierList, String label) {
        List<MultiplierTier> tiers = new ArrayList<>();
        for (Map<?, ?> tierMap : tierList) {
            Object thresholdObj = tierMap.get("threshold");
            Object multiplierObj = tierMap.get("multiplier");
            if (thresholdObj == null || multiplierObj == null) continue;
            try {
                int threshold = Integer.parseInt(thresholdObj.toString());
                double multiplier = Double.parseDouble(multiplierObj.toString());
                Object iconObj = tierMap.get("icon");
                String icon = iconObj == null ? null : iconObj.toString();
                tiers.add(new MultiplierTier(threshold, multiplier, icon, parseTierCommands(tierMap.get("commands"))));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid tier values for " + label);
            }
        }
        return tiers;
    }

    private List<String> parseTierCommands(Object raw) {
        if (!(raw instanceof List)) return null;
        List<String> commands = new ArrayList<>();
        for (Object entry : (List<?>) raw) {
            if (entry != null) {
                String command = entry.toString().trim();
                if (!command.isEmpty()) commands.add(command);
            }
        }
        return commands.isEmpty() ? null : commands;
    }

    private void mergeMissingDefaults() {
        java.io.InputStream in = plugin.getResource("config.yml");
        if (in == null) return;
        try {
            org.bukkit.configuration.file.YamlConfiguration defaults =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                            new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            boolean missing = false;
            for (String key : defaults.getKeys(true)) {
                if (!plugin.getConfig().contains(key)) {
                    missing = true;
                    break;
                }
            }
            if (missing) {
                plugin.getConfig().setDefaults(defaults);
                plugin.getConfig().options().copyDefaults(true);
                plugin.saveConfig();
                plugin.reloadConfig();
                plugin.getLogger().info("Updated config.yml with new default keys.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not merge config defaults: " + e.getMessage());
        } finally {
            try {
                in.close();
            } catch (java.io.IOException ignored) {
            }
        }
    }

    private long parseInterval(String s) {
        if (s == null) return 0L;
        s = s.trim();
        try {
            double hours = Double.parseDouble(s);
            return (long) (hours * 3600_000L);
        } catch (NumberFormatException ignored) {
        }
        switch (s.toUpperCase()) {
            case "HOURLY":  return 3600_000L;
            case "DAILY":   return 24L * 3600_000L;
            case "WEEKLY":  return 7L * 24L * 3600_000L;
            case "MONTHLY": return 30L * 24L * 3600_000L;
            default:
                plugin.getLogger().warning("Unknown auto-reset interval '" + s + "', defaulting to WEEKLY.");
                return 7L * 24L * 3600_000L;
        }
    }

    public boolean isAutoResetEnabled() {
        return autoResetEnabled;
    }

    public long getAutoResetIntervalMillis() {
        return autoResetIntervalMillis;
    }

    public boolean isDebug() {
        return debug;
    }
}
