package ru.deepprogression.tools;

import org.bukkit.Material;
import ru.deepprogression.model.Branch;
import ru.deepprogression.model.Section;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Реестр всех предметов DeepProgression 3.0:
 *  - 73 насадки (bit_&lt;ветка&gt;);
 *  - 219 веточных инструментов (3 тира × 73 ветки);
 *  - 33 универсальных инструмента (3 тира × 11 секций);
 *  - инфраструктура: зонд, батарея, чанковый бур, тайник, склад,
 *    зарядная станция, геосканер, кирка геолога;
 *  - 8 секционных машин (пилорама, компостер, сушилка, стекловар,
 *    люминафабрика, сборочный стан, алтарь, хорус-жнейка);
 *  - инженерные компоненты (детали, платы, узлы).
 */
public final class Tools {

    public enum ToolType {
        DRILL,          // инструмент ветки (любая секция)
        UNIVERSAL,      // универсальный инструмент секции
        GEOLOGIST,      // кирка геолога (legacy, шахтёрская секция)
        PROSPECTOR, CHUNK_DRILL, BATTERY,
        COMPONENT,      // инженерная деталь / плата / узел / насадка
        POUCH, VAULT, STATION, SCANNER,
        SECTION_SCANNER, // секционный сканер v3.1 (скан чанка по веткам секции)
        CRYSTAL,        // кристалл активного перка v3.1
        MACHINE,        // интерактивная секционная машина
        GADGET          // ручное приспособление секции v3.2
    }

    public static final class ToolSpec {
        public final String id;
        public final String name;
        public final ToolType type;
        public final Branch branch;    // для DRILL
        public final Section section;  // для UNIVERSAL (null у legacy)
        public final Material icon;
        public final boolean glow;
        public final int mult;         // множитель по умолчанию (переопределяется конфигом)
        public final String[] desc;    // строки lore

        ToolSpec(String id, String name, ToolType type, Branch branch, Section section,
                 Material icon, boolean glow, int mult, String[] desc) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.branch = branch;
            this.section = section;
            this.icon = icon;
            this.glow = glow;
            this.mult = mult;
            this.desc = desc;
        }
    }

    private static final Map<String, ToolSpec> REG = new LinkedHashMap<>();

    private Tools() {}

    // ---------- множители ----------

    /** Множитель тира по редкости ветки. */
    public static int branchMult(Branch b, int tier) {
        if (b == Branch.STONE) return switch (tier) { case 1 -> 3; case 2 -> 6; default -> 10; };
        return switch (b.rarity) {
            case COMMON -> switch (tier) { case 1 -> 25; case 2 -> 50; default -> 100; };
            case MEDIUM -> switch (tier) { case 1 -> 15; case 2 -> 30; default -> 60; };
            case RARE -> switch (tier) { case 1 -> 8; case 2 -> 15; default -> 30; };
        };
    }

    /** Множитель универсального инструмента секции. */
    public static int universalMult(Section s, int tier) {
        if (s == Section.MINING) return switch (tier) { case 1 -> 10; case 2 -> 25; default -> 50; };
        return switch (s) {
            case WOOD, FARM, NETHER, END, ENGINE -> switch (tier) {
                case 1 -> 10; case 2 -> 25; default -> 50; };
            case FLORA, MASON, LIGHT, SEA -> switch (tier) {
                case 1 -> 8; case 2 -> 20; default -> 40; };
            default -> switch (tier) { case 1 -> 5; case 2 -> 12; default -> 25; };
        };
    }

    /** Множитель по умолчанию (Cfg.mult падает сюда, если конфиг не задан). */
    public static int defaultMult(String toolId) {
        ToolSpec s = REG.get(toolId);
        return s != null ? s.mult : 1;
    }

    // ---------- инициализация ----------

    public static void init() {
        REG.clear();

        // ---------- инженерные компоненты ----------
        component("steel_ingot", "Стальной слиток", Material.IRON_INGOT, true,
                "Основа инженерии DeepProgression.",
                "Крафт: железо + уголь.");
        component("steel_plate", "Стальная пластина", Material.IRON_BARS, true,
                "Штампуется из стальных слитков.",
                "Основа корпусов, рам и станций.");
        component("copper_wire", "Медная проволока", Material.STRING, false,
                "Тянется из медных слитков.",
                "Основа катушек и плат.");
        component("copper_coil", "Медная катушка", Material.IRON_CHAIN, false,
                "Намотана из медной проволоки.",
                "Нужна в платах, узлах и станциях.");
        component("redstone_coil", "Редстоун-катушка", Material.IRON_CHAIN, true,
                "Резонансная катушка на редстоуне.",
                "Сердце узлов питания.");
        component("coal_electrode", "Угольный электрод", Material.COAL, true,
                "Прессованный угольный стержень.",
                "Используется в платах и насадках.");
        component("iron_gear", "Железная шестерня", Material.IRON_NUGGET, true,
                "Точная механическая шестерня.",
                "Основа узлов привода.");
        component("quartz_chip", "Кварцевый чип", Material.QUARTZ, true,
                "Микросхема из обработанного кварца.",
                "Мозг плат и узлов управления.");
        component("diamond_edge", "Алмазная кромка", Material.SHEARS, true,
                "Гранёное алмазное лезвие.",
                "Для элитных плат и насадок.");
        component("netherite_core", "Незеритовый сердечник", Material.NETHERITE_SCRAP, true,
                "Ядро из переплавленного адского металла.",
                "Для незеритовых насадок.");
        component("obsidian_casing", "Обсидиановый каркас", Material.OBSIDIAN, false,
                "Сверхпрочный корпусной каркас.",
                "Гасит тепло и взрывы.",
                "Основа термоядра и склада ресурсов.");
        component("reinforced_frame", "Усиленная рама", Material.LANTERN, true,
                "Несущая рама из стальных пластин",
                "и шестерни. Основа тяжёлых машин.");
        component("drill_rod", "Буровая штанга", Material.BREEZE_ROD, true,
                "Кованая стальная штанга инструмента.");
        component("thermal_core", "Термоядро", Material.MAGMA_CREAM, true,
                "Тепловое ядро в обсидиановом каркасе.",
                "Сердце чанкового бура и стекловара.");
        component("scan_module", "Модуль геосканирования", Material.AMETHYST_SHARD, true,
                "Аметистовый сенсор с чипом.",
                "Сердце зонда и геосканера.");
        component("saw_blade", "Пильный диск", Material.SHEARS, true,
                "Зубчатый диск с алмазной кромкой.",
                "Сердце пилорамы лесоруба.");
        component("bio_module", "Био-модуль", Material.BONE_MEAL, true,
                "Ферментатор органики.",
                "Сердце компостера земледельца.");
        component("salt_filter", "Солевой фильтр", Material.PRISMARINE_SHARD, true,
                "Фильтрует соль и концентраты.",
                "Сердце морской сушилки.");
        component("lumin_core", "Световое ядро", Material.GLOWSTONE_DUST, true,
                "Концентрат чистого света.",
                "Сердце люминафабрики.");
        component("servo_drive", "Сервопривод", Material.CLOCK, true,
                "Точный привод с редстоун-катушкой.",
                "Сердце сборочного стана.");
        component("flame_relic", "Пламенная реликвия", Material.BLAZE_POWDER, true,
                "Вечное пламя в стальном ободе.",
                "Сердце пламенного алтаря.");
        component("chorus_lens", "Хорусовая линза", Material.ENDER_EYE, true,
                "Линза, преломляющая пространство.",
                "Сердце хорус-жнейки.");

        // ---------- платы ----------
        component("board_basic", "Простая плата", Material.PAPER, true,
                "Печатная плата: бумага, проволока,",
                "угольный электрод. Уровень I.");
        component("board_advanced", "Улучшенная плата", Material.REPEATER, true,
                "Плата с кварцевыми чипами.",
                "Уровень II.");
        component("board_elite", "Элитная плата", Material.COMPARATOR, true,
                "Плата на алмазных кромках.",
                "Уровень III.");

        // ---------- узлы ----------
        component("node_drive", "Узел привода", Material.PISTON, true,
                "Механика: шестерни + плата.",
                "Крутит штангу инструмента.");
        component("node_power", "Узел питания", Material.REDSTONE_LAMP, true,
                "Энергоблок: катушки + сталь.",
                "Питает насадку инструмента.");
        component("node_control", "Узел управления", Material.CLOCK, true,
                "Электроника: чипы + плата II.",
                "Задаёт алгоритм работы.");
        component("tool_body", "Корпус инструмента", Material.SHULKER_SHELL, true,
                "Корпус вокруг узла привода:",
                "пластины и штанги.",
                "Основа всех инструментов.");

        // ---------- насадки по всем 73 веткам ----------
        for (Branch b : Branch.values()) {
            String id = "bit_" + b.name().toLowerCase(Locale.ROOT);
            component(id, "Насадка «" + b.display + "»", b.medalRawIcon,
                    b.rarity == Branch.Rarity.RARE,
                    "Рабочая насадка инструментов",
                    "ветки " + b.display + " (" + b.section.display + ").");
        }
        component("bit_universal", "Сборная насадка", Material.DIAMOND, true,
                "Универсальная насадка для универсальных",
                "инструментов любой секции.");

        // ---------- инструменты веток: 3 тира ----------
        for (Section sec : Section.values()) {
            for (Branch b : Branch.ofSection(sec)) {
                String low = b.name().toLowerCase(Locale.ROOT);
                for (int tier = 1; tier <= 3; tier++) {
                    String roman = tier == 1 ? "I" : tier == 2 ? "II" : "III";
                    String name = TextCap(sec.toolNoun) + " «" + b.display + "» " + roman;
                    boolean glow = tier == 3 || b.rarity == Branch.Rarity.RARE && tier >= 2;
                    spec("tool_" + low + "_" + tier, name, ToolType.DRILL,
                            b, sec, iconFor(sec.kind, tier), glow, branchMult(b, tier), null);
                }
            }
        }

        // ---------- универсальные инструменты секций ----------
        for (Section sec : Section.values()) {
            String low = sec.name().toLowerCase(Locale.ROOT);
            for (int tier = 1; tier <= 3; tier++) {
                String roman = tier == 1 ? "I" : tier == 2 ? "II" : "III";
                String name = "Универсальный " + sec.toolNoun + " " + roman
                        + " &8(" + sec.display + ")";
                spec("universal_" + low + "_" + tier, name, ToolType.UNIVERSAL,
                        null, sec, iconFor(sec.kind, tier), tier == 3,
                        universalMult(sec, tier), null);
            }
        }

        // ---------- инфраструктура v1-v2 ----------
        spec("geologist_pick", "Кирка геолога", ToolType.GEOLOGIST, null, null,
                Material.IRON_PICKAXE, false, 2, null);
        spec("prospector", "Зонд георазведки", ToolType.PROSPECTOR, null, null,
                Material.SPYGLASS, false, 0, null);
        spec("battery", "Геологическая батарея", ToolType.BATTERY, null, null,
                Material.COPPER_BULB, false, 0, null);
        spec("chunk_drill", "Чанковый бур", ToolType.CHUNK_DRILL, null, null,
                Material.HEAVY_CORE, true, 0, null);

        // ---------- хранилища и машины ----------
        spec("ore_pouch", "Тайник ресурсов", ToolType.POUCH, null, null,
                Material.BUNDLE, true, 0, null);
        spec("ore_vault", "Склад ресурсов", ToolType.VAULT, null, null,
                Material.BARREL, false, 0, null);
        spec("charge_station", "Зарядная станция", ToolType.STATION, null, null,
                Material.FURNACE, false, 0, null);
        spec("geo_scanner", "Геосканер", ToolType.SCANNER, null, null,
                Material.SMOKER, false, 0, null);

        // ---------- секционные сканеры v3.1 (по сканеру на секцию) ----------
        scanner("scanner_wood", "Лесной компас", Section.WOOD, Material.CLOCK);
        scanner("scanner_farm", "Агродатчик", Section.FARM, Material.WHEAT);
        scanner("scanner_flora", "Лупа флориста", Section.FLORA, Material.FERN);
        scanner("scanner_sea", "Сонар рыболова", Section.SEA, Material.PRISMARINE_CRYSTALS);
        scanner("scanner_mason", "Грунтовый щуп", Section.MASON, Material.CLAY_BALL);
        scanner("scanner_light", "Люксметр", Section.LIGHT, Material.OCHRE_FROGLIGHT);
        scanner("scanner_engine", "Сигнальный детектор", Section.ENGINE, Material.OBSERVER);
        scanner("scanner_nether", "Адский визор", Section.NETHER, Material.SOUL_LANTERN);
        scanner("scanner_end", "Энд-осциллятор", Section.END, Material.END_CRYSTAL);
        scanner("scanner_anomaly", "Эхорезонатор", Section.ANOMALY, Material.ECHO_SHARD);

        // ---------- кристаллы активных перков v3.1 ----------
        for (Section sec : Section.values()) {
            String low = sec.name().toLowerCase(Locale.ROOT);
            spec("crystal_" + low, "Кристалл " + crystalGenitive(sec), ToolType.CRYSTAL,
                    null, sec, Material.AMETHYST_SHARD, true, 0,
                    new String[]{"Активация: &fShift+ПКМ", abilityOf(sec)});
        }

        machine("sawmill", "Пилорама", Material.STONECUTTER,
                "Перепиливает брёвна в доски с бонусом.");
        machine("composter", "Компостер", Material.COMPOSTER,
                "Перерабатывает урожай в костную муку.");
        machine("herbarium", "Гербарий-пресс", Material.DECORATED_POT,
                "Прессует цветы в красители.");
        machine("dryer", "Морская сушилка", Material.BLAST_FURNACE,
                "Сушит водоросли, даёт концентраты.");
        machine("glassworks", "Стекловар", Material.GRINDSTONE,
                "Плавит песок в стекло без печи.");
        machine("luminaforge", "Люминафабрика", Material.LECTERN,
                "Извлекает свет из светящихся растений.");
        machine("assembler", "Сборочный стан", Material.TRAPPED_CHEST,
                "Штампует шестерни из железа.");
        machine("altar", "Пламенный алтарь", Material.BREWING_STAND,
                "Раскрывает магму до магмового крема.");
        machine("chorus_reaper", "Хорус-жнейка", Material.ENCHANTING_TABLE,
                "Обмолачивает хорус, ловит жемчуг.");
        machine("echo_chamber", "Эхо-камера", Material.JUKEBOX,
                "Резонирует скульк в эхо-осколки.");

        // ---------- машины v3.2 (вторая машина каждой секции) ----------
        machine("resinworks", "Смолокурня", Material.LOOM,
                "Коптит смолу в кирпичи, прессует листву.");
        machine("bakery", "Пекарня", Material.BEEHIVE,
                "Печёт хлеб и печёную картошку без топлива.");
        machine("nursery", "Питомник растений", Material.CHISELED_BOOKSHELF,
                "Выращивает бамбук, грибы и красители.");
        machine("coral_farm", "Коралловая ферма", Material.CONDUIT,
                "Вращает кораллы в блоки и призмарин.");
        machine("stone_works", "Камнеобрабатывающий стан", Material.BELL,
                "Режет песчаник, сушит бетон, дробит кремень.");
        machine("candleworks", "Свечная мастерская", Material.DAYLIGHT_DETECTOR,
                "Льёт свечи из сот и переносит свет.");
        machine("rail_works", "Рельсовый стан", Material.HOPPER,
                "Штампует рельсы из железа и золота.");
        machine("foundry", "Незер-литейня", Material.RESPAWN_ANCHOR,
                "Плавит незеррак в кирпичи, золото из камня.");
        machine("end_forge", "Энд-кузня", Material.BEACON,
                "Плачет обсидианом и варит пурпур.");
        machine("resonance_chamber", "Резонансная камера", Material.CALIBRATED_SCULK_SENSOR,
                "Раскрывает скульк в эхо и катализаторы.");

        // ---------- ручные приспособления v3.2 ----------
        gadget("pruner", "Сучкорез", Section.WOOD, Material.WOODEN_AXE,
                "ПКМ по бревну: листва 3×3×3 — в инвентарь.",
                "Дроп и прогресс — как при ручной добыче.");
        gadget("seeder", "Ручная сеялка", Section.FARM, Material.WOODEN_HOE,
                "ПКМ по грядке: сеет 3×3 семенами из инвентаря.",
                "Расход семян 1:1.");
        gadget("bonewand", "Костяной веер", Section.FLORA, Material.BONE,
                "ПКМ по растению: +1 стадия роста 3×3.",
                "Расход: 3 костной муки.");
        gadget("air_tank", "Дыхательный баллон", Section.SEA, Material.GLASS_BOTTLE,
                "ПКМ: дыхание и грация дельфина.",
                "Заряды — общий пул с зондами:",
                "заряжается батареей/станцией/геосканером.");
        gadget("trowel", "Шпатель бетонщика", Section.MASON, Material.IRON_SHOVEL,
                "ПКМ по порошку: бетон 3×3×3 без воды.",
                "Работает по любому цвету порошка.");
        gadget("lightwand", "Жезл световода", Section.LIGHT, Material.BLAZE_ROD,
                "ПКМ: ставит источник света из инвентаря",
                "в точку прицела (до 12 блоков).");
        gadget("oiler", "Инженерная маслёнка", Section.ENGINE, Material.SLIME_BALL,
                "ПКМ по машине: сброс её кулдауна.",
                "Работает на станции, геосканере и машинах.");
        gadget("thermos", "Магмовый термос", Section.NETHER, Material.LAVA_BUCKET,
                "ПКМ: огнестойкость 90с.",
                "Расход: 4 магмы из инвентаря.");
        gadget("voidsail", "Парус пустоты", Section.END, Material.PHANTOM_MEMBRANE,
                "ПКМ: медленное падение 60с.",
                "Расход: 1 жемчуг Края.");
        gadget("jammer", "Глушитель эха", Section.ANOMALY, Material.ECHO_SHARD,
                "ПКМ: сенсоры не слышат тебя 90с.",
                "Ранний аналог перка «Глушитель вибраций».");
    }

    /** Описание способности кристалла (lore). */
    private static String abilityOf(Section s) {
        return switch (s) {
            case MINING -> "«Жиловой раж»: спешка II + ночное зрение 90с.";
            case WOOD -> "«Валка»: валил дерево целиком (до 64 брёвен).";
            case FARM -> "«Жатва»: собирает все зрелые культуры 5×5.";
            case FLORA -> "«Цветение»: ускоряет рост растений 5×5.";
            case SEA -> "«Прилив»: притягивает ресурсы в радиусе 8.";
            case MASON -> "«Кладка»: мост 1×5 из блоков инвентаря.";
            case LIGHT -> "«Люстра»: ставит 8 источников света по кругу.";
            case ENGINE -> "«Форсаж»: скорость II + спешка II 60с.";
            case NETHER -> "«Жаровой щит»: огнестойкость 120с.";
            case END -> "«Фазовый шаг»: рывок до 8 блоков.";
            case ANOMALY -> "«Эхолокация»: находит скульк в соседних чанках.";
        };
    }

    private static String crystalGenitive(Section s) {
        return switch (s) {
            case MINING -> "горняка";
            case WOOD -> "лесоруба";
            case FARM -> "земледельца";
            case FLORA -> "флориста";
            case SEA -> "рыболова";
            case MASON -> "каменщика";
            case LIGHT -> "световода";
            case ENGINE -> "инженера";
            case NETHER -> "незера";
            case END -> "Края";
            case ANOMALY -> "аномалий";
        };
    }

    private static void scanner(String id, String name, Section s, Material icon) {
        spec(id, name, ToolType.SECTION_SCANNER, null, s, icon, false, 0,
                new String[]{"Скан чанка по веткам секции " + s.display + "."});
    }

    private static String TextCap(String s) {
        return s == null || s.isEmpty() ? s
                : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static Material iconFor(Section.Kind kind, int tier) {
        return switch (kind) {
            case PICKAXE -> tier == 1 ? Material.IRON_PICKAXE
                    : tier == 2 ? Material.DIAMOND_PICKAXE : Material.NETHERITE_PICKAXE;
            case AXE -> tier == 1 ? Material.IRON_AXE
                    : tier == 2 ? Material.DIAMOND_AXE : Material.NETHERITE_AXE;
            case HOE -> tier == 1 ? Material.IRON_HOE
                    : tier == 2 ? Material.DIAMOND_HOE : Material.NETHERITE_HOE;
            case SHOVEL -> tier == 1 ? Material.IRON_SHOVEL
                    : tier == 2 ? Material.DIAMOND_SHOVEL : Material.NETHERITE_SHOVEL;
            case SHEARS -> Material.SHEARS;
        };
    }

    private static void spec(String id, String name, ToolType type, Branch branch,
                             Section section, Material icon, boolean glow,
                             int mult, String[] desc) {
        REG.put(id, new ToolSpec(id, name, type, branch, section, icon, glow, mult, desc));
    }

    private static void component(String id, String name, Material icon,
                                  boolean glow, String... desc) {
        REG.put(id, new ToolSpec(id, name, ToolType.COMPONENT, null, null,
                icon, glow, 0, desc));
    }

    private static void machine(String id, String name, Material icon, String desc) {
        REG.put(id, new ToolSpec(id, name, ToolType.MACHINE, null, null,
                icon, false, 0, new String[]{desc}));
    }

    private static void gadget(String id, String name, Section s, Material icon,
                               String... desc) {
        REG.put(id, new ToolSpec(id, name, ToolType.GADGET, null, s,
                icon, false, 0, desc));
    }

    /** Все ручные приспособления (selftest). */
    public static List<ToolSpec> gadgets() {
        List<ToolSpec> l = new ArrayList<>();
        for (ToolSpec s : REG.values()) if (s.type == ToolType.GADGET) l.add(s);
        return l;
    }

    // ---------- доступ ----------

    public static ToolSpec get(String id) {
        return id == null ? null : REG.get(id);
    }

    public static Collection<ToolSpec> all() {
        return REG.values();
    }

    public static List<ToolSpec> roots() {
        List<ToolSpec> l = new ArrayList<>();
        for (ToolSpec s : REG.values()) if (s.type != ToolType.COMPONENT) l.add(s);
        return l;
    }

    public static List<ToolSpec> drills() {
        List<ToolSpec> l = new ArrayList<>();
        for (ToolSpec s : REG.values()) if (s.type == ToolType.DRILL) l.add(s);
        return l;
    }

    public static List<ToolSpec> machines() {
        List<ToolSpec> l = new ArrayList<>();
        for (ToolSpec s : REG.values()) if (s.type == ToolType.MACHINE) l.add(s);
        return l;
    }

    public static List<ToolSpec> universals() {
        List<ToolSpec> l = new ArrayList<>();
        for (ToolSpec s : REG.values()) if (s.type == ToolType.UNIVERSAL) l.add(s);
        return l;
    }

    public static List<ToolSpec> components() {
        List<ToolSpec> l = new ArrayList<>();
        for (ToolSpec s : REG.values()) if (s.type == ToolType.COMPONENT) l.add(s);
        return l;
    }

    public static boolean exists(String id) {
        return id != null && REG.containsKey(id);
    }

    /** Число из шанса 0.25 -> "25%" */
    public static String chancePct(double chance) {
        long pct = Math.round(chance * 1000.0);
        if (pct % 10 == 0) return (pct / 10) + "%";
        return String.format(Locale.ROOT, "%.1f%%", chance * 100.0);
    }
}
