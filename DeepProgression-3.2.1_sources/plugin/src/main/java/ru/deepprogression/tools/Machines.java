package ru.deepprogression.tools;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.deepprogression.storage.Vaults;
import ru.deepprogression.util.Cfg;
import ru.deepprogression.util.Keys;
import ru.deepprogression.util.Msg;
import ru.deepprogression.util.Text;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Машины DeepProgression 3.0.
 *  - Хранилища/станции v2: склад ресурсов, зарядная станция, геосканер.
 *  - Секционные машины v3 (ПКМ с ресурсом, кулдаун на блок):
 *    Пилорама, Компостер, Морская сушилка, Стекловар, Люминафабрика,
 *    Сборочный стан, Пламенный алтарь, Хорус-жнейка.
 * Все машины: защита от взрывов/поршней, дроп предмета при ломании,
 * хопперы не засоряют (фильтр в Listeners).
 */
public final class Machines {

    /** Машины, которые разрешено ставить. */
    public static final List<String> PLACEABLE = List.of(
            "ore_vault", "charge_station", "geo_scanner",
            "sawmill", "composter", "herbarium", "dryer", "glassworks",
            "luminaforge", "assembler", "altar", "chorus_reaper", "echo_chamber",
            "resinworks", "bakery", "nursery", "coral_farm", "stone_works",
            "candleworks", "rail_works", "foundry", "end_forge", "resonance_chamber");

    /** Правило переработки: вход → выход (+ бонусный шанс). */
    public record Rule(Material in, int inQty, String outTool, Material out,
                       int outQty, double bonusChance, Material bonus, int bonusQty) {}

    private static final Map<String, List<Rule>> RULES = new java.util.LinkedHashMap<>();

    private static void rules(String machine, Rule... rs) {
        java.util.List<Rule> list = new java.util.ArrayList<>();
        for (Rule r : rs) {
            if (r != null && r.in() != null && (r.out() != null || r.outTool() != null)) list.add(r);
        }
        RULES.put(machine, list);
    }

    private static Rule rule(Material in, int inQty, Material out, int outQty) {
        return new Rule(in, inQty, null, out, outQty, 0, null, 0);
    }

    private static Rule ruleBonus(Material in, int inQty, Material out, int outQty,
                                  double bonus, Material bonusItem, int bonusQty) {
        return new Rule(in, inQty, null, out, outQty, bonus, bonusItem, bonusQty);
    }

    static {
        // Пилорама: бревно → 6 досок (+30% 2 палки)
        String[] woods = {"OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA",
                "DARK_OAK", "MANGROVE", "CHERRY", "PALE_OAK"};
        java.util.List<Rule> saw = new java.util.ArrayList<>();
        for (String w : woods) {
            Material log = Material.matchMaterial(w + "_LOG");
            Material planks = Material.matchMaterial(w + "_PLANKS");
            if (log != null && planks != null) {
                saw.add(ruleBonus(log, 1, planks, 6, 0.3, Material.STICK, 2));
            }
        }
        saw.add(ruleBonus(Material.matchMaterial("CRIMSON_STEM"), 1,
                Material.matchMaterial("CRIMSON_PLANKS"), 6, 0.3, Material.STICK, 2));
        saw.add(ruleBonus(Material.matchMaterial("WARPED_STEM"), 1,
                Material.matchMaterial("WARPED_PLANKS"), 6, 0.3, Material.STICK, 2));
        saw.add(ruleBonus(Material.matchMaterial("BAMBOO_BLOCK"), 1,
                Material.matchMaterial("BAMBOO_PLANKS"), 6, 0.3, Material.STICK, 2));
        saw.removeIf(java.util.Objects::isNull);
        RULES.put("sawmill", saw);

        // Компостер: урожай → костная мука
        rules("composter",
                ruleBonus(Material.WHEAT, 8, Material.BONE_MEAL, 2, 0.0, null, 0),
                ruleBonus(Material.CARROT, 8, Material.BONE_MEAL, 2, 0.0, null, 0),
                ruleBonus(Material.POTATO, 8, Material.BONE_MEAL, 2, 0.0, null, 0),
                ruleBonus(Material.BEETROOT, 8, Material.BONE_MEAL, 2, 0.0, null, 0),
                ruleBonus(Material.MELON_SLICE, 8, Material.BONE_MEAL, 2, 0.0, null, 0),
                ruleBonus(Material.SUGAR_CANE, 16, Material.BONE_MEAL, 2, 0.0, null, 0),
                ruleBonus(Material.COCOA_BEANS, 8, Material.BONE_MEAL, 2, 0.0, null, 0),
                ruleBonus(Material.matchMaterial("NETHER_WART"), 8, Material.BONE_MEAL, 2, 0.0, null, 0),
                ruleBonus(Material.matchMaterial("BAMBOO"), 16, Material.BONE_MEAL, 2, 0.0, null, 0),
                ruleBonus(Material.matchMaterial("KELP"), 16, Material.BONE_MEAL, 2, 0.0, null, 0));
        RULES.get("composter").removeIf(java.util.Objects::isNull);
        // Гербарий-пресс: цветы → красители
        rules("herbarium",
                ruleBonus(Material.matchMaterial("POPPY"), 8, Material.matchMaterial("RED_DYE"), 4, 0.1, Material.BONE_MEAL, 1),
                ruleBonus(Material.matchMaterial("DANDELION"), 8, Material.matchMaterial("YELLOW_DYE"), 4, 0.1, Material.BONE_MEAL, 1),
                ruleBonus(Material.matchMaterial("BLUE_ORCHID"), 8, Material.matchMaterial("LIGHT_BLUE_DYE"), 4, 0.1, Material.BONE_MEAL, 1),
                ruleBonus(Material.matchMaterial("ALLIUM"), 8, Material.matchMaterial("LIGHT_GRAY_DYE"), 4, 0.1, Material.BONE_MEAL, 1),
                ruleBonus(Material.matchMaterial("AZURE_BLUET"), 8, Material.matchMaterial("LIGHT_GRAY_DYE"), 4, 0.1, Material.BONE_MEAL, 1),
                ruleBonus(Material.matchMaterial("RED_TULIP"), 8, Material.matchMaterial("RED_DYE"), 4, 0.1, Material.BONE_MEAL, 1),
                ruleBonus(Material.matchMaterial("ORANGE_TULIP"), 8, Material.matchMaterial("ORANGE_DYE"), 4, 0.1, Material.BONE_MEAL, 1),
                ruleBonus(Material.matchMaterial("WHITE_TULIP"), 8, Material.matchMaterial("WHITE_DYE"), 4, 0.1, Material.BONE_MEAL, 1),
                ruleBonus(Material.matchMaterial("PINK_TULIP"), 8, Material.matchMaterial("PINK_DYE"), 4, 0.1, Material.BONE_MEAL, 1),
                ruleBonus(Material.matchMaterial("OXEYE_DAISY"), 8, Material.matchMaterial("WHITE_DYE"), 4, 0.1, Material.BONE_MEAL, 1),
                ruleBonus(Material.matchMaterial("CORNFLOWER"), 8, Material.matchMaterial("BLUE_DYE"), 4, 0.1, Material.BONE_MEAL, 1),
                ruleBonus(Material.matchMaterial("LILY_OF_THE_VALLEY"), 8, Material.matchMaterial("WHITE_DYE"), 4, 0.1, Material.BONE_MEAL, 1),
                ruleBonus(Material.matchMaterial("WITHER_ROSE"), 8, Material.matchMaterial("BLACK_DYE"), 4, 0.1, Material.BONE_MEAL, 1),
                ruleBonus(Material.matchMaterial("SUNFLOWER"), 4, Material.matchMaterial("YELLOW_DYE"), 4, 0.1, Material.BONE_MEAL, 1),
                ruleBonus(Material.matchMaterial("LILAC"), 4, Material.matchMaterial("PURPLE_DYE"), 4, 0.1, Material.BONE_MEAL, 1),
                ruleBonus(Material.matchMaterial("ROSE_BUSH"), 4, Material.matchMaterial("RED_DYE"), 6, 0.1, Material.BONE_MEAL, 1),
                ruleBonus(Material.matchMaterial("PEONY"), 4, Material.matchMaterial("PINK_DYE"), 4, 0.1, Material.BONE_MEAL, 1),
                ruleBonus(Material.matchMaterial("TORCHFLOWER"), 8, Material.matchMaterial("ORANGE_DYE"), 4, 0.1, Material.BONE_MEAL, 1),
                ruleBonus(Material.matchMaterial("PINK_PETALS"), 8, Material.matchMaterial("PINK_DYE"), 4, 0.1, Material.BONE_MEAL, 1),
                ruleBonus(Material.matchMaterial("DEAD_BUSH"), 8, Material.matchMaterial("BROWN_DYE"), 4, 0.1, Material.STICK, 2));
        rules("dryer",
                ruleBonus(Material.KELP, 16, Material.DRIED_KELP_BLOCK, 2, 0.2,
                        Material.PRISMARINE_SHARD, 1),
                ruleBonus(Material.SEAGRASS, 16, Material.DRIED_KELP_BLOCK, 2, 0.2,
                        Material.PRISMARINE_SHARD, 1));

        // Стекловар: песок → стекло (без топлива)
        rules("glassworks",
                rule(Material.SAND, 4, Material.GLASS, 4),
                rule(Material.matchMaterial("RED_SAND"), 4, Material.GLASS, 4));
        RULES.get("glassworks").removeIf(java.util.Objects::isNull);

        // Люминафабрика: светящиеся растения → пыль светокамня
        rules("luminaforge",
                ruleBonus(Material.GLOW_BERRIES, 8, Material.GLOWSTONE_DUST, 1, 0.15,
                        Material.GLOW_INK_SAC, 1),
                ruleBonus(Material.GLOW_LICHEN, 8, Material.GLOWSTONE_DUST, 2, 0.0, null, 0),
                ruleBonus(Material.SEA_PICKLE, 2, Material.GLOWSTONE_DUST, 1, 0.1,
                        Material.PRISMARINE_CRYSTALS, 1));
        RULES.get("luminaforge").removeIf(java.util.Objects::isNull);

        // Сборочный стан: железо → шестерни (выгоднее ванильного крафта)
        rules("assembler",
                new Rule(Material.IRON_INGOT, 3, "iron_gear", null, 1, 0.15,
                        Material.REDSTONE, 2));

        // Пламенный алтарь: магма → крем (ваниль даёт 4, алтарь — 5 + бонус)
        rules("altar",
                ruleBonus(Material.MAGMA_BLOCK, 1, Material.MAGMA_CREAM, 5, 0.2,
                        Material.BLAZE_POWDER, 1));

        // Хорус-жнейка: плод хоруса → лопнувший 1:1 (как ванильная плавка) + жемчуг
        rules("chorus_reaper",
                ruleBonus(Material.CHORUS_FRUIT, 8, Material.POPPED_CHORUS_FRUIT, 8, 0.12,
                        Material.ENDER_PEARL, 1));

        // Эхо-камера: скульк → эхо-осколки (аномалии)
        rules("echo_chamber",
                ruleBonus(Material.SCULK, 24, Material.ECHO_SHARD, 1, 0.15,
                        Material.AMETHYST_SHARD, 2),
                ruleBonus(Material.matchMaterial("SCULK_CATALYST"), 1,
                        Material.ECHO_SHARD, 2, 0.25,
                        Material.matchMaterial("DISC_FRAGMENT_5"), 1),
                ruleBonus(Material.matchMaterial("SCULK_VEIN"), 32, Material.ECHO_SHARD, 1,
                        0.10, Material.GLOWSTONE_DUST, 2));

        // ==================== Машины v3.2 ====================

        // Смолокурня: смола → кирпичи (без плавки), листва → костная мука
        rules("resinworks",
                ruleBonus(Material.matchMaterial("RESIN_CLUMP"), 8,
                        Material.matchMaterial("RESIN_BRICK"), 9, 0.10,
                        Material.matchMaterial("RESIN_BLOCK"), 1),
                ruleBonus(Material.matchMaterial("OAK_LEAVES"), 16, Material.BONE_MEAL, 2,
                        0.12, Material.APPLE, 1),
                ruleBonus(Material.matchMaterial("SPRUCE_LEAVES"), 16, Material.BONE_MEAL, 2, 0, null, 0),
                ruleBonus(Material.matchMaterial("BIRCH_LEAVES"), 16, Material.BONE_MEAL, 2, 0, null, 0),
                ruleBonus(Material.matchMaterial("JUNGLE_LEAVES"), 16, Material.BONE_MEAL, 2, 0, null, 0),
                ruleBonus(Material.matchMaterial("ACACIA_LEAVES"), 16, Material.BONE_MEAL, 2, 0, null, 0),
                ruleBonus(Material.matchMaterial("DARK_OAK_LEAVES"), 16, Material.BONE_MEAL, 2, 0, null, 0),
                ruleBonus(Material.matchMaterial("MANGROVE_LEAVES"), 16, Material.BONE_MEAL, 2, 0, null, 0),
                ruleBonus(Material.matchMaterial("CHERRY_LEAVES"), 16, Material.BONE_MEAL, 2, 0, null, 0),
                ruleBonus(Material.matchMaterial("PALE_OAK_LEAVES"), 16, Material.BONE_MEAL, 2, 0, null, 0),
                ruleBonus(Material.matchMaterial("AZALEA_LEAVES"), 16, Material.BONE_MEAL, 2, 0, null, 0),
                ruleBonus(Material.matchMaterial("FLOWERING_AZALEA_LEAVES"), 16, Material.BONE_MEAL, 2, 0, null, 0));
        if (RULES.get("resinworks") != null) RULES.get("resinworks").removeIf(java.util.Objects::isNull);

        // Пекарня: хлеб, печёная картошка, суп, арбузы — без топлива
        rules("bakery",
                ruleBonus(Material.WHEAT, 9, Material.BREAD, 4, 0.10, Material.BONE_MEAL, 1),
                ruleBonus(Material.POTATO, 8, Material.matchMaterial("BAKED_POTATO"), 8,
                        0.10, Material.BONE_MEAL, 1),
                ruleBonus(Material.BEETROOT, 12, Material.matchMaterial("BEETROOT_SOUP"), 2,
                        0.10, Material.BONE_MEAL, 1),
                ruleBonus(Material.MELON_SLICE, 18, Material.MELON, 2, 0.0, null, 0));

        // Питомник растений: бамбук, грибы, красители
        rules("nursery",
                ruleBonus(Material.matchMaterial("BAMBOO"), 16,
                        Material.matchMaterial("BAMBOO_BLOCK"), 2, 0.10, Material.BAMBOO, 4),
                ruleBonus(Material.RED_MUSHROOM, 8, Material.RED_MUSHROOM, 10,
                        0.10, Material.BROWN_MUSHROOM, 2),
                ruleBonus(Material.BROWN_MUSHROOM, 8, Material.BROWN_MUSHROOM, 10,
                        0.10, Material.RED_MUSHROOM, 2),
                ruleBonus(Material.CACTUS, 8, Material.matchMaterial("GREEN_DYE"), 10,
                        0.10, Material.BONE_MEAL, 1));

        // Коралловая ферма: кораллы → блоки, призмарин
        java.util.List<Rule> corals = new java.util.ArrayList<>();
        for (String c : new String[]{"TUBE", "BRAIN", "BUBBLE", "FIRE", "HORN"}) {
            Material coral = Material.matchMaterial(c + "_CORAL");
            Material block = Material.matchMaterial(c + "_CORAL_BLOCK");
            Material fan = Material.matchMaterial(c + "_CORAL_FAN");
            if (coral != null && block != null) {
                corals.add(ruleBonus(coral, 16, block, 5, 0.10, fan, 1));
            }
        }
        Material shards = Material.matchMaterial("PRISMARINE_SHARD");
        if (shards != null) {
            corals.add(ruleBonus(shards, 16, Material.PRISMARINE, 5, 0.10,
                    Material.matchMaterial("PRISMARINE_CRYSTALS"), 1));
        }
        RULES.put("coral_farm", corals);

        // Камнеобрабатывающий стан: песчаник, бетон, кремень
        java.util.List<Rule> stoneRules = new java.util.ArrayList<>();
        stoneRules.add(ruleBonus(Material.SAND, 16, Material.SANDSTONE, 5, 0.10,
                Material.matchMaterial("RED_SANDSTONE"), 1));
        for (String col : new String[]{"WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW",
                "LIME", "PINK", "GRAY", "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE",
                "BROWN", "GREEN", "RED", "BLACK"}) {
            Material powder = Material.matchMaterial(col + "_CONCRETE_POWDER");
            Material solid = Material.matchMaterial(col + "_CONCRETE");
            if (powder != null && solid != null) stoneRules.add(rule(powder, 8, solid, 8));
        }
        stoneRules.add(ruleBonus(Material.GRAVEL, 16, Material.FLINT, 2, 0.10, Material.FLINT, 2));
        stoneRules.add(ruleBonus(Material.SANDSTONE, 8, Material.matchMaterial("CUT_SANDSTONE"),
                10, 0.0, null, 0));
        RULES.put("stone_works", stoneRules);

        // Свечная мастерская: свечи из сот, перенос света
        rules("candleworks",
                ruleBonus(Material.matchMaterial("HONEYCOMB"), 8, Material.CANDLE, 8,
                        0.10, Material.GLOWSTONE_DUST, 2),
                ruleBonus(Material.matchMaterial("GLOW_LICHEN"), 16,
                        Material.matchMaterial("GLOW_BERRIES"), 4, 0.10,
                        Material.matchMaterial("GLOW_BERRIES"), 2),
                ruleBonus(Material.matchMaterial("SEA_PICKLE"), 8, Material.matchMaterial("SEA_PICKLE"), 10,
                        0.10, Material.matchMaterial("PRISMARINE_CRYSTALS"), 1));

        // Рельсовый стан: рельсы и электрические рельсы выгоднее ванили
        rules("rail_works",
                ruleBonus(Material.IRON_INGOT, 6, Material.RAIL, 16, 0.10,
                        Material.matchMaterial("POWERED_RAIL"), 4),
                ruleBonus(Material.GOLD_INGOT, 6, Material.matchMaterial("POWERED_RAIL"), 8,
                        0.10, Material.REDSTONE, 2));

        // Незер-литейня: кирпичи, золото, полировка — без печки
        rules("foundry",
                ruleBonus(Material.NETHERRACK, 32, Material.matchMaterial("NETHER_BRICKS"), 8,
                        0.10, Material.matchMaterial("NETHER_BRICK"), 2),
                ruleBonus(Material.matchMaterial("GILDED_BLACKSTONE"), 8,
                        Material.GOLD_NUGGET, 6, 0.10, Material.GOLD_INGOT, 1),
                ruleBonus(Material.matchMaterial("BLACKSTONE"), 16,
                        Material.matchMaterial("POLISHED_BLACKSTONE"), 20, 0.0, null, 0),
                ruleBonus(Material.matchMaterial("BASALT"), 16,
                        Material.matchMaterial("POLISHED_BASALT"), 20, 0.0, null, 0));

        // Энд-кузня: плачущий обсидиан, пурпур, якорь — эксклюзивные крафты
        rules("end_forge",
                ruleBonus(Material.OBSIDIAN, 16, Material.matchMaterial("CRYING_OBSIDIAN"), 1,
                        0.15, Material.matchMaterial("CRYING_OBSIDIAN"), 1),
                ruleBonus(Material.matchMaterial("POPPED_CHORUS_FRUIT"), 16,
                        Material.matchMaterial("PURPUR_BLOCK"), 5, 0.10,
                        Material.matchMaterial("PURPUR_PILLAR"), 1),
                ruleBonus(Material.END_STONE, 16, Material.matchMaterial("END_STONE_BRICKS"), 8,
                        0.0, null, 0),
                ruleBonus(Material.OBSIDIAN, 12, Material.matchMaterial("RESPAWN_ANCHOR"), 1,
                        0.05, Material.matchMaterial("CRYING_OBSIDIAN"), 1));

        // Резонансная камера: эхо, калибровка, катализаторы
        rules("resonance_chamber",
                ruleBonus(Material.matchMaterial("SCULK_SHRIEKER"), 1, Material.ECHO_SHARD, 3,
                        0.20, Material.matchMaterial("DISC_FRAGMENT_5"), 1),
                ruleBonus(Material.matchMaterial("SCULK_SENSOR"), 2,
                        Material.matchMaterial("CALIBRATED_SCULK_SENSOR"), 1,
                        0.10, Material.AMETHYST_SHARD, 1),
                ruleBonus(Material.SCULK, 32, Material.matchMaterial("SCULK_CATALYST"), 1,
                        0.10, Material.AMETHYST_SHARD, 2));
    }

    private Machines() {}

    public static boolean isMachineItem(ItemStack it) {
        String id = Items.toolId(it);
        return id != null && PLACEABLE.contains(id);
    }

    private static String titleOf(String id) {
        Tools.ToolSpec s = Tools.get(id);
        return s != null ? s.name : id;
    }

    // ---------- Размещение ----------
    public static void place(BlockPlaceEvent e) {
        String id = Items.toolId(e.getItemInHand());
        if (id == null || !PLACEABLE.contains(id)) return;
        Block b = e.getBlockPlaced();
        var state = b.getState();
        if (state instanceof TileState ts) {
            // блоки с block-entity — тег в самом блоке (как в 3.1)
            ts.getPersistentDataContainer().set(Keys.MACHINE, PersistentDataType.STRING, id);
            if (ts instanceof org.bukkit.Nameable nameable) {
                nameable.setCustomName(Text.color("&5" + titleOf(id)));
            }
            ts.update();
        } else {
            // v3.2: блоки без block-entity (компостер, камнерез, точило, станок,
            // якорь) — тег в PDC чанка по позиции (дефект 3.1: такие машины
            // не ставились — place() их отменял)
            var chunk = b.getChunk();
            chunk.getPersistentDataContainer().set(Vaults.chunkMachineKey(b),
                    PersistentDataType.STRING, id);
        }
    }

    // ---------- Ломание ----------
    public static void handleBreak(BlockBreakEvent e) {
        String id = Vaults.machineId(e.getBlock());
        if (id == null) return;
        Block b = e.getBlock();
        boolean creative = e.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE;
        e.setDropItems(false);
        // v3.2: снять тег чанка (для машин без block-entity)
        if (!(b.getState() instanceof TileState)) {
            b.getChunk().getPersistentDataContainer().remove(Vaults.chunkMachineKey(b));
            b.getChunk().getPersistentDataContainer().remove(Vaults.chunkCooldownKey(b));
        }
        if (creative) {
            if (b.getState() instanceof org.bukkit.block.Container c) {
                c.getInventory().clear();
            }
            b.getWorld().playSound(b.getLocation(), "minecraft:block.stone.break", 0.8f, 0.9f);
            return;
        }
        // содержимое контейнера-машины — дропнуть (страховка)
        if (b.getState() instanceof org.bukkit.block.Container c) {
            for (ItemStack it : c.getInventory().getContents()) {
                if (it != null && !it.getType().isAir()) {
                    b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), it);
                }
            }
            c.getInventory().clear();
        }
        ItemStack item = Items.build(id);
        if (item != null) {
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), item);
        }
        e.getPlayer().playSound(b.getLocation(), "minecraft:block.stone.break", 0.8f, 0.9f);
    }

    // ---------- Взрывы / поршни ----------
    public static void protectFromExplosion(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> Vaults.machineId(b) != null);
    }

    public static void protectFromExplosion(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> Vaults.machineId(b) != null);
    }

    public static void protectFromPiston(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) {
            if (Vaults.machineId(b) != null) { e.setCancelled(true); return; }
        }
    }

    public static void protectFromPiston(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) {
            if (Vaults.machineId(b) != null) { e.setCancelled(true); return; }
        }
    }

    /** Кулдаун машины: TileState — в блоке, без block-entity — в PDC чанка. */
    static long getCooldown(Block block) {
        var state = block.getState();
        if (state instanceof TileState ts) {
            return ts.getPersistentDataContainer()
                    .getOrDefault(Keys.CD_COOLDOWN, PersistentDataType.LONG, 0L);
        }
        return block.getChunk().getPersistentDataContainer()
                .getOrDefault(Vaults.chunkCooldownKey(block), PersistentDataType.LONG, 0L);
    }

    static void setCooldown(Block block, long epochMs) {
        var state = block.getState();
        if (state instanceof TileState ts) {
            ts.getPersistentDataContainer().set(Keys.CD_COOLDOWN,
                    PersistentDataType.LONG, epochMs);
            ts.update();
        } else {
            block.getChunk().getPersistentDataContainer().set(Vaults.chunkCooldownKey(block),
                    PersistentDataType.LONG, epochMs);
        }
    }

    // ---------- Секционные машины ----------
    public static void useMachine(Player p, Block block, String machineId, ItemStack hand) {
        useMachine(p, block, machineId, hand, false);
    }

    /** Shift+ПКМ — bulk-режим: до 8 циклов правила за один кулдаун. */
    public static void useMachine(Player p, Block block, String machineId, ItemStack hand,
                                  boolean bulk) {
        var state = block.getState();
        if (!(state instanceof TileState)
                && !ru.deepprogression.storage.Vaults.CHUNK_MACHINE_MATS.contains(block.getType())) {
            return;
        }
        long now = System.currentTimeMillis();
        long cd = Cfg.machineCooldown() * 1000L;
        long last = getCooldown(block);
        if (now - last < cd) {
            long wait = (cd - (now - last) + 999) / 1000;
            Msg.send(p, "machine.cooldown", "n", String.valueOf(wait));
            return;
        }
        List<Rule> rules = RULES.get(machineId);
        if (rules == null || rules.isEmpty()) {
            Msg.send(p, "machine.no-rule");
            return;
        }
        int cycles = bulk ? Math.max(1, Cfg.machineBulk()) : 1;
        int totalIn = 0, totalOut = 0, bonusCount = 0, doneCycles = 0;
        Rule used = null;
        Material usedIn = null, usedOut = null;
        String usedOutTool = null;
        for (int c = 0; c < cycles; c++) {
            Rule r = null;
            for (Rule cand : rules) {
                if (cand.inQty() <= countMaterial(p, cand.in())) { r = cand; break; }
            }
            if (r == null) break;
            p.getInventory().removeItem(new ItemStack(r.in(), r.inQty()));
            totalIn += r.inQty();
            // Выход: сначала тайник, затем инвентарь, остаток на землю
            if (r.outTool() != null) {
                ItemStack out = Items.build(r.outTool());
                if (out != null) {
                    out.setAmount(Math.max(1, r.outQty()));
                    var leftover = p.getInventory().addItem(out);
                    for (ItemStack rest : leftover.values()) {
                        p.getWorld().dropItemNaturally(p.getLocation(), rest);
                    }
                }
                usedOutTool = r.outTool();
            } else {
                Drills.give(p, block.getLocation(), r.out(), r.outQty());
                usedOut = r.out();
            }
            totalOut += r.outQty();
            used = r;
            doneCycles++;
            if (r.bonus() != null && r.bonusChance() > 0
                    && ThreadLocalRandom.current().nextDouble() < r.bonusChance()) {
                Drills.give(p, block.getLocation(), r.bonus(), r.bonusQty());
                bonusCount += r.bonusQty();
            }
        }
        if (used == null || totalIn == 0) {
            Rule first = rules.get(0);
            Msg.send(p, "machine.need", "qty", String.valueOf(first.inQty()),
                    "item", Items.ruName(first.in()));
            return;
        }

        setCooldown(block, now);
        Msg.send(p, "machine.done",
                "qty", String.valueOf(totalOut),
                "item", usedOutTool != null
                        ? titleOf(usedOutTool)
                        : Items.ruName(usedOut),
                "in", String.valueOf(totalIn),
                "item-in", Items.ruName(used.in()));
        if (bonusCount > 0) {
            Msg.action(p, "machine.bonus", "n", String.valueOf(bonusCount),
                    "item", Items.ruName(used.bonus()));
        }
        if (bulk && doneCycles > 1) Msg.send(p, "machine.bulk", "n", String.valueOf(doneCycles));
        p.playSound(block.getLocation(), soundOf(machineId), 0.9f, 1.1f);
        particles(block, machineId);
    }

    private static Sound soundOf(String machineId) {
        return switch (machineId) {
            case "sawmill" -> Sound.BLOCK_WOOD_BREAK;
            case "composter" -> Sound.BLOCK_COMPOSTER_FILL;
            case "herbarium" -> Sound.BLOCK_COMPOSTER_FILL;
            case "dryer" -> Sound.BLOCK_FIRE_EXTINGUISH;
            case "glassworks" -> Sound.BLOCK_GLASS_BREAK;
            case "luminaforge" -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
            case "assembler" -> Sound.BLOCK_ANVIL_USE;
            case "altar" -> Sound.BLOCK_LAVA_POP;
            case "chorus_reaper" -> Sound.ENTITY_ENDERMAN_TELEPORT;
            case "echo_chamber" -> Sound.BLOCK_SCULK_SENSOR_CLICKING;
            case "resinworks" -> Sound.BLOCK_SMOKER_SMOKE;
            case "bakery" -> Sound.BLOCK_BEEHIVE_WORK;
            case "nursery" -> Sound.BLOCK_MOSS_BREAK;
            case "coral_farm" -> Sound.BLOCK_CONDUIT_AMBIENT;
            case "stone_works" -> Sound.BLOCK_BELL_USE;
            case "candleworks" -> Sound.BLOCK_CANDLE_EXTINGUISH;
            case "rail_works" -> Sound.BLOCK_METAL_PLACE;
            case "foundry" -> Sound.BLOCK_RESPAWN_ANCHOR_CHARGE;
            case "end_forge" -> Sound.BLOCK_BEACON_POWER_SELECT;
            case "resonance_chamber" -> Sound.BLOCK_SCULK_CATALYST_BLOOM;
            default -> Sound.BLOCK_WOOD_BREAK;
        };
    }

    private static void particles(Block block, String machineId) {
        var loc = block.getLocation().add(0.5, 0.8, 0.5);
        var world = block.getWorld();
        switch (machineId) {
            case "sawmill" -> world.spawnParticle(Particle.CRIT, loc, 8, 0.3, 0.2, 0.3, 0.05);
            case "composter" -> world.spawnParticle(Particle.COMPOSTER, loc, 10, 0.2, 0.2, 0.2, 0);
            case "dryer" -> world.spawnParticle(Particle.SMOKE, loc, 12, 0.25, 0.25, 0.25, 0.01);
            case "glassworks" -> world.spawnParticle(Particle.FLAME, loc, 6, 0.2, 0.2, 0.2, 0.01);
            case "luminaforge" -> world.spawnParticle(Particle.END_ROD, loc, 8, 0.25, 0.25, 0.25, 0.02);
            case "assembler" -> world.spawnParticle(Particle.CRIT, loc, 10, 0.3, 0.2, 0.3, 0.08);
            case "altar" -> world.spawnParticle(Particle.LAVA, loc, 5, 0.25, 0.2, 0.25, 0);
            case "chorus_reaper" -> world.spawnParticle(Particle.PORTAL, loc, 14, 0.3, 0.3, 0.3, 0.4);
            case "echo_chamber" -> world.spawnParticle(Particle.SOUL, loc, 10, 0.25, 0.25, 0.25, 0.02);
            case "resinworks" -> world.spawnParticle(Particle.CRIT, loc, 10, 0.25, 0.2, 0.25, 0.06);
            case "bakery" -> world.spawnParticle(Particle.SMOKE, loc, 8, 0.25, 0.2, 0.25, 0.01);
            case "nursery" -> world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 10, 0.3, 0.2, 0.3, 0);
            case "coral_farm" -> world.spawnParticle(Particle.BUBBLE, loc, 12, 0.3, 0.25, 0.3, 0.02);
            case "stone_works" -> world.spawnParticle(Particle.NOTE, loc, 6, 0.3, 0.2, 0.3, 0.5);
            case "candleworks" -> world.spawnParticle(Particle.END_ROD, loc, 6, 0.25, 0.2, 0.25, 0.02);
            case "rail_works" -> world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 10, 0.3, 0.2, 0.3, 0.05);
            case "foundry" -> world.spawnParticle(Particle.LAVA, loc, 6, 0.25, 0.2, 0.25, 0);
            case "end_forge" -> world.spawnParticle(Particle.PORTAL, loc, 12, 0.3, 0.2, 0.3, 0.3);
            case "resonance_chamber" -> world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 8, 0.25, 0.2, 0.25, 0.02);
            default -> world.spawnParticle(Particle.CRIT, loc, 6, 0.2, 0.2, 0.2, 0.05);
        }
    }

    private static int countMaterial(Player p, Material m) {
        if (m == null) return 0;
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && it.getType() == m) n += it.getAmount();
        }
        return n;
    }

    // ---------- Зарядная станция ----------
    public static void useStation(Player p, Block block, ItemStack hand) {
        int magma = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block below = block.getRelative(dx, -1, dz);
                if (below.getType() == Material.MAGMA_BLOCK) magma++;
            }
        }
        int need = Cfg.stationMinMagma();
        if (magma < need) {
            Msg.send(p, "station.no-platform", "need", String.valueOf(need),
                    "have", String.valueOf(magma));
            return;
        }
        var state = block.getState();
        if (!(state instanceof TileState ts)) return;
        long now = System.currentTimeMillis();
        long cd = Cfg.stationCooldown() * 1000L;
        long last = ts.getPersistentDataContainer()
                .getOrDefault(Keys.CD_COOLDOWN, PersistentDataType.LONG, 0L);
        if (now - last < cd) {
            long wait = (cd - (now - last) + 999) / 1000;
            Msg.send(p, "station.cooldown", "n", String.valueOf(wait));
            return;
        }

        String id = Items.toolId(hand);
        // v3.2: null-guard — пустая рука/нетегированный предмет не роняет обработчик
        boolean probeLike = "prospector".equals(id) || "air_tank".equals(id)
                || (id != null && id.startsWith("scanner_"));
        if (probeLike) {
            ItemMeta meta = hand.getItemMeta();
            if (meta == null) return;
            int cur = meta.getPersistentDataContainer()
                    .getOrDefault(Keys.PROBE_CHARGES, PersistentDataType.INTEGER, 0);
            int max = Cfg.probeMaxCharges();
            if (cur >= max) {
                Msg.send(p, "battery.max-charges", "charges", String.valueOf(max));
                return;
            }
            int next = Math.min(max, cur + Cfg.stationProbeCharges());
            meta.getPersistentDataContainer().set(Keys.PROBE_CHARGES,
                    PersistentDataType.INTEGER, next);
            hand.setItemMeta(meta);
            ts.getPersistentDataContainer().set(Keys.CD_COOLDOWN,
                    PersistentDataType.LONG, now);
            ts.update();
            Msg.send(p, "station.probe-charged", "n", String.valueOf(next - cur),
                    "charges", String.valueOf(next), "magma", String.valueOf(magma));
            p.playSound(block.getLocation(), "minecraft:block.lava.pop", 0.9f, 1.4f);
            block.getWorld().spawnParticle(Particle.LAVA,
                    block.getLocation().add(0.5, 1.2, 0.5), 5, 0.3, 0.2, 0.3, 0);
        } else if ("chunk_drill".equals(id)) {
            if (ChunkDrill.forceCharge(hand)) {
                ts.getPersistentDataContainer().set(Keys.CD_COOLDOWN,
                        PersistentDataType.LONG, now);
                ts.update();
                Msg.send(p, "station.drill-charged", "magma", String.valueOf(magma));
                p.playSound(block.getLocation(), "minecraft:block.lava.pop", 1.0f, 0.8f);
            } else {
                Msg.send(p, "station.drill-not-empty");
            }
        } else {
            Msg.send(p, "station.help", "need", String.valueOf(need),
                    "have", String.valueOf(magma));
        }
    }

    // ---------- Геосканер ----------
    public static void useScanner(Player p, Block block) {
        var state = block.getState();
        if (!(state instanceof TileState ts)) return;
        long now = System.currentTimeMillis();
        long cd = Cfg.scannerCooldown() * 1000L;
        long last = ts.getPersistentDataContainer()
                .getOrDefault(Keys.CD_COOLDOWN, PersistentDataType.LONG, 0L);
        if (now - last < cd) {
            long wait = (cd - (now - last) + 999) / 1000;
            Msg.send(p, "scanner.cooldown", "n", String.valueOf(wait));
            return;
        }
        ts.getPersistentDataContainer().set(Keys.CD_COOLDOWN,
                PersistentDataType.LONG, now);
        ts.update();
        Probe.scan(p);
        p.playSound(block.getLocation(), "minecraft:block.amethyst_block.chime", 1.0f, 1.2f);
    }
}
