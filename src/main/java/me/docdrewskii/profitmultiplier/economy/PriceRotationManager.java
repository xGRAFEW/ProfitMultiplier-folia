package me.docdrewskii.profitmultiplier.economy;

import me.docdrewskii.profitmultiplier.ProfitMultiplier;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional random/rotating price feature: on a schedule (either a fixed daily clock time, e.g.
 * "00:00", or a repeating interval, e.g. "6h" / "1d"), every material with a configured base
 * price gets an independently-randomized current price within +/- a configured percentage.
 * The rotated prices and the next-rotation timestamp are persisted so both survive a restart.
 */
public class PriceRotationManager {

    private static final Pattern TIME_OF_DAY = Pattern.compile("^([01]?\\d|2[0-3]):([0-5]\\d)$");
    private static final Pattern DURATION = Pattern.compile("^(\\d+)\\s*([smhd])?$", Pattern.CASE_INSENSITIVE);

    private final ProfitMultiplier plugin;
    private final File file;
    private final Random random = new Random();

    private boolean enabled;
    private double variancePercent;
    private String schedule;

    private final Map<Material, Double> currentPrices = new HashMap<>();
    private volatile long nextRotationMillis;

    public PriceRotationManager(ProfitMultiplier plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "prices.yml");
    }

    public void load() {
        enabled = plugin.getConfig().getBoolean("price-rotation.enabled", false);
        variancePercent = Math.max(0, plugin.getConfig().getDouble("price-rotation.variance-percent", 20));
        schedule = plugin.getConfig().getString("price-rotation.schedule", "6h");

        currentPrices.clear();
        nextRotationMillis = 0L;
        if (!enabled) return;

        if (file.exists()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            nextRotationMillis = yml.getLong("next-rotation", 0L);
            org.bukkit.configuration.ConfigurationSection sec = yml.getConfigurationSection("current");
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    Material mat = safeMaterial(key);
                    if (mat != null) currentPrices.put(mat, sec.getDouble(key));
                }
            }
        }

        if (nextRotationMillis <= 0L) {
            nextRotationMillis = computeNext(System.currentTimeMillis());
            save();
        }
    }

    /** Called periodically (see FoliaScheduler.runGlobalTimer registration in onEnable). */
    public void tick() {
        if (!enabled) return;
        if (System.currentTimeMillis() < nextRotationMillis) return;
        rerollAll();
        nextRotationMillis = computeNext(System.currentTimeMillis());
        save();
        plugin.getInventoryPriceLoreManager().refreshAllOnline();
    }

    private void rerollAll() {
        for (Material material : plugin.getConfigManager().getPricedMaterials()) {
            Double base = plugin.getConfigManager().getPrice(material);
            if (base == null || base <= 0) continue;
            double swing = (random.nextDouble() * 2.0 - 1.0) * (variancePercent / 100.0);
            double rotated = Math.max(0.01, base * (1.0 + swing));
            currentPrices.put(material, rotated);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * The price to actually charge/display for this material right now — the rotated price
     * once at least one rotation has happened, otherwise the plain configured base price.
     * Returns {@code null} if the material has no configured price at all (not sellable).
     */
    public Double getCurrentPrice(Material material) {
        Double base = plugin.getConfigManager().getPrice(material);
        if (base == null) return null;
        if (!enabled) return base;
        Double rotated = currentPrices.get(material);
        return rotated != null ? rotated : base;
    }

    public long getNextRotationMillis() {
        return nextRotationMillis;
    }

    /** Formats the time left until the next rotation as e.g. "1d 4h 12m", "45m", or "now". */
    public String formatCountdown() {
        if (!enabled) return "N/A";
        long remaining = nextRotationMillis - System.currentTimeMillis();
        if (remaining <= 0) return "now";

        long totalMinutes = remaining / 60000L;
        long days = totalMinutes / (60 * 24);
        long hours = (totalMinutes / 60) % 24;
        long minutes = totalMinutes % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (days > 0 || hours > 0) sb.append(hours).append("h ");
        sb.append(minutes).append("m");
        return sb.toString().trim();
    }

    private long computeNext(long fromMillis) {
        Matcher timeMatch = TIME_OF_DAY.matcher(schedule.trim());
        if (timeMatch.matches()) {
            int hour = Integer.parseInt(timeMatch.group(1));
            int minute = Integer.parseInt(timeMatch.group(2));
            ZoneId zone = ZoneId.systemDefault();
            LocalDateTime now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(fromMillis), zone);
            LocalDateTime candidate = LocalDateTime.of(now.toLocalDate(), LocalTime.of(hour, minute));
            if (!candidate.isAfter(now)) {
                candidate = LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.of(hour, minute));
            }
            return candidate.atZone(zone).toInstant().toEpochMilli();
        }

        long intervalMillis = parseDurationMillis(schedule);
        if (intervalMillis <= 0) intervalMillis = 6L * 60L * 60L * 1000L; // fallback: 6h
        return fromMillis + intervalMillis;
    }

    private long parseDurationMillis(String raw) {
        if (raw == null) return -1;
        Matcher m = DURATION.matcher(raw.trim());
        if (!m.matches()) return -1;
        long value = Long.parseLong(m.group(1));
        String unit = m.group(2) == null ? "h" : m.group(2).toLowerCase();
        switch (unit) {
            case "s": return value * 1000L;
            case "m": return value * 60_000L;
            case "d": return value * 24L * 60L * 60L * 1000L;
            case "h":
            default:  return value * 60L * 60L * 1000L;
        }
    }

    private Material safeMaterial(String name) {
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public synchronized void save() {
        if (!enabled) return;
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("next-rotation", nextRotationMillis);
        for (Map.Entry<Material, Double> e : currentPrices.entrySet()) {
            yml.set("current." + e.getKey().name(), e.getValue());
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save prices.yml: " + ex.getMessage());
        }
    }
}
