package me.docdrewskii.profitmultiplier.data;

import me.docdrewskii.profitmultiplier.ProfitMultiplier;
import me.docdrewskii.profitmultiplier.api.ResetCause;
import me.docdrewskii.profitmultiplier.api.events.PlayerDataResetEvent;
import me.docdrewskii.profitmultiplier.api.events.ServerDataResetEvent;
import me.docdrewskii.profitmultiplier.config.ConfigManager;
import me.docdrewskii.profitmultiplier.util.FoliaScheduler;
import me.docdrewskii.profitmultiplier.util.VersionHelper;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {

    private final ProfitMultiplier plugin;
    private final File file;

    private final Map<UUID, Map<Material, Long>> sold = new ConcurrentHashMap<>();
    private final Map<UUID, Double> bonusTotals = new ConcurrentHashMap<>();
    private final Map<UUID, Double> lastBonus = new ConcurrentHashMap<>();

    private volatile long lastReset = 0L;
    private volatile boolean dirty = false;

    public PlayerDataManager(ProfitMultiplier plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        sold.clear();
        bonusTotals.clear();
        if (!file.exists()) {
            lastReset = System.currentTimeMillis();
            save();
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        lastReset = yml.getLong("meta.last-reset", System.currentTimeMillis());

        ConfigurationSection players = yml.getConfigurationSection("players");
        if (players != null) {
            for (String uuidStr : players.getKeys(false)) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidStr);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                ConfigurationSection sec = players.getConfigurationSection(uuidStr);
                if (sec == null) continue;

                ConfigurationSection soldSec = sec.getConfigurationSection("sold");
                if (soldSec != null) {
                    Map<Material, Long> map = new ConcurrentHashMap<>();
                    for (String matName : soldSec.getKeys(false)) {
                        Material mat = VersionHelper.resolveMaterial(matName);
                        if (mat == null) continue;
                        map.put(mat, soldSec.getLong(matName));
                    }
                    if (!map.isEmpty()) sold.put(uuid, map);
                }

                double bonus = sec.getDouble("bonus", 0.0);
                if (bonus != 0.0) bonusTotals.put(uuid, bonus);
            }
        }
    }

    public long getSold(UUID uuid, Material mat) {
        Map<Material, Long> m = sold.get(uuid);
        if (m == null) return 0L;
        Long v = m.get(mat);
        return v == null ? 0L : v;
    }

    public long addSold(UUID uuid, Material mat, int amount) {
        Map<Material, Long> m = sold.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        long nv = (m.getOrDefault(mat, 0L)) + amount;
        m.put(mat, nv);
        dirty = true;
        return nv;
    }

    public void setSold(UUID uuid, Material mat, long amount) {
        Map<Material, Long> m = sold.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        if (amount <= 0) {
            m.remove(mat);
        } else {
            m.put(mat, amount);
        }
        dirty = true;
    }

    public long getTotalSold(UUID uuid) {
        Map<Material, Long> m = sold.get(uuid);
        if (m == null) return 0L;
        long total = 0L;
        for (long v : m.values()) total += v;
        return total;
    }

    public long getGroupSold(UUID uuid, java.util.Collection<Material> materials) {
        Map<Material, Long> m = sold.get(uuid);
        if (m == null) return 0L;
        long total = 0L;
        for (Material mat : materials) {
            Long v = m.get(mat);
            if (v != null) total += v;
        }
        return total;
    }

    public Map<Material, Long> getAll(UUID uuid) {
        Map<Material, Long> m = sold.get(uuid);
        return m == null ? Collections.emptyMap() : new HashMap<>(m);
    }

    public void addBonus(UUID uuid, double amount) {
        bonusTotals.merge(uuid, amount, Double::sum);
        lastBonus.put(uuid, amount);
        dirty = true;
    }

    public void setLastBonus(UUID uuid, double amount) {
        lastBonus.put(uuid, amount);
    }

    public double getBonusTotal(UUID uuid) {
        return bonusTotals.getOrDefault(uuid, 0.0);
    }

    public double getLastBonus(UUID uuid) {
        return lastBonus.getOrDefault(uuid, 0.0);
    }

    public boolean resetPlayer(UUID uuid) {
        return resetPlayer(uuid, ResetCause.UNKNOWN);
    }

    public boolean resetPlayer(UUID uuid, ResetCause cause) {
        boolean had = sold.remove(uuid) != null;
        had |= bonusTotals.remove(uuid) != null;
        lastBonus.remove(uuid);
        if (had) {
            dirty = true;
            save();
            fireSync(new PlayerDataResetEvent(uuid, cause));
        }
        return had;
    }

    public int resetAll() {
        return resetAll(ResetCause.UNKNOWN);
    }

    public int resetAll(ResetCause cause) {
        int n = sold.size();
        sold.clear();
        bonusTotals.clear();
        lastBonus.clear();
        lastReset = System.currentTimeMillis();
        dirty = true;
        save();
        fireSync(new ServerDataResetEvent(n, cause));
        return n;
    }

    private void fireSync(Event event) {
        FoliaScheduler.runGlobal(plugin, () -> Bukkit.getPluginManager().callEvent(event));
    }

    public long getLastReset() {
        return lastReset;
    }

    public void saveIfDirty() {
        if (dirty) save();
    }

    public synchronized void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("meta.last-reset", lastReset);
        for (Map.Entry<UUID, Map<Material, Long>> e : sold.entrySet()) {
            String base = "players." + e.getKey();
            for (Map.Entry<Material, Long> me : e.getValue().entrySet()) {
                yml.set(base + ".sold." + me.getKey().name(), me.getValue());
            }
        }
        for (Map.Entry<UUID, Double> e : bonusTotals.entrySet()) {
            yml.set("players." + e.getKey() + ".bonus", e.getValue());
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yml.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save data.yml: " + ex.getMessage());
        }
    }

    public void checkAutoReset() {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isAutoResetEnabled()) return;
        long interval = cfg.getAutoResetIntervalMillis();
        if (interval <= 0) return;

        long now = System.currentTimeMillis();
        if (lastReset <= 0) {
            lastReset = now;
            dirty = true;
            save();
            return;
        }
        if (now - lastReset >= interval) {
            int n = resetAll(ResetCause.AUTOMATIC);
            plugin.getLogger().info("Auto-reset triggered: cleared sold-totals for " + n + " players.");
        }
    }
}
