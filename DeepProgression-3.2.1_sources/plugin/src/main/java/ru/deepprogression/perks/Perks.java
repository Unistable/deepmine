package ru.deepprogression.perks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.deepprogression.DeepProgressionPlugin;
import ru.deepprogression.model.Branch;
import ru.deepprogression.model.Section;
import ru.deepprogression.progress.PlayerProfile;
import ru.deepprogression.progress.ProfileStore;
import ru.deepprogression.tools.Items;
import ru.deepprogression.tools.Tools;
import ru.deepprogression.util.Cfg;
import ru.deepprogression.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Система пассивных перков DeepProgression 3.1.
 * 4 перка на секцию; открываются лучшим званием ветки секции
 * (пороги 3/4/5/7), включаются/выключаются в /dp perks.
 */
public final class Perks {

    /** Пассивный перк: id, секция, порог звания, название, описание. */
    public record Perk(String id, Section section, int tier, String name, String desc) {}

    public static final List<Perk> ALL = new ArrayList<>();
    private static final Map<String, Perk> BY_ID = new java.util.HashMap<>();
    private static final Map<Section, List<Perk>> BY_SECTION = new java.util.EnumMap<>(Section.class);
    private static final Map<UUID, Long> LAVA_GUARD = new java.util.HashMap<>();
    private static final Map<UUID, Long> PEARL_TAG = new java.util.HashMap<>();
    private static DeepProgressionPlugin plugin;

    static {
        // ==================== ШАХТЁР ====================
        add("mine_fall_shield", Section.MINING, 3, "Крепкий шлем",
                "Урон от падения −30% (в шахтах вся жизнь).");
        add("mine_double", Section.MINING, 4, "Двойная жила",
                "Шанс двойного дропа руды при добыче без спец-бура.");
        add("mine_sight", Section.MINING, 5, "Глубинное зрение",
                "Ночное зрение ниже уровня y=0.");
        add("mine_haste", Section.MINING, 7, "Глубинная спешка",
                "Спешка II ниже уровня y=0.");
        // ==================== ЛЕСОРУБ ====================
        add("wood_replant", Section.WOOD, 3, "Подсадка",
                "25% шанс авто-пересадки саженца при валке бревна.");
        add("wood_double", Section.WOOD, 4, "Запасливый дровосек",
                "10% шанс двойного дропа лесных веток.");
        add("wood_speed", Section.WOOD, 5, "Лесная поступь",
                "Скорость I в лесных биомах.");
        add("wood_leaf", Section.WOOD, 7, "Дупло удачи",
                "20% шанс +1 саженца из сломанной листвы.");
        // ==================== ЗЕМЛЕДЕЛЕЦ ====================
        add("farm_bonus", Section.FARM, 3, "Зелёный палец",
                "25% шанс +1 урожая при сборе грядок.");
        add("farm_replant", Section.FARM, 4, "Севоборот",
                "50% шанс авто-пересадки семян при сборе зрелых культур.");
        add("farm_honey", Section.FARM, 5, "Пасечная аура",
                "20% шанс +1 соты при сборе ульев и сотовых блоков.");
        add("farm_double", Section.FARM, 7, "Богатый сбор",
                "10% шанс двойного дропа агро-веток.");
        // ==================== ФЛОРИСТ ====================
        add("flora_double", Section.FLORA, 3, "Бережный сбор",
                "15% шанс двойного дропа флоры.");
        add("flora_speed", Section.FLORA, 4, "Среди цветов",
                "Скорость I в цветочных и луговых биомах.");
        add("flora_mush", Section.FLORA, 5, "Грибная аура",
                "20% шанс +1 гриба из грибных блоков.");
        add("flora_soft", Section.FLORA, 7, "Мягкая листва",
                "Нет урона от падения на листья, мох и азалию.");
        // ==================== РЫБОЛОВ ====================
        add("sea_breath", Section.SEA, 3, "Дыхание глубин",
                "Бесконечное дыхание под водой.");
        add("sea_fin", Section.SEA, 4, "Плавник",
                "Грация дельфина в воде.");
        add("sea_double", Section.SEA, 5, "Сборщик глубин",
                "15% шанс двойного дропа морских веток.");
        add("sea_freeze", Section.SEA, 7, "Теплокровность",
                "Иммунитет к заморозке.");
        // ==================== КАМЕНЩИК ====================
        add("mason_double", Section.MASON, 3, "Знаток кладки",
                "15% шанс двойного дропа веток каменщика.");
        add("mason_haste", Section.MASON, 4, "Руки мастера",
                "Спешка I при работе секционным инструментом каменщика.");
        add("mason_soft", Section.MASON, 5, "Верёвка монтажника",
                "Урон от падения −15%.");
        add("mason_powder", Section.MASON, 7, "Стройплощадка",
                "Нет урона от падения на кладку, песок и снег.");
        // ==================== СВЕТОВОД ====================
        add("light_aura", Section.LIGHT, 3, "Чистая аура",
                "Эффект Тьмы не действует.");
        add("light_double", Section.LIGHT, 4, "Светоносец",
                "15% шанс двойного дропа световых веток.");
        add("light_speed", Section.LIGHT, 5, "Ночной курьер",
                "Скорость I в темноте (свет < 7).");
        add("light_fire", Section.LIGHT, 7, "Солярный барьер",
                "Урон от огня −50%.");
        // ==================== ИНЖЕНЕР ====================
        add("engine_double", Section.ENGINE, 3, "Контролёр механизмов",
                "15% шанс двойного дропа инженерных веток.");
        add("engine_xp", Section.ENGINE, 4, "Опытный мастер",
                "+25% опыта за добычу инженерных веток.");
        add("engine_lightning", Section.ENGINE, 5, "Изоляция",
                "Иммунитет к молнии.");
        add("engine_rail", Section.ENGINE, 7, "Рельсовый ветер",
                "Скорость II, когда стоишь на рельсах.");
        // ==================== НЕЗЕР ====================
        add("nether_fire", Section.NETHER, 3, "Огнеупорность",
                "Урон от огня и лавы −30%.");
        add("nether_double", Section.NETHER, 4, "Жаровня",
                "15% шанс двойного дропа незерских веток.");
        add("nether_soul", Section.NETHER, 5, "Поступь душ",
                "Скорость I на песке и почве душ.");
        add("nether_lava", Section.NETHER, 7, "Термозащита",
                "Огнестойкость 5с при получении огненного урона (кд 30с).");
        // ==================== КРАЙ ====================
        add("end_double", Section.END, 3, "Собиратель пустот",
                "15% шанс двойного дропа веток Края.");
        add("end_soft", Section.END, 4, "Крылья Края",
                "Нет урона от падения в Крае.");
        add("end_pearl", Section.END, 5, "Фазовый мастер",
                "Жемчуг Края не наносит урона.");
        add("ender_calm", Section.END, 7, "Спокойный взгляд",
                "Эндермены не агрятся от твоего взгляда.");
        // ==================== АНОМАЛИИ ====================
        add("sculk_quiet", Section.ANOMALY, 3, "Глушитель вибраций",
                "Скульк-сенсоры не слышат твоих шагов.");
        add("sculk_double", Section.ANOMALY, 4, "Резонанс",
                "15% шанс двойного дропа скулька.");
        add("dark_immune", Section.ANOMALY, 5, "Тёмная адаптация",
                "Иммунитет к Тьме глубокой тьмы.");
        add("echo_sense", Section.ANOMALY, 7, "Эхо-слух",
                "Ночное зрение в полной темноте.");
    }

    private static void add(String id, Section s, int tier, String name, String desc) {
        Perk p = new Perk(id, s, tier, name, desc);
        ALL.add(p);
        BY_ID.put(id, p);
        BY_SECTION.computeIfAbsent(s, k -> new ArrayList<>()).add(p);
    }

    private Perks() {}

    public static void init(DeepProgressionPlugin pl) {
        plugin = pl;
    }

    public static Perk byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static List<Perk> ofSection(Section s) {
        return BY_SECTION.getOrDefault(s, List.of());
    }

    // ---------- доступ ----------

    /** Лучшее звание среди веток секции. */
    public static int bestTier(PlayerProfile prof, Section s) {
        int best = 0;
        for (Branch b : Branch.ofSection(s)) best = Math.max(best, prof.getTier(b));
        return best;
    }

    /** Перк активен: открытым званием и не выключен игроком. */
    public static boolean has(Player p, Perk perk) {
        if (p == null || perk == null) return false;
        PlayerProfile prof = ProfileStore.get(p.getUniqueId());
        if (prof.isPerkOff(perk.id())) return false;
        return bestTier(prof, perk.section()) >= perk.tier();
    }

    public static boolean has(Player p, String perkId) {
        return has(p, BY_ID.get(perkId));
    }

    // ---------- боевые хуки (вызывает Listeners) ----------

    /** Модификация урона. Возвращает 0 если урон отменить, иначе множитель (1.0 — без изменений). */
    public static double damageModifier(Player p, org.bukkit.event.entity.EntityDamageEvent e) {
        var cause = e.getCause();
        switch (cause) {
            case FALL -> {
                if (has(p, "end_soft") && p.getWorld().getEnvironment()
                        == org.bukkit.World.Environment.THE_END) return 0;
                Block under = p.getLocation().getBlock().getRelative(BlockFace.DOWN);
                if (has(p, "flora_soft") && isSoftLeaf(under.getType())) return 0;
                if (has(p, "mason_powder") && Blocks2.isMasonGround(under.getType())) return 0;
                double m = 1.0;
                if (has(p, "mine_fall_shield")) m *= 0.7;
                if (has(p, "mason_soft")) m *= 0.85;
                return m;
            }
            case FREEZE -> {
                if (has(p, "sea_freeze")) return 0;
            }
            case LIGHTNING -> {
                if (has(p, "engine_lightning")) return 0;
            }
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> {
                if (has(p, "nether_lava")) lavaGuard(p);
                double m = 1.0;
                if (has(p, "nether_fire")) m *= 0.7;
                if (has(p, "light_fire") && (cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FIRE
                        || cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FIRE_TICK)) m *= 0.5;
                return m;
            }
            default -> { }
        }
        return 1.0;
    }

    private static void lavaGuard(Player p) {
        long now = System.currentTimeMillis();
        long last = LAVA_GUARD.getOrDefault(p.getUniqueId(), 0L);
        if (now - last < 30_000L) return;
        LAVA_GUARD.put(p.getUniqueId(), now);
        p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,
                5 * 20, 0, true, false, true));
        p.getWorld().spawnParticle(Particle.FLAME,
                p.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.02);
    }

    /** Метка телепорта жемчугом (для end_pearl). */
    public static void tagPearl(Player p) {
        PEARL_TAG.put(p.getUniqueId(), System.currentTimeMillis());
    }

    /** Урон от падения сразу после жемчуга — отменить при перке. */
    public static boolean pearlFree(Player p) {
        Long t = PEARL_TAG.remove(p.getUniqueId());
        return t != null && System.currentTimeMillis() - t < 1500L && has(p, "end_pearl");
    }

    /** Эндермены не агрятся. */
    public static boolean enderCalm(Player p) {
        return has(p, "ender_calm");
    }

    /** Сенсоры не слышат. */
    public static boolean sculkQuiet(Player p) {
        return has(p, "sculk_quiet");
    }

    /** +25% опыта (engine_xp) — множитель опыта за блок ветки. */
    public static int xpModifier(Player p, Branch b) {
        if (b != null && b.section == Section.ENGINE && has(p, "engine_xp")) return 125;
        return 100;
    }

    /** Шанс двойного дропа секции для ветки (0 — нет). */
    public static double sectionDoubleChance(Player p, Branch b) {
        if (b == null) return 0.0;
        String id = switch (b.section) {
            case MINING -> "mine_double";
            case WOOD -> "wood_double";
            case FARM -> "farm_double";
            case FLORA -> "flora_double";
            case SEA -> "sea_double";
            case MASON -> "mason_double";
            case LIGHT -> "light_double";
            case ENGINE -> "engine_double";
            case NETHER -> "nether_double";
            case END -> "end_double";
            case ANOMALY -> "sculk_double";
        };
        if (!has(p, id)) return 0.0;
        return Cfg.perkSectionDouble(id);
    }

    /** Бонусные «+1» перки. Возвращает null или материал бонуса. */
    public record Bonus(Material item, int qty, double chance) {}

    public static Bonus bonusDrop(Player p, Block block, Branch b) {
        if (b == null || block == null) return null;
        Material m = block.getType();
        String n = m.name();
        if (b.section == Section.FARM && has(p, "farm_bonus")
                && (n.equals("WHEAT") || n.equals("CARROTS") || n.equals("POTATOES")
                || n.equals("BEETROOTS") || n.equals("MELON") || n.equals("PUMPKIN")
                || n.equals("SUGAR_CANE") || n.equals("COCOA") || n.equals("NETHER_WART"))) {
            return new Bonus(firstDrop(block), 1, Cfg.perkChance("farm_bonus", 0.25));
        }
        if (b == Branch.HONEY && has(p, "farm_honey")
                && (n.equals("BEE_NEST") || n.equals("BEEHIVE") || n.equals("HONEYCOMB_BLOCK"))) {
            return new Bonus(Material.HONEYCOMB, 1, Cfg.perkChance("farm_honey", 0.20));
        }
        if (b == Branch.MUSHROOMS && has(p, "flora_mush")
                && (n.endsWith("MUSHROOM_BLOCK") || n.equals("MUSHROOM_STEM"))) {
            return new Bonus(n.startsWith("RED") ? Material.RED_MUSHROOM : Material.BROWN_MUSHROOM,
                    1, Cfg.perkChance("flora_mush", 0.20));
        }
        if (b.section == Section.WOOD && has(p, "wood_leaf") && n.endsWith("_LEAVES")) {
            String base = n.substring(0, n.length() - "_LEAVES".length());
            Material sap = Material.matchMaterial(base + "_SAPLING");
            if (sap != null) return new Bonus(sap, 1, Cfg.perkChance("wood_leaf", 0.20));
        }
        return null;
    }

    private static Material firstDrop(Block block) {
        var drops = block.getDrops();
        for (var d : drops) if (d != null && !d.getType().isAir()) return d.getType();
        return null;
    }

    private static boolean isSoftLeaf(Material m) {
        String n = m.name();
        return n.endsWith("_LEAVES") || n.equals("MOSS_BLOCK") || n.equals("MOSS_CARPET")
                || n.equals("AZALEA") || n.equals("FLOWERING_AZALEA")
                || n.equals("AZALEA_LEAVES") || n.equals("FLOWERING_AZALEA_LEAVES")
                || n.equals("PALE_MOSS_BLOCK") || n.equals("PALE_MOSS_CARPET")
                || n.equals("SPONGE") || n.equals("WET_SPONGE") || n.equals("HAY_BLOCK");
    }

    /** Материалы «стройплощадки» каменщика (mason_powder). */
    private static final class Blocks2 {
        static boolean isMasonGround(Material m) {
            Branch b = ru.deepprogression.model.Blocks.branchOf(m);
            return b != null && b.section == Section.MASON;
        }
    }

    // ---------- периодические эффекты (2с) ----------

    public static void startTask(DeepProgressionPlugin pl) {
        Bukkit.getScheduler().runTaskTimer(pl, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.isDead()) continue;
                applyAmbient(p);
            }
        }, 40L, 40L);
    }

    private static void applyAmbient(Player p) {
        Location loc = p.getLocation();
        // Периодические эффекты — короткая длительность, обновление каждые 2с
        if (has(p, "mine_sight") && loc.getY() < 0) nightVision(p);
        if (has(p, "mine_haste") && loc.getY() < 0) haste(p, 1);
        if (has(p, "wood_speed") && biomeMatches(loc,
                "forest", "jungle", "taiga", "grove", "mangrove", "bamboo", "cherry")) speed(p, 0);
        if (has(p, "flora_speed") && biomeMatches(loc,
                "forest", "meadow", "flower", "plains", "cherry", "sunflower")) speed(p, 0);
        if (has(p, "sea_breath") && p.isInWater()) waterBreath(p);
        if (has(p, "sea_fin") && p.isInWater()) dolphinsGrace(p);
        if (has(p, "mason_haste") && holdsToolOf(p, Section.MASON)) haste(p, 0);
        if (has(p, "light_speed") && loc.getBlock().getLightLevel() < 7) speed(p, 0);
        if (has(p, "nether_soul")) {
            Material under = loc.getBlock().getRelative(BlockFace.DOWN).getType();
            if (under == Material.SOUL_SAND || under == Material.SOUL_SOIL) speed(p, 0);
        }
        if (has(p, "engine_rail")) {
            Material under = loc.getBlock().getRelative(BlockFace.DOWN).getType();
            if (under == Material.RAIL || under == Material.POWERED_RAIL
                    || under == Material.DETECTOR_RAIL || under == Material.ACTIVATOR_RAIL) speed(p, 1);
        }
        if (has(p, "echo_sense") && loc.getBlock().getLightLevel() < 4) nightVision(p);
        if (has(p, "light_aura") || has(p, "dark_immune")) {
            p.removePotionEffect(PotionEffectType.DARKNESS);
        }
    }

    private static boolean biomeMatches(Location loc, String... keys) {
        String path = loc.getBlock().getBiome().getKey().getKey();
        for (String k : keys) if (path.contains(k)) return true;
        return false;
    }

    private static boolean holdsToolOf(Player p, Section s) {
        String id = Items.toolId(p.getInventory().getItemInMainHand());
        if (id == null) return false;
        Tools.ToolSpec spec = Tools.get(id);
        if (spec == null) return false;
        if (spec.type == Tools.ToolType.DRILL) {
            return spec.branch != null && spec.branch.section == s;
        }
        return spec.type == Tools.ToolType.UNIVERSAL && spec.section == s;
    }

    private static void nightVision(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                3 * 20, 0, true, false, true));
    }

    private static void haste(Player p, int amp) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,
                3 * 20, amp, true, false, true));
    }

    private static void speed(Player p, int amp) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                3 * 20, amp, true, false, true));
    }

    private static void waterBreath(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING,
                3 * 20, 0, true, false, true));
    }

    private static void dolphinsGrace(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE,
                3 * 20, 0, true, false, true));
    }

    /** Пересадка саженца/семян через тик (вызывается после ломания). */
    public static void scheduleReplant(DeepProgressionPlugin pl, Player p, Block block, Branch b) {
        if (b == null || block == null) return;
        Material mat = block.getType();
        String n = mat.name();
        Material replant = null;
        if (b.section == Section.WOOD && has(p, "wood_replant")) {
            if (n.endsWith("_LOG") && !n.startsWith("STRIPPED")
                    && !n.startsWith("CRIMSON") && !n.startsWith("WARPED")) {
                String base = n.substring(0, n.length() - "_LOG".length());
                replant = base.equals("MANGROVE")
                        ? Material.matchMaterial("MANGROVE_PROPAGULE")
                        : Material.matchMaterial(base + "_SAPLING");
            }
        } else if (b.section == Section.FARM && has(p, "farm_replant")) {
            var data = block.getBlockData();
            if (data instanceof org.bukkit.block.data.Ageable ag
                    && ag.getAge() >= ag.getMaximumAge()) {
                replant = switch (n) {
                    case "WHEAT" -> Material.WHEAT;
                    case "CARROTS" -> Material.CARROT;
                    case "POTATOES" -> Material.POTATO;
                    case "BEETROOTS" -> Material.matchMaterial("BEETROOT_SEEDS");
                    default -> null;
                };
            }
        }
        if (replant == null) return;
        double chance = b.section == Section.FARM
                ? Cfg.perkChance("farm_replant", 0.5)
                : Cfg.perkChance("wood_replant", 0.25);
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;
        Material finalMat = replant;
        Location loc = block.getLocation();
        var snapped = loc.getChunk().getChunkSnapshot(false, false, false);
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            Block target = loc.getBlock();
            if (target.getType().isAir()) {
                target.setType(finalMat, false);
                target.getWorld().spawnParticle(Particle.COMPOSTER,
                        loc.clone().add(0.5, 0.5, 0.5), 6, 0.2, 0.2, 0.2, 0);
            }
        }, 1L);
    }

    /** Очистка кэшей при выходе. */
    public static void clear(UUID uuid) {
        LAVA_GUARD.remove(uuid);
        PEARL_TAG.remove(uuid);
    }

    /** Общее число перков (selftest). */
    public static int count() {
        return ALL.size();
    }
}
