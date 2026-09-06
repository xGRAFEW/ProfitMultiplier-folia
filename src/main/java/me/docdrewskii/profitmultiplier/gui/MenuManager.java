package me.docdrewskii.profitmultiplier.gui;

import me.docdrewskii.profitmultiplier.ProfitMultiplier;
import me.docdrewskii.profitmultiplier.config.ConfigManager;
import me.docdrewskii.profitmultiplier.data.PlayerDataManager;
import me.docdrewskii.profitmultiplier.gui.item.ItemResolver;
import me.docdrewskii.profitmultiplier.model.ItemGroup;
import me.docdrewskii.profitmultiplier.model.MultiplierTier;
import me.docdrewskii.profitmultiplier.util.FoliaScheduler;
import me.docdrewskii.profitmultiplier.util.NumberUtil;
import me.docdrewskii.profitmultiplier.util.TextUtil;
import me.docdrewskii.profitmultiplier.util.VersionHelper;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MenuManager {

    private final ProfitMultiplier plugin;
    private final ActionExecutor actionExecutor;
    private final Map<String, Menu> menus = new LinkedHashMap<>();

    public MenuManager(ProfitMultiplier plugin) {
        this.plugin = plugin;
        this.actionExecutor = new ActionExecutor(plugin);
    }

    public ActionExecutor getActionExecutor() {
        return actionExecutor;
    }

    public void loadAll() {
        menus.clear();
        File folder = new File(plugin.getDataFolder(), "menus");
        if (!folder.exists()) {
            folder.mkdirs();
        }

        saveDefaultMenus(folder);

        File[] files = folder.listFiles((dir, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            try {
                Menu menu = loadMenu(file);
                if (menu != null) {
                    menus.put(menu.getName(), menu);
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to load menu '" + file.getName() + "': " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + menus.size() + " menu(s): " + menus.keySet());
    }

    private void saveDefaultMenus(File folder) {
        boolean scanned = false;
        try {
            java.net.URL location = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
            try (java.util.jar.JarFile jar = new java.util.jar.JarFile(new File(location.toURI()))) {
                java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (name.startsWith("menus/") && !name.endsWith("/")
                            && name.toLowerCase(Locale.ROOT).endsWith(".yml")) {
                        scanned = true;
                        if (!new File(plugin.getDataFolder(), name).exists()) {
                            plugin.saveResource(name, false);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // Could not read the jar (exploded classpath in dev) — fall back to known names.
        }
        if (!scanned) {
            for (String name : new String[]{"sellmulti.yml", "groups.yml"}) {
                if (!new File(folder, name).exists()) {
                    try {
                        plugin.saveResource("menus/" + name, false);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
    }

    private Menu loadMenu(File file) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        String name = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);

        String title = yml.getString("title", "&8" + name);
        int size = resolveSize(yml);
        boolean update = yml.getBoolean("update", false);
        String openPermission = firstNonNull(
                yml.getString("open-permission", null),
                yml.getString("permission", null));

        String statusUnlocked = yml.getString("status-unlocked", "&aUNLOCKED");
        String statusLocked = yml.getString("status-locked", "&cLOCKED");

        ConfigurationSection bar = yml.getConfigurationSection("progress-bar");
        int barLength = bar != null ? bar.getInt("length", 20) : 20;
        String barSymbol = bar != null ? bar.getString("symbol", "▌") : "▌";
        String barComplete = bar != null ? bar.getString("complete", "&a") : "&a";
        String barIncomplete = bar != null ? bar.getString("incomplete", "&7") : "&7";

        MenuItem filler = null;
        ConfigurationSection fillerSec = yml.getConfigurationSection("filler");
        if (fillerSec != null && fillerSec.getBoolean("enabled", true)) {
            filler = parseItem("filler", fillerSec, true);
        }

        List<MenuItem> items = new ArrayList<>();
        ConfigurationSection itemsSec = yml.getConfigurationSection("items");
        if (itemsSec != null) {
            for (String key : itemsSec.getKeys(false)) {
                ConfigurationSection sec = itemsSec.getConfigurationSection(key);
                if (sec == null) continue;
                MenuItem item = parseItem(key, sec, false);
                if (item.getSlots().isEmpty()) {
                    plugin.getLogger().warning("Menu '" + name + "' item '" + key + "' has no slot(s) — skipped.");
                    continue;
                }
                items.add(item);
            }
        }

        List<Integer> contentSlots = readSlotList(yml.getList("content-slots", new ArrayList<>()));
        String contentSource = yml.getString("content-source", null);
        MenuItem contentTemplate = null;
        ConfigurationSection contentSec = yml.getConfigurationSection("content");
        if (contentSec != null) {
            contentTemplate = parseItem("content", contentSec, false);
        }
        if (!contentSlots.isEmpty() && (contentTemplate == null || contentSource == null)) {
            plugin.getLogger().warning("Menu '" + name + "' has content-slots but is missing a "
                    + "'content:' template or 'content-source:' — pagination disabled.");
        }

        return new Menu(name, title, size, filler, items, openPermission, update,
                statusUnlocked, statusLocked, barLength, barSymbol, barComplete, barIncomplete,
                contentSlots, contentTemplate, contentSource);
    }

    private int resolveSize(YamlConfiguration yml) {
        int size;
        if (yml.contains("size")) {
            size = yml.getInt("size", 27);
        } else if (yml.contains("rows")) {
            size = yml.getInt("rows", 3) * 9;
        } else {
            size = 27;
        }
        if (size < 9) size = 9;
        if (size > 54) size = 54;
        if (size % 9 != 0) size = ((size / 9) + 1) * 9;
        return Math.min(size, 54);
    }

    private MenuItem parseItem(String id, ConfigurationSection sec, boolean isFiller) {
        String icon = firstNonNull(
                sec.getString("material", null),
                sec.getString("item", null),
                sec.getString("icon", null),
                "STONE");

        List<Integer> slots = isFiller ? new ArrayList<>() : readSlots(sec);
        int amount = clamp(sec.getInt("amount", 1), 1, 64);
        String name = sec.getString("name", null);
        List<String> lore = sec.getStringList("lore");
        int cmd = sec.getInt("custom-model-data", sec.getInt("model-data", -1));
        boolean glow = sec.getBoolean("glow", false);
        List<String> flags = sec.getStringList("item-flags");
        String viewPermission = sec.getString("view-permission", null);
        String group = sec.getString("group", null);
        long tier = sec.contains("tier") ? sec.getLong("tier", -1L) : -1L;
        boolean hideOnFirstPage = sec.getBoolean("hide-on-first-page", false);
        boolean hideOnLastPage = sec.getBoolean("hide-on-last-page", false);

        Map<ClickActionType, List<MenuAction>> actions =
                isFiller ? MenuItem.emptyActions() : parseActions(sec);

        return new MenuItem(id, icon, slots, amount, name, lore, cmd, glow, flags,
                viewPermission, group, tier, hideOnFirstPage, hideOnLastPage, actions);
    }

    private List<Integer> readSlots(ConfigurationSection sec) {
        List<Integer> slots = new ArrayList<>();
        if (sec.contains("slot")) {
            slots.add(sec.getInt("slot"));
        }
        if (sec.contains("slots")) {
            slots.addAll(readSlotList(sec.getList("slots", new ArrayList<>())));
        }
        return slots;
    }

    private List<Integer> readSlotList(List<?> rawList) {
        List<Integer> slots = new ArrayList<>();
        if (rawList == null) return slots;
        for (Object raw : rawList) {
            String s = String.valueOf(raw).trim();
            int dash = s.indexOf('-');
            if (dash > 0) {
                try {
                    int from = Integer.parseInt(s.substring(0, dash).trim());
                    int to = Integer.parseInt(s.substring(dash + 1).trim());
                    for (int i = Math.min(from, to); i <= Math.max(from, to); i++) slots.add(i);
                } catch (NumberFormatException ignored) {
                }
            } else {
                try {
                    slots.add(Integer.parseInt(s));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return slots;
    }

    private Map<ClickActionType, List<MenuAction>> parseActions(ConfigurationSection itemSec) {
        Map<ClickActionType, List<MenuAction>> map = MenuItem.emptyActions();
        if (itemSec == null) return map;

        if (itemSec.isList("actions")) {
            addActions(map, ClickActionType.ANY, itemSec.getStringList("actions"));
            return map;
        }
        ConfigurationSection actSec = itemSec.getConfigurationSection("actions");
        if (actSec == null) return map;
        for (String key : actSec.getKeys(false)) {
            ClickActionType type = ClickActionType.fromKey(key);
            if (type == null) {
                plugin.getLogger().warning("Unknown click type '" + key + "' in menu actions.");
                continue;
            }
            addActions(map, type, actSec.getStringList(key));
        }
        return map;
    }

    private void addActions(Map<ClickActionType, List<MenuAction>> map, ClickActionType type, List<String> lines) {
        List<MenuAction> parsed = map.computeIfAbsent(type, k -> new ArrayList<>());
        for (String line : lines) {
            MenuAction action = MenuAction.parse(line);
            if (action != null) parsed.add(action);
        }
    }

    public boolean open(Player player, String menuName) {
        Menu menu = getMenu(menuName);
        if (menu == null) {
            plugin.getLang().send(player, "menu-unknown", "{menu}", menuName);
            return false;
        }
        if (menu.getOpenPermission() != null && !player.hasPermission(menu.getOpenPermission())) {
            plugin.getLang().send(player, "no-permission");
            return false;
        }

        Map<String, String> titleTokens = new HashMap<>();
        titleTokens.put("player", player.getName());
        String title = TextUtil.render(player, menu.getTitle(), titleTokens);
        if (!VersionHelper.IS_MODERN && title.length() > 32) {
            title = title.substring(0, 32);
        }

        MenuHolder holder = new MenuHolder(menu, player);
        Inventory inv = Bukkit.createInventory(holder, menu.getSize(), title);
        holder.setInventory(inv);
        renderContents(player, menu, inv);
        player.openInventory(inv);
        return true;
    }

    public void refresh(Player player) {
        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
        if (holder instanceof MenuHolder) {
            MenuHolder mh = (MenuHolder) holder;
            renderContents(player, mh.getMenu(), mh.getInventory());
            player.updateInventory();
        }
    }

    public void nextPage(Player player) {
        changePage(player, +1, -1);
    }

    public void previousPage(Player player) {
        changePage(player, -1, -1);
    }

    public void gotoPage(Player player, int oneBasedPage) {
        changePage(player, 0, oneBasedPage - 1);
    }

    private void changePage(Player player, int delta, int absoluteZeroBased) {
        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
        if (!(holder instanceof MenuHolder)) return;
        MenuHolder mh = (MenuHolder) holder;
        int target = absoluteZeroBased >= 0 ? absoluteZeroBased : mh.getPage() + delta;
        mh.setPage(Math.max(0, target));

        renderContents(player, mh.getMenu(), mh.getInventory());
        player.updateInventory();
    }

    public void refreshOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            FoliaScheduler.runForPlayer(plugin, player, () -> {
                InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
                if (holder instanceof MenuHolder) {
                    MenuHolder mh = (MenuHolder) holder;
                    if (mh.getMenu().isUpdate() && mh.getInventory() != null) {
                        renderContents(player, mh.getMenu(), mh.getInventory());
                        player.updateInventory();
                    }
                }
            });
        }
    }

    private void renderContents(Player player, Menu menu, Inventory inv) {
        inv.clear();
        MenuHolder holder = (inv.getHolder() instanceof MenuHolder) ? (MenuHolder) inv.getHolder() : null;
        if (holder != null) holder.clearClicks();

        List<Map<String, String>> entries = menu.isPaginated()
                ? buildDynamicEntries(player, menu)
                : Collections.<Map<String, String>>emptyList();
        int perPage = menu.getContentSlots().size();
        int totalPages = 1;
        int page = 0;
        if (menu.isPaginated() && perPage > 0) {
            totalPages = Math.max(1, (entries.size() + perPage - 1) / perPage);
            page = holder != null ? holder.getPage() : 0;
            if (page > totalPages - 1) page = totalPages - 1;
            if (page < 0) page = 0;
            if (holder != null) holder.setPage(page);
        }

        Map<String, String> pageTokens = new HashMap<>();
        pageTokens.put("page", String.valueOf(page + 1));
        pageTokens.put("total_pages", String.valueOf(totalPages));
        pageTokens.put("next_page", String.valueOf(Math.min(totalPages, page + 2)));
        pageTokens.put("previous_page", String.valueOf(Math.max(1, page)));

        boolean firstPage = page == 0;
        boolean lastPage = page >= totalPages - 1;

        for (MenuItem item : menu.getItems()) {
            if (item.getViewPermission() != null && !player.hasPermission(item.getViewPermission())) continue;
            if (item.isHideOnFirstPage() && firstPage) continue;
            if (item.isHideOnLastPage() && lastPage) continue;

            Map<String, String> tokens = computeTokens(player, menu, item);
            tokens.putAll(pageTokens);
            ItemStack stack = buildStack(player, item, tokens);
            for (int slot : item.getSlots()) {
                if (slot >= 0 && slot < menu.getSize()) {
                    inv.setItem(slot, stack.clone());
                    if (holder != null && item.hasAnyActions()) holder.putClick(slot, item, tokens);
                }
            }
        }

        if (menu.isPaginated() && perPage > 0) {
            MenuItem template = menu.getContentTemplate();
            int start = page * perPage;
            for (int i = 0; i < perPage; i++) {
                int idx = start + i;
                if (idx >= entries.size()) break;
                int slot = menu.getContentSlots().get(i);
                if (slot < 0 || slot >= menu.getSize()) continue;

                Map<String, String> tokens = new HashMap<>(entries.get(idx));
                tokens.putAll(pageTokens);
                ItemStack stack = buildStack(player, template, tokens);
                inv.setItem(slot, stack);
                if (holder != null && template.hasAnyActions()) holder.putClick(slot, template, tokens);
            }
        }

        MenuItem filler = menu.getFiller();
        if (filler != null) {
            Map<String, String> tokens = new HashMap<>();
            tokens.put("player", player.getName());
            tokens.putAll(pageTokens);
            ItemStack stack = buildStack(player, filler, tokens);
            for (int i = 0; i < menu.getSize(); i++) {
                if (inv.getItem(i) == null) {
                    inv.setItem(i, stack.clone());
                }
            }
        }
    }

    private ItemStack buildStack(Player player, MenuItem item, Map<String, String> tokens) {
        String iconResolved = TextUtil.papi(player, TextUtil.tokens(item.getIcon(), tokens));
        ItemStack stack = ItemResolver.resolve(iconResolved);
        if (item.getAmount() > 1) {
            stack.setAmount(item.getAmount());
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (item.getName() != null) {
                meta.setDisplayName(TextUtil.render(player, item.getName(), tokens));
            }
            if (item.getLore() != null && !item.getLore().isEmpty()) {
                meta.setLore(TextUtil.render(player, item.getLore(), tokens));
            }
            applyModelData(meta, item.getCustomModelData());
            applyFlags(meta, item.getItemFlags());
            if (item.isGlow()) {
                applyGlow(meta);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private Map<String, String> computeTokens(Player player, Menu menu, MenuItem item) {
        if (item.getGroup() != null) {
            ItemGroup group = plugin.getConfigManager().getGroup(item.getGroup());
            if (group != null) {
                return computeGroupTokens(player, menu, group, item.getTier());
            }
        }
        Map<String, String> t = new HashMap<>();
        t.put("player", player.getName());
        return t;
    }

    private Map<String, String> computeGroupTokens(Player player, Menu menu, ItemGroup group, long tierThreshold) {
        ConfigManager cfg = plugin.getConfigManager();
        PlayerDataManager pdm = plugin.getDataManager();

        Map<String, String> t = new HashMap<>();
        t.put("player", player.getName());

        long sold = pdm.getGroupSold(player.getUniqueId(), group.getMaterials());
        double scale = cfg.getThresholdScale(player);
        double current = cfg.groupMultiplierAtCount(group, sold, scale);

        t.put("group", group.getName());
        t.put("group_name", group.getDisplayName() != null ? group.getDisplayName() : capitalize(group.getName()));
        t.put("sold", NumberUtil.commas(sold));
        t.put("sold_raw", Long.toString(sold));
        t.put("sold_short", NumberUtil.abbreviate(sold));
        t.put("current_multiplier", NumberUtil.multiplier(current));
        t.put("max_multiplier", NumberUtil.multiplier(group.getMaxMultiplier()));
        t.put("tier_count", String.valueOf(group.getTiers().size()));
        if (group.getIcon() != null) t.put("icon", group.getIcon());

        long goal;
        boolean unlocked;
        boolean maxed = false;
        double displayMult;

        if (tierThreshold >= 0) {
            goal = ConfigManager.scaledThreshold((int) tierThreshold, scale);
            unlocked = sold >= goal;
            displayMult = tierMultiplier(cfg, group, tierThreshold);
        } else {
            long next = cfg.groupNextThresholdAbove(group, sold, scale);
            maxed = (next == Long.MAX_VALUE);
            goal = maxed ? 0L : next;
            unlocked = maxed;
            displayMult = current;
        }

        long remaining = unlocked ? 0L : Math.max(0L, goal - sold);

        t.put("multiplier", NumberUtil.multiplier(displayMult));
        t.put("threshold", maxed ? "MAX" : NumberUtil.commas(goal));
        t.put("threshold_raw", maxed ? "MAX" : Long.toString(goal));
        t.put("threshold_short", maxed ? "MAX" : NumberUtil.abbreviate(goal));
        t.put("remaining", NumberUtil.commas(remaining));
        t.put("remaining_short", NumberUtil.abbreviate(remaining));
        t.put("percent", String.valueOf(NumberUtil.percent(sold, goal)));
        t.put("progress_percent", String.valueOf(NumberUtil.percent(sold, goal)));
        t.put("progress_bar", buildBar(menu, sold, goal));
        t.put("status", unlocked ? menu.getStatusUnlocked() : menu.getStatusLocked());
        t.put("unlocked", String.valueOf(unlocked));
        return t;
    }

    private List<Map<String, String>> buildDynamicEntries(Player player, Menu menu) {
        List<Map<String, String>> entries = new ArrayList<>();
        ConfigManager cfg = plugin.getConfigManager();
        String source = menu.getContentSource().trim();
        String lower = source.toLowerCase(Locale.ROOT);

        if (lower.equals("groups")) {
            for (ItemGroup group : cfg.getGroups()) {
                Map<String, String> tokens = computeGroupTokens(player, menu, group, -1L);
                tokens.put("icon", group.getIcon() != null ? group.getIcon() : defaultGroupIcon(group));
                entries.add(tokens);
            }
            return entries;
        }

        int colon = source.indexOf(':');
        if (colon > 0) {
            String type = lower.substring(0, colon);
            String groupName = source.substring(colon + 1).trim();
            if (type.equals("tiers") || type.equals("group-tiers") || type.equals("group_tiers")) {
                ItemGroup group = cfg.getGroup(groupName);
                if (group != null) {
                    int n = 1;
                    for (MultiplierTier tier : group.getTiers()) {
                        Map<String, String> tokens = computeGroupTokens(player, menu, group, tier.getThreshold());
                        tokens.put("tier_number", String.valueOf(n++));
                        String icon = tier.getIcon() != null ? tier.getIcon()
                                : (group.getIcon() != null ? group.getIcon() : defaultGroupIcon(group));
                        tokens.put("icon", icon);
                        entries.add(tokens);
                    }
                } else {
                    plugin.getLogger().warning("Menu '" + menu.getName() + "' content-source references unknown group '" + groupName + "'.");
                }
            }
        }
        return entries;
    }

    private String defaultGroupIcon(ItemGroup group) {
        for (Material m : group.getMaterials()) {
            return m.name();
        }
        return "PAPER";
    }

    private double tierMultiplier(ConfigManager cfg, ItemGroup group, long threshold) {
        for (MultiplierTier tier : group.getTiers()) {
            if (tier.getThreshold() == threshold) return tier.getMultiplier();
        }
        return cfg.groupMultiplierAtCount(group, threshold);
    }

    private String buildBar(Menu menu, long current, long goal) {
        String symbol = menu.getBarSymbol();
        char c = (symbol == null || symbol.isEmpty()) ? '|' : symbol.charAt(0);
        return NumberUtil.progressBar(current, goal, menu.getBarLength(), c,
                menu.getBarComplete(), menu.getBarIncomplete());
    }

    private void applyModelData(ItemMeta meta, int cmd) {
        if (cmd < 0) return;
        try {
            meta.setCustomModelData(cmd);
        } catch (Throwable ignored) {

        }
    }

    private void applyFlags(ItemMeta meta, List<String> flags) {
        if (flags == null || flags.isEmpty()) return;
        for (String raw : flags) {
            try {
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Unknown item-flag in menu: " + raw);
            }
        }
    }

    private void applyGlow(ItemMeta meta) {
        try {
            Enchantment ench = glowEnchantment();
            if (ench != null) {
                meta.addEnchant(ench, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    private Enchantment glowEnchantment() {
        Enchantment ench = Enchantment.getByName("INFINITY");
        if (ench == null) ench = Enchantment.getByName("DURABILITY");
        if (ench == null) ench = Enchantment.getByName("UNBREAKING");
        if (ench == null) {
            Enchantment[] all = Enchantment.values();
            if (all != null && all.length > 0) ench = all[0];
        }
        return ench;
    }

    public Menu getMenu(String name) {
        return name == null ? null : menus.get(name.toLowerCase(Locale.ROOT));
    }

    public Set<String> getMenuNames() {
        return menus.keySet();
    }

    private static String firstNonNull(String... values) {
        for (String v : values) {
            if (v != null) return v;
        }
        return null;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String capitalize(String raw) {
        String s = raw.replace('_', ' ').replace('-', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : s.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1).toLowerCase())
                  .append(' ');
            }
        }
        return sb.toString().trim();
    }
}
