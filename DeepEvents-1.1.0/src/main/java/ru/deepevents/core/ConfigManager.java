package ru.deepevents.core;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Типизированный доступ к config.yml. */
public final class ConfigManager {

    /** Запись таблицы лута. */
    public record LootEntry(Material material, int min, int max, double chance) {}

    /** Запись благословения Звездопада. */
    public record Blessing(String name, PotionEffectType effect, int seconds, int amplifier, double weight) {}

    /** Запись радиационного эффекта. */
    public record RadEffect(PotionEffectType type, int duration) {}

    private final JavaPlugin plugin;
    private FileConfiguration cfg;

    private final Map<String, List<LootEntry>> lootCache = new HashMap<>();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        cfg = plugin.getConfig();
        lootCache.clear();
        // Валидация лут-таблиц сразу при загрузке — чтобы ошибки были видны в логе
        for (String key : List.of("kraken", "nuke")) {
            List<LootEntry> loot = loot(key);
            Msg.log("Лут-таблица '" + key + "': " + loot.size() + " записей.");
        }
        List<Blessing> b = blessings();
        Msg.log("Благословения Звездопада: " + b.size() + " записей.");
    }

    // ---------- general ----------

    public List<String> worlds() {
        return cfg.getStringList("general.worlds");
    }

    public boolean schedulerEnabled() { return cfg.getBoolean("general.scheduler.enabled", true); }
    public int schedulerMinMinutes() { return cfg.getInt("general.scheduler.min-interval-minutes", 45); }
    public int schedulerMaxMinutes() { return cfg.getInt("general.scheduler.max-interval-minutes", 90); }
    public int schedulerWarnSeconds() { return cfg.getInt("general.scheduler.warn-seconds", 60); }
    public int requireOnlinePlayers() { return cfg.getInt("general.scheduler.require-online-players", 1); }

    public Map<String, Double> schedulerWeights() {
        Map<String, Double> w = new HashMap<>();
        ConfigurationSection s = cfg.getConfigurationSection("general.scheduler.weights");
        if (s != null) for (String k : s.getKeys(false)) w.put(k.toUpperCase(), s.getDouble(k, 1.0));
        return w;
    }

    public int minDistanceFromPlayers() { return cfg.getInt("general.min-distance-from-players", 120); }
    public int minDistanceFromSpawn() { return cfg.getInt("general.min-distance-from-spawn", 200); }

    // ---------- kraken ----------

    public boolean krakenEnabled() { return cfg.getBoolean("kraken.enabled", true); }
    public int krakenSearchRadius() { return cfg.getInt("kraken.search-radius", 800); }
    public double krakenHp() { return cfg.getDouble("kraken.hp", 600); }
    public double krakenScale() { return cfg.getDouble("kraken.scale", 2.6); }
    public int krakenDisengageSeconds() { return cfg.getInt("kraken.disengage-seconds", 300); }
    public int krakenDespawnSeconds() { return cfg.getInt("kraken.despawn-seconds", 900); }

    public int krakenAcolytes() { return cfg.getInt("kraken.shield.acolytes", 3); }
    public int krakenVulnerableSeconds() { return cfg.getInt("kraken.shield.vulnerable-seconds", 25); }
    public double krakenRegenPerSecond() { return cfg.getDouble("kraken.shield.regen-per-second", 0.5); }
    public double krakenAcolyteHp() { return cfg.getDouble("kraken.shield.acolyte-hp", 60); }

    public int krakenTentacleCount() { return cfg.getInt("kraken.tentacles.count", 7); }
    public double krakenSlamDamage() { return cfg.getDouble("kraken.tentacles.slam.damage", 8); }
    public double krakenSlamRadius() { return cfg.getDouble("kraken.tentacles.slam.radius", 3); }
    public int[] krakenSlamInterval() { return secondsRange("kraken.tentacles.slam.interval-seconds", 9, 15); }
    public double krakenSlamWarnSeconds() { return cfg.getDouble("kraken.tentacles.slam.warn-seconds", 1.2); }

    public double waveAmplitude() { return cfg.getDouble("kraken.waves.amplitude", 1.7); }
    public double waveLength() { return cfg.getDouble("kraken.waves.wavelength", 7.0); }
    public double waveSpeed() { return cfg.getDouble("kraken.waves.speed", 0.85); }
    public int waveColumns() { return cfg.getInt("kraken.waves.width-columns", 11); }
    public double waveColumnSpacing() { return cfg.getDouble("kraken.waves.column-spacing", 1.1); }
    public double waveDamage() { return cfg.getDouble("kraken.waves.damage", 5.0); }
    public int[] waveInterval() { return secondsRange("kraken.waves.interval-seconds", 13, 20); }
    public double waveMaxDistance() { return cfg.getDouble("kraken.waves.max-distance", 45); }

    public int[] whirlInterval() { return secondsRange("kraken.whirlpools.interval-seconds", 11, 17); }
    public double whirlRadius() { return cfg.getDouble("kraken.whirlpools.pull-radius", 7); }
    public double whirlDamage() { return cfg.getDouble("kraken.whirlpools.damage", 3); }
    public double whirlLifetimeSeconds() { return cfg.getDouble("kraken.whirlpools.lifetime-seconds", 8); }

    public int minionGroupSize() { return cfg.getInt("kraken.minions.group-size", 4); }
    public int minionIntervalSeconds() { return cfg.getInt("kraken.minions.interval-seconds", 35); }
    public int minionCap() { return cfg.getInt("kraken.minions.cap", 8); }

    public Material trophyMaterial() {
        return Material.matchMaterial(cfg.getString("kraken.trophy.material", "HEART_OF_THE_SEA"));
    }

    public String trophyName() { return cfg.getString("kraken.trophy.name", "Сердце Кракена"); }

    // ---------- nuke ----------

    public boolean nukeEnabled() { return cfg.getBoolean("nuke.enabled", true); }
    public int nukeCraterRadius() { return cfg.getInt("nuke.crater-radius", 27); }
    public int nukeCraterDepth() { return cfg.getInt("nuke.crater-depth", 13); }
    public double nukeFalloutMult() { return cfg.getDouble("nuke.fallout-radius-mult", 1.6); }
    public int nukeSirenSeconds() { return cfg.getInt("nuke.siren-seconds", 10); }

    public double shockRange() { return cfg.getDouble("nuke.shockwave.range", 70); }
    public double shockSpeed() { return cfg.getDouble("nuke.shockwave.speed", 2.2); }
    public double shockMaxDamage() { return cfg.getDouble("nuke.shockwave.max-damage", 12); }

    public boolean radiationPermanent() { return cfg.getBoolean("nuke.radiation.permanent", true); }
    public int radiationPeriodTicks() { return cfg.getInt("nuke.radiation.period-ticks", 70); }

    /** Резолв эффекта с поддержкой старых имён Bukkit (CONFUSION->NAUSEA и т.д.). */
    public static PotionEffectType resolveEffect(String name) {
        if (name == null) return null;
        String up = name.trim().toUpperCase();
        PotionEffectType t = PotionEffectType.getByName(up);
        if (t != null) return t;
        return switch (up) {
            case "CONFUSION" -> PotionEffectType.NAUSEA;
            case "SLOW" -> PotionEffectType.SLOWNESS;
            case "SLOW_DIGGING" -> PotionEffectType.MINING_FATIGUE;
            case "JUMP" -> PotionEffectType.JUMP_BOOST;
            case "INCREASE_DAMAGE" -> PotionEffectType.STRENGTH;
            default -> null;
        };
    }

    public List<RadEffect> radiationEffects() {
        List<RadEffect> out = new ArrayList<>();
        for (Map<?, ?> m : cfg.getMapList("nuke.radiation.effects")) {
            Object type = m.get("type");
            int dur = m.get("duration") instanceof Number n ? n.intValue() : 100;
            PotionEffectType t = resolveEffect(type == null ? null : String.valueOf(type));
            if (t != null) out.add(new RadEffect(t, dur));
        }
        return out;
    }

    // ---------- starfall ----------

    public boolean starfallEnabled() { return cfg.getBoolean("starfall.enabled", true); }
    public int starfallDurationSeconds() { return cfg.getInt("starfall.duration-seconds", 300); }
    public boolean starfallForceNight() { return cfg.getBoolean("starfall.force-night", true); }
    public int[] meteorInterval() { return secondsRange("starfall.meteor-interval-seconds", 7, 13); }
    public int shardDespawnSeconds() { return cfg.getInt("starfall.shard-despawn-seconds", 90); }
    public int wishCost() { return cfg.getInt("starfall.wish.cost", 3); }

    public List<Blessing> blessings() {
        List<Blessing> out = new ArrayList<>();
        for (Map<?, ?> m : cfg.getMapList("starfall.blessings")) {
            String name = m.get("name") != null ? String.valueOf(m.get("name")) : "Благословение";
            String eff = m.get("effect") != null ? String.valueOf(m.get("effect")) : "LUCK";
            PotionEffectType t = resolveEffect(eff);
            if (t == null) {
                Msg.warn("Неизвестный эффект в starfall.blessings: " + eff);
                continue;
            }
            int seconds = m.get("seconds") instanceof Number n ? n.intValue() : 300;
            int amp = m.get("amplifier") instanceof Number n2 ? n2.intValue() : 0;
            double weight = m.get("weight") instanceof Number n3 ? n3.doubleValue() : 10.0;
            out.add(new Blessing(name, t, seconds, amp, weight));
        }
        return out;
    }

    // ---------- loot ----------

    public List<LootEntry> loot(String eventKey) {
        return lootCache.computeIfAbsent(eventKey, k -> {
            List<LootEntry> out = new ArrayList<>();
            List<Map<?, ?>> maps = cfg.getMapList(k + ".loot");
            for (Map<?, ?> m : maps) {
                String mat = String.valueOf(m.get("material"));
                Material material = Material.matchMaterial(mat);
                if (material == null) {
                    Msg.warn("Неизвестный материал в луте '" + k + "': " + mat);
                    continue;
                }
                int min = number(m.get("min"), 1);
                int max = number(m.get("max"), 1);
                double chance = m.get("chance") instanceof Number n ? n.doubleValue() : 100.0;
                out.add(new LootEntry(material, min, max, chance));
            }
            return out;
        });
    }

    private static int number(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    // ---------- helpers ----------

    public int[] secondsRange(String path, int defMin, int defMax) {
        List<Integer> list = cfg.getIntegerList(path);
        if (list.size() == 2) return new int[]{list.get(0), list.get(1)};
        return new int[]{defMin, defMax};
    }

    public boolean isEventWorld(World w) {
        return w != null && worlds().contains(w.getName());
    }
}
