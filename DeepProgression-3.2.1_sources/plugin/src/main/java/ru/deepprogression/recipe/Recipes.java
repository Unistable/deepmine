package ru.deepprogression.recipe;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.deepprogression.DeepProgressionPlugin;
import ru.deepprogression.model.Branch;
import ru.deepprogression.model.Section;
import ru.deepprogression.progress.Quests;
import ru.deepprogression.tools.Items;
import ru.deepprogression.util.Keys;
import ru.deepprogression.util.Msg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Крафты DeepProgression 3.0 — двойной механизм:
 *  1) ванильные ShapedRecipe для рецептов из чисто ванильных материалов;
 *  2) ручной matcher (PrepareItemCraftEvent + клик результата) для рецептов
 *     с кастомными ингредиентами и медалями (гейты прогрессии).
 * Веточные инструменты и универсалы генерируются циклами из реестра.
 */
public final class Recipes {

    /** Ингредиент: ванильный материал | наш предмет (toolId) | медаль звания. */
    public record Ing(Material mat, String toolId, String medalBranch, int medalTier) {
        public static Ing of(Material m) { return new Ing(m, null, null, 0); }
        public static Ing tool(String id) { return new Ing(null, id, null, 0); }
        public static Ing medal(String branch, int tier) { return new Ing(null, null, branch, tier); }
    }

    public record Spec(String resultId, int count, String[] rows, Map<Character, Ing> ing) {}

    private static final Map<String, Spec> SPECS = new LinkedHashMap<>();
    private static final Map<String, String[]> VAN_SHAPE = new LinkedHashMap<>();
    private static final Map<String, Map<Character, Material>> VAN_ING = new LinkedHashMap<>();
    private static final Set<NamespacedKey> OURS = new HashSet<>();
    private static NamespacedKey geologistKey;

    /** Флагманская ветка секции — гейт универсальных инструментов и машин. */
    public static Branch flagshipOf(Section s) {
        return switch (s) {
            case MINING -> Branch.IRON;
            case WOOD -> Branch.OAK;
            case FARM -> Branch.WHEAT;
            case FLORA -> Branch.FLOWERS;
            case SEA -> Branch.KELP;
            case MASON -> Branch.SAND;
            case LIGHT -> Branch.GLOWSTONE;
            case ENGINE -> Branch.MECHANISMS;
            case NETHER -> Branch.NETHERRACK;
            case END -> Branch.END_STONE;
            case ANOMALY -> Branch.SCULK;
        };
    }

    private Recipes() {}

    public static void registerAll(DeepProgressionPlugin plugin) {
        SPECS.clear();
        VAN_SHAPE.clear();
        VAN_ING.clear();
        OURS.clear();
        geologistKey = new NamespacedKey(plugin, "geologist_pick");

        // ---------- Ванильные рецепты (чистые ингредиенты) ----------
        vanilla(plugin, "geologist_pick",
                new String[]{"CIC", "IDI", "SSS"},
                Map.of('C', Material.COBBLESTONE, 'I', Material.IRON_INGOT,
                        'D', Material.DIAMOND, 'S', Material.STICK));
        vanilla(plugin, "steel_ingot",
                new String[]{"IIC"},
                Map.of('I', Material.IRON_INGOT, 'C', Material.COAL));
        vanilla(plugin, "copper_wire",
                new String[]{"C"}, 3,
                Map.of('C', Material.COPPER_INGOT));
        vanilla(plugin, "redstone_coil",
                new String[]{"RCR"},
                Map.of('R', Material.REDSTONE, 'C', Material.COPPER_INGOT));
        vanilla(plugin, "coal_electrode",
                new String[]{"CCC", ".C."}, 2,
                Map.of('C', Material.COAL));
        vanilla(plugin, "quartz_chip",
                new String[]{"QQ", "RR"}, 2,
                Map.of('Q', Material.QUARTZ, 'R', Material.REDSTONE));
        vanilla(plugin, "iron_gear",
                new String[]{"I I", ".I.", "I I"},
                Map.of('I', Material.IRON_INGOT));
        vanilla(plugin, "obsidian_casing",
                new String[]{"OO", "OO"},
                Map.of('O', Material.OBSIDIAN));
        vanilla(plugin, "diamond_edge",
                new String[]{"D D", ".D."},
                Map.of('D', Material.DIAMOND));
        vanilla(plugin, "netherite_core",
                new String[]{"DDD", ".N."},
                Map.of('D', Material.DIAMOND, 'N', Material.ANCIENT_DEBRIS));
        vanilla(plugin, "lumin_core",
                new String[]{"GDG", ".S."},
                Map.of('G', Material.GLOWSTONE_DUST, 'D', Material.GLOW_INK_SAC,
                        'S', Material.SEA_LANTERN));
        vanilla(plugin, "chorus_lens",
                new String[]{".E.", "A.A", ".C."},
                Map.of('E', Material.ENDER_EYE, 'A', Material.AMETHYST_SHARD,
                        'C', Material.COPPER_INGOT));

        // ---------- Ручные рецепты: инженерные детали ----------
        spec("steel_plate", 2, new String[]{"SS"},
                Map.of('S', Ing.tool("steel_ingot")));
        spec("copper_coil", 1, new String[]{"W", "R", "W"},
                Map.of('W', Ing.tool("copper_wire"), 'R', Ing.of(Material.REDSTONE)));
        spec("drill_rod", 1, new String[]{"S", "S", "S"},
                Map.of('S', Ing.tool("steel_ingot")));
        spec("reinforced_frame", 1, new String[]{"PPP", "PGP", "PPP"},
                Map.of('P', Ing.tool("steel_plate"), 'G', Ing.tool("iron_gear")));
        spec("thermal_core", 1, new String[]{".O.", "MMM", ".C."},
                Map.of('O', Ing.tool("obsidian_casing"),
                        'M', Ing.of(Material.MAGMA_CREAM),
                        'C', Ing.tool("copper_coil")));
        spec("scan_module", 1, new String[]{"A", "Q", "C"},
                Map.of('A', Ing.of(Material.AMETHYST_SHARD),
                        'Q', Ing.tool("quartz_chip"), 'C', Ing.tool("copper_coil")));
        spec("saw_blade", 1, new String[]{"DID", ".G."},
                Map.of('D', Ing.tool("diamond_edge"),
                        'I', Ing.of(Material.IRON_INGOT),
                        'G', Ing.tool("iron_gear")));
        spec("bio_module", 1, new String[]{"BWB", ".P."},
                Map.of('B', Ing.of(Material.BONE_MEAL),
                        'W', Ing.of(Material.WHEAT),
                        'P', Ing.tool("board_basic")));
        spec("salt_filter", 1, new String[]{"S.S", ".C."},
                Map.of('S', Ing.of(Material.PRISMARINE_SHARD),
                        'C', Ing.tool("copper_coil")));
        spec("servo_drive", 1, new String[]{".K.", "RCG"},
                Map.of('K', Ing.of(Material.CLOCK),
                        'R', Ing.tool("redstone_coil"),
                        'C', Ing.tool("copper_coil"),
                        'G', Ing.tool("iron_gear")));
        spec("flame_relic", 1, new String[]{".O.", "BBB", ".G."},
                Map.of('O', Ing.tool("obsidian_casing"),
                        'B', Ing.of(Material.BLAZE_POWDER),
                        'G', Ing.of(Material.GOLD_INGOT)));

        // ---------- Платы ----------
        spec("board_basic", 2, new String[]{"PPP", "W.E"},
                Map.of('P', Ing.of(Material.PAPER),
                        'W', Ing.tool("copper_wire"), 'E', Ing.tool("coal_electrode")));
        spec("board_advanced", 1, new String[]{"QCQ", ".B."},
                Map.of('Q', Ing.tool("quartz_chip"),
                        'C', Ing.tool("copper_coil"), 'B', Ing.tool("board_basic")));
        spec("board_elite", 1, new String[]{"D D", ".A."},
                Map.of('D', Ing.tool("diamond_edge"), 'A', Ing.tool("board_advanced")));

        // ---------- Узлы ----------
        spec("node_drive", 1, new String[]{"G G", ".B.", "G G"},
                Map.of('G', Ing.tool("iron_gear"), 'B', Ing.tool("board_basic")));
        spec("node_power", 1, new String[]{"R W", ".S."},
                Map.of('R', Ing.tool("redstone_coil"),
                        'W', Ing.tool("copper_coil"), 'S', Ing.tool("steel_plate")));
        spec("node_control", 1, new String[]{"Q C", ".B."},
                Map.of('Q', Ing.tool("quartz_chip"),
                        'C', Ing.tool("copper_coil"), 'B', Ing.tool("board_advanced")));
        spec("tool_body", 1, new String[]{"PDP", "RRR", "PPP"},
                Map.of('P', Ing.tool("steel_plate"),
                        'D', Ing.tool("node_drive"),
                        'R', Ing.tool("drill_rod")));

        // ---------- Насадки веток: сырьё ×3 + стальная пластина ----------
        for (Section sec : Section.values()) {
            for (Branch b : Branch.ofSection(sec)) {
                String low = b.name().toLowerCase(Locale.ROOT);
                spec("bit_" + low, 1, new String[]{"MMM", ".S."},
                        Map.of('M', Ing.of(b.medalRawIcon),
                                'S', Ing.tool("steel_plate")));
            }
        }

        // ---------- Инструменты веток: 3 тира × 73 ветки ----------
        for (Section sec : Section.values()) {
            for (Branch b : Branch.ofSection(sec)) {
                String low = b.name().toLowerCase(Locale.ROOT);
                String bit = "bit_" + low;
                // Тир I: насадка + шестерня + стальная пластина (входной инструмент)
                spec("tool_" + low + "_1", 1, new String[]{"C", "G", "S"},
                        Map.of('C', Ing.tool(bit),
                                'G', Ing.tool("iron_gear"),
                                'S', Ing.tool("steel_plate")));
                // Тир II: узлы + плата улучшенная + медаль 2
                spec("tool_" + low + "_2", 1, new String[]{".C.", "PNM", ".B."},
                        Map.of('C', Ing.tool(bit),
                                'P', Ing.tool("node_power"),
                                'N', Ing.tool("node_control"),
                                'M', Ing.medal(b.name(), 2),
                                'B', Ing.tool("tool_body")));
                // Тир III: узлы + элитная плата + рама + медаль 4
                spec("tool_" + low + "_3", 1, new String[]{".C.", "PNM", ".F."},
                        Map.of('C', Ing.tool(bit),
                                'P', Ing.tool("node_power"),
                                'N', Ing.tool("node_control"),
                                'M', Ing.medal(b.name(), 4),
                                'F', Ing.tool("reinforced_frame")));
            }
        }

        // ---------- Универсальные инструменты секций: 3 тира × 11 ----------
        for (Section sec : Section.values()) {
            String low = sec.name().toLowerCase(Locale.ROOT);
            Branch flag = flagshipOf(sec);
            spec("universal_" + low + "_1", 1, new String[]{".U.", ".G.", ".R."},
                    Map.of('U', Ing.tool("bit_universal"),
                            'G', Ing.tool("iron_gear"),
                            'R', Ing.of(flag.medalRawIcon)));
            spec("universal_" + low + "_2", 1, new String[]{".U.", "PGM", ".B."},
                    Map.of('U', Ing.tool("bit_universal"),
                            'P', Ing.tool("node_power"),
                            'G', Ing.tool("iron_gear"),
                            'M', Ing.medal(flag.name(), 2),
                            'B', Ing.tool("tool_body")));
            spec("universal_" + low + "_3", 1, new String[]{".U.", "PNM", ".F."},
                    Map.of('U', Ing.tool("bit_universal"),
                            'P', Ing.tool("node_power"),
                            'N', Ing.tool("node_control"),
                            'M', Ing.medal(flag.name(), 4),
                            'F', Ing.tool("reinforced_frame")));
        }
        spec("bit_universal", 1, new String[]{"DQE", ".S."},
                Map.of('D', Ing.of(Material.DIAMOND), 'Q', Ing.of(Material.QUARTZ),
                        'E', Ing.of(Material.EMERALD), 'S', Ing.tool("steel_plate")));

        // ---------- Инфраструктура ----------
        spec("prospector", 1, new String[]{"S M", ".C.", ".R."},
                Map.of('S', Ing.tool("steel_plate"),
                        'M', Ing.tool("scan_module"),
                        'C', Ing.tool("copper_coil"),
                        'R', Ing.of(Material.REDSTONE)));
        spec("battery", 1, new String[]{"RCR", ".Q."},
                Map.of('R', Ing.of(Material.REDSTONE),
                        'C', Ing.tool("copper_coil"),
                        'Q', Ing.tool("quartz_chip")));
        spec("chunk_drill", 1, new String[]{"FTF", "PBN", "FEF"},
                Map.of('F', Ing.tool("reinforced_frame"),
                        'T', Ing.tool("thermal_core"),
                        'B', Ing.tool("bit_debris"),
                        'P', Ing.tool("node_power"),
                        'N', Ing.tool("node_control"),
                        'E', Ing.tool("board_elite")));

        // ---------- Секционные сканеры v3.1 (по сканеру на секцию, кроме шахтёрской) ----------
        for (Section sec : Section.values()) {
            if (sec == Section.MINING) continue; // у шахтёров зонд + геосканер
            String low = sec.name().toLowerCase(Locale.ROOT);
            Branch flag = flagshipOf(sec);
            spec("scanner_" + low, 1, new String[]{".P.", ".M.", ".C."},
                    Map.of('P', Ing.of(flag.medalRawIcon),
                            'M', Ing.tool("scan_module"),
                            'C', Ing.tool("copper_coil")));
        }

        // ---------- Кристаллы активных перков v3.1 ----------
        for (Section sec : Section.values()) {
            String low = sec.name().toLowerCase(Locale.ROOT);
            Branch flag = flagshipOf(sec);
            spec("crystal_" + low, 1, new String[]{".S.", "AM.", ".C."},
                    Map.of('S', Ing.tool("scan_module"),
                            'A', Ing.of(Material.AMETHYST_SHARD),
                            'M', Ing.medal(flag.name(), 3),
                            'C', Ing.tool("copper_coil")));
        }

        // ---------- Хранилища ----------
        spec("ore_pouch", 1, new String[]{"LCL", "S S"},
                Map.of('L', Ing.of(Material.LEATHER),
                        'C', Ing.tool("copper_coil"),
                        'S', Ing.tool("steel_plate")));
        spec("ore_vault", 1, new String[]{"SPS", "OBO", "SPS"},
                Map.of('S', Ing.tool("steel_ingot"),
                        'P', Ing.tool("steel_plate"),
                        'O', Ing.tool("obsidian_casing"),
                        'B', Ing.of(Material.BARREL)));

        // ---------- Машины секций ----------
        spec("charge_station", 1, new String[]{"RMR", "SFS", "RMR"},
                Map.of('R', Ing.of(Material.REDSTONE),
                        'M', Ing.of(Material.MAGMA_CREAM),
                        'S', Ing.tool("steel_plate"),
                        'F', Ing.of(Material.FURNACE)));
        spec("geo_scanner", 1, new String[]{"SGS", "QQQ", "RRR"},
                Map.of('S', Ing.tool("steel_plate"),
                        'G', Ing.tool("scan_module"),
                        'Q', Ing.tool("quartz_chip"),
                        'R', Ing.of(Material.REDSTONE)));

        machine("sawmill", "S", "saw_blade", Branch.OAK, 3);
        machine("composter", "B", "bio_module", Branch.WHEAT, 3);
        machine("herbarium", "H", "bio_module", Branch.FLOWERS, 3);
        machine("dryer", "S", "salt_filter", Branch.KELP, 3);
        machine("glassworks", "T", "thermal_core", Branch.SAND, 3);
        machine("luminaforge", "L", "lumin_core", Branch.GLOWSTONE, 2);
        machine("assembler", "V", "servo_drive", Branch.MECHANISMS, 3);
        machine("altar", "R", "flame_relic", Branch.MAGMA, 3);
        machine("chorus_reaper", "C", "chorus_lens", Branch.CHORUS, 3);
        // Эхо-камера (аномалии): рама + скульк + медь скулька тир 2 + плата II
        spec("echo_chamber", 1, new String[]{".F.", "RM.", ".W."},
                Map.of('F', Ing.tool("reinforced_frame"),
                        'R', Ing.of(Material.SCULK),
                        'M', Ing.medal("SCULK", 2),
                        'W', Ing.tool("board_advanced")));

        // ==================== v3.2: вторые машины секций ====================
        // Шаблон «элитной» машины: рама + компонент + элитная плата + медаль флагмана
        // тир 3 (шаблон отличается от машин 3.1 — иначе matcher затеня́л бы рецепты).
        spec("resinworks", 1, new String[]{".F.", "CE.", ".W."},
                Map.of('F', Ing.tool("reinforced_frame"),
                        'C', Ing.tool("saw_blade"),
                        'E', Ing.tool("board_elite"),
                        'W', Ing.medal("OAK", 3)));
        spec("bakery", 1, new String[]{".F.", "CE.", ".W."},
                Map.of('F', Ing.tool("reinforced_frame"),
                        'C', Ing.tool("bio_module"),
                        'E', Ing.tool("board_elite"),
                        'W', Ing.medal("WHEAT", 3)));
        spec("nursery", 1, new String[]{".F.", "CE.", ".W."},
                Map.of('F', Ing.tool("reinforced_frame"),
                        'C', Ing.tool("quartz_chip"),
                        'E', Ing.tool("board_elite"),
                        'W', Ing.medal("FLOWERS", 3)));
        spec("coral_farm", 1, new String[]{".F.", "CE.", ".W."},
                Map.of('F', Ing.tool("reinforced_frame"),
                        'C', Ing.tool("salt_filter"),
                        'E', Ing.tool("board_elite"),
                        'W', Ing.medal("KELP", 3)));
        spec("stone_works", 1, new String[]{".F.", "CE.", ".W."},
                Map.of('F', Ing.tool("reinforced_frame"),
                        'C', Ing.tool("iron_gear"),
                        'E', Ing.tool("board_elite"),
                        'W', Ing.medal("SAND", 3)));
        spec("candleworks", 1, new String[]{".F.", "CE.", ".W."},
                Map.of('F', Ing.tool("reinforced_frame"),
                        'C', Ing.tool("lumin_core"),
                        'E', Ing.tool("board_elite"),
                        'W', Ing.medal("GLOWSTONE", 3)));
        spec("rail_works", 1, new String[]{".F.", "CE.", ".W."},
                Map.of('F', Ing.tool("reinforced_frame"),
                        'C', Ing.tool("servo_drive"),
                        'E', Ing.tool("board_elite"),
                        'W', Ing.medal("MECHANISMS", 3)));
        spec("foundry", 1, new String[]{".F.", "CE.", ".W."},
                Map.of('F', Ing.tool("reinforced_frame"),
                        'C', Ing.tool("flame_relic"),
                        'E', Ing.tool("board_elite"),
                        'W', Ing.medal("MAGMA", 3)));
        spec("end_forge", 1, new String[]{".F.", "CE.", ".W."},
                Map.of('F', Ing.tool("reinforced_frame"),
                        'C', Ing.tool("chorus_lens"),
                        'E', Ing.tool("board_elite"),
                        'W', Ing.medal("CHORUS", 3)));
        spec("resonance_chamber", 1, new String[]{".F.", "CE.", ".W."},
                Map.of('F', Ing.tool("reinforced_frame"),
                        'C', Ing.of(Material.ECHO_SHARD),
                        'E', Ing.tool("board_elite"),
                        'W', Ing.medal("SCULK", 3)));

        // ==================== v3.2: ручные приспособления ====================
        // Сучкорез: шестерни + катушка + медаль дуба 2
        spec("pruner", 1, new String[]{"G.G", ".C.", ".M."},
                Map.of('G', Ing.tool("iron_gear"),
                        'C', Ing.tool("copper_coil"),
                        'M', Ing.medal("OAK", 2)));
        // Ручная сеялка: костная мука + пластина + медаль пшеницы 2
        spec("seeder", 1, new String[]{"B.P", ".C.", ".M."},
                Map.of('B', Ing.of(Material.BONE_MEAL),
                        'P', Ing.tool("steel_plate"),
                        'C', Ing.tool("copper_coil"),
                        'M', Ing.medal("WHEAT", 2)));
        // Костяной веер: кость + катушка + медаль цветов 2
        spec("bonewand", 1, new String[]{"K.K", ".C.", ".M."},
                Map.of('K', Ing.of(Material.BONE),
                        'C', Ing.tool("copper_coil"),
                        'M', Ing.medal("FLOWERS", 2)));
        // Дыхательный баллон: стекло + катушка + медаль водорослей 2
        spec("air_tank", 1, new String[]{"G.G", "C.C", ".M."},
                Map.of('G', Ing.of(Material.GLASS),
                        'C', Ing.tool("copper_coil"),
                        'M', Ing.medal("KELP", 2)));
        // Шпатель бетонщика: шестерня + пластина + медаль песка 2
        spec("trowel", 1, new String[]{".G.", "PSP", ".M."},
                Map.of('G', Ing.tool("iron_gear"),
                        'P', Ing.tool("steel_plate"),
                        'S', Ing.of(Material.SAND),
                        'M', Ing.medal("SAND", 2)));
        // Жезл световода: свет-ядро + катушка + медаль светокамня 2
        spec("lightwand", 1, new String[]{".L.", "GC.", ".M."},
                Map.of('L', Ing.tool("lumin_core"),
                        'G', Ing.of(Material.GLOWSTONE_DUST),
                        'C', Ing.tool("copper_coil"),
                        'M', Ing.medal("GLOWSTONE", 2)));
        // Инженерная маслёнка: катушки + шестерня + медаль механизмов 2
        spec("oiler", 1, new String[]{"C.C", ".S.", ".M."},
                Map.of('C', Ing.tool("copper_coil"),
                        'S', Ing.of(Material.SLIME_BALL),
                        'M', Ing.medal("MECHANISMS", 2)));
        // Магмовый термос: магмовый крем + незеритовый сердечник (F3) + медаль магмы 2
        spec("thermos", 1, new String[]{"M.M", "NC.", ".D."},
                Map.of('M', Ing.of(Material.MAGMA_CREAM),
                        'N', Ing.tool("netherite_core"),
                        'C', Ing.tool("copper_coil"),
                        'D', Ing.medal("MAGMA", 2)));
        // Парус пустоты: перепонка + жемчуг + медаль энд-камня 2
        spec("voidsail", 1, new String[]{"P.P", "WC.", ".M."},
                Map.of('P', Ing.of(Material.PHANTOM_MEMBRANE),
                        'W', Ing.of(Material.ENDER_PEARL),
                        'C', Ing.tool("copper_coil"),
                        'M', Ing.medal("END_STONE", 2)));
        // Глушитель эха: скульк + катушка + медаль скулька 2
        spec("jammer", 1, new String[]{"S.S", ".C.", ".M."},
                Map.of('S', Ing.of(Material.SCULK),
                        'C', Ing.tool("copper_coil"),
                        'M', Ing.medal("SCULK", 2)));
    }

    /** Машина: рама + секционный компонент + медаль флагмана + плата II. */
    private static void machine(String id, String compChar, String compId,
                                Branch gate, int tier) {
        spec(id, 1, new String[]{".F.", compChar + "M.", ".W."},
                Map.of('F', Ing.tool("reinforced_frame"),
                        Character.valueOf(compChar.charAt(0)), Ing.tool(compId),
                        'M', Ing.medal(gate.name(), tier),
                        'W', Ing.tool("board_advanced")));
    }

    // ---------- Ванильная регистрация ----------
    private static void vanilla(DeepProgressionPlugin plugin, String id, String[] shape,
                                Map<Character, Material> ing) {
        vanilla(plugin, id, shape, 1, ing);
    }

    private static void vanilla(DeepProgressionPlugin plugin, String id, String[] shape, int count,
                                Map<Character, Material> ing) {
        ItemStack result = Items.build(id);
        if (result == null) return;
        if (count > 1) result.setAmount(count);
        NamespacedKey key = new NamespacedKey(plugin, id);
        ShapedRecipe r = new ShapedRecipe(key, result);
        r.shape(sanitize(shape));
        for (var e : ing.entrySet()) {
            r.setIngredient(e.getKey(), new RecipeChoice.MaterialChoice(e.getValue()));
        }
        try {
            Bukkit.addRecipe(r);
            OURS.add(key);
            VAN_SHAPE.put(id, sanitize(shape));
            VAN_ING.put(id, ing);
        } catch (IllegalStateException ex) {
            plugin.getLogger().warning("Крафт " + id + " уже зарегистрирован.");
        }
    }

    private static void spec(String resultId, int count, String[] rows,
                             Map<Character, Ing> ing) {
        // v3.2: пробелы в шейпах канонизируются в точки — иначе matcher трактовал
        // пробел как ингредиент и рецепты (node_power, prospector, ore_pouch…)
        // никогда не совпадали (дефект 3.1).
        SPECS.put(resultId, new Spec(resultId, count, trimRows(sanitize(rows)), ing));
    }

    /** Пробел → точка (пустая ячейка). */
    public static String[] sanitize(String[] rows) {
        String[] out = new String[rows.length];
        for (int i = 0; i < rows.length; i++) out[i] = rows[i].replace(' ', '.');
        return out;
    }

    // ---------- Данные для GUI ----------
    public static String[] rows(String id) {
        Spec s = SPECS.get(id);
        if (s != null) return s.rows();
        return VAN_SHAPE.get(id);
    }

    public static Ing ingredient(String id, char c) {
        Spec s = SPECS.get(id);
        if (s != null) return s.ing().get(c);
        Material m = VAN_ING.get(id) == null ? null : VAN_ING.get(id).get(c);
        return m == null ? null : Ing.of(m);
    }

    public static int resultCount(String id) {
        Spec s = SPECS.get(id);
        return s != null ? s.count() : 1;
    }

    public static Spec specOf(String id) {
        return SPECS.get(id);
    }

    /** Все ручные рецепты (для selftest). */
    public static java.util.Set<String> specIds() {
        return SPECS.keySet();
    }

    public static int count() {
        return SPECS.size() + VAN_SHAPE.size();
    }

    public static boolean isVanillaResult(String id) {
        return VAN_SHAPE.containsKey(id);
    }

    /** Все toolId, используемые как ингредиенты в ручных рецептах (для selftest). */
    public static Set<String> usedToolIds() {
        Set<String> used = new HashSet<>();
        for (Spec s : SPECS.values()) {
            for (Ing i : s.ing().values()) {
                if (i.toolId() != null) used.add(i.toolId());
            }
        }
        return used;
    }

    /** Корневые предметы (крафтятся игроками напрямую). */
    public static Set<String> rootIds() {
        Set<String> used = usedToolIds();
        Set<String> roots = new HashSet<>();
        for (String id : SPECS.keySet()) {
            if (!used.contains(id)) roots.add(id);
        }
        roots.addAll(VAN_SHAPE.keySet());
        return roots;
    }

    // ---------- Подготовка результата ----------
    public static void handlePrepare(PrepareItemCraftEvent e) {
        CraftingInventory inv = e.getInventory();
        ItemStack[] matrix = inv.getMatrix();

        // 1) Подзарядка зонда/сканеров батареями
        int probes = 0, batteries = 0;
        ItemStack probe = null;
        boolean anyTagged = false;
        for (ItemStack it : matrix) {
            String id = Items.toolId(it);
            if (id == null) continue;
            anyTagged = true;
            if (isProbeItem(id)) { probes++; probe = it; }
            else if (id.equals("battery")) batteries++;
        }
        if (probes == 1 && batteries >= 1) {
            int add = batteries * ru.deepprogression.util.Cfg.batteryCharges();
            int max = ru.deepprogression.util.Cfg.probeMaxCharges();
            ItemMeta meta = probe.getItemMeta();
            if (meta == null) return;
            int cur = meta.getPersistentDataContainer()
                    .getOrDefault(Keys.PROBE_CHARGES, PersistentDataType.INTEGER, 0);
            int next = Math.min(max, cur + add);
            if (next == cur && cur >= max) {
                if (e.getView().getPlayer() instanceof org.bukkit.entity.Player pp) {
                    Msg.send(pp, "battery.max-charges", "charges", String.valueOf(max));
                }
                inv.setResult(null);
                return;
            }
            ItemStack charged = Items.build(isProbeItem2(probe) ? Items.toolId(probe) : "prospector");
            ItemMeta m2 = charged.getItemMeta();
            m2.getPersistentDataContainer().set(Keys.PROBE_CHARGES,
                    PersistentDataType.INTEGER, next);
            charged.setItemMeta(m2);
            inv.setResult(charged);
            if (e.getView().getPlayer() instanceof org.bukkit.entity.Player pp) {
                Msg.send(pp, "battery.recharged",
                        "n", String.valueOf(next - cur), "charges", String.valueOf(next));
            }
            return;
        }

        // 2) Ручные рецепты
        String specId = matchMatrix(matrix);
        if (specId != null) {
            Spec s = SPECS.get(specId);
            ItemStack res = Items.build(specId);
            if (res != null) {
                if (s.count() > 1) res.setAmount(s.count());
                inv.setResult(res);
            }
            return;
        }

        // 3) Защита: тегированные предметы не участвуют в чужих рецептах
        if (anyTagged) {
            Recipe r = e.getRecipe();
            boolean ourGeologist = r instanceof ShapedRecipe sr
                    && geologistKey.equals(sr.getKey());
            if (!ourGeologist) inv.setResult(null);
        }
    }

    /** Зонд, все секционные сканеры и дыхательный баллон — общий пул зарядов. */
    public static boolean isProbeItem(String toolId) {
        return "prospector".equals(toolId) || "air_tank".equals(toolId)
                || toolId.startsWith("scanner_");
    }

    private static boolean isProbeItem2(ItemStack it) {
        String id = Items.toolId(it);
        return id != null && isProbeItem(id);
    }

    // ---------- Клик по результату (ручное потребление) ----------
    public static boolean handleCraftClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getView().getTopInventory() instanceof CraftingInventory inv)) return false;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return false;
        if (e.getRawSlot() != 0) return false;
        ItemStack current = e.getCurrentItem();
        if (current == null || current.getType().isAir()) return false;
        String id = Items.toolId(current);
        if (id == null) return false;
        if (!(e.getWhoClicked() instanceof org.bukkit.entity.Player p)) return false;

        // Ванильно зарегистрированные: ваниль потребит сам; считаем контракты
        if (isVanillaResult(id)) {
            ItemStack cursor = e.getCursor();
            boolean cursorFree = cursor == null || cursor.getType().isAir()
                    || (cursor.getType() == current.getType()
                        && current.getAmount() + cursor.getAmount() <= current.getMaxStackSize());
            if (cursorFree) Quests.onCraft(p, id);
            return true;
        }

        String specId = matchMatrix(inv.getMatrix());
        if (specId == null || !specId.equals(id)) return false;

        e.setCancelled(true);
        boolean shift = e.getClick().isShiftClick();
        int maxCycles = shift ? 64 : 1;
        int done = 0;
        for (int i = 0; i < maxCycles; i++) {
            String m = matchMatrix(inv.getMatrix());
            if (m == null || !m.equals(id)) break;
            Spec s = SPECS.get(m);
            ItemStack res = Items.build(m);
            if (res == null) break;
            if (s != null && s.count() > 1) res.setAmount(s.count());
            // D1: сначала потребляем ингредиенты, только потом выдаём результат
            consume(inv);
            Quests.onCraft(p, id);
            done++;
            var leftover = p.getInventory().addItem(res);
            if (!leftover.isEmpty()) {
                for (ItemStack rest : leftover.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), rest);
                }
                break;
            }
        }
        if (done > 0) {
            inv.setResult(null);
            p.updateInventory();
            String name = id;
            ItemStack preview = Items.build(id);
            if (preview != null && preview.getItemMeta() != null) {
                name = preview.getItemMeta().getDisplayName();
            }
            Msg.send(p, "craft.done", "n", String.valueOf(done), "item", name);
            p.playSound(p.getLocation(), "minecraft:entity.experience_orb.pickup", 0.7f, 1.4f);
        } else {
            Msg.send(p, "craft.no-space");
        }
        return true;
    }

    private static void consume(CraftingInventory inv) {
        ItemStack[] matrix = inv.getMatrix();
        for (int i = 0; i < matrix.length; i++) {
            ItemStack it = matrix[i];
            if (it == null || it.getType().isAir()) continue;
            if (it.getAmount() > 1) {
                it.setAmount(it.getAmount() - 1);
                inv.setItem(i + 1, it);
            } else {
                inv.setItem(i + 1, null);
            }
        }
    }

    // ---------- Матчинг сетки ----------
    public static String matchMatrix(ItemStack[] matrix) {
        if (matrix == null || (matrix.length != 4 && matrix.length != 9)) return null;
        int size = matrix.length == 4 ? 2 : 3;
        int minR = -1, maxR = -1, minC = -1, maxC = -1;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                ItemStack it = matrix[r * size + c];
                if (it == null || it.getType().isAir()) continue;
                if (minR < 0) { minR = r; maxR = r; minC = c; maxC = c; }
                else {
                    minR = Math.min(minR, r); maxR = Math.max(maxR, r);
                    minC = Math.min(minC, c); maxC = Math.max(maxC, c);
                }
            }
        }
        if (minR < 0) return null;
        int h = maxR - minR + 1, w = maxC - minC + 1;

        outer:
        for (Spec s : SPECS.values()) {
            String[] rows = s.rows();
            if (rows.length != h || rows[0].length() != w) continue;
            for (int r = 0; r < h; r++) {
                for (int c = 0; c < w; c++) {
                    char ch = rows[r].charAt(c);
                    ItemStack it = matrix[(minR + r) * size + (minC + c)];
                    if (ch == '.') {
                        if (it != null && !it.getType().isAir()) continue outer;
                    } else {
                        Ing ing = s.ing().get(ch);
                        if (ing == null) continue outer;
                        if (it == null || it.getType().isAir()) continue outer;
                        if (!ingMatches(ing, it)) continue outer;
                    }
                }
            }
            return s.resultId();
        }
        return null;
    }

    private static boolean ingMatches(Ing ing, ItemStack it) {
        if (ing.mat() != null) {
            // v3.2: медаль не подменяет ванильный материал своей иконки
            return it.getType() == ing.mat() && Items.toolId(it) == null
                    && Items.medalBranch(it) == null;
        }
        if (ing.toolId() != null) {
            return ing.toolId().equals(Items.toolId(it));
        }
        String branch = Items.medalBranch(it);
        int tier = Items.medalTier(it);
        return branch != null && branch.equals(ing.medalBranch())
                && tier >= ing.medalTier();
    }

    /** Обрезка пустых рядов/столбцов шейпа (канонизация). */
    private static String[] trimRows(String[] rows) {
        int minR = -1, maxR = -1, minC = 99, maxC = -1;
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length(); c++) {
                if (rows[r].charAt(c) != '.') {
                    if (minR < 0) { minR = r; maxR = r; minC = c; maxC = c; }
                    else {
                        minR = Math.min(minR, r); maxR = Math.max(maxR, r);
                        minC = Math.min(minC, c); maxC = Math.max(maxC, c);
                    }
                }
            }
        }
        if (minR < 0) return rows;
        List<String> out = new ArrayList<>();
        for (int r = minR; r <= maxR; r++) {
            out.add(rows[r].substring(minC, maxC + 1));
        }
        return out.toArray(new String[0]);
    }
}
