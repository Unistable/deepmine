package ru.deepprogression.model;

import org.bukkit.Material;
import ru.deepprogression.util.Cfg;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Реестр блоков DeepProgression 3.0: все ветки всех секций.
 * Единый источник истины для добычи, зонда, чанкового бура и фильтров.
 * Все имена материалов регистрируются null-safe (mat()): отсутствующие
 * в текущей версии API пропускаются с накоплением списка для лога.
 */
public final class Blocks {

    /** Стандартный дроп руды/рудного блока (без учёта удачи): предмет + количество. */
    public record DropSpec(Material item, int units) {}

    private static final Map<Material, Branch> BRANCH = new EnumMap<>(Material.class);
    private static final Map<Material, DropSpec> ORES = new EnumMap<>(Material.class);
    private static final Map<Branch, Set<Material>> VAULT_ITEMS = new EnumMap<>(Branch.class);
    private static final Set<Material> BONUS_BASE = EnumSet.noneOf(Material.class);
    private static final Map<Branch, DropSpec> BONUS = new EnumMap<>(Branch.class);
    private static final List<String> MISSING = new ArrayList<>();

    private static final String[] COLORS = {"WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE",
            "YELLOW", "LIME", "PINK", "GRAY", "LIGHT_GRAY", "CYAN", "PURPLE",
            "BLUE", "BROWN", "GREEN", "RED", "BLACK"};

    private Blocks() {}

    public static void init() {
        registerMining();
        registerWood();
        registerFarm();
        registerFlora();
        registerSea();
        registerMason();
        registerLight();
        registerEngine();
        registerNether();
        registerEnd();
        registerAnomaly();
    }

    // ==================== helpers ====================

    private static Material mat(String name) {
        Material m = Material.matchMaterial(name);
        if (m == null && !MISSING.contains(name)) MISSING.add(name);
        return m;
    }

    /**
     * v3.2.1: есть ли у материала предметная форма (можно создать ItemStack).
     * Блоки вроде *_CORAL_WALL_FAN, CAVE_VINES, KELP_PLANT существуют только как
     * блоки — попадание их в предметные реестры ломало GUI экономики.
     * Вне запущенного сервера (юнит-аудит) реестр недоступен — возвращаем true,
     * чтобы не отсекать валидные записи; на живом сервере isItem() точен.
     */
    public static boolean isItemSafe(Material m) {
        if (m == null) return false;
        try {
            return m.isItem();
        } catch (Throwable registryUnavailable) {
            return true;
        }
    }

    private static void ore(Branch b, String block, String drop, int units) {
        Material m = mat(block);
        Material d = mat(drop);
        if (m == null || d == null || !isItemSafe(d)) return;
        if (ORES.containsKey(m)) return;
        ORES.put(m, new DropSpec(d, units));
        BRANCH.put(m, b);
        vaultItemMat(b, d);
    }

    /** Блок ветки: сам себе дроп, попадает в хранилища. */
    private static void selfItem(Branch b, String block) {
        Material m = mat(block);
        if (m == null) return;
        BRANCH.put(m, b);
        // v3.2.1: в хранилища — только материалы с предметной формой
        if (isItemSafe(m)) vaultItemMat(b, m);
    }

    /** Блок ветки без попадания в хранилища (прогресс, но не ресурс). */
    private static void self(Branch b, String block) {
        Material m = mat(block);
        if (m == null) return;
        BRANCH.put(m, b);
    }

    /** Предмет ветки (не блок): только фильтр хранилищ. */
    private static void vaultItemMat(Branch b, Material m) {
        // v3.2.1: отсекаем блоки без предметной формы (безопасность GUI)
        if (m != null && isItemSafe(m)) VAULT_ITEMS.computeIfAbsent(b, k -> EnumSet.noneOf(Material.class)).add(m);
    }

    private static void vaultItem(Branch b, String item) {
        vaultItemMat(b, mat(item));
    }

    private static void bonusBase(String... blocks) {
        for (String s : blocks) {
            Material m = mat(s);
            if (m != null) BONUS_BASE.add(m);
        }
    }

    private static void bonus(Branch b, String item, int units) {
        Material m = mat(item);
        if (m != null) BONUS.put(b, new DropSpec(m, units));
    }

    // ==================== ШАХТЁР (12) ====================

    private static void registerMining() {
        // Руды
        ore(Branch.COAL, "COAL_ORE", "COAL", 1);
        ore(Branch.COAL, "DEEPSLATE_COAL_ORE", "COAL", 1);
        ore(Branch.COPPER, "COPPER_ORE", "RAW_COPPER", 3);
        ore(Branch.COPPER, "DEEPSLATE_COPPER_ORE", "RAW_COPPER", 3);
        ore(Branch.IRON, "IRON_ORE", "RAW_IRON", 1);
        ore(Branch.IRON, "DEEPSLATE_IRON_ORE", "RAW_IRON", 1);
        ore(Branch.GOLD, "GOLD_ORE", "RAW_GOLD", 1);
        ore(Branch.GOLD, "DEEPSLATE_GOLD_ORE", "RAW_GOLD", 1);
        ore(Branch.GOLD, "NETHER_GOLD_ORE", "GOLD_NUGGET", 4);
        ore(Branch.REDSTONE, "REDSTONE_ORE", "REDSTONE", 4);
        ore(Branch.REDSTONE, "DEEPSLATE_REDSTONE_ORE", "REDSTONE", 4);
        ore(Branch.LAPIS, "LAPIS_ORE", "LAPIS_LAZULI", 6);
        ore(Branch.LAPIS, "DEEPSLATE_LAPIS_ORE", "LAPIS_LAZULI", 6);
        ore(Branch.DIAMOND, "DIAMOND_ORE", "DIAMOND", 1);
        ore(Branch.DIAMOND, "DEEPSLATE_DIAMOND_ORE", "DIAMOND", 1);
        ore(Branch.EMERALD, "EMERALD_ORE", "EMERALD", 1);
        ore(Branch.EMERALD, "DEEPSLATE_EMERALD_ORE", "EMERALD", 1);
        ore(Branch.QUARTZ, "NETHER_QUARTZ_ORE", "QUARTZ", 1);
        ore(Branch.DEBRIS, "ANCIENT_DEBRIS", "ANCIENT_DEBRIS", 1);
        vaultItem(Branch.DEBRIS, "NETHERITE_SCRAP");
        vaultItem(Branch.DEBRIS, "NETHERITE_INGOT");

        // Рудные блоки (складирование) — тоже "руда" с дропом ×9/×4
        ore(Branch.COAL, "COAL_BLOCK", "COAL", 9);
        ore(Branch.COPPER, "COPPER_BLOCK", "COPPER_INGOT", 9);
        ore(Branch.COPPER, "RAW_COPPER_BLOCK", "RAW_COPPER", 9);
        ore(Branch.IRON, "IRON_BLOCK", "IRON_INGOT", 9);
        ore(Branch.IRON, "RAW_IRON_BLOCK", "RAW_IRON", 9);
        ore(Branch.GOLD, "GOLD_BLOCK", "GOLD_INGOT", 9);
        ore(Branch.GOLD, "RAW_GOLD_BLOCK", "RAW_GOLD", 9);
        ore(Branch.REDSTONE, "REDSTONE_BLOCK", "REDSTONE", 9);
        ore(Branch.LAPIS, "LAPIS_BLOCK", "LAPIS_LAZULI", 9);
        ore(Branch.DIAMOND, "DIAMOND_BLOCK", "DIAMOND", 9);
        ore(Branch.EMERALD, "EMERALD_BLOCK", "EMERALD", 9);
        ore(Branch.DEBRIS, "NETHERITE_BLOCK", "NETHERITE_INGOT", 9);
        ore(Branch.QUARTZ, "QUARTZ_BLOCK", "QUARTZ", 4);
        ore(Branch.QUARTZ, "QUARTZ_PILLAR", "QUARTZ", 4);
        ore(Branch.AMETHYST, "AMETHYST_BLOCK", "AMETHYST_SHARD", 4);
        ore(Branch.AMETHYST, "AMETHYST_CLUSTER", "AMETHYST_SHARD", 4);
        ore(Branch.AMETHYST, "LARGE_AMETHYST_BUD", "AMETHYST_SHARD", 4);
        ore(Branch.AMETHYST, "MEDIUM_AMETHYST_BUD", "AMETHYST_SHARD", 2);
        ore(Branch.AMETHYST, "SMALL_AMETHYST_BUD", "AMETHYST_SHARD", 1);
        self(Branch.AMETHYST, "BUDDING_AMETHYST"); // без шёлка не дропается

        // Размещённая пыль редстоуна — прогресс и ванильный дроп
        self(Branch.REDSTONE, "REDSTONE_WIRE");

        // Каменное семейство
        String[] stone = {"STONE", "COBBLESTONE", "DEEPSLATE", "COBBLED_DEEPSLATE",
                "GRANITE", "DIORITE", "ANDESITE", "TUFF", "CALCITE",
                "DRIPSTONE_BLOCK", "POINTED_DRIPSTONE", "SMOOTH_STONE",
                "STONE_STAIRS", "STONE_SLAB", "STONE_PRESSURE_PLATE",
                "COBBLESTONE_STAIRS", "COBBLESTONE_SLAB", "COBBLESTONE_WALL",
                "COBBLED_DEEPSLATE_STAIRS", "COBBLED_DEEPSLATE_SLAB",
                "COBBLED_DEEPSLATE_WALL", "GRANITE_STAIRS", "GRANITE_SLAB",
                "DIORITE_STAIRS", "DIORITE_SLAB", "ANDESITE_STAIRS",
                "ANDESITE_SLAB", "TUFF_STAIRS", "TUFF_SLAB", "TUFF_WALL",
                "TUFF_BRICKS", "CHISELED_TUFF", "POLISHED_TUFF"};
        for (String s : stone) selfItem(Branch.STONE, s);

        // Бонусная база: камень и природные массивы миров
        bonusBase("STONE", "COBBLESTONE", "DEEPSLATE", "COBBLED_DEEPSLATE",
                "GRANITE", "DIORITE", "ANDESITE", "TUFF", "CALCITE",
                "NETHERRACK", "BLACKSTONE", "BASALT", "END_STONE", "SMOOTH_STONE");

        // Бонусные предметы "из камня"
        bonus(Branch.STONE, "COAL", 1);
        bonus(Branch.COAL, "COAL", 1);
        bonus(Branch.COPPER, "RAW_COPPER", 1);
        bonus(Branch.IRON, "RAW_IRON", 1);
        bonus(Branch.GOLD, "RAW_GOLD", 1);
        bonus(Branch.REDSTONE, "REDSTONE", 2);
        bonus(Branch.LAPIS, "LAPIS_LAZULI", 2);
        bonus(Branch.DIAMOND, "DIAMOND", 1);
        bonus(Branch.EMERALD, "EMERALD", 1);
        bonus(Branch.QUARTZ, "QUARTZ", 1);
        bonus(Branch.DEBRIS, "ANCIENT_DEBRIS", 1);
        bonus(Branch.AMETHYST, "AMETHYST_SHARD", 1);
    }

    // ==================== ЛЕСОРУБ (10) ====================

    private static void registerWood() {
        String[] woods = {"OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA",
                "DARK_OAK", "MANGROVE", "CHERRY", "PALE_OAK"};
        for (String w : woods) {
            Branch b = Branch.valueOf(w);
            selfItem(b, w + "_LOG");
            selfItem(b, w + "_WOOD");
            selfItem(b, "STRIPPED_" + w + "_LOG");
            selfItem(b, "STRIPPED_" + w + "_WOOD");
            selfItem(b, w + "_PLANKS");
            selfItem(b, w + "_STAIRS");
            selfItem(b, w + "_SLAB");
            self(b, w + "_LEAVES");
            if (w.equals("MANGROVE")) {
                // у мангрового нет сапляка-семени — саженец уже зарегистрирован как MANGROVE_PROPAGULE
            } else {
                self(b, w + "_SAPLING");
            }
        }
        // Мангровые особенности
        selfItem(Branch.MANGROVE, "MANGROVE_ROOTS");
        selfItem(Branch.MANGROVE, "MUDDY_MANGROVE_ROOTS");
        self(Branch.MANGROVE, "MANGROVE_PROPAGULE");
        // Смола пейл-дуба
        selfItem(Branch.RESIN, "RESIN_CLUMP");
        selfItem(Branch.RESIN, "RESIN_BLOCK");
        self(Branch.RESIN, "CREAKING_HEART");
        vaultItem(Branch.RESIN, "RESIN_BRICK");
    }

    // ==================== ЗЕМЛЕДЕЛЕЦ (11) ====================

    private static void registerFarm() {
        selfItem(Branch.WHEAT, "WHEAT");
        selfItem(Branch.WHEAT, "HAY_BLOCK");
        vaultItem(Branch.WHEAT, "WHEAT_SEEDS");
        self(Branch.CARROT, "CARROTS"); // v3.2.1: грядка — блок без предмета
        vaultItem(Branch.CARROT, "CARROT");
        self(Branch.POTATO, "POTATOES"); // v3.2.1: грядка — блок без предмета
        vaultItem(Branch.POTATO, "POTATO");
        self(Branch.BEETROOT, "BEETROOTS"); // v3.2.1: грядка — блок без предмета
        vaultItem(Branch.BEETROOT, "BEETROOT");
        vaultItem(Branch.BEETROOT, "BEETROOT_SEEDS");
        selfItem(Branch.MELON, "MELON");
        vaultItem(Branch.MELON, "MELON_SLICE");
        vaultItem(Branch.MELON, "MELON_SEEDS");
        selfItem(Branch.PUMPKIN, "PUMPKIN");
        selfItem(Branch.PUMPKIN, "CARVED_PUMPKIN");
        vaultItem(Branch.PUMPKIN, "PUMPKIN_SEEDS");
        selfItem(Branch.SUGAR_CANE, "SUGAR_CANE");
        vaultItem(Branch.SUGAR_CANE, "SUGAR");
        self(Branch.COCOA, "COCOA"); // v3.2.1: какао-плоды — блок без предмета
        vaultItem(Branch.COCOA, "COCOA_BEANS");
        selfItem(Branch.NETHER_WART, "NETHER_WART");
        for (String c : COLORS) selfItem(Branch.WOOL, c + "_WOOL");
        selfItem(Branch.HONEY, "HONEY_BLOCK");
        selfItem(Branch.HONEY, "HONEYCOMB_BLOCK");
        self(Branch.HONEY, "BEE_NEST");
        self(Branch.HONEY, "BEEHIVE");
        vaultItem(Branch.HONEY, "HONEYCOMB");
        vaultItem(Branch.HONEY, "HONEY_BOTTLE");
        vaultItem(Branch.HONEY, "HONEY_BLOCK");
    }

    // ==================== ФЛОРИСТ (6) ====================

    private static void registerFlora() {
        selfItem(Branch.CACTUS, "CACTUS");
        selfItem(Branch.CACTUS, "CACTUS_FLOWER");
        selfItem(Branch.BAMBOO, "BAMBOO");
        selfItem(Branch.BAMBOO, "BAMBOO_BLOCK");
        selfItem(Branch.BAMBOO, "BAMBOO_MOSAIC");
        self(Branch.BAMBOO, "BAMBOO_SAPLING");
        vaultItem(Branch.BAMBOO, "BAMBOO_PLANKS");
        String[] flowers = {"DANDELION", "POPPY", "BLUE_ORCHID", "ALLIUM",
                "AZURE_BLUET", "RED_TULIP", "ORANGE_TULIP", "WHITE_TULIP",
                "PINK_TULIP", "OXEYE_DAISY", "CORNFLOWER", "LILY_OF_THE_VALLEY",
                "WITHER_ROSE", "TORCHFLOWER", "OPEN_EYEBLOSSOM", "CLOSED_EYEBLOSSOM",
                "PINK_PETALS", "WILDFLOWERS", "SUNFLOWER", "LILAC", "ROSE_BUSH",
                "PEONY", "SHORT_GRASS", "TALL_GRASS", "FERN", "LARGE_FERN", "DEAD_BUSH"};
        for (String f : flowers) selfItem(Branch.FLOWERS, f);
        selfItem(Branch.MOSS, "MOSS_BLOCK");
        selfItem(Branch.MOSS, "MOSS_CARPET");
        selfItem(Branch.MOSS, "PALE_MOSS_BLOCK");
        selfItem(Branch.MOSS, "PALE_MOSS_CARPET");
        selfItem(Branch.MOSS, "PALE_HANGING_MOSS");
        selfItem(Branch.MOSS, "HANGING_ROOTS");
        selfItem(Branch.MOSS, "ROOTED_DIRT");
        selfItem(Branch.AZALEA, "AZALEA");
        selfItem(Branch.AZALEA, "FLOWERING_AZALEA");
        selfItem(Branch.AZALEA, "AZALEA_LEAVES");
        selfItem(Branch.AZALEA, "FLOWERING_AZALEA_LEAVES");
        selfItem(Branch.AZALEA, "BIG_DRIPLEAF");
        selfItem(Branch.AZALEA, "SMALL_DRIPLEAF");
        selfItem(Branch.MUSHROOMS, "BROWN_MUSHROOM");
        selfItem(Branch.MUSHROOMS, "RED_MUSHROOM");
        selfItem(Branch.MUSHROOMS, "BROWN_MUSHROOM_BLOCK");
        selfItem(Branch.MUSHROOMS, "RED_MUSHROOM_BLOCK");
        selfItem(Branch.MUSHROOMS, "MUSHROOM_STEM");
        selfItem(Branch.MUSHROOMS, "CRIMSON_FUNGUS");
        selfItem(Branch.MUSHROOMS, "WARPED_FUNGUS");
    }

    // ==================== РЫБОЛОВ (4) ====================

    private static void registerSea() {
        selfItem(Branch.KELP, "KELP");
        self(Branch.KELP, "KELP_PLANT");      // v3.2.1: подводное растение — блок без предмета
        selfItem(Branch.KELP, "SEAGRASS");
        self(Branch.KELP, "TALL_SEAGRASS"); // v3.2.1: блок без предмета
        selfItem(Branch.KELP, "DRIED_KELP_BLOCK");
        vaultItem(Branch.KELP, "DRIED_KELP");
        String[] corals = {"TUBE", "BRAIN", "BUBBLE", "FIRE", "HORN"};
        for (String c : corals) {
            selfItem(Branch.CORAL, c + "_CORAL_BLOCK");
            selfItem(Branch.CORAL, c + "_CORAL");
            selfItem(Branch.CORAL, c + "_CORAL_FAN");
            self(Branch.CORAL, c + "_CORAL_WALL_FAN"); // v3.2.1: фикс "HORN_CORAL_WALL_FAN isn't an item"
            selfItem(Branch.CORAL, "DEAD_" + c + "_CORAL_BLOCK");
        }
        selfItem(Branch.SPONGE, "SPONGE");
        selfItem(Branch.SPONGE, "WET_SPONGE");
        selfItem(Branch.PRISMARINE, "PRISMARINE");
        selfItem(Branch.PRISMARINE, "PRISMARINE_BRICKS");
        selfItem(Branch.PRISMARINE, "DARK_PRISMARINE");
        vaultItem(Branch.PRISMARINE, "PRISMARINE_SHARD");
    }

    // ==================== КАМЕНЩИК (8) ====================

    private static void registerMason() {
        selfItem(Branch.SAND, "SAND");
        selfItem(Branch.SAND, "RED_SAND");
        selfItem(Branch.GRAVEL, "GRAVEL");
        selfItem(Branch.SANDSTONE, "SANDSTONE");
        selfItem(Branch.SANDSTONE, "CUT_SANDSTONE");
        selfItem(Branch.SANDSTONE, "CHISELED_SANDSTONE");
        selfItem(Branch.SANDSTONE, "SMOOTH_SANDSTONE");
        selfItem(Branch.SANDSTONE, "RED_SANDSTONE");
        selfItem(Branch.SANDSTONE, "CUT_RED_SANDSTONE");
        selfItem(Branch.SANDSTONE, "CHISELED_RED_SANDSTONE");
        selfItem(Branch.SANDSTONE, "SMOOTH_RED_SANDSTONE");
        selfItem(Branch.SNOW_ICE, "SNOW");
        selfItem(Branch.SNOW_ICE, "SNOW_BLOCK");
        selfItem(Branch.SNOW_ICE, "ICE");
        selfItem(Branch.SNOW_ICE, "PACKED_ICE");
        selfItem(Branch.SNOW_ICE, "BLUE_ICE");
        vaultItem(Branch.SNOW_ICE, "SNOWBALL");
        selfItem(Branch.GLASS, "GLASS");
        selfItem(Branch.GLASS, "TINTED_GLASS");
        selfItem(Branch.GLASS, "GLASS_PANE");
        for (String c : COLORS) {
            selfItem(Branch.GLASS, c + "_STAINED_GLASS");
            selfItem(Branch.GLASS, c + "_STAINED_GLASS_PANE");
        }
        selfItem(Branch.BRICKS, "BRICKS");
        selfItem(Branch.BRICKS, "STONE_BRICKS");
        selfItem(Branch.BRICKS, "MOSSY_STONE_BRICKS");
        selfItem(Branch.BRICKS, "CRACKED_STONE_BRICKS");
        selfItem(Branch.BRICKS, "CHISELED_STONE_BRICKS");
        selfItem(Branch.BRICKS, "DEEPSLATE_BRICKS");
        selfItem(Branch.BRICKS, "DEEPSLATE_TILES");
        selfItem(Branch.BRICKS, "CRACKED_DEEPSLATE_BRICKS");
        selfItem(Branch.BRICKS, "CRACKED_DEEPSLATE_TILES");
        selfItem(Branch.BRICKS, "CHISELED_DEEPSLATE");
        selfItem(Branch.BRICKS, "POLISHED_DEEPSLATE");
        selfItem(Branch.BRICKS, "NETHER_BRICKS");
        selfItem(Branch.BRICKS, "RED_NETHER_BRICKS");
        selfItem(Branch.BRICKS, "CRACKED_NETHER_BRICKS");
        selfItem(Branch.BRICKS, "CHISELED_NETHER_BRICKS");
        selfItem(Branch.BRICKS, "MOSSY_COBBLESTONE");
        vaultItem(Branch.BRICKS, "BRICK");
        selfItem(Branch.TERRACOTTA, "TERRACOTTA");
        for (String c : COLORS) selfItem(Branch.TERRACOTTA, c + "_TERRACOTTA");
        for (String c : COLORS) {
            selfItem(Branch.CONCRETE, c + "_CONCRETE");
            selfItem(Branch.CONCRETE, c + "_CONCRETE_POWDER");
        }
    }

    // ==================== СВЕТОВОД (6) ====================

    private static void registerLight() {
        selfItem(Branch.GLOWSTONE, "GLOWSTONE");
        vaultItem(Branch.GLOWSTONE, "GLOWSTONE_DUST");
        selfItem(Branch.SEA_LANTERN, "SEA_LANTERN");
        vaultItem(Branch.SEA_LANTERN, "PRISMARINE_CRYSTALS");
        selfItem(Branch.SHROOMLIGHT, "SHROOMLIGHT");
        selfItem(Branch.FROG_LIGHT, "OCHRE_FROGLIGHT");
        selfItem(Branch.FROG_LIGHT, "VERDANT_FROGLIGHT");
        selfItem(Branch.FROG_LIGHT, "PEARLESCENT_FROGLIGHT");
        selfItem(Branch.CANDLE, "CANDLE");
        for (String c : COLORS) selfItem(Branch.CANDLE, c + "_CANDLE");
        selfItem(Branch.GLOW_FLORA, "GLOW_LICHEN");
        self(Branch.GLOW_FLORA, "CAVE_VINES");       // v3.2.1: блок без предмета (дроп — GLOW_BERRIES)
        self(Branch.GLOW_FLORA, "CAVE_VINES_PLANT"); // v3.2.1: блок без предмета
        selfItem(Branch.GLOW_FLORA, "SEA_PICKLE");
        selfItem(Branch.GLOW_FLORA, "JACK_O_LANTERN");
        selfItem(Branch.GLOW_FLORA, "END_ROD");
        selfItem(Branch.GLOW_FLORA, "LANTERN");
        selfItem(Branch.GLOW_FLORA, "SOUL_LANTERN");
        selfItem(Branch.GLOW_FLORA, "REDSTONE_LAMP");
        vaultItem(Branch.GLOW_FLORA, "GLOW_BERRIES");
        vaultItem(Branch.GLOW_FLORA, "GLOW_INK_SAC");
    }

    // ==================== ИНЖЕНЕР (3) ====================

    private static void registerEngine() {
        String[] mech = {"PISTON", "STICKY_PISTON", "OBSERVER", "HOPPER", "DROPPER",
                "DISPENSER", "CRAFTER", "REPEATER", "COMPARATOR", "LEVER",
                "TRIPWIRE_HOOK", "NOTE_BLOCK", "JUKEBOX", "TARGET",
                "DAYLIGHT_DETECTOR", "REDSTONE_TORCH"};
        for (String s : mech) selfItem(Branch.MECHANISMS, s);
        // Меднотех: стадии окисления × формы
        String[] stages = {"", "EXPOSED_", "WEATHERED_", "OXIDIZED_",
                "WAXED_", "WAXED_EXPOSED_", "WAXED_WEATHERED_", "WAXED_OXIDIZED_"};
        String[] forms = {"CUT_COPPER", "CUT_COPPER_STAIRS", "CUT_COPPER_SLAB",
                "CHISELED_COPPER", "COPPER_GRATE", "COPPER_BULB",
                "COPPER_DOOR", "COPPER_TRAPDOOR", "COPPER_BARS", "COPPER_CHEST",
                "COPPER_GOLEM_STATUE"};
        for (String st : stages) {
            for (String f : forms) selfItem(Branch.COPPER_TECH, st + f);
        }
        selfItem(Branch.COPPER_TECH, "COPPER_TORCH");
        selfItem(Branch.COPPER_TECH, "COPPER_LANTERN");
        for (String r : new String[]{"RAIL", "POWERED_RAIL", "DETECTOR_RAIL",
                "ACTIVATOR_RAIL"}) selfItem(Branch.RAILS, r);
    }

    // ==================== НЕЗЕР (8) ====================

    private static void registerNether() {
        selfItem(Branch.NETHERRACK, "NETHERRACK");
        selfItem(Branch.SOUL, "SOUL_SAND");
        selfItem(Branch.SOUL, "SOUL_SOIL");
        selfItem(Branch.MAGMA, "MAGMA_BLOCK");
        vaultItem(Branch.MAGMA, "MAGMA_CREAM");
        for (String s : new String[]{"BLACKSTONE", "GILDED_BLACKSTONE",
                "POLISHED_BLACKSTONE", "POLISHED_BLACKSTONE_BRICKS",
                "CHISELED_POLISHED_BLACKSTONE",
                "CRACKED_POLISHED_BLACKSTONE_BRICKS"}) selfItem(Branch.BLACKSTONE, s);
        for (String s : new String[]{"BASALT", "POLISHED_BASALT", "SMOOTH_BASALT"})
            selfItem(Branch.BASALT, s);
        for (String s : new String[]{"CRIMSON_STEM", "CRIMSON_HYPHAE",
                "STRIPPED_CRIMSON_STEM", "STRIPPED_CRIMSON_HYPHAE", "CRIMSON_PLANKS",
                "CRIMSON_NYLIUM", "CRIMSON_ROOTS"}) selfItem(Branch.CRIMSON, s);
        for (String s : new String[]{"WARPED_STEM", "WARPED_HYPHAE",
                "STRIPPED_WARPED_STEM", "STRIPPED_WARPED_HYPHAE", "WARPED_PLANKS",
                "WARPED_NYLIUM", "WARPED_ROOTS"}) selfItem(Branch.WARPED, s);
        for (String s : new String[]{"NETHER_WART_BLOCK", "WARPED_WART_BLOCK",
                "TWISTING_VINES", "WEEPING_VINES", "NETHER_SPROUTS"}) selfItem(Branch.WART, s);
        self(Branch.WART, "TWISTING_VINES_PLANT"); // v3.2.1: блок без предмета
        self(Branch.WART, "WEEPING_VINES_PLANT");  // v3.2.1: блок без предмета
    }

    // ==================== КРАЙ (4) ====================

    private static void registerEnd() {
        selfItem(Branch.END_STONE, "END_STONE");
        selfItem(Branch.END_STONE, "END_STONE_BRICKS");
        selfItem(Branch.PURPUR, "PURPUR_BLOCK");
        selfItem(Branch.PURPUR, "PURPUR_PILLAR");
        selfItem(Branch.CHORUS, "CHORUS_PLANT");
        selfItem(Branch.CHORUS, "CHORUS_FLOWER");
        vaultItem(Branch.CHORUS, "CHORUS_FRUIT");
        vaultItem(Branch.CHORUS, "POPPED_CHORUS_FRUIT");
        vaultItem(Branch.CHORUS, "ENDER_PEARL");
        selfItem(Branch.OBSIDIAN, "OBSIDIAN");
        selfItem(Branch.OBSIDIAN, "CRYING_OBSIDIAN");
        selfItem(Branch.OBSIDIAN, "RESPAWN_ANCHOR");
    }

    // ==================== АНОМАЛИИ (1) ====================

    private static void registerAnomaly() {
        for (String s : new String[]{"SCULK", "SCULK_VEIN", "SCULK_CATALYST",
                "SCULK_SHRIEKER", "SCULK_SENSOR", "CALIBRATED_SCULK_SENSOR"})
            selfItem(Branch.SCULK, s);
    }

    // ==================== API ====================

    /** Ветка блока, null — не относится. */
    public static Branch branchOf(Material m) {
        return m == null ? null : BRANCH.get(m);
    }

    /** Только руды (для зонда и чанкового бура). */
    public static Branch branchOfOre(Material m) {
        Branch b = branchOf(m);
        return (b != null && ORES.containsKey(m)) ? b : null;
    }

    /** Стандартный дроп руды, null — не руда. */
    public static DropSpec dropOf(Material m) {
        return m == null ? null : ORES.get(m);
    }

    /** Предмет считается ресурсом какой-то ветки (для тайника/склада/автоподбора). */
    public static boolean isVaultItem(Material m) {
        return branchOfItem(m) != null;
    }

    /** Ветка, которой принадлежит предмет-ресурс. */
    public static Branch branchOfItem(Material m) {
        if (m == null) return null;
        for (var e : VAULT_ITEMS.entrySet()) if (e.getValue().contains(m)) return e.getKey();
        return null;
    }

    /** Бонусная база: камень и природные массивы миров. */
    public static boolean isBonusBase(Material m) {
        return m != null && BONUS_BASE.contains(m);
    }

    /** Бонусный предмет "из камня" для ветки инструмента. */
    public static DropSpec bonusOf(Branch b) {
        return BONUS.get(b);
    }

    /** Базовый камень для замещения руды при выработке чанкбура. */
    public static Material baseFor(Material oreBlock, int y) {
        if (oreBlock == Material.NETHER_QUARTZ_ORE || oreBlock == Material.NETHER_GOLD_ORE
                || oreBlock == Material.ANCIENT_DEBRIS) return Material.NETHERRACK;
        if (y < 0) return Material.DEEPSLATE;
        return Material.STONE;
    }

    /** Все ресурсы всех веток. */
    public static Set<Material> vaultItems() {
        Set<Material> all = EnumSet.noneOf(Material.class);
        for (Set<Material> s : VAULT_ITEMS.values()) all.addAll(s);
        return all;
    }

    /** Материалы, отсутствующие в текущем API (для лога при запуске). */
    public static List<String> missingMaterials() {
        return MISSING;
    }

    /** Дешёвая проверка "мир разрешён". */
    public static boolean worldAllowed(org.bukkit.World w) {
        return Cfg.worldAllowed(w);
    }
}
