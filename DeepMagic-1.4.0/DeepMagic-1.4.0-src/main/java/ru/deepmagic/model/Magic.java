package ru.deepmagic.model;

import org.bukkit.Material;
import ru.deepprogression.model.Branch;
import ru.deepprogression.model.Branch.Rarity;

import java.util.EnumMap;
import java.util.Map;

/**
 * Реестр DeepMagic: 73 ветки DeepProgression → вещество → посох → механика.
 * Канонический источник данных. Свой список материалов НЕ используется:
 * вход компрессора — только vault-предметы реестра Blocks DeepProgression.
 */
public final class Magic {

    public static final String[] TIER_NAMES = {"Спрессованное", "Двойное Сжатие", "Абсолютное Сжатие"};

    /** Стихии посохов (реагенты матрицы). */
    public enum Element {
        FIRE("Огонь", "&c"), WATER("Вода", "&b"), ICE("Лёд", "&f"),
        LIGHTNING("Молния", "&e"), EARTH("Земля", "&6"), AIR("Воздух", "&7"),
        LIFE("Жизнь", "&a"), LIGHT("Свет", "&e"), DARK("Тьма", "&8"),
        VOID("Пустота", "&5"), ECHO("Эхо", "&9"), TECH("Техно", "&7"), ARCANE("Аркана", "&3");
        public final String display;
        public final String color;
        Element(String d, String c) { display = d; color = c; }
    }

    /** Роли (анти-бис: ровно одна главная роль, максимум ничего лишнего). */
    public enum Role {
        CONTROL("Контроль"), DAMAGE("Урон"), SUPPORT("Поддержка"),
        MOBILITY("Мобильность"), RESOURCES("Ресурсы"), ENVIRONMENT("Окружение"),
        RECON("Разведка"), DEFENSE("Защита");
        public final String display;
        Role(String d) { display = d; }
    }

    /** Уникальные механики. Selftest запрещает дубликаты и GENERIC. */
    public enum Mech {
        STONE_SHELL, CARBON_COAT, COPPER_ANCHOR, IRON_YOKE, GOLD_BRIBE,
        REDSTONE_PULSE, LAPIS_MARK, QUARTZ_PING, DIAMOND_EDGE, EMERALD_DEAL,
        DEBRIS_QUAKE, AMETHYST_RESONATE,
        OAK_BARK, SPRUCE_FROST_CONE, BIRCH_CLEANSE, JUNGLE_HOOK, ACACIA_THORNS,
        DARK_OAK_VEIL, MANGROVE_ROOTS, CHERRY_STORM, PALE_OAK_PETRIFY, RESIN_TRAP,
        WHEAT_HARVEST, CARROT_VITAMIN, POTATO_KITCHEN, BEETROOT_FEAST, MELON_QUENCH,
        PUMPKIN_SCARE, CANE_RUSH, COCOA_FORTIFY, WART_BOMB, WOOL_SILENCE, HONEY_GLAZE,
        CACTUS_RING, BAMBOO_JUMP, FLOWER_CHARM, MOSS_CUSHION, AZALEA_TOXIN,
        MUSHROOM_CLOUD,
        KELP_WATERS, CORAL_REEF, SPONGE_DRAIN, PRISMARINE_BEAM,
        SAND_STORM, GRAVEL_HAMMER, SANDSTONE_BRIDGE, ICE_BIND, GLASS_FADE,
        BRICKS_BASTION, TERRACOTTA_MASK, CONCRETE_TOMB,
        GLOWSTONE_FLASH, SEA_LANTERN_BEACON, SHROOMLIGHT_AURA, FROG_RAIN,
        CANDLE_RITUAL, GLOWFLORA_INNERLIGHT,
        MECH_PUSH, GALVANIC_ARC, RAIL_DASH,
        NETHERRACK_PYRE, SOUL_FLAME, MAGMA_IGNITE, BLACKSTONE_VORTEX,
        BASALT_PILLAR, CRIMSON_RAGE, WARPED_SHIFT, WART_ROT,
        ENDSTONE_OBELISK, PURPUR_LEVITATE, CHORUS_PHASE, OBSIDIAN_SKIN,
        SCULK_PULSE
    }

    public record StaffDef(Branch branch, String substance, String staff,
                           Element element, Role role, Material fuel, int fuelQty,
                           int cooldown, Mech mech,
                           String substanceLore, String staffLore) {}

    private static final Map<Branch, StaffDef> DEFS = new EnumMap<>(Branch.class);

    private static void def(Branch b, String substance, String staff, Element el, Role role,
                            Material fuel, int fuelQty, int cd, Mech mech,
                            String subLore, String staffLore) {
        DEFS.put(b, new StaffDef(b, substance, staff, el, role, fuel, fuelQty, cd, mech,
                subLore, staffLore));
    }

    static {
        // ==================== ШАХТЁР (12) ====================
        def(Branch.STONE, "Сверхспрессованный Литоструктурный Моноблок",
                "Посох Каменного Панциря", Element.EARTH, Role.DEFENSE,
                Material.COBBLESTONE, 4, 15, Mech.STONE_SHELL,
                "Горы, спрессованные до предела плотности. Пахнет забоем.",
                "Каменный панцирь: поглощает урон (1 камень за полученный удар), блокирует поджог.");
        def(Branch.COAL, "Сверхспрессованный Аморфный Углеродный Монолит",
                "Угольный Посох", Element.DARK, Role.CONTROL,
                Material.COAL, 2, 8, Mech.CARBON_COAT,
                "Миллионы лет света, спрессованные в чёрную тишину. Не гаснет.",
                "Покрывает цель углём: ЛЮБОЙ магический огонь по покрытому — ВЕЧНОЕ горение до воды. Shift: угольная пыль — удушение.");
        def(Branch.COPPER, "Сверхспрессованный Электропроводный Медный Пруток",
                "Посох Медного Проводника", Element.LIGHTNING, Role.ENVIRONMENT,
                Material.RAW_COPPER, 2, 25, Mech.COPPER_ANCHOR,
                "Медная жила, растянутая в идеальный проводник. Гудит перед грозой.",
                "Медный якорь: молнии в радиусе 24 притягиваются к точке, разряд цепью по меченым.");
        def(Branch.IRON, "Сверхспрессованный Магнитный Железный Стержень",
                "Посох Железного Ярма", Element.EARTH, Role.CONTROL,
                Material.RAW_IRON, 2, 30, Mech.IRON_YOKE,
                "Железная тяжесть в чистом виде. Держит в руках — и держит тебя.",
                "Железное ярмо: цель прижата к земле (слоу, без прыжков, быстрое падение).");
        def(Branch.GOLD, "Сверхспрессованный Инертный Золотой Нуклеус",
                "Посох Золотого Подкупа", Element.LIGHT, Role.SUPPORT,
                Material.GOLD_NUGGET, 4, 20, Mech.GOLD_BRIBE,
                "Жадность мира, лишённая блеска. Абсолютно честная.",
                "Золотой подкуп: незер-мобы и зомби не трогают цель, но урон по ней +50%.");
        def(Branch.REDSTONE, "Сверхспрессованный Импульсный Редстоуновый Конденсатор",
                "Посох Импульсного Разряда", Element.LIGHTNING, Role.DAMAGE,
                Material.REDSTONE, 2, 6, Mech.REDSTONE_PULSE,
                "Сигнал без схемы. Ноль и единица, спрессованные в кулак.",
                "Разряд: урон 4 + наэлектризованность (молнии и разряды ×2); кликает рычаги и двери на расстоянии.");
        def(Branch.LAPIS, "Сверхспрессованный Чародейный Лазурный Пигмент",
                "Посох Лазурной Печати", Element.ARCANE, Role.SUPPORT,
                Material.LAPIS_LAZULI, 2, 18, Mech.LAPIS_MARK,
                "Синева глубин, растворённая в чарах. Кладут в печать.",
                "Лазурная печать: весь урон посохов по цели +3, цель светится для команды.");
        def(Branch.QUARTZ, "Сверхспрессованный Звонкий Кварцевый Призмо-Слиток",
                "Посох Кварцевого Пинга", Element.LIGHT, Role.RECON,
                Material.QUARTZ, 1, 10, Mech.QUARTZ_PING,
                "Кварц без единой трещины. Звенит, если рядом чужое тепло.",
                "Кварцевый пинг: подсвечивает живое сквозь стены в радиусе 12.");
        def(Branch.DIAMOND, "Сверхспрессованный Монокристаллический Алмазный Клинок",
                "Посох Алмазной Грани", Element.ICE, Role.DAMAGE,
                Material.DIAMOND, 1, 7, Mech.DIAMOND_EDGE,
                "Один кристалл вместо тысячи. Идеальная плоскость разрушения.",
                "Конус-срез: урон 6, игнорирует 20% брони; главная цель — хрупкая (следующий удар ×2).");
        def(Branch.EMERALD, "Сверхспрессованный Живородящий Изумрудный Сгусток",
                "Посох Изумрудного Договора", Element.LIFE, Role.SUPPORT,
                Material.EMERALD, 1, 5, Mech.EMERALD_DEAL,
                "Зелёное согласие тысяч сделок. Изумруд помнит каждого купца.",
                "Договор: лечит цель на 6 сердец, снимает обморожение и гниль, гасит обычное горение.");
        def(Branch.DEBRIS, "Сверхспрессованный Древний Незеритовый Стержень",
                "Посох Незеритовой Жара", Element.FIRE, Role.DEFENSE,
                Material.NETHERITE_SCRAP, 1, 12, Mech.DEBRIS_QUAKE,
                "Металл погибшей эпохи. Слишком стар, чтобы остывать.",
                "Пока в руке — уголь не липнет (жар выжигает пыль). Ударная волна 3×3: урон 8, отброс, поджиг.");
        def(Branch.AMETHYST, "Сверхспрессованный Резонансный Аметистовый Звон",
                "Посох Аметистового Резонанса", Element.ECHO, Role.SUPPORT,
                Material.AMETHYST_SHARD, 1, 15, Mech.AMETHYST_RESONATE,
                "Хрустальный аккорд, сжатый до одной ноты. Отзывается на всё живое.",
                "Резонансная волна: урон 2 в радиусе 8, ВСЕ состояния продлеваются вдвое, резонирующие получают 6.");

        // ==================== ЛЕСОРУБ (10) ====================
        def(Branch.OAK, "Сверхспрессованная Дубовая Кора-Броня",
                "Посох Дубовой Стойкости", Element.LIFE, Role.DEFENSE,
                Material.OAK_PLANKS, 3, 30, Mech.OAK_BARK,
                "Тысячелетняя кора, спрессованная в живую броню. Растёт вместе с вами.",
                "Кора дуба: поглощение урона, восстанавливается сама, пока держится.");
        def(Branch.SPRUCE, "Сверхспрессованный Смолистый Еловый Экстракт",
                "Посох Хвойного Мороза", Element.ICE, Role.CONTROL,
                Material.SPRUCE_PLANKS, 3, 10, Mech.SPRUCE_FROST_CONE,
                "Смола северных лесов. Холодит сильнее, чем пахнет.",
                "Хвойный конус: обморожение толпе в конусе 6, лёгкий холодный урон.");
        def(Branch.BIRCH, "Сверхспрессованный Берёзовый Живительный Сок",
                "Посох Берёзового Сока", Element.LIFE, Role.SUPPORT,
                Material.BIRCH_PLANKS, 3, 45, Mech.BIRCH_CLEANSE,
                "Первая весенняя влага белого леса. Смывает всё дурное.",
                "Берёзовый сок: снимает ВСЕ состояния с себя или цели, регенерация на 30 с.");
        def(Branch.JUNGLE, "Сверхспрессованный Лианный Джунглевый Хват",
                "Посох Лианы-Крюка", Element.LIFE, Role.MOBILITY,
                Material.JUNGLE_PLANKS, 3, 12, Mech.JUNGLE_HOOK,
                "Сила лиан, тянущих к солнцу сквозь полог джунглей.",
                "Лиана-крюк: притягивает цель к себе или себя к блоку (до 10 блоков).");
        def(Branch.ACACIA, "Сверхспрессованный Колючий Акациевый Ёж",
                "Посох Акациевых Колючек", Element.LIFE, Role.DEFENSE,
                Material.ACACIA_PLANKS, 3, 30, Mech.ACACIA_THORNS,
                "Пустынная колючка, готовая отвечать на любой удар.",
                "Колючий ореол на 20 с: каждый удар в ближнем бою по вам — 3 урона обидчику.");
        def(Branch.DARK_OAK, "Сверхспрессованный Тёмный Дубовый Морок",
                "Посох Тёмного Покрова", Element.DARK, Role.CONTROL,
                Material.DARK_OAK_PLANKS, 3, 25, Mech.DARK_OAK_VEIL,
                "Ночь, украденная у тёмного леса и слепленная в ком.",
                "Тёмный покров: зона тьмы 5×5 на 15 с — мобы слепнут, вы видите; стращает трусов.");
        def(Branch.MANGROVE, "Сверхспрессованный Мангровый Корневой Плетень",
                "Посох Мангровых Корней", Element.LIFE, Role.CONTROL,
                Material.MANGROVE_PLANKS, 3, 30, Mech.MANGROVE_ROOTS,
                "Корневой войлок болот: держит и лечит своих, держит чужих намертво.",
                "Корневая сеть 3×3 на 30 с: враги оплетены корнями, вы на сети лечитесь.");
        def(Branch.CHERRY, "Сверхспрессованный Вишнёвый Лепестковый Сироп",
                "Посох Вишнёвого Вихря", Element.LIFE, Role.CONTROL,
                Material.CHERRY_PLANKS, 3, 25, Mech.CHERRY_STORM,
                "Весна вишнёвой долины, уваренная до сладкой дымки.",
                "Лепестковый вихрь: мобы в радиусе 5 очарованы — нейтральны и идут к вам.");
        def(Branch.PALE_OAK, "Сверхспрессованная Бледная Мерцающая Жила",
                "Посох Бледной Окаменелости", Element.ICE, Role.CONTROL,
                Material.PALE_OAK_PLANKS, 3, 40, Mech.PALE_OAK_PETRIFY,
                "Древесина бледного сада: застывший ужас в каждом волокне.",
                "Окаменение: цель цепенеет на 5 с (не двигается, но действует) и светится.");
        def(Branch.RESIN, "Сверхспрессованный Смоляной Янтарный Капкан",
                "Посох Смоляного Капкана", Element.LIFE, Role.CONTROL,
                Material.RESIN_CLUMP, 2, 14, Mech.RESIN_TRAP,
                "Янтарь с мушкой внутри. Кто-то не успел.",
                "Смоляной капкан: цель липкая (слоу III); от огня горит долго, от льда — треснет.");

        // ==================== ЗЕМЛЕДЕЛЕЦ (11) ====================
        def(Branch.WHEAT, "Сверхспрессованный Золотой Колосовой Композит",
                "Посох Урожайного Призыва", Element.LIFE, Role.RESOURCES,
                Material.WHEAT, 4, 5, Mech.WHEAT_HARVEST,
                "Десять тысяч колосьев, сжатые до одного золотого кирпича.",
                "Урожайный призыв: притягивает дроп в радиусе 8 и досеивает пустые грядки 3×3.");
        def(Branch.CARROT, "Сублимированный Морковный Композит",
                "Морковный Посох", Element.LIFE, Role.SUPPORT,
                Material.CARROT, 2, 20, Mech.CARROT_VITAMIN,
                "64 000 морковок, лишённых воды и времени. Витамины в чистом виде.",
                "Морковное зрение: ночное зрение 120 с себе и союзникам. Пассивка: витаминный щит раз в 3 мин спасает от смертельного урона (5 морковок).");
        def(Branch.POTATO, "Сублимированный Крахмалистый Картофельный Денатурат",
                "Посох Полевой Кухни", Element.EARTH, Role.RESOURCES,
                Material.POTATO, 2, 8, Mech.POTATO_KITCHEN,
                "Крахмал без воды: одна ложка кормит роту.",
                "Полевая кухня: мгновенно печёт до 16 картофелин, сытость III группе на 60 с.");
        def(Branch.BEETROOT, "Сублимированный Свекольный Купоросный Концентрат",
                "Посох Свекольного Обеда", Element.LIFE, Role.SUPPORT,
                Material.BEETROOT, 4, 25, Mech.BEETROOT_FEAST,
                "Борщевой концентрат армейского запаса. Восстанавливает буквально всё.",
                "Свекольный обед: мгновенно +4 сердца всей группе в радиусе 5.");
        def(Branch.MELON, "Сублимированный Арбузный Влагоконденсат",
                "Посох Арбузной Влаги", Element.WATER, Role.DEFENSE,
                Material.MELON_SLICE, 3, 15, Mech.MELON_QUENCH,
                "Летняя мякоть, из которой выжали саму жару.",
                "Арбузная влага: тушит огонь в радиусе 6 (включая ВЕЧНОЕ горение), цель пропитана водой.");
        def(Branch.PUMPKIN, "Сублимированный Тыквенный Лик-Стереолит",
                "Посох Тыквенного Лика", Element.DARK, Role.CONTROL,
                Material.PUMPKIN, 1, 18, Mech.PUMPKIN_SCARE,
                "Лицо, вырезанное в ночь сбора урожая. Смотрит в душу.",
                "Лик урожая: конус страха на 8 — мобы бегут от вас, позолоченных особенно пугает.");
        def(Branch.SUGAR_CANE, "Сублимированный Кристаллический Тростниковый Сахарат",
                "Посох Сахарного Рывка", Element.AIR, Role.MOBILITY,
                Material.SUGAR_CANE, 4, 25, Mech.CANE_RUSH,
                "Чистая энергия тростника: кристалл, который хочет бежать.",
                "Сахарный рывок: скорость III и прыжок I на 20 с.");
        def(Branch.COCOA, "Сублимированный Горький Какао-Плитолит",
                "Посох Какао-Плоти", Element.LIFE, Role.SUPPORT,
                Material.COCOA_BEANS, 3, 6, Mech.COCOA_FORTIFY,
                "Чёрный шоколад в его честнейшей форме. Горечь = сила.",
                "Какао-плоть: мгновенно +4 сердца и насыщение.");
        def(Branch.NETHER_WART, "Сублимированный Адский Наростовый Настой",
                "Посох Наростовой Заразы", Element.ARCANE, Role.DAMAGE,
                Material.NETHER_WART, 2, 8, Mech.WART_BOMB,
                "Настойка на адском наросте. Заражение — вопрос времени.",
                "Наростовая зараза: бросок-бомба, яд площади; умершие от яда заражают соседей.");
        def(Branch.WOOL, "Сублимированный Мягкий Шерстяной Кокон-Войлок",
                "Посох Шерстяной Тишины", Element.AIR, Role.CONTROL,
                Material.WHITE_WOOL, 3, 30, Mech.WOOL_SILENCE,
                "Отара в одном рулоне. Абсолютная тишина — если обернуться.",
                "Шерстяной кокон: цель беззвучна для сенсоров и не атакует (игроки 8 с, мобы 20 с).");
        def(Branch.HONEY, "Сублимированный Вязкий Медовый Тягун",
                "Посох Медового Залива", Element.EARTH, Role.CONTROL,
                Material.HONEYCOMB, 2, 20, Mech.HONEY_GLAZE,
                "Мёд, из которого вынули само время. Течёт против гравитации.",
                "Медовый залив: лужа липкого мёда 3×3 — вязкая зона; прямое попадание делает липкой цель.");

        // ==================== ФЛОРИСТ (6) ====================
        def(Branch.CACTUS, "Сублимированный Игольчатый Кактусовый Плот",
                "Посох Кактусового Кольца", Element.EARTH, Role.ENVIRONMENT,
                Material.CACTUS, 2, 20, Mech.CACTUS_RING,
                "Пустыня, научившаяся защищаться миллионом игл.",
                "Кактусовое кольцо: живая изгородь 5×5 — терраформ и урон при касании.");
        def(Branch.BAMBOO, "Сублимированный Полый Бамбуковый Ствол-Пружина",
                "Посох Бамбукового Батута", Element.AIR, Role.MOBILITY,
                Material.BAMBOO, 3, 25, Mech.BAMBOO_JUMP,
                "Бамбук, растущий со скоростью дня за секунду. Теперь и вверх.",
                "Бамбуковый батут: прыжок III и плавное падение на 15 с.");
        def(Branch.FLOWERS, "Сублимированный Нектарный Цветочный Эликсир",
                "Посох Цветочного Обаяния", Element.LIFE, Role.CONTROL,
                Material.POPPY, 2, 15, Mech.FLOWER_CHARM,
                "Вся прелесть луга в одной капле. Спорить с ней невозможно.",
                "Цветочное обаяние: один моб очарован на 20 с — мирен и следует за вами.");
        def(Branch.MOSS, "Сублимированный Моховой Мягкий Дёрн",
                "Посох Мшистой Мягкости", Element.EARTH, Role.DEFENSE,
                Material.MOSS_BLOCK, 2, 40, Mech.MOSS_CUSHION,
                "Зелёная тишина старого леса. Смягчает любые падения.",
                "Моховая подушка: зона 7×7 на 60 с — без урона от падения, медленное падение.");
        def(Branch.AZALEA, "Сублимированный Дурманящий Азалиевый Порошок",
                "Посох Азалиевого Дурмана", Element.LIFE, Role.DAMAGE,
                Material.AZALEA, 2, 10, Mech.AZALEA_TOXIN,
                "Цветение, которое лучше не нюхать. Сонливость гарантирована.",
                "Азалиевый дурман: яд II и вялость на 8 с, цель в спорах — огонь выжжет вспышкой.");
        def(Branch.MUSHROOMS, "Сублимированный Плотный Споровой Мицелий",
                "Посох Грибных Спор", Element.LIFE, Role.CONTROL,
                Material.RED_MUSHROOM, 2, 22, Mech.MUSHROOM_CLOUD,
                "Миллиард спор в одном блоке. Дышать осторожно.",
                "Облако спор 5×5: тошнота и растерянность; в спорах легко вспыхивают от огня.");

        // ==================== РЫБОЛОВ (4) ====================
        def(Branch.KELP, "Сублимированный Питательный Водорослевый Брикет",
                "Посох Водорослевых Объятий", Element.WATER, Role.SUPPORT,
                Material.KELP, 3, 25, Mech.KELP_WATERS,
                "Морская глубина, спрессованная в съедобный слиток.",
                "Водорослевые объятия: себе — полный водный режим на 60 с; цели — оплетение (слоу, без прыжков).");
        def(Branch.CORAL, "Сверхспрессованный Живой Коралловый Риф-Каркас",
                "Посох Кораллового Рифа", Element.WATER, Role.ENVIRONMENT,
                Material.TUBE_CORAL, 2, 12, Mech.CORAL_REEF,
                "Риф, выросший за одну ночь. Живой и тёплый на ощупь.",
                "Коралловый риф: стенка кораллов 1×5 перед вами — мгновенное укрытие.");
        def(Branch.SPONGE, "Сверхспрессованный Пористый Губчатый Осушитель",
                "Посох Губки-Осушителя", Element.WATER, Role.ENVIRONMENT,
                Material.SPONGE, 1, 30, Mech.SPONGE_DRAIN,
                "Сухость как принцип. Пьёт океан и просит добавки.",
                "Осушение: убирает воду в радиусе 5, снимает пропитанность (и защиту вечного огня!).");
        def(Branch.PRISMARINE, "Сверхспрессованный Мерцающий Призмариновый Кристалл-Фокус",
                "Посох Призмаринового Луча", Element.WATER, Role.DAMAGE,
                Material.PRISMARINE_SHARD, 2, 7, Mech.PRISMARINE_BEAM,
                "Свет затонувших храмов, сфокусированный до лезвия.",
                "Призмариновый луч: урон 5 и толчок на 12 блоков, цель пропитана водой.");

        // ==================== КАМЕНЩИК (8) ====================
        def(Branch.SAND, "Сверхспрессованный Сыпучий Стеклогранулат",
                "Посох Песчаной Бури", Element.EARTH, Role.CONTROL,
                Material.SAND, 3, 14, Mech.SAND_STORM,
                "Песок, который помнит, что был стеклом. И не прочь снова.",
                "Песчаная буря: слепота конусу на 8; прямое попадание остекляет цель (осколки от удара).");
        def(Branch.GRAVEL, "Сверхспрессованный Гравийный Молот-Груз",
                "Посох Гравитационного Молота", Element.EARTH, Role.DAMAGE,
                Material.GRAVEL, 3, 12, Mech.GRAVEL_HAMMER,
                "Тяжесть отвесных карьеров в карманном формате.",
                "Гравитационный молот: столб гравия над целью — удар падающим камнем и придавливание.");
        def(Branch.SANDSTONE, "Сверхспрессованный Слоистый Песчаниковый Литоблок",
                "Посох Каменного Моста", Element.EARTH, Role.MOBILITY,
                Material.SANDSTONE, 2, 10, Mech.SANDSTONE_BRIDGE,
                "Слои миллионов лет, уложенные в один вздох.",
                "Каменный мост 1×5 и толчок вперёд — путь появляется под вашими шагами.");
        def(Branch.SNOW_ICE, "Сверхспрессованный Криогенный Ледяной Нуклеус",
                "Посох Ледяного Окования", Element.ICE, Role.CONTROL,
                Material.SNOWBALL, 4, 35, Mech.ICE_BIND,
                "Зима на пике формы. Оставляет синяки на времени.",
                "Ледяные оковы: сильное обморожение цели; в воде цель вмерзает в лёд.");
        def(Branch.GLASS, "Сверхспрессованный Абсолютно Прозрачный Стекломассив",
                "Посох Стеклянного Камуфляжа", Element.AIR, Role.SUPPORT,
                Material.GLASS, 2, 30, Mech.GLASS_FADE,
                "Стекло, пропустившее сквозь себя само понятие «видно».",
                "Стеклянный камуфляж: невидимость на 15 с, НЕ снимается атакой — только водой, бетоном или светом.");
        def(Branch.BRICKS, "Сверхспрессованная Обожжённая Кирпичная Кладка",
                "Посох Кирпичного Бастиона", Element.EARTH, Role.DEFENSE,
                Material.BRICK, 4, 25, Mech.BRICKS_BASTION,
                "Стена, которая пережила свой город.",
                "Кирпичный бастион: мгновенный купол 3×3×3 вокруг вас.");
        def(Branch.TERRACOTTA, "Сверхспрессованный Глазурованный Терракотовый Керамит",
                "Посох Терракотовой Маски", Element.EARTH, Role.SUPPORT,
                Material.TERRACOTTA, 2, 35, Mech.TERRACOTTA_MASK,
                "Обжиг, после которого лицо становится декором.",
                "Терракотовая маска: на 20 с мобы вас не таргетят — вы часть пейзажа.");
        def(Branch.CONCRETE, "Сверхспрессованный Схватывающийся Бетонный Саркофаг",
                "Посох Бетонного Саркофага", Element.EARTH, Role.CONTROL,
                Material.WHITE_CONCRETE_POWDER, 2, 45, Mech.CONCRETE_TOMB,
                "Время, застывшее на 28-й день. Стирает всё лишнее.",
                "Бетонный саркофаг: цель обездвижена 5 с; СТИРАЕТ все состояния (и баффы, и угрозы).");

        // ==================== СВЕТОВОД (6) ====================
        def(Branch.GLOWSTONE, "Сверхспрессованный Слепящий Светокаменный Фотонит",
                "Посох Световой Вспышки", Element.LIGHT, Role.CONTROL,
                Material.GLOWSTONE_DUST, 2, 20, Mech.GLOWSTONE_FLASH,
                "Свет в такой плотности, что темнота сдаётся без боя.",
                "Световая вспышка: ослепление в радиусе 6 на 5 с; очарованные союзники лечатся светом.");
        def(Branch.SEA_LANTERN, "Сверхспрессованный Пульсирующий Морской Люминофор",
                "Посох Морского Маяка", Element.LIGHT, Role.SUPPORT,
                Material.PRISMARINE_CRYSTALS, 2, 30, Mech.SEA_LANTERN_BEACON,
                "Пульс глубин, вынесенный на берег.",
                "Морской маяк: столб света 3×3, группе — сила подводного источника на 30 с.");
        def(Branch.SHROOMLIGHT, "Сверхспрессованный Тёплый Светогрибной Фитиль",
                "Посох Тёплого Свечения", Element.LIGHT, Role.SUPPORT,
                Material.SHROOMLIGHT, 1, 40, Mech.SHROOMLIGHT_AURA,
                "Тёплый свет, растущий на тёмных местах. Всегда без спешки.",
                "Тёплая аура: союзники в радиусе 5 регенерируют — свет, который лечит.");
        def(Branch.FROG_LIGHT, "Сверхспрессованный Лунный Жабий Светоноситель",
                "Посох Жабьего Дождя", Element.WATER, Role.ENVIRONMENT,
                Material.OCHRE_FROGLIGHT, 1, 90, Mech.FROG_RAIN,
                "Лунный свет, пойманный квакающей ночью.",
                "Жабий дождь: вызывает дождь на 60 с — тушит ВЕЧНОЕ горение по всему миру; молнии ищут позолоченных и заряженных.");
        def(Branch.CANDLE, "Сверхспрессованный Восковой Ритуальный Огнивник",
                "Посох Свечной Ритуалистики", Element.LIGHT, Role.SUPPORT,
                Material.CANDLE, 1, 35, Mech.CANDLE_RITUAL,
                "Семь огней, сведённые к одному огниву.",
                "Свечной ритуал: круг из 7 свечей на 20 с — сила посохов в зоне +25%, топливо в зоне не тратится.");
        def(Branch.GLOW_FLORA, "Сверхспрессованный Светоносный Люмен-Синтезат",
                "Посох Внутреннего Света", Element.LIGHT, Role.SUPPORT,
                Material.GLOW_BERRIES, 2, 25, Mech.GLOWFLORA_INNERLIGHT,
                "Свет, который больше не нужен снаружи.",
                "Внутренний свет: цель светится изнутри 60 с (видна всем и в полной тьме) — метка и маяк.");

        // ==================== ИНЖЕНЕР (3) ====================
        def(Branch.MECHANISMS, "Сверхспрессованный Кинетический Поршневой Узел",
                "Посох Поршневого Толчка", Element.TECH, Role.CONTROL,
                Material.LEVER, 1, 12, Mech.MECH_PUSH,
                "Механика, спрессованная в один безотказный жест.",
                "Поршневой толчок: отбрасывает цель на 6 блоков силой поршня.");
        def(Branch.COPPER_TECH, "Сверхспрессованный Гальванический Меднотех-Электролит",
                "Посох Гальванической Дуги", Element.LIGHTNING, Role.DAMAGE,
                Material.COPPER_TORCH, 1, 8, Mech.GALVANIC_ARC,
                "Электролиз в чистом виде: дуга выбирает кратчайший путь.",
                "Гальваническая дуга: цепь до 3 целей (урон 3); мокрые и заряженные — приоритет и двойной урон.");
        def(Branch.RAILS, "Сверхспрессованный Магистральный Рельсовый Ствол",
                "Посох Рельсового Разгона", Element.TECH, Role.MOBILITY,
                Material.RAIL, 2, 15, Mech.RAIL_DASH,
                "Магистраль, свёрнутая в рулон.",
                "Рельсовый разгон: линия рельсов 1×8 и скорость II — дорога сама несёт вас.");

        // ==================== НЕЗЕР (8) ====================
        def(Branch.NETHERRACK, "Сверхспрессованный Вечнотлеющий Незерраковый Очаг",
                "Посох Адской Подложки", Element.FIRE, Role.ENVIRONMENT,
                Material.NETHERRACK, 4, 18, Mech.NETHERRACK_PYRE,
                "Плоть ада: не горит, но вечно тлеет.",
                "Адская подложка: зажигает землю под целью — на незерраке огонь не гаснет никогда.");
        def(Branch.SOUL, "Сверхспрессованная Связка Проклятых Душ",
                "Посох Пламени Душ", Element.DARK, Role.CONTROL,
                Material.SOUL_SAND, 2, 14, Mech.SOUL_FLAME,
                "Крик, застывший в песке душ.",
                "Пламя душ: синий огонь (урон 4), цель отмечена — телепорты (жемчуг, эндермены) заблокированы.");
        def(Branch.MAGMA, "Сверхспрессованное Раскалённое Магмовое Ядро",
                "Посох Магмового Жара", Element.FIRE, Role.DAMAGE,
                Material.MAGMA_BLOCK, 2, 6, Mech.MAGMA_IGNITE,
                "Сердце вулкана, не знающее слова «остыть».",
                "Магмовый жар: поджигает цель на 8 с. ЗАКОН: угольное покрытие + этот жар = ВЕЧНОЕ горение до воды. Влажных не берёт.");
        def(Branch.BLACKSTONE, "Сверхспрессованный Чёрный Притягивающий Камень",
                "Посох Чёрного Притяжения", Element.DARK, Role.CONTROL,
                Material.BLACKSTONE, 2, 25, Mech.BLACKSTONE_VORTEX,
                "Тяжесть бездны, у которой появилась цель.",
                "Чёрное притяжение: вихрь стягивает всех в точке прицела к центру.");
        def(Branch.BASALT, "Сверхспрессованный Столбчатый Базальтовый Шпиль",
                "Посох Базальтового Шпиля", Element.EARTH, Role.ENVIRONMENT,
                Material.BASALT, 2, 20, Mech.BASALT_PILLAR,
                "Геометрия извержений: прямая линия из хаоса.",
                "Базальтовый шпиль: поднимает цель столбом в 4 блока — пьедестал или тюрьма.");
        def(Branch.CRIMSON, "Сверхспрессованный Багровый Стимулятор Ярости",
                "Посох Багрового Бешенства", Element.LIFE, Role.CONTROL,
                Material.CRIMSON_PLANKS, 3, 30, Mech.CRIMSON_RAGE,
                "Лес, который кричит. Теперь кричит в вас.",
                "Багровое бешенство: моб бросается на ближайшего (даже своего); игрок впадает в ярость: +25% урона, посохи и жемчуг заблокированы на 10 с.");
        def(Branch.WARPED, "Сверхспрессованный Фазовый Искривлённый Смещатель",
                "Посох Искривлённого Шага", Element.VOID, Role.CONTROL,
                Material.WARPED_PLANKS, 3, 25, Mech.WARPED_SHIFT,
                "Пространство, перепутавшее свои концы.",
                "Искривлённый шаг: телепортирует цель в случайную точку в 8 блоках — фазовый сброс планов.");
        def(Branch.WART, "Сверхспрессованный Гнилостный Некро-Блок",
                "Посох Гнили", Element.DARK, Role.DAMAGE,
                Material.NETHER_WART_BLOCK, 2, 12, Mech.WART_ROT,
                "Гниль, доведённая до совершенства.",
                "Гниль: увядание I на 8 с — регенерация бессильна; свет лечит гниль.");

        // ==================== КРАЙ (4) ====================
        def(Branch.END_STONE, "Сверхспрессованный Пустотный Энд-каменный Обелиск",
                "Посох Пустотного Обелиска", Element.VOID, Role.CONTROL,
                Material.END_STONE, 3, 40, Mech.ENDSTONE_OBELISK,
                "Кромка мира, спрессованная в зубец. Пустота признаёт его.",
                "Пустотный обелиск: зона 8×8 на 30 с — телепорты внутри запрещены (жемчуг, хорус, искривление).");
        def(Branch.PURPUR, "Сверхспрессованный Пурпурный Антигравитон-Блок",
                "Посох Пурпурной Левитации", Element.AIR, Role.MOBILITY,
                Material.PURPUR_BLOCK, 2, 20, Mech.PURPUR_LEVITATE,
                "Камень, забывший о внизу.",
                "Пурпурная левитация: цель или вы парите 10 с — поднять врага или взлететь.");
        def(Branch.CHORUS, "Сверхспрессованный Хорусовый Фазофрукт",
                "Посох Хорусового Перелёта", Element.VOID, Role.MOBILITY,
                Material.CHORUS_FRUIT, 1, 8, Mech.CHORUS_PHASE,
                "Прыжок между гранями мира, уместившийся в плоде.",
                "Хорусовый перелёт: телепорт в точку взгляда до 12 бл.; фазовый сдвиг смывает ваши состояния.");
        def(Branch.OBSIDIAN, "Сверхспрессованный Чёрный Обсидиановый Кожух",
                "Посох Обсидиановой Кожи", Element.EARTH, Role.DEFENSE,
                Material.OBSIDIAN, 1, 30, Mech.OBSIDIAN_SKIN,
                "Терпение вулкана в окончательной форме.",
                "Обсидиановая кожа: сопротивление III и иммунитет к отбрасыванию на 10 с.");

        // ==================== АНОМАЛИИ (1) ====================
        def(Branch.SCULK, "Сверхспрессованный Резонирующий Скульк-Эхокамень",
                "Посох Эхолокации", Element.ECHO, Role.RECON,
                Material.SCULK, 1, 12, Mech.SCULK_PULSE,
                "Тишина, которая слушает. Теперь говорит.",
                "Эхо-импульс: подсвечивает всё живое в 16 бл., резонансные разряды накладывают гниль.");
    }

    public static StaffDef def(Branch b) { return DEFS.get(b); }

    public static java.util.Collection<StaffDef> all() { return DEFS.values(); }

    public static boolean ready() { return DEFS.size() == Branch.values().length; }

    /** Единиц ресурса для вещества тира I ветки. */
    public static long unitsFor(Branch b) {
        return ru.deepmagic.util.Conf.unitsFor(b.rarity);
    }
}
