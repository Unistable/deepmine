package ru.deepmagic.util;

import org.bukkit.configuration.file.FileConfiguration;
import ru.deepmagic.DeepMagicPlugin;
import ru.deepprogression.model.Branch;
import ru.deepmagic.model.Magic;

import java.util.Map;

/** Типизированный доступ к config.yml DeepMagic. */
public final class Conf {
    private static FileConfiguration c;
    private static DeepMagicPlugin plugin;

    private Conf() {}

    public static void init(DeepMagicPlugin pl) {
        plugin = pl;
        pl.saveDefaultConfig();
        c = pl.getConfig();
    }

    public static void reload(DeepMagicPlugin pl) {
        pl.reloadConfig();
        c = pl.getConfig();
    }

    public static String prefix() {
        return c.getString("messages.prefix", "&8[&5Магия&8] &r");
    }

    public static boolean worldAllowed(org.bukkit.World w) {
        if (w == null) return false;
        java.util.List<String> list = c.getStringList("worlds");
        return list.isEmpty() || list.contains(w.getName());
    }

    // ---------- компрессор ----------
    public static int cycleTicks() { return Math.max(10, c.getInt("compressor.cycle_ticks", 40)); }
    public static int unitsPerCycle() { return Math.max(16, c.getInt("compressor.units_per_cycle", 512)); }
    public static int fuelPerCycle() { return Math.max(1, c.getInt("compressor.fuel_per_cycle", 1)); }
    public static long unitsFor(Branch.Rarity r) {
        return switch (r) {
            case COMMON -> Math.max(64, c.getInt("compressor.units_common", 64000));
            case MEDIUM -> Math.max(64, c.getInt("compressor.units_medium", 16000));
            case RARE -> Math.max(64, c.getInt("compressor.units_rare", 4000));
        };
    }
    public static int recompressGate2() { return c.getInt("compressor.recompress_gate_tier2", 6); }
    public static int recompressGate3() { return c.getInt("compressor.recompress_gate_tier3", 9); }

    // ---------- посохи ----------
    public static int craftGateTier() { return Math.max(1, c.getInt("staffs.craft_gate_tier", 3)); }
    public static String component(int tier) {
        return c.getString("staffs.component_tier" + tier,
                tier == 1 ? "steel_plate" : tier == 2 ? "node_power" : "board_elite");
    }
    public static boolean usePouchFuel() { return c.getBoolean("staffs.use_pouch_fuel", true); }

    // ---------- pvp ----------
    public static boolean pvpEnabled() { return c.getBoolean("pvp.enabled", true); }
    public static int spawnSafeRadius() { return c.getInt("pvp.spawn_safe_radius", 64); }
    public static double eternalDps() { return c.getDouble("pvp.eternal_burn_dps", 1.0); }

    /**
     * Разрешён ли периодический урон (DoT: вечное горение, гниль) по цели.
     * Игрокам не наносим при выключенном PvP и в спавн-зоне.
     */
    public static boolean dotAllowed(org.bukkit.entity.LivingEntity v) {
        if (!(v instanceof org.bukkit.entity.Player)) return true;
        if (!pvpEnabled()) return false;
        int r = spawnSafeRadius();
        if (r <= 0) return true;
        return v.getWorld().getSpawnLocation()
                .distanceSquared(v.getLocation()) > (double) r * r;
    }

    // ---------- пыль ----------
    public static int dustDuration() { return c.getInt("dust.duration", 10); }
    public static double dustRadius() { return c.getDouble("dust.radius", 3.0); }
    public static int dustGrace() { return c.getInt("dust.grace_seconds", 4); }
    public static double dustDps() { return c.getDouble("dust.dps_after_grace", 1.0); }
    public static int dustCooldown() { return c.getInt("dust.cooldown", 60); }

    /** Кулдаун посоха: переопределение из конфига или значение реестра. */
    public static int cooldown(Magic.StaffDef def) {
        int cd = c.getInt("cooldowns." + def.branch().name(), 0);
        return cd > 0 ? cd : def.cooldown();
    }

    // ---------- служебное ----------
    public static String tierName(int tier) {
        java.util.List<String> l = c.getStringList("lore.tier_names");
        if (tier >= 1 && tier <= l.size()) return l.get(tier - 1);
        return Magic.TIER_NAMES[tier - 1];
    }
}
