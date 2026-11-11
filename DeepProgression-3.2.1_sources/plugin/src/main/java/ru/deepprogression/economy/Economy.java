package ru.deepprogression.economy;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.deepprogression.DeepProgressionPlugin;
import ru.deepprogression.model.Blocks;
import ru.deepprogression.model.Branch;
import ru.deepprogression.util.Cfg;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Глобальная экономика DeepProgression 3.1.
 * Каждый ресурсный материал имеет индекс 1–10:
 *   index = clamp(1..10, round(V / (1 + log10(1 + total/5000))))
 * где V — собственная ценность (редкость) материала, total — сколько единиц
 * добыли все игроки сервера. Чем больше добыто — тем ниже индекс (предложение),
 * чем ценнее материал — тем выше (спрос/ценность).
 * Хранение: economy.yml (total + week + метка недели), автосохранение.
 */
public final class Economy {

    private static final Map<String, Integer> TOTAL = new ConcurrentHashMap<>();
    private static final Map<String, Integer> WEEK = new ConcurrentHashMap<>();
    private static final Map<Material, Integer> VALUES = new HashMap<>();
    private static String weekKey = "";
    private static DeepProgressionPlugin plugin;
    private static boolean dirty = false;

    private Economy() {}

    // ---------- инициализация ----------

    public static void init(DeepProgressionPlugin pl) {
        plugin = pl;
        VALUES.clear();
        registerDefaults();
        load();
        // Добавляем в индекс все материалы, ценность которых ещё не задана
        for (Material m : Blocks.vaultItems()) {
            // v3.2.1: защита GUI экономики от блоков без предметной формы
            if (!Blocks.isItemSafe(m)) continue;
            VALUES.computeIfAbsent(m, Economy::defaultOf);
        }
        for (Branch b : Branch.values()) {
            Material m = b.medalRawIcon;
            if (m != null) VALUES.computeIfAbsent(m, Economy::defaultOf);
        }
        // Переопределения из config.yml: economy.values.<MATERIAL>
        var vs = Cfg.section("economy.values");
        if (vs != null) {
            for (String k : vs.getKeys(false)) {
                Material m = Material.matchMaterial(k);
                if (m != null) VALUES.put(m, vs.getInt(k, 2));
            }
        }
    }

    /** Собственная ценность V по группам материалов (1–10). */
    private static void registerDefaults() {
        put(10, "NETHERITE_INGOT", "NETHERITE_BLOCK", "NETHER_STAR");
        put(9, "ANCIENT_DEBRIS", "NETHERITE_SCRAP", "DIAMOND", "EMERALD",
                "ECHO_SHARD", "DISC_FRAGMENT_5", "DIAMOND_BLOCK", "EMERALD_BLOCK");
        put(8, "SPONGE", "WET_SPONGE", "ENDER_PEARL", "HEART_OF_THE_SEA");
        put(7, "GOLD_INGOT", "RAW_GOLD", "GOLD_BLOCK", "AMETHYST_SHARD",
                "CRYING_OBSIDIAN", "AMETHYST_BLOCK");
        put(6, "PRISMARINE_SHARD", "PRISMARINE_CRYSTALS", "POPPED_CHORUS_FRUIT",
                "GLOW_INK_SAC", "RESIN_BRICK", "HONEYCOMB");
        put(5, "IRON_INGOT", "RAW_IRON", "COPPER_INGOT", "RAW_COPPER", "QUARTZ",
                "REDSTONE", "LAPIS_LAZULI", "GLOWSTONE_DUST", "MAGMA_CREAM",
                "NETHER_WART", "OBSIDIAN");
        put(4, "COAL", "HONEY_BOTTLE", "GLOW_BERRIES");
        put(3, "GOLD_NUGGET", "COCOA_BEANS", "CHORUS_FRUIT", "SOUL_SAND", "SOUL_SOIL",
                "END_STONE", "END_STONE_BRICKS", "PURPUR_BLOCK", "PURPUR_PILLAR");
        put(2, "WHEAT", "CARROT", "POTATO", "BEETROOT", "MELON_SLICE", "SUGAR",
                "SUGAR_CANE", "GLASS", "TINTED_GLASS", "GLASS_PANE", "BRICK",
                "MELON", "PUMPKIN", "HAY_BLOCK", "DRIED_KELP_BLOCK", "MAGMA_BLOCK",
                "NETHER_WART_BLOCK", "WARPED_WART_BLOCK", "RESIN_CLUMP", "RESIN_BLOCK",
                "GLOWSTONE", "SHROOMLIGHT", "SEA_LANTERN", "OCHRE_FROGLIGHT",
                "VERDANT_FROGLIGHT", "PEARLESCENT_FROGLIGHT", "CARVED_PUMPKIN",
                "HONEY_BLOCK", "HONEYCOMB_BLOCK");
        put(1, "WHEAT_SEEDS", "BEETROOT_SEEDS", "MELON_SEEDS", "PUMPKIN_SEEDS",
                "SAND", "RED_SAND", "GRAVEL", "SNOWBALL", "DRIED_KELP", "KELP",
                "SEAGRASS", "NETHERRACK", "BLACKSTONE", "BASALT",
                "CRIMSON_PLANKS", "WARPED_PLANKS", "NETHER_BRICKS",
                "OAK_PLANKS", "SPRUCE_PLANKS", "BIRCH_PLANKS", "JUNGLE_PLANKS",
                "ACACIA_PLANKS", "DARK_OAK_PLANKS", "MANGROVE_PLANKS",
                "CHERRY_PLANKS", "PALE_OAK_PLANKS", "BAMBOO_PLANKS", "BAMBOO",
                "CACTUS");
        // веточные иконки-предметы (logs)
        for (String w : new String[]{"OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA",
                "DARK_OAK", "MANGROVE", "CHERRY", "PALE_OAK", "CRIMSON", "WARPED"}) {
            put(2, w + "_LOG");
        }
        for (String c : new String[]{"TUBE", "BRAIN", "BUBBLE", "FIRE", "HORN"}) {
            put(3, c + "_CORAL", c + "_CORAL_BLOCK", c + "_CORAL_FAN");
        }
        for (String s : new String[]{"WHITE_WOOL", "RED_WOOL", "BLUE_WOOL", "BLACK_WOOL",
                "GREEN_WOOL", "YELLOW_WOOL", "ORANGE_WOOL", "LIGHT_BLUE_WOOL",
                "LIME_WOOL", "PINK_WOOL", "GRAY_WOOL", "LIGHT_GRAY_WOOL", "CYAN_WOOL",
                "PURPLE_WOOL", "MAGENTA_WOOL", "BROWN_WOOL"}) put(2, s);
    }

    private static void put(int v, String... mats) {
        for (String s : mats) {
            Material m = Material.matchMaterial(s);
            // v3.2.1: блоки без предметной формы не попадают в ценности (GUI экономики)
            if (m != null && ru.deepprogression.model.Blocks.isItemSafe(m)) VALUES.put(m, v);
        }
    }

    /** Дефолтная ценность для незарегистрированного материала. */
    private static int defaultOf(Material m) {
        String n = m.name();
        if (n.contains("NETHERITE")) return 10;
        if (n.contains("DIAMOND") || n.contains("EMERALD")) return 9;
        if (n.contains("AMETHYST") || n.contains("GOLD")) return 7;
        if (n.contains("IRON") || n.contains("COPPER") || n.contains("QUARTZ")
                || n.contains("LAPIS") || n.contains("REDSTONE")) return 5;
        if (n.contains("COAL") || n.contains("CHARCOAL")) return 4;
        if (n.contains("_LOG") || n.contains("_PLANKS") || n.contains("_WOOD")) return 2;
        if (n.contains("WOOL")) return 2;
        return 2;
    }

    // ---------- учёт добычи ----------

    /** Начислить добычу (главный поток). */
    public static void mined(Material m, int units) {
        if (m == null || units <= 0) return;
        String key = m.name();
        TOTAL.merge(key, units, Integer::sum);
        WEEK.merge(key, units, Integer::sum);
        dirty = true;
    }

    // ---------- индекс ----------

    /** Текущий индекс материала 1..10. */
    public static int index(Material m) {
        if (m == null) return 1;
        Integer v = VALUES.get(m);
        if (v == null) v = defaultOf(m);
        int total = TOTAL.getOrDefault(m.name(), 0);
        int div = Math.max(100, Cfg.economyDivisor());
        double f = 1.0 / (1.0 + Math.log10(1.0 + total / (double) div));
        int idx = (int) Math.round(v * f);
        return Math.max(1, Math.min(10, idx));
    }

    /** Собственная ценность V (без учёта предложения). */
    public static int baseValue(Material m) {
        Integer v = VALUES.get(m);
        return v == null ? defaultOf(m) : v;
    }

    public static int total(Material m) {
        return m == null ? 0 : TOTAL.getOrDefault(m.name(), 0);
    }

    public static int week(Material m) {
        return m == null ? 0 : WEEK.getOrDefault(m.name(), 0);
    }

    public static boolean tracked(Material m) {
        return m != null && VALUES.containsKey(m);
    }

    public static List<Material> trackedMaterials() {
        List<Material> out = new ArrayList<>(VALUES.keySet());
        out.sort((a, b) -> {
            int ia = index(a), ib = index(b);
            if (ia != ib) return Integer.compare(ib, ia);
            int va = baseValue(a), vb = baseValue(b);
            if (va != vb) return Integer.compare(vb, va);
            return Integer.compare(total(b), total(a));
        });
        return out;
    }

    /** Множитель наград контрактов по индексу (дефицит → щедрее). */
    public static double rewardFactor(Branch branch) {
        if (branch == null) return 1.0;
        Material m = primaryOf(branch);
        int idx = index(m);
        return Math.max(0.6, 1.0 + (idx - 5) * 0.08);
    }

    /** Первичный ресурс ветки (для индекса ветки). */
    public static Material primaryOf(Branch b) {
        Blocks.DropSpec d = Blocks.bonusOf(b);
        if (d != null) return d.item();
        Material m = b.medalRawIcon;
        return m != null ? m : b.guiIcon;
    }

    // ---------- сохранение ----------

    public static void saveAsync() {
        if (!dirty || plugin == null) return;
        dirty = false;
        Map<String, Integer> totalCopy = new HashMap<>(TOTAL);
        Map<String, Integer> weekCopy = new HashMap<>(WEEK);
        String wk = weekKey;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(totalCopy, weekCopy, wk));
    }

    public static void saveSync() {
        write(new HashMap<>(TOTAL), new HashMap<>(WEEK), weekKey);
    }

    private static void write(Map<String, Integer> total, Map<String, Integer> week, String wk) {
        try {
            File f = new File(plugin.getDataFolder(), "economy.yml");
            YamlConfiguration y = new YamlConfiguration();
            y.set("week", wk);
            for (Map.Entry<String, Integer> e : total.entrySet()) {
                y.set("total." + e.getKey(), e.getValue());
            }
            for (Map.Entry<String, Integer> e : week.entrySet()) {
                y.set("week_counts." + e.getKey(), e.getValue());
            }
            y.save(f);
        } catch (Exception ex) {
            plugin.getLogger().warning("Не удалось сохранить economy.yml: " + ex.getMessage());
        }
    }

    private static void load() {
        File f = new File(plugin.getDataFolder(), "economy.yml");
        if (!f.exists()) {
            weekKey = currentWeek();
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        weekKey = y.getString("week", currentWeek());
        ConfigurationSection t = y.getConfigurationSection("total");
        if (t != null) {
            for (String k : t.getKeys(false)) TOTAL.put(k, t.getInt(k));
        }
        ConfigurationSection w = y.getConfigurationSection("week_counts");
        if (w != null) {
            for (String k : w.getKeys(false)) WEEK.put(k, w.getInt(k));
        }
        // Смена недели — обнуляем недельные счётчики
        if (!currentWeek().equals(weekKey)) {
            weekKey = currentWeek();
            WEEK.clear();
            dirty = true;
        }
    }

    public static void tickWeek() {
        if (!currentWeek().equals(weekKey)) {
            weekKey = currentWeek();
            WEEK.clear();
            dirty = true;
            saveAsync();
        }
    }

    private static String currentWeek() {
        LocalDate d = LocalDate.now();
        java.time.temporal.WeekFields wf = java.time.temporal.WeekFields.ISO;
        return d.getYear() + "-W" + d.get(wf.weekOfWeekBasedYear());
    }

    /** Статистика для selftest. */
    public static int trackedCount() {
        return VALUES.size();
    }

    /** Топ дефицита (минимальная добыча). */
    public static List<Material> scarcest(int n) {
        List<Material> all = trackedMaterials();
        all.sort(Comparator.comparingInt(Economy::total));
        return all.subList(0, Math.min(n, all.size()));
    }
}
