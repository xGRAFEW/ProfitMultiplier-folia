package me.docdrewskii.profitmultiplier.milestone;

import me.docdrewskii.profitmultiplier.ProfitMultiplier;
import me.docdrewskii.profitmultiplier.config.ConfigManager;
import me.docdrewskii.profitmultiplier.model.ItemGroup;
import me.docdrewskii.profitmultiplier.model.MilestoneCommands;
import me.docdrewskii.profitmultiplier.model.MultiplierTier;
import me.docdrewskii.profitmultiplier.util.FoliaScheduler;
import me.docdrewskii.profitmultiplier.util.NumberUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MilestoneManager {

    private final ProfitMultiplier plugin;

    private final List<String> onTier = new ArrayList<>();
    private final List<String> onMaxTier = new ArrayList<>();
    private boolean includeDefaultLadder;
    private DiscordWebhook discord;

    public MilestoneManager(ProfitMultiplier plugin) {
        this.plugin = plugin;
    }

    public void load() {
        onTier.clear();
        onMaxTier.clear();
        includeDefaultLadder = false;
        discord = null;

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("milestones");
        if (section == null) return;

        includeDefaultLadder = section.getBoolean("include-default-ladder", false);
        ConfigurationSection commands = section.getConfigurationSection("commands");
        if (commands != null) {
            addCommands(onTier, commands.getStringList("on-tier"));
            addCommands(onMaxTier, commands.getStringList("on-max-tier"));
        }

        discord = DiscordWebhook.parse(plugin, section.getConfigurationSection("discord"));
    }

    private void addCommands(List<String> target, List<String> raw) {
        for (String command : raw) {
            if (command != null && !command.trim().isEmpty()) {
                target.add(command.trim());
            }
        }
    }

    public void handleCrossings(Player player, Material material, ItemGroup group,
                                List<MultiplierTier> tiers, long prev, long now, double scale) {
        if (tiers == null || tiers.isEmpty()) return;
        if (!includeDefaultLadder && plugin.getConfigManager().isDefaultLadder(tiers)) return;
        for (int i = 0; i < tiers.size(); i++) {
            MultiplierTier tier = tiers.get(i);
            long threshold = ConfigManager.scaledThreshold(tier.getThreshold(), scale);
            if (threshold > prev && threshold <= now) {
                fire(player, material, group, tier, i + 1, tiers.size(), threshold, now);
            }
        }
    }

    private void fire(Player player, Material material, ItemGroup group, MultiplierTier tier,
                      int tierNumber, int tierCount, long threshold, long total) {
        boolean max = tierNumber == tierCount;
        Map<String, String> placeholders = buildPlaceholders(
                player, material, group, tier, tierNumber, tierCount, threshold, total);

        runCommands(tier.getCommands(), placeholders);

        MilestoneCommands scoped = group != null ? group.getMilestones()
                : plugin.getConfigManager().getItemMilestones(material);
        if (scoped != null) {
            runCommands(scoped.getOnTier(), placeholders);
            if (max) {
                runCommands(scoped.getOnMaxTier(), placeholders);
            }
        }

        runCommands(onTier, placeholders);
        if (max) {
            runCommands(onMaxTier, placeholders);
        }

        if (discord != null && (max || !discord.isMaxTierOnly())) {
            discord.send(placeholders);
        }
    }

    private Map<String, String> buildPlaceholders(Player player, Material material, ItemGroup group,
                                                  MultiplierTier tier, int tierNumber, int tierCount,
                                                  long threshold, long total) {
        String item = friendly(material.name());
        String groupName = group == null ? ""
                : (group.getDisplayName() != null ? group.getDisplayName() : friendly(group.getName()));

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{player}", player.getName());
        placeholders.put("{uuid}", player.getUniqueId().toString());
        placeholders.put("{item}", item);
        placeholders.put("{item_raw}", material.name());
        placeholders.put("{group}", groupName);
        placeholders.put("{name}", group != null ? groupName : item);
        placeholders.put("{ladder}", group != null ? "group" : "item");
        placeholders.put("{tier}", String.valueOf(tierNumber));
        placeholders.put("{tiers}", String.valueOf(tierCount));
        placeholders.put("{threshold}", String.valueOf(threshold));
        placeholders.put("{threshold_formatted}", NumberUtil.commas(threshold));
        placeholders.put("{total}", String.valueOf(total));
        placeholders.put("{total_formatted}", NumberUtil.commas(total));
        placeholders.put("{multiplier}", NumberUtil.multiplier(tier.getMultiplier()));
        return placeholders;
    }

    private void runCommands(List<String> commands, Map<String, String> placeholders) {
        for (String command : commands) {
            String resolved = apply(command, placeholders);
            if (resolved.startsWith("/")) {
                resolved = resolved.substring(1);
            }
            String finalCommand = resolved;
            FoliaScheduler.runGlobal(plugin, () -> {
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
                } catch (Throwable t) {
                    plugin.getLogger().warning("Milestone command failed: '" + finalCommand + "': " + t.getMessage());
                }
            });
        }
    }

    static String apply(String text, Map<String, String> placeholders) {
        if (text == null || text.isEmpty()) return text;
        String out = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            out = out.replace(entry.getKey(), entry.getValue());
        }
        return out;
    }

    private String friendly(String raw) {
        String name = raw.replace('_', ' ').toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }
}
