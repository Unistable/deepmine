package ru.deepprogression.tools;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.persistence.PersistentDataType;
import ru.deepprogression.DeepProgressionPlugin;
import ru.deepprogression.model.Blocks;
import ru.deepprogression.model.Branch;
import ru.deepprogression.model.Section;
import ru.deepprogression.util.Cfg;
import ru.deepprogression.util.Keys;
import ru.deepprogression.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Построение всех предметов: инструменты, медали, компоненты, машины. */
public final class Items {

    private static final Map<String, String> RU = new HashMap<>();

    static {
        // шахтёрское
        RU.put("COAL", "уголь"); RU.put("RAW_COPPER", "сырая медь");
        RU.put("RAW_IRON", "сырое железо"); RU.put("RAW_GOLD", "сырое золото");
        RU.put("GOLD_NUGGET", "золотой самородок"); RU.put("REDSTONE", "редстоун");
        RU.put("LAPIS_LAZULI", "лазурит"); RU.put("DIAMOND", "алмаз");
        RU.put("EMERALD", "изумруд"); RU.put("QUARTZ", "кварц");
        RU.put("ANCIENT_DEBRIS", "древний обломок"); RU.put("NETHERITE_SCRAP", "незеритовый скрап");
        RU.put("NETHERITE_INGOT", "незеритовый слиток");
        RU.put("COPPER_INGOT", "медный слиток"); RU.put("IRON_INGOT", "железный слиток");
        RU.put("GOLD_INGOT", "золотой слиток");
        RU.put("COAL_ORE", "угольная руда"); RU.put("DEEPSLATE_COAL_ORE", "глубинная угольная руда");
        RU.put("AMETHYST_SHARD", "осколок аметиста"); RU.put("AMETHYST", "аметист");
        RU.put("AMETHYST_CLUSTER", "аметистовый кластер");
        // базовые
        RU.put("STICK", "палка"); RU.put("OBSIDIAN", "обсидиан");
        RU.put("MAGMA_CREAM", "магмовый крем"); RU.put("PAPER", "бумага");
        RU.put("LEATHER", "кожа"); RU.put("BARREL", "бочка"); RU.put("FURNACE", "печь");
        RU.put("BLAST_FURNACE", "доменная печь"); RU.put("SMOKER", "коптильня");
        RU.put("STONECUTTER", "камнерез"); RU.put("COMPOSTER", "компостер");
        RU.put("GRINDSTONE", "точило"); RU.put("LECTERN", "кафедра");
        RU.put("BREWING_STAND", "варочная стойка"); RU.put("ENCHANTING_TABLE", "стол зачарований");
        RU.put("TRAPPED_CHEST", "ловушка-сундук"); RU.put("REDSTONE", "редстоун");
        RU.put("MAGMA_BLOCK", "магмовый блок"); RU.put("BONE_MEAL", "костная мука");
        RU.put("BONE", "кость"); RU.put("STRING", "нить"); RU.put("PRISMARINE_SHARD", "осколок призмарина");
        RU.put("PRISMARINE_CRYSTALS", "кристаллы призмарина"); RU.put("SPONGE", "губка");
        RU.put("WET_SPONGE", "мокрая губка"); RU.put("GLOWSTONE_DUST", "пыль светокамня");
        RU.put("GLOWSTONE", "светокамень"); RU.put("SEA_LANTERN", "морской фонарь");
        RU.put("SHROOMLIGHT", "светогриб"); RU.put("GLOW_BERRIES", "светящиеся ягоды");
        RU.put("GLOW_INK_SAC", "светящийся чернильный мешок"); RU.put("GLOW_LICHEN", "светящийся лишайник");
        RU.put("CANDLE", "свеча"); RU.put("LANTERN", "фонарь"); RU.put("SOUL_LANTERN", "фонарь душ");
        RU.put("END_ROD", "энд-стержень"); RU.put("REDSTONE_LAMP", "редстоуновая лампа");
        RU.put("SEA_PICKLE", "морской огурец"); RU.put("JACK_O_LANTERN", "светильник Джека");
        RU.put("BLAZE_POWDER", "огненный порошок"); RU.put("ENDER_EYE", "глаз Края");
        RU.put("ENDER_PEARL", "жемчуг Края"); RU.put("CHORUS_FRUIT", "плод хоруса");
        RU.put("POPPED_CHORUS_FRUIT", "лопнувший плод хоруса");
        RU.put("CHORUS_PLANT", "растение хоруса"); RU.put("CHORUS_FLOWER", "цветок хоруса");
        RU.put("END_STONE", "энд-камень"); RU.put("END_STONE_BRICKS", "энд-каменные кирпичи");
        RU.put("PURPUR_BLOCK", "пурпурный блок"); RU.put("PURPUR_PILLAR", "пурпурная колонна");
        RU.put("OBSIDIAN", "обсидиан"); RU.put("CRYING_OBSIDIAN", "плачущий обсидиан");
        RU.put("RESPAWN_ANCHOR", "якорь возрождения");
        RU.put("NETHERRACK", "незеррак"); RU.put("SOUL_SAND", "песок душ"); RU.put("SOUL_SOIL", "почва душ");
        RU.put("BLACKSTONE", "чёрный камень"); RU.put("GILDED_BLACKSTONE", "позолоченный чёрный камень");
        RU.put("BASALT", "базальт"); RU.put("CRIMSON_STEM", "багровый стебель");
        RU.put("WARPED_STEM", "искривлённый стебель"); RU.put("CRIMSON_PLANKS", "багровые доски");
        RU.put("WARPED_PLANKS", "искривлённые доски"); RU.put("CRIMSON_NYLIUM", "багровый нилий");
        RU.put("WARPED_NYLIUM", "искривлённый нилий"); RU.put("CRIMSON_ROOTS", "багровые корни");
        RU.put("WARPED_ROOTS", "искривлённые корни");
        RU.put("NETHER_WART", "адский нарост"); RU.put("NETHER_WART_BLOCK", "блок адского нароста");
        RU.put("WARPED_WART_BLOCK", "блок искривлённого нароста");
        RU.put("TWISTING_VINES", "вьющиеся лозы"); RU.put("WEEPING_VINES", "плакучие лозы");
        RU.put("SCULK", "скулк"); RU.put("SCULK_VEIN", "скулк-жила"); RU.put("SCULK_CATALYST", "скулк-катализатор");
        RU.put("SCULK_SHRIEKER", "скулк-визгун"); RU.put("SCULK_SENSOR", "скулк-сенсор");
        RU.put("CALIBRATED_SCULK_SENSOR", "калиброванный скульк-сенсор");
        // дерево
        RU.put("OAK_LOG", "дубовое бревно"); RU.put("SPRUCE_LOG", "еловое бревно");
        RU.put("BIRCH_LOG", "берёзовое бревно"); RU.put("JUNGLE_LOG", "бревно джунглей");
        RU.put("ACACIA_LOG", "акациевое бревно"); RU.put("DARK_OAK_LOG", "бревно тёмного дуба");
        RU.put("MANGROVE_LOG", "мангровое бревно"); RU.put("CHERRY_LOG", "вишнёвое бревно");
        RU.put("PALE_OAK_LOG", "бревно белого дуба");
        RU.put("OAK_PLANKS", "дубовые доски"); RU.put("SPRUCE_PLANKS", "еловые доски");
        RU.put("BIRCH_PLANKS", "берёзовые доски"); RU.put("JUNGLE_PLANKS", "доски джунглей");
        RU.put("ACACIA_PLANKS", "акациевые доски"); RU.put("DARK_OAK_PLANKS", "доски тёмного дуба");
        RU.put("MANGROVE_PLANKS", "мангровые доски"); RU.put("CHERRY_PLANKS", "вишнёвые доски");
        RU.put("PALE_OAK_PLANKS", "доски белого дуба");
        RU.put("RESIN_CLUMP", "комок смолы"); RU.put("RESIN_BLOCK", "смоляной блок");
        RU.put("RESIN_BRICK", "смоляной кирпич"); RU.put("CREAKING_HEART", "скрипящее сердце");
        // ферма
        RU.put("WHEAT", "пшеница"); RU.put("WHEAT_SEEDS", "семена пшеницы");
        RU.put("HAY_BLOCK", "стог сена"); RU.put("CARROT", "морковь");
        RU.put("CARROTS", "морковь"); RU.put("POTATO", "картофель");
        RU.put("POTATOES", "картофель"); RU.put("BEETROOT", "свёкла");
        RU.put("BEETROOTS", "свёкла"); RU.put("BEETROOT_SEEDS", "семена свёклы");
        RU.put("MELON", "арбуз"); RU.put("MELON_SLICE", "ломтик арбуза");
        RU.put("MELON_SEEDS", "семена арбуза"); RU.put("PUMPKIN", "тыква");
        RU.put("CARVED_PUMPKIN", "резная тыква"); RU.put("PUMPKIN_SEEDS", "семена тыквы");
        RU.put("SUGAR_CANE", "сахарный тростник"); RU.put("SUGAR", "сахар");
        RU.put("COCOA", "какао-плоды"); RU.put("COCOA_BEANS", "какао-бобы");
        RU.put("HONEYCOMB", "пчелиные соты"); RU.put("HONEY_BOTTLE", "медовая бутылка");
        RU.put("HONEY_BLOCK", "медовый блок"); RU.put("HONEYCOMB_BLOCK", "сотовый блок");
        // флора
        RU.put("CACTUS", "кактус"); RU.put("CACTUS_FLOWER", "цветок кактуса");
        RU.put("BAMBOO", "бамбук"); RU.put("BAMBOO_BLOCK", "блок бамбука");
        RU.put("BAMBOO_MOSAIC", "бамбуковая мозаика"); RU.put("BAMBOO_PLANKS", "бамбуковые доски");
        RU.put("POPPY", "мак"); RU.put("DANDELION", "одуванчик");
        RU.put("MOSS_BLOCK", "блок мха"); RU.put("MOSS_CARPET", "мшистый ковёр");
        RU.put("PALE_MOSS_BLOCK", "блок бледного мха"); RU.put("AZALEA", "азалия");
        RU.put("FLOWERING_AZALEA", "цветущая азалия"); RU.put("BROWN_MUSHROOM", "коричневый гриб");
        RU.put("RED_MUSHROOM", "красный гриб"); RU.put("DEAD_BUSH", "сухой куст");
        RU.put("SHORT_GRASS", "трава"); RU.put("TALL_GRASS", "высокая трава");
        // каменщик
        RU.put("SAND", "песок"); RU.put("RED_SAND", "красный песок");
        RU.put("GRAVEL", "гравий"); RU.put("SANDSTONE", "песчаник");
        RU.put("RED_SANDSTONE", "красный песчаник"); RU.put("CUT_SANDSTONE", "резной песчаник");
        RU.put("SMOOTH_SANDSTONE", "гладкий песчаник"); RU.put("SNOW_BLOCK", "снежный блок");
        RU.put("SNOWBALL", "снежок"); RU.put("ICE", "лёд"); RU.put("PACKED_ICE", "спрессованный лёд");
        RU.put("BLUE_ICE", "голубой лёд"); RU.put("GLASS", "стекло");
        RU.put("TINTED_GLASS", "тонированное стекло"); RU.put("GLASS_PANE", "стеклянная панель");
        RU.put("BRICKS", "кирпичный блок"); RU.put("BRICK", "кирпич");
        RU.put("STONE_BRICKS", "каменные кирпичи"); RU.put("MOSSY_STONE_BRICKS", "мшистые каменные кирпичи");
        RU.put("CRACKED_STONE_BRICKS", "потрескавшиеся каменные кирпичи");
        RU.put("DEEPSLATE_BRICKS", "глубинные кирпичи"); RU.put("DEEPSLATE_TILES", "глубинная плитка");
        RU.put("POLISHED_DEEPSLATE", "полированный глубинный сланец");
        RU.put("NETHER_BRICKS", "незерские кирпичи"); RU.put("RED_NETHER_BRICKS", "красные незерские кирпичи");
        RU.put("TERRACOTTA", "терракота"); RU.put("CONCRETE", "бетон");
        RU.put("CONCRETE_POWDER", "бетонный порошок");
        RU.put("COBBLESTONE", "булыжник"); RU.put("STONE", "камень");
        RU.put("DEEPSLATE", "глубинный сланец"); RU.put("COBBLED_DEEPSLATE", "булыжник глубинного сланца");
        RU.put("GRANITE", "гранит"); RU.put("DIORITE", "диорит"); RU.put("ANDESITE", "андезит");
        RU.put("TUFF", "туф"); RU.put("CALCITE", "кальцит");
        RU.put("KELP", "водоросли"); RU.put("DRIED_KELP", "сушёные водоросли");
        RU.put("DRIED_KELP_BLOCK", "блок сушёных водорослей");
        RU.put("SEAGRASS", "морская трава"); RU.put("TUBE_CORAL_BLOCK", "блок трубчатого коралла");
        RU.put("BRAIN_CORAL_BLOCK", "блок коралловых мозгов"); RU.put("BUBBLE_CORAL_BLOCK", "блок пузырчатого коралла");
        RU.put("FIRE_CORAL_BLOCK", "блок огненного коралла"); RU.put("HORN_CORAL_BLOCK", "блок рогового коралла");
        RU.put("TUBE_CORAL", "трубчатый коралл"); RU.put("BRAIN_CORAL", "коралловые мозги");
        RU.put("BUBBLE_CORAL", "пузырчатый коралл"); RU.put("FIRE_CORAL", "огненный коралл");
        RU.put("HORN_CORAL", "роговой коралл");
        // инженерия
        RU.put("PISTON", "поршень"); RU.put("STICKY_PISTON", "липкий поршень");
        RU.put("OBSERVER", "наблюдатель"); RU.put("HOPPER", "воронка");
        RU.put("DROPPER", "выбрасыватель"); RU.put("DISPENSER", "раздатчик");
        RU.put("CRAFTER", "автокрафтер"); RU.put("REPEATER", "повторитель");
        RU.put("COMPARATOR", "компаратор"); RU.put("LEVER", "рычаг");
        RU.put("TRIPWIRE_HOOK", "крючок растяжки"); RU.put("NOTE_BLOCK", "музыкальный блок");
        RU.put("JUKEBOX", "проигрыватель"); RU.put("TARGET", "мишень");
        RU.put("DAYLIGHT_DETECTOR", "датчик дневного света");
        RU.put("REDSTONE_TORCH", "редстоуновый факел"); RU.put("RAIL", "рельсы");
        RU.put("POWERED_RAIL", "электрические рельсы"); RU.put("DETECTOR_RAIL", "рельсы-детектор");
        RU.put("ACTIVATOR_RAIL", "активирующие рельсы");
        RU.put("COPPER_BLOCK", "медный блок"); RU.put("CUT_COPPER", "резная медь");
        RU.put("NETHER_STAR", "звезда Незера"); RU.put("DIAMOND_BLOCK", "алмазный блок");
        RU.put("EMERALD_BLOCK", "изумрудный блок"); RU.put("ECHO_SHARD", "эхо-осколок");
        RU.put("DISC_FRAGMENT_5", "фрагмент пластинки 5"); RU.put("HEART_OF_THE_SEA", "сердце моря");
        RU.put("TOTEM_OF_UNDYING", "тотем бессмертия"); RU.put("SLIME_BALL", "слизь");
        RU.put("PHANTOM_MEMBRANE", "перепонка фантома"); RU.put("CLAY_BALL", "глина");
        RU.put("COPPER_GRATE", "медная решётка"); RU.put("COPPER_BULB", "медная лампа");
        RU.put("COPPER_BARS", "медная решётка-прутья"); RU.put("COPPER_CHEST", "медный сундук");
        RU.put("COPPER_GOLEM_STATUE", "статуя медного голема"); RU.put("COPPER_TORCH", "медный факел");
        RU.put("COPPER_LANTERN", "медный фонарь");
    }

    private static final String[] COLOR_KEYS = {"WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE",
            "YELLOW", "LIME", "PINK", "GRAY", "LIGHT_GRAY", "CYAN", "PURPLE",
            "BLUE", "BROWN", "GREEN", "RED", "BLACK"};

    /** Гендер основы: M/F/N — для согласования цветового прилагательного. */
    private static char genderOf(String baseKey) {
        return switch (baseKey) {
            case "WOOL", "STAINED_GLASS_PANE", "TERRACOTTA", "CANDLE" -> 'F';
            case "STAINED_GLASS" -> 'N';
            default -> 'M';
        };
    }

    private static String adjFor(int i, char gender) {
        String m = COLOR_RU[i];
        return switch (gender) {
            case 'F' -> m.endsWith("ой") ? m.substring(0, m.length() - 2) + "ая"
                    : m.substring(0, m.length() - 2) + "ая";
            case 'N' -> m.endsWith("ой") ? m.substring(0, m.length() - 2) + "ое"
                    : m.substring(0, m.length() - 2) + "ое";
            default -> m;
        };
    }

    public static String ruName(Material m) {
        if (m == null) return "?";
        String s = RU.get(m.name());
        if (s != null) return s;
        String n = m.name();
        // цветные семейства: WHITE_WOOL, RED_CONCRETE_POWDER, BLACK_TERRACOTTA...
        for (int i = 0; i < COLOR_KEYS.length; i++) {
            if (n.startsWith(COLOR_KEYS[i] + "_")) {
                String rest = n.substring(COLOR_KEYS[i].length() + 1);
                if (rest.equals("WOOL") || rest.equals("STAINED_GLASS")
                        || rest.equals("STAINED_GLASS_PANE") || rest.equals("CONCRETE")
                        || rest.equals("CONCRETE_POWDER") || rest.equals("TERRACOTTA")
                        || rest.equals("CANDLE")) {
                    String base = RU.get(rest);
                    if (base == null) base = lowerPretty(rest);
                    return adjFor(i, genderOf(rest)) + " " + base;
                }
            }
        }
        if (n.endsWith("_CORAL_FAN")) return "веер " + ruName(coreMat(n.replace("_CORAL_FAN", "_CORAL")));
        if (n.endsWith("_CORAL_WALL_FAN")) return "настенный веер " + ruName(coreMat(n.replace("_CORAL_WALL_FAN", "_CORAL")));
        if (n.startsWith("DEAD_") && n.endsWith("_CORAL_BLOCK")) return "мёртвый " + ruName(coreMat(n.substring(5)));
        if (n.endsWith("_STAIRS")) return "ступени " + ruName(coreMat(n.replace("_STAIRS", "")));
        if (n.endsWith("_SLAB")) return "плита " + ruName(coreMat(n.replace("_SLAB", "")));
        if (n.endsWith("_WALL")) return "стена " + ruName(coreMat(n.replace("_WALL", "")));
        if (n.startsWith("STRIPPED_")) return "обтёсанное " + ruName(coreMat(n.substring(9)));
        if (n.endsWith("_WOOD")) return "дерево " + ruName(coreMat(n.replace("_WOOD", "_LOG")));
        if (n.endsWith("_LEAVES")) return "листва " + ruName(coreMat(n.replace("_LEAVES", "")));
        return lowerPretty(n);
    }

    private static final String[] COLOR_RU = {"белый", "оранжевый", "пурпурный",
            "голубой", "жёлтый", "лаймовый", "розовый", "серый", "светло-серый",
            "бирюзовый", "сиреневый", "синий", "коричневый", "зелёный",
            "красный", "чёрный"};

    private Items() {}

    public static void init(DeepProgressionPlugin pl) { }

    private static Material coreMat(String name) {
        return Material.matchMaterial(name);
    }

    private static String lowerPretty(String s) {
        return s.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    public static String toolId(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(Keys.TOOL_ID, PersistentDataType.STRING);
    }

    public static int medalTier(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return 0;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return 0;
        String tag = meta.getPersistentDataContainer().get(Keys.MEDAL, PersistentDataType.STRING);
        if (tag == null || !tag.contains(":")) return 0;
        try {
            return Integer.parseInt(tag.substring(tag.indexOf(':') + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String medalBranch(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return null;
        String tag = meta.getPersistentDataContainer().get(Keys.MEDAL, PersistentDataType.STRING);
        if (tag == null || !tag.contains(":")) return null;
        return tag.substring(0, tag.indexOf(':'));
    }

    private static String nameColor(Tools.ToolType t) {
        return switch (t) {
            case DRILL -> "&f";
            case UNIVERSAL -> "&b";
            case GEOLOGIST -> "&a";
            case PROSPECTOR -> "&d";
            case BATTERY -> "&e";
            case CHUNK_DRILL -> "&c";
            case COMPONENT -> "&7";
            case POUCH -> "&6";
            case VAULT -> "&6";
            case STATION -> "&5";
            case SCANNER -> "&5";
            case SECTION_SCANNER -> "&d";
            case CRYSTAL -> "&d";
            case MACHINE -> "&5";
            case GADGET -> "&3";
        };
    }

    public static ItemStack build(String toolId) {
        Tools.ToolSpec s = Tools.get(toolId);
        if (s == null) return null;
        int mult = Math.max(1, s.mult);
        boolean silk = s.section != null && s.section.silk;

        List<String> lore = new ArrayList<>();
        switch (s.type) {
            case DRILL -> {
                Branch b = s.branch;
                lore.add("&7Ветка: " + b.color + b.display + " &8(" + b.section.color + b.section.display + "&8)");
                lore.add("&7Добыча своей ветки: &e×" + Text.num(mult));
                if (silk) lore.add("&7Шёлковое касание: &eесть&7 (блоки целиком)");
                double ch = Cfg.fromStoneChance(toolId, b);
                if (ch > 0 && Blocks.bonusOf(b) != null) {
                    Blocks.DropSpec bd = Blocks.bonusOf(b);
                    lore.add("&7Из камня: &e" + Tools.chancePct(ch)
                            + " &7шанс &e+" + bd.units() + " " + ruName(bd.item()));
                }
                lore.add("&8Неразрушим");
                lore.add("&7Чужие ветки — обычный ванильный дроп.");
            }
            case UNIVERSAL -> {
                lore.add("&7Секция: " + (s.section == null ? "&eШахтёр" : s.section.color + s.section.display));
                lore.add("&7Все ветки секции: &e×" + Text.num(mult));
                if (silk) lore.add("&7Шёлковое касание: &eесть&7 (блоки целиком)");
                lore.add("&8Неразрушим");
            }
            case GEOLOGIST -> {
                lore.add("&7Все руды шахтёрской секции: &e×" + Text.num(mult));
                double ch = Cfg.fromStoneChance(toolId, Branch.COAL);
                if (ch > 0) lore.add("&7Уголь из камня: &e" + Tools.chancePct(ch) + " &7шанс");
                lore.add("&8Неразрушим · входной инструмент геолога");
            }
            case PROSPECTOR -> {
                lore.add("&7ПКМ — скан чанка: количество руд");
                lore.add("&7и индекс богатства.");
                lore.add("&7Заряды: &e" + Cfg.probeMaxCharges() + "&7/&e" + Cfg.probeMaxCharges());
                lore.add("&7Подзарядка: батарея в оффхенд + ПКМ");
                lore.add("&7или крафт: зонд + батареи.");
            }
            case BATTERY -> {
                lore.add("&7Даёт &e+" + Cfg.batteryCharges() + " заряда &7зонду георазведки.");
                lore.add("&7ПКМ с зондом в оффхенде — зарядка,");
                lore.add("&7или крафт: зонд + батареи.");
            }
            case CHUNK_DRILL -> {
                lore.add("&7ПКМ: &fзарядить (&e" + Cfg.magmaCost() + " магмы&f)");
                lore.add("&7ПКМ в чанке: &fвыработка — выгребает");
                lore.add("&fвсе руды чанка, опустошая его");
                lore.add("&7Shift+ПКМ: &fзабрать добычу из бура");
                lore.add("&8КПД " + Math.round(Cfg.efficiency() * 100) + "% · Кулдаун "
                        + Cfg.cooldownSeconds() + "с · Лимит " + Text.num(Cfg.maxUnits()) + " ед.");
            }
            case COMPONENT -> {
                lore.add("&8Инженерный компонент DeepProgression");
                if (s.desc != null) for (String d : s.desc) lore.add("&7" + d);
                lore.add("&8/dp crafts " + s.id + " — рецепт");
            }
            case POUCH -> {
                lore.add("&7Портативное хранилище ресурсов:");
                lore.add("&7ресурсы любых профессий сами летят");
                lore.add("&fв тайник&7, не забивая инвентарь.");
                lore.add("&7Лимит: &e" + Text.num(Cfg.pouchMaxUnits()) + " ед.");
                lore.add("&7ПКМ: &fоткрыть тайник");
                lore.add("&7Shift+ПКМ: &fссыпать ресурсы из инвентаря");
            }
            case VAULT -> {
                lore.add("&7Стационарное хранилище ресурсов (27 слотов).");
                lore.add("&7Ставится блоком. Хопперы принимают");
                lore.add("&7только ресурсы профессий.");
                lore.add("&7Взрывоустойчив: &fобсидиановый пояс&7 в каркасе.");
                lore.add("&7Shift+ПКМ с ресурсом: &fссыпка из инвентаря");
                lore.add("&7ПКМ: &fоткрыть");
            }
            case STATION -> {
                lore.add("&7Заряжает технику от магмы.");
                lore.add("&7Поставь на блок, под ним — платформа");
                lore.add("&7из &e" + Cfg.stationMinMagma() + "+ магмы &7(3×3 на y-1).");
                lore.add("&7ПКМ с зондом: &f+" + Cfg.stationProbeCharges() + " заряда");
                lore.add("&7ПКМ с чанковым буром: &fполный заряд");
                lore.add("&8Кулдаун станции: " + Cfg.stationCooldown() + "с");
            }
            case SCANNER -> {
                lore.add("&7Стационарный георазведочный сканер.");
                lore.add("&7ПКМ: &fбесплатный скан чанка");
                lore.add("&8Кулдаун: " + Cfg.scannerCooldown() + "с");
            }
            case SECTION_SCANNER -> {
                if (s.section != null) {
                    lore.add("&7Скан чанка по веткам секции "
                            + s.section.color + s.section.display);
                    lore.add("&7ПКМ: &fполный отчёт в чат");
                }
                lore.add("&7Заряды: &e" + Cfg.probeMaxCharges() + "&7/&e" + Cfg.probeMaxCharges());
                lore.add("&7Подзарядка: батарея/станция/геосканер.");
            }
            case CRYSTAL -> {
                if (s.section != null) {
                    lore.add("&7Секция: " + s.section.color + s.section.display);
                    lore.add("&7Активация: &fShift+ПКМ");
                    lore.add("&7Кулдаун: &e" + Cfg.activeCooldown(s.section) + "с");
                }
                if (s.desc != null) for (String d : s.desc) lore.add("&7" + d);
            }
            case MACHINE -> {
                if (s.desc != null) for (String d : s.desc) lore.add("&7" + d);
                lore.add("&7Поставь блок, встань рядом и жми &fПКМ&7");
                lore.add("&7с ресурсом в руке (Shift — весь стек).");
                String ms = sectionOfMachine(s.id);
                if (!ms.equals("—")) lore.add("&8Машина секции " + ms);
            }
            case GADGET -> {
                if (s.section != null) {
                    lore.add("&7Секция: " + s.section.color + s.section.display);
                    lore.add("&7ПКМ — использовать.");
                }
                if (s.desc != null) for (String d : s.desc) lore.add("&7" + d);
                // v3.2: динамические числа из конфига (не соврут при смене настроек)
                if (s.id.equals("air_tank")) {
                    int max = Cfg.probeMaxCharges();
                    lore.add("&7Дыхание: &e" + Cfg.airTankBreath() + "с&7 · Грация: &e"
                            + Cfg.airTankGrace() + "с");
                    lore.add("&7Заряды: &e" + max + "&7/&e" + max);
                }
                if (s.id.equals("thermos")) {
                    lore.add("&7Длительность: &e" + Cfg.thermosDuration()
                            + "с&7 · Магма: &e" + Cfg.thermosMagma());
                }
                if (s.id.equals("voidsail")) {
                    lore.add("&7Длительность: &e" + Cfg.voidSailDuration() + "с");
                }
                if (s.id.equals("jammer")) {
                    lore.add("&7Длительность: &e" + Cfg.jammerDuration() + "с");
                }
                lore.add("&8Неразрушим");
            }
        }

        boolean toolIcon = switch (s.icon) {
            case IRON_PICKAXE, DIAMOND_PICKAXE, NETHERITE_PICKAXE,
                    IRON_AXE, DIAMOND_AXE, NETHERITE_AXE,
                    IRON_HOE, DIAMOND_HOE, NETHERITE_HOE,
                    IRON_SHOVEL, DIAMOND_SHOVEL, NETHERITE_SHOVEL, SHEARS -> true;
            default -> false;
        };
        boolean eff = toolIcon && s.type != Tools.ToolType.PROSPECTOR
                && s.type != Tools.ToolType.BATTERY
                && s.type != Tools.ToolType.CHUNK_DRILL;

        String color = s.type == Tools.ToolType.DRILL && s.branch != null
                ? s.branch.color : nameColor(s.type);
        ItemStack it = base(s.icon, color + s.name, lore, s.glow, eff);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(Keys.TOOL_ID, PersistentDataType.STRING, toolId);
            // v3.2: предметы с пулом зарядов не стакаются (иначе декремент косится
            // на весь стек — заряды теряются)
            if (s.type == Tools.ToolType.PROSPECTOR || s.type == Tools.ToolType.SECTION_SCANNER
                    || "air_tank".equals(toolId)) {
                try { meta.setMaxStackSize(1); } catch (Exception ignored) { }
            }
            // v3.2: дыхательный баллон — общий пул зарядов с зондами (полный при создании)
            if (s.type == Tools.ToolType.PROSPECTOR || s.type == Tools.ToolType.SECTION_SCANNER
                    || "air_tank".equals(toolId)) {
                meta.getPersistentDataContainer().set(Keys.PROBE_CHARGES,
                        PersistentDataType.INTEGER, Cfg.probeMaxCharges());
            }
            if (s.type == Tools.ToolType.CHUNK_DRILL) {
                meta.getPersistentDataContainer().set(Keys.CD_STATE,
                        PersistentDataType.STRING, "EMPTY");
            }
            it.setItemMeta(meta);
        }
        return it;
    }

    private static String sectionOfMachine(String id) {
        return switch (id) {
            case "sawmill", "resinworks" -> "Лесоруб";
            case "composter", "bakery" -> "Земледелец";
            case "herbarium", "nursery" -> "Флорист";
            case "dryer", "coral_farm" -> "Рыболов";
            case "glassworks", "stone_works" -> "Каменщик";
            case "luminaforge", "candleworks" -> "Световод";
            case "assembler", "rail_works" -> "Инженер";
            case "altar", "foundry" -> "Незер";
            case "chorus_reaper", "end_forge" -> "Край";
            case "echo_chamber", "resonance_chamber" -> "Аномалии";
            default -> "—";
        };
    }

    /** Медаль звания. */
    public static ItemStack medalItem(Branch b, int tier) {
        Material icon = tier <= 3 ? b.medalRawIcon : (tier <= 9 ? b.guiIcon : Material.NETHER_STAR);
        String name = "&6Медаль &8· " + b.tierColored(tier) + " &8· " + b.color + b.display;
        List<String> lore = List.of(
                "&7Звание ветки &f" + b.display + " &8(" + b.section.display + ")",
                "&8Порог: " + Text.num(Cfg.thresholds(b)[Math.min(9, Math.max(0, tier - 1))]) + " бл.",
                "&7Символ профессиональной славы DeepProgression");
        ItemStack it = base(icon, name, lore, tier >= 10, false);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(Keys.MEDAL,
                    PersistentDataType.STRING, b.name() + ":" + tier);
            it.setItemMeta(meta);
        }
        return it;
    }

    private static ItemStack base(Material icon, String name, List<String> lore,
                                  boolean glow, boolean toolEnchants) {
        ItemStack it = new ItemStack(icon);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.setDisplayName(Text.color(name));
        meta.setLore(lore.stream().map(Text::color).toList());
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        if (toolEnchants) {
            Enchantment eff = Enchantment.getByKey(NamespacedKey.minecraft("efficiency"));
            if (eff != null) meta.addEnchant(eff, 6, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        if (glow) {
            Enchantment luck = Enchantment.getByKey(NamespacedKey.minecraft("luck"));
            if (luck != null) meta.addEnchant(luck, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        it.setItemMeta(meta);
        return it;
    }
}
