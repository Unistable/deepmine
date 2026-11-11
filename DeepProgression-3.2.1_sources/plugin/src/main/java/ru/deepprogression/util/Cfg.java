package ru.deepprogression.util;

import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import ru.deepprogression.DeepProgressionPlugin;
import ru.deepprogression.model.Branch;
import ru.deepprogression.model.Branch.Rarity;
import ru.deepprogression.model.Section;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Типизированный доступ к config.yml. */
public final class Cfg {

    private static FileConfiguration c;
    private static final Map<String, Integer> MULT_DEFAULTS = new HashMap<>();
    static {
        MULT_DEFAULTS.put("geologist_pick", 2);
        MULT_DEFAULTS.put("drill_coal", 100);
        MULT_DEFAULTS.put("drill_stone", 3);
        MULT_DEFAULTS.put("drill_copper", 50);
        MULT_DEFAULTS.put("drill_iron", 50);
        MULT_DEFAULTS.put("drill_gold", 50);
        MULT_DEFAULTS.put("drill_redstone", 50);
        MULT_DEFAULTS.put("drill_lapis", 50);
        MULT_DEFAULTS.put("drill_quartz", 50);
        MULT_DEFAULTS.put("drill_diamond", 25);
        MULT_DEFAULTS.put("drill_emerald", 25);
        MULT_DEFAULTS.put("drill_debris", 10);
        MULT_DEFAULTS.put("universal_1", 10);
        MULT_DEFAULTS.put("universal_2", 25);
        MULT_DEFAULTS.put("universal_3", 50);
    }

    private Cfg() {}

    public static void init(DeepProgressionPlugin plugin) {
        plugin.saveDefaultConfig();
        c = plugin.getConfig();
    }

    public static void reload(DeepProgressionPlugin plugin) {
        plugin.reloadConfig();
        c = plugin.getConfig();
    }

    public static boolean worldAllowed(World w) {
        if (w == null) return false;
        List<String> worlds = c.getStringList("worlds");
        return worlds.isEmpty() || worlds.contains(w.getName());
    }

    public static int mult(String toolId) {
        Integer legacy = MULT_DEFAULTS.get(toolId);
        int def = legacy != null ? legacy
                : ru.deepprogression.tools.Tools.defaultMult(toolId);
        return c.getInt("multipliers." + toolId, def);
    }

    /** Шанс бонуса "из камня" для инструмента на ветке (0 если не положен). */
    public static double fromStoneChance(String toolId, Branch branch) {
        boolean branchTool = toolId.startsWith("drill_") || toolId.startsWith("tool_");
        if (branch != Branch.COAL && branch != Branch.STONE) {
            return branchTool ? c.getDouble("from_stone." + toolId, 0.0) : 0.0;
        }
        if (branchTool) return c.getDouble("from_stone." + toolId, 0.0);
        if (toolId.startsWith("universal_")) return c.getDouble("from_stone.universal", 0.0);
        if (toolId.equals("geologist_pick")) return c.getDouble("from_stone.geologist_pick", 0.0);
        return 0.0;
    }

    public static double progMultDrill() { return c.getDouble("progression.multiplier_drill", 2.0); }
    public static double progMultUniversal() { return c.getDouble("progression.multiplier_universal", 1.5); }
    public static double progMultGeologist() { return c.getDouble("progression.multiplier_geologist", 1.2); }
    public static double progMultChunk() { return c.getDouble("progression.multiplier_chunk", 0.5); }
    public static boolean creativeCounts() { return c.getBoolean("progression.creative_counts", false); }

    public static long[] thresholds(Branch b) {
        String key = "progression.thresholds." + switch (b.rarity) {
            case COMMON -> "common";
            case MEDIUM -> "medium";
            case RARE -> "rare";
        };
        List<Integer> list = c.getIntegerList(key);
        long[] arr = new long[10];
        for (int i = 0; i < 10; i++) {
            arr[i] = (i < list.size() && list.get(i) != null) ? list.get(i) : (16L << i);
        }
        return arr;
    }

    public static double weight(Branch b) {
        return c.getDouble("progression.weights." + b.name(), 1.0);
    }

    public static int magmaCost() { return c.getInt("chunk_drill.magma_cost", 128); }
    public static double efficiency() { return c.getDouble("chunk_drill.efficiency", 0.9); }
    public static int cooldownSeconds() { return c.getInt("chunk_drill.cooldown_seconds", 30); }
    public static int maxUnits() { return c.getInt("chunk_drill.max_units", 20000); }

    public static int probeMaxCharges() { return c.getInt("prospector.max_charges", 8); }
    public static int probeCooldownSeconds() { return c.getInt("prospector.cooldown_seconds", 3); }

    public static int batteryCharges() { return c.getInt("battery.charges", 2); }

    public static int broadcastFromTier() { return c.getInt("medals.broadcast_from_tier", 8); }
    public static boolean medalGiveItem() { return c.getBoolean("medals.give_item", true); }

    public static boolean autoPickup() { return c.getBoolean("general.auto_pickup", true); }
    public static double effectChance() { return c.getDouble("general.auto_pickup_chunk", 0.05); }

    // --- v2.0: хранилища, машины, контракты, жилы удачи, перки ---
    /**
     * Лимит рудного тайника в единицах. Жёстко ограничен окном GUI 27×64 = 1728
     * (U3): большие значения обрезаются — иначе часть содержимого терялась
     * при отображении.
     */
    public static int pouchMaxUnits() {
        int v = c.getInt("storage.pouch_max_units", 1728);
        return Math.max(1, Math.min(v, 1728));
    }

    public static int stationMinMagma() { return c.getInt("charge_station.min_magma", 5); }
    public static int stationCooldown() { return c.getInt("charge_station.cooldown_seconds", 60); }
    public static int stationProbeCharges() { return c.getInt("charge_station.probe_charges", 3); }

    public static int scannerCooldown() { return c.getInt("geo_scanner.cooldown_seconds", 30); }

    public static int machineCooldown() { return c.getInt("machines.cooldown_seconds", 2); }

    public static double luckyChance() { return c.getDouble("lucky_veins.chance", 0.003); }
    public static int luckyMult() { return c.getInt("lucky_veins.multiplier", 25); }
    public static boolean luckyBroadcast() { return c.getBoolean("lucky_veins.broadcast_rare", true); }

    public static int perkFromTier() { return c.getInt("perks.double_drop_from_tier", 4); }
    public static double perkChancePerTier() { return c.getDouble("perks.double_drop_chance_per_tier", 0.02); }
    public static double perkChanceCap() { return c.getDouble("perks.double_drop_chance_cap", 0.10); }

    public static int questsPerDay() { return c.getInt("quests.per_day", 3); }

    // --- v3.1: перки, активные способности, экономика, bulk-машины ---

    /** Шансы «+1» перков (переопределяются в конфиге). */
    public static double perkChance(String perkId, double def) {
        return c.getDouble("perks.chances." + perkId, def);
    }

    private static final Map<String, Double> SECTION_DOUBLE = new HashMap<>();
    static {
        SECTION_DOUBLE.put("mine_double", 0.10);
        SECTION_DOUBLE.put("wood_double", 0.10);
        SECTION_DOUBLE.put("farm_double", 0.10);
        SECTION_DOUBLE.put("flora_double", 0.15);
        SECTION_DOUBLE.put("sea_double", 0.15);
        SECTION_DOUBLE.put("mason_double", 0.15);
        SECTION_DOUBLE.put("light_double", 0.15);
        SECTION_DOUBLE.put("engine_double", 0.15);
        SECTION_DOUBLE.put("nether_double", 0.15);
        SECTION_DOUBLE.put("end_double", 0.15);
        SECTION_DOUBLE.put("sculk_double", 0.15);
    }

    /** Шанс двойного дропа секционного перка. */
    public static double perkSectionDouble(String perkId) {
        Double def = SECTION_DOUBLE.get(perkId);
        if (def == null) return 0.0;
        return c.getDouble("perks.section_double." + perkId, def);
    }

    private static final Map<String, Integer> ACTIVE_CD = new HashMap<>();
    static {
        ACTIVE_CD.put("MINING", 300);
        ACTIVE_CD.put("WOOD", 180);
        ACTIVE_CD.put("FARM", 180);
        ACTIVE_CD.put("FLORA", 120);
        ACTIVE_CD.put("SEA", 120);
        ACTIVE_CD.put("MASON", 60);
        ACTIVE_CD.put("LIGHT", 120);
        ACTIVE_CD.put("ENGINE", 240);
        ACTIVE_CD.put("NETHER", 240);
        ACTIVE_CD.put("END", 90);
        ACTIVE_CD.put("ANOMALY", 180);
    }

    /** Кулдаун активного перка секции, секунды. */
    public static int activeCooldown(Section s) {
        if (s == null) return 180;
        return c.getInt("actives." + s.name().toLowerCase(java.util.Locale.ROOT)
                + "_cooldown", ACTIVE_CD.getOrDefault(s.name(), 180));
    }

    /** Циклов bulk-режима машин (Shift+ПКМ). */
    public static int machineBulk() { return c.getInt("machines.bulk_multiplier", 8); }

    // --- v3.2: ручные приспособления ---

    /** Кулдаун приспособления (сек). */
    public static int gadgetCooldown(String id) {
        return c.getInt("gadgets." + id + ".cooldown", 2);
    }

    public static int airTankBreath() { return c.getInt("gadgets.air_tank.breath", 120); }
    public static int airTankGrace() { return c.getInt("gadgets.air_tank.grace", 60); }
    public static int thermosMagma() { return c.getInt("gadgets.thermos.magma", 4); }
    public static int thermosDuration() { return c.getInt("gadgets.thermos.duration", 90); }
    public static int voidSailDuration() { return c.getInt("gadgets.voidsail.duration", 60); }
    public static int jammerDuration() { return c.getInt("gadgets.jammer.duration", 90); }

    /** Делитель предложения в экономике (чем больше — тем медленнее падает индекс). */
    public static int economyDivisor() { return c.getInt("economy.supply_divisor", 5000); }

    /** Секция конфига (для переопределений ценности). */
    public static org.bukkit.configuration.ConfigurationSection section(String path) {
        return c == null ? null : c.getConfigurationSection(path);
    }
}
