package ru.deepprogression.model;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Ветка материала DeepProgression: секция, название, цвета, иконки,
 * редкость, 10 званий. Имена старых веток v2.x (STONE..DEBRIS) сохранены
 * для миграции профилей игроков.
 */
public enum Branch {

    // ==================== ШАХТЁР (12) ====================
    STONE(Section.MINING, "Камень", "&7", Material.COBBLESTONE, Material.COBBLESTONE, Rarity.COMMON, null,
            new String[]{"Каменотёс-новичок", "Подмастерье каменотёса", "Каменотёс",
                    "Резчик камня", "Мастер каменоломни", "Ветеран каменоломен",
                    "Инженер каменных разработок", "Бригадир каменного карьера",
                    "Директор каменоломен", "Каменный титан"}),

    COAL(Section.MINING, "Уголь", "&8", Material.COAL_BLOCK, Material.COAL, Rarity.COMMON, null,
            new String[]{"Новичок угледобытчик", "Помощник шахтёра", "Шахтёр угольных штолен",
                    "Мастер угольного забоя", "Ветеран угольных копей", "Инженер угольной шахты",
                    "Бригадир углепромысла", "Директор угольной шахты",
                    "Владыка угольных глубин", "Угольный магнат"}),

    COPPER(Section.MINING, "Медь", "&6", Material.COPPER_BLOCK, Material.RAW_COPPER, Rarity.COMMON, null,
            new String[]{"Новичок медедобытчик", "Ученик медеплавильщика", "Медный старатель",
                    "Мастер медного рудника", "Ветеран медных рудников", "Инженер медеплава",
                    "Бригадир медного промысла", "Директор медного завода",
                    "Владыка медных жил", "Медный барон"}),

    IRON(Section.MINING, "Железо", "&f", Material.IRON_BLOCK, Material.RAW_IRON, Rarity.COMMON, null,
            new String[]{"Новичок железодобытчик", "Ученик кузнеца", "Железный старатель",
                    "Мастер железного рудника", "Ветеран железных рудников", "Инженер доменной печи",
                    "Бригадир железного промысла", "Директор металлургического завода",
                    "Владыка железных жил", "Железный магнат"}),

    GOLD(Section.MINING, "Золото", "&e", Material.GOLD_BLOCK, Material.RAW_GOLD, Rarity.MEDIUM, null,
            new String[]{"Новичок старатель", "Золотой искатель", "Копатель золотых жил",
                    "Мастер золотого рудника", "Ветеран золотых приисков", "Инженер аффинажа",
                    "Бригадир золотого промысла", "Директор золотого прииска",
                    "Владыка золотых глубин", "Золотой магнат"}),

    REDSTONE(Section.MINING, "Редстоун", "&c", Material.REDSTONE_BLOCK, Material.REDSTONE, Rarity.MEDIUM, null,
            new String[]{"Новичок сигнальщик", "Юный инженер", "Копатель редстоуна",
                    "Мастер редстоуновых жил", "Ветеран редстоуновых рудников", "Инженер сигнальных схем",
                    "Бригадир редстоунового промысла", "Директор редстоунового завода",
                    "Повелитель сигналов", "Повелитель редстоуна"}),

    LAPIS(Section.MINING, "Лазурит", "&9", Material.LAPIS_BLOCK, Material.LAPIS_LAZULI, Rarity.MEDIUM, null,
            new String[]{"Новичок красильщик", "Ученик алхимика", "Копатель лазурита",
                    "Мастер лазуритовых жил", "Ветеран лазуритовых рудников", "Инженер лазуритового промысла",
                    "Бригадир синего камня", "Директор лазуритового рудника",
                    "Владыка синих глубин", "Владыка лазурита"}),

    QUARTZ(Section.MINING, "Кварц", "&f", Material.QUARTZ_BLOCK, Material.QUARTZ, Rarity.MEDIUM, null,
            new String[]{"Новичок кварцедобытчик", "Искатель кварца", "Копатель кварцевых жил",
                    "Мастер кварцевого рудника", "Ветеран незерских выработок", "Инженер кварцевого промысла",
                    "Бригадир нижних выработок", "Директор кварцевой фабрики",
                    "Владыка незерских глубин", "Кварцевый барон"}),

    DIAMOND(Section.MINING, "Алмаз", "&b", Material.DIAMOND_BLOCK, Material.DIAMOND, Rarity.RARE, null,
            new String[]{"Новичок алмазодобытчик", "Ученик гранильщика", "Копатель алмазных жил",
                    "Мастер алмазной шахты", "Ветеран алмазных копей", "Инженер алмазного промысла",
                    "Бригадир алмазной шахты", "Директор алмазной фабрики",
                    "Владыка алмазных глубин", "Алмазный магнат"}),

    EMERALD(Section.MINING, "Изумруд", "&a", Material.EMERALD_BLOCK, Material.EMERALD, Rarity.RARE, null,
            new String[]{"Новичок изумрудодобытчик", "Ученик торговца", "Копатель изумрудных жил",
                    "Мастер изумрудной шахты", "Ветеран изумрудных копей", "Инженер изумрудного промысла",
                    "Бригадир изумрудной шахты", "Директор изумрудной фабрики",
                    "Владыка изумрудных глубин", "Изумрудный магнат"}),

    DEBRIS(Section.MINING, "Незерит", "&5", Material.NETHERITE_BLOCK, Material.NETHERITE_SCRAP, Rarity.RARE, null,
            new String[]{"Соискатель древностей", "Юный археолог-шахтёр", "Копатель древних обломков",
                    "Мастер незеритовых выработок", "Ветеран глубин незера", "Инженер незеритового промысла",
                    "Бригадир нижней тверди", "Директор незеритовой фабрики",
                    "Владыка древних глубин", "Незеритовая легенда"}),

    AMETHYST(Section.MINING, "Аметист", "&d", Material.AMETHYST_BLOCK, Material.AMETHYST_SHARD, Rarity.MEDIUM, null,
            new String[]{"Новичок кристаллограф", "Искатель аметистовых жеод", "Сборщик кристаллических наростов",
                    "Мастер аметистовых гротов", "Ветеран геодных пещер", "Инженер кристаллической огранки",
                    "Бригадир аметистовых промыслов", "Директор кристалловой фабрики",
                    "Владыка резонирующих недр", "Аметистовый архонт"}),

    // ==================== ЛЕСОРУБ (10) ====================
    OAK(Section.WOOD, "Дуб", "&e", Material.OAK_LOG, Material.OAK_PLANKS, Rarity.COMMON, "дубовых", null),
    SPRUCE(Section.WOOD, "Ель", "&2", Material.SPRUCE_LOG, Material.SPRUCE_PLANKS, Rarity.COMMON, "еловых", null),
    BIRCH(Section.WOOD, "Берёза", "&f", Material.BIRCH_LOG, Material.BIRCH_PLANKS, Rarity.COMMON, "берёзовых", null),
    JUNGLE(Section.WOOD, "Джунгли", "&a", Material.JUNGLE_LOG, Material.JUNGLE_PLANKS, Rarity.COMMON, "лианных", null),
    ACACIA(Section.WOOD, "Акация", "&6", Material.ACACIA_LOG, Material.ACACIA_PLANKS, Rarity.COMMON, "акациевых", null),
    DARK_OAK(Section.WOOD, "Тёмный дуб", "&8", Material.DARK_OAK_LOG, Material.DARK_OAK_PLANKS, Rarity.COMMON, "тёмных", null),
    MANGROVE(Section.WOOD, "Мангр", "&3", Material.MANGROVE_LOG, Material.MANGROVE_PLANKS, Rarity.COMMON, "мангровых", null),
    CHERRY(Section.WOOD, "Вишня", "&d", Material.CHERRY_LOG, Material.CHERRY_PLANKS, Rarity.COMMON, "вишнёвых", null),
    PALE_OAK(Section.WOOD, "Белый дуб", "&7", Material.PALE_OAK_LOG, Material.PALE_OAK_PLANKS, Rarity.COMMON, "белых", null),
    RESIN(Section.WOOD, "Смола", "&6", Material.RESIN_CLUMP, Material.RESIN_BRICK, Rarity.MEDIUM, "янтарных", null),

    // ==================== ЗЕМЛЕДЕЛЕЦ (11) ====================
    WHEAT(Section.FARM, "Пшеница", "&e", Material.WHEAT, Material.WHEAT, Rarity.COMMON, "злаковых", null),
    CARROT(Section.FARM, "Морковь", "&6", Material.CARROT, Material.CARROT, Rarity.COMMON, "морковных", null),
    POTATO(Section.FARM, "Картофель", "&e", Material.POTATO, Material.POTATO, Rarity.COMMON, "картофельных", null),
    BEETROOT(Section.FARM, "Свёкла", "&c", Material.BEETROOT, Material.BEETROOT, Rarity.COMMON, "свекольных", null),
    MELON(Section.FARM, "Арбуз", "&a", Material.MELON, Material.MELON_SLICE, Rarity.COMMON, "бахчевых", null),
    PUMPKIN(Section.FARM, "Тыква", "&6", Material.PUMPKIN, Material.PUMPKIN, Rarity.COMMON, "тыквенных", null),
    SUGAR_CANE(Section.FARM, "Тростник", "&a", Material.SUGAR_CANE, Material.SUGAR_CANE, Rarity.COMMON, "тростниковых", null),
    COCOA(Section.FARM, "Какао", "&6", Material.COCOA_BEANS, Material.COCOA_BEANS, Rarity.COMMON, "какаовых", null),
    NETHER_WART(Section.FARM, "Адский нарост", "&c", Material.NETHER_WART, Material.NETHER_WART, Rarity.MEDIUM, "адских", null),
    WOOL(Section.FARM, "Шерсть", "&f", Material.WHITE_WOOL, Material.WHITE_WOOL, Rarity.COMMON, null,
            new String[]{"Юный пастух", "Пастырь овечьих отар", "Стригаль пушистых стад",
                    "Мастер шерстяного промысла", "Ветеран стрижки", "Инженер шерстяной мануфактуры",
                    "Бригадир текстильного комбината", "Директор шерстяной фабрики",
                    "Владыка пастбищ", "Текстильный магнат"}),
    HONEY(Section.FARM, "Мёд", "&6", Material.HONEY_BLOCK, Material.HONEYCOMB, Rarity.MEDIUM, null,
            new String[]{"Юный бортник", "Пасечник лесных полян", "Сборщик сотовых нектаров",
                    "Мастер ульевых пасек", "Ветеран медового промысла", "Инженер медоварни",
                    "Бригадир восковой мануфактуры", "Директор медового комбината",
                    "Владыка пасечных угодий", "Медовый барон"}),

    // ==================== ФЛОРИСТ (6) ====================
    CACTUS(Section.FLORA, "Кактус", "&a", Material.CACTUS, Material.CACTUS, Rarity.COMMON, "пустынных", null),
    BAMBOO(Section.FLORA, "Бамбук", "&a", Material.BAMBOO_BLOCK, Material.BAMBOO, Rarity.COMMON, "бамбуковых", null),
    FLOWERS(Section.FLORA, "Цветы", "&d", Material.POPPY, Material.POPPY, Rarity.COMMON, "полевых", null),
    MOSS(Section.FLORA, "Мох", "&2", Material.MOSS_BLOCK, Material.MOSS_BLOCK, Rarity.MEDIUM, "мшистых", null),
    AZALEA(Section.FLORA, "Азалия", "&a", Material.FLOWERING_AZALEA, Material.AZALEA, Rarity.MEDIUM, "цветущих", null),
    MUSHROOMS(Section.FLORA, "Грибы", "&c", Material.RED_MUSHROOM, Material.RED_MUSHROOM, Rarity.COMMON, "грибных", null),

    // ==================== РЫБОЛОВ (4) ====================
    KELP(Section.SEA, "Водоросли", "&2", Material.KELP, Material.KELP, Rarity.COMMON, "водорослевых", null),
    CORAL(Section.SEA, "Кораллы", "&d", Material.TUBE_CORAL, Material.TUBE_CORAL_BLOCK, Rarity.MEDIUM, "коралловых", null),
    SPONGE(Section.SEA, "Губки", "&e", Material.SPONGE, Material.SPONGE, Rarity.RARE, "губковых", null),
    PRISMARINE(Section.SEA, "Призмарин", "&b", Material.PRISMARINE, Material.PRISMARINE, Rarity.MEDIUM, "призмариновых", null),

    // ==================== КАМЕНЩИК (8) ====================
    SAND(Section.MASON, "Песок", "&e", Material.SAND, Material.SAND, Rarity.COMMON, "песчаных", null),
    GRAVEL(Section.MASON, "Гравий", "&7", Material.GRAVEL, Material.GRAVEL, Rarity.COMMON, "гравийных", null),
    SANDSTONE(Section.MASON, "Песчаник", "&e", Material.SANDSTONE, Material.SANDSTONE, Rarity.COMMON, "песчаниковых", null),
    SNOW_ICE(Section.MASON, "Лёд", "&b", Material.SNOW_BLOCK, Material.SNOWBALL, Rarity.COMMON, "ледяных", null),
    GLASS(Section.MASON, "Стекло", "&f", Material.GLASS, Material.GLASS, Rarity.MEDIUM, "стекольных", null),
    BRICKS(Section.MASON, "Кладка", "&6", Material.BRICKS, Material.BRICK, Rarity.COMMON, "кирпичных", null),
    TERRACOTTA(Section.MASON, "Терракота", "&6", Material.TERRACOTTA, Material.TERRACOTTA, Rarity.COMMON, "терракотовых", null),
    CONCRETE(Section.MASON, "Бетон", "&f", Material.WHITE_CONCRETE, Material.WHITE_CONCRETE_POWDER, Rarity.COMMON, "бетонных", null),

    // ==================== СВЕТОВОД (6) ====================
    GLOWSTONE(Section.LIGHT, "Светокамень", "&e", Material.GLOWSTONE, Material.GLOWSTONE_DUST, Rarity.MEDIUM, "светокаменных", null),
    SEA_LANTERN(Section.LIGHT, "Морской фонарь", "&b", Material.SEA_LANTERN, Material.SEA_LANTERN, Rarity.RARE, "морских", null),
    SHROOMLIGHT(Section.LIGHT, "Светогриб", "&6", Material.SHROOMLIGHT, Material.SHROOMLIGHT, Rarity.MEDIUM, "светогрибных", null),
    FROG_LIGHT(Section.LIGHT, "Жабий свет", "&a", Material.OCHRE_FROGLIGHT, Material.VERDANT_FROGLIGHT, Rarity.RARE, "жабьих", null),
    CANDLE(Section.LIGHT, "Свечи", "&f", Material.CANDLE, Material.CANDLE, Rarity.MEDIUM, "восковых", null),
    GLOW_FLORA(Section.LIGHT, "Свечения", "&e", Material.GLOW_BERRIES, Material.GLOW_BERRIES, Rarity.MEDIUM, "светоносных", null),

    // ==================== ИНЖЕНЕР (3) ====================
    MECHANISMS(Section.ENGINE, "Механизмы", "&c", Material.PISTON, Material.PISTON, Rarity.MEDIUM, "механических", null),
    COPPER_TECH(Section.ENGINE, "Меднотех", "&6", Material.COPPER_GRATE, Material.COPPER_INGOT, Rarity.COMMON, "меднолитых", null),
    RAILS(Section.ENGINE, "Рельсы", "&7", Material.RAIL, Material.RAIL, Rarity.COMMON, "рельсовых", null),

    // ==================== НЕЗЕР (8) ====================
    NETHERRACK(Section.NETHER, "Незеррак", "&8", Material.NETHERRACK, Material.NETHERRACK, Rarity.COMMON, "адских", null),
    SOUL(Section.NETHER, "Души", "&8", Material.SOUL_SAND, Material.SOUL_SAND, Rarity.COMMON, "душевных", null),
    MAGMA(Section.NETHER, "Магма", "&6", Material.MAGMA_BLOCK, Material.MAGMA_CREAM, Rarity.MEDIUM, "магмовых", null),
    BLACKSTONE(Section.NETHER, "Чёрный камень", "&8", Material.BLACKSTONE, Material.BLACKSTONE, Rarity.COMMON, "чернокаменных", null),
    BASALT(Section.NETHER, "Базальт", "&7", Material.BASALT, Material.BASALT, Rarity.COMMON, "базальтовых", null),
    CRIMSON(Section.NETHER, "Багровый лес", "&c", Material.CRIMSON_STEM, Material.CRIMSON_PLANKS, Rarity.COMMON, "багровых", null),
    WARPED(Section.NETHER, "Искривлённый лес", "&3", Material.WARPED_STEM, Material.WARPED_PLANKS, Rarity.COMMON, "искривлённых", null),
    WART(Section.NETHER, "Бородавки", "&c", Material.NETHER_WART_BLOCK, Material.NETHER_WART_BLOCK, Rarity.COMMON, "бородавчатых", null),

    // ==================== КРАЙ (4) ====================
    END_STONE(Section.END, "Энд-камень", "&e", Material.END_STONE, Material.END_STONE, Rarity.COMMON, "энд-каменных", null),
    PURPUR(Section.END, "Пурпур", "&d", Material.PURPUR_BLOCK, Material.PURPUR_BLOCK, Rarity.MEDIUM, "пурпурных", null),
    CHORUS(Section.END, "Хорус", "&d", Material.CHORUS_FLOWER, Material.CHORUS_FRUIT, Rarity.COMMON, "хорусовых", null),
    OBSIDIAN(Section.END, "Обсидиан", "&5", Material.OBSIDIAN, Material.OBSIDIAN, Rarity.MEDIUM, "обсидиановых", null),

    // ==================== АНОМАЛИИ (1) ====================
    SCULK(Section.ANOMALY, "Скульк", "&9", Material.SCULK, Material.SCULK, Rarity.RARE, "скулковых", null);

    public enum Rarity { COMMON, MEDIUM, RARE }

    public static final String[] TIER_COLORS =
            {"&7", "&a", "&b", "&e", "&6", "&d", "&9", "&c", "&5", "&6&l"};

    public final Section section;
    public final String display;
    public final String color;
    public final Material guiIcon;      // иконка ветки / блок званий 4-9
    public final Material medalRawIcon; // иконка званий 1-3
    public final Rarity rarity;
    public final String flavor;         // слово-окрас для шаблона секции
    public final String[] medals;       // явные звания (может быть null)

    Branch(Section section, String display, String color, Material guiIcon,
           Material medalRawIcon, Rarity rarity, String flavor, String[] medals) {
        this.section = section;
        this.display = display;
        this.color = color;
        this.guiIcon = guiIcon;
        this.medalRawIcon = medalRawIcon;
        this.rarity = rarity;
        this.flavor = flavor;
        this.medals = medals;
    }

    public String tierName(int tier) { // tier 1..10
        if (tier < 1 || tier > 10) return "";
        if (medals != null) return medals[tier - 1];
        return section.ladder[tier - 1].replace("{f}", flavor);
    }

    public String tierColored(int tier) {
        return TIER_COLORS[Math.min(9, Math.max(0, tier - 1))] + tierName(tier);
    }

    public static Branch byId(String id) {
        if (id == null) return null;
        try { return Branch.valueOf(id.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException e) { return null; }
    }

    /** Ветки секции (в порядке объявления). */
    public static List<Branch> ofSection(Section s) {
        List<Branch> out = new ArrayList<>();
        for (Branch b : values()) if (b.section == s) out.add(b);
        return out;
    }
}
