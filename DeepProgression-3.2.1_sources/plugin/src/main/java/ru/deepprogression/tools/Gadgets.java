package ru.deepprogression.tools;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.deepprogression.DeepProgressionPlugin;
import ru.deepprogression.economy.Economy;
import ru.deepprogression.model.Blocks;
import ru.deepprogression.model.Branch;
import ru.deepprogression.model.Placed;
import ru.deepprogression.model.Section;
import ru.deepprogression.progress.Medals;
import ru.deepprogression.progress.PlayerProfile;
import ru.deepprogression.progress.ProfileStore;
import ru.deepprogression.util.Cfg;
import ru.deepprogression.util.Keys;
import ru.deepprogression.util.Msg;
import ru.deepprogression.util.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ручные приспособления секций v3.2 (ПКМ). Кулдауны — в профиле игрока
 * (ключ gadget_<id>, сохраняются между сессиями). Все механики с расходом
 * ресурсов, лимитами и совместимостью с защитными плагинами.
 */
public final class Gadgets {

    /** Активные «глушители эха»: uuid → epoch мс окончания эффекта. */
    private static final Map<UUID, Long> JAMMED = new ConcurrentHashMap<>();

    /** Флаг синтетических событий защиты (чтобы собственные слушатели не срабатывали дважды). */
    private static boolean FIRING_INTERNAL = false;

    /** True, пока Gadgets стреляет синтетические BlockBreakEvent (для Listeners). */
    public static boolean isFiringInternal() { return FIRING_INTERNAL; }

    private Gadgets() {}

    public static boolean jammed(Player p) {
        Long until = JAMMED.get(p.getUniqueId());
        return until != null && System.currentTimeMillis() < until;
    }

    public static void clear(UUID uuid) {
        JAMMED.remove(uuid);
    }

    // ---------- маршрутизация ----------

    public static void use(Player p, String toolId) {
        if (!Cfg.worldAllowed(p.getWorld())) return;
        // v3.2: креатив не капает прогрессию/не гоняет механики (как Drills.handle)
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE && !Cfg.creativeCounts()) {
            Msg.send(p, "gadget.creative");
            return;
        }
        PlayerProfile prof = ProfileStore.get(p.getUniqueId());
        long now = System.currentTimeMillis();
        long cd = Cfg.gadgetCooldown(toolId) * 1000L;
        String key = "gadget_" + toolId;
        long last = prof.activeCooldown(key);
        if (now - last < cd) {
            Msg.send(p, "gadget.cooldown", "n", String.valueOf((cd - (now - last) + 999) / 1000));
            return;
        }
        boolean ok = switch (toolId) {
            case "pruner" -> pruner(p);
            case "seeder" -> seeder(p);
            case "bonewand" -> bonewand(p);
            case "air_tank" -> airTank(p);
            case "trowel" -> trowel(p);
            case "lightwand" -> lightwand(p);
            case "oiler" -> oiler(p);
            case "thermos" -> thermos(p);
            case "voidsail" -> voidsail(p);
            case "jammer" -> jammer(p);
            default -> false;
        };
        if (ok) {
            prof.setActiveCooldown(key, now);
            ProfileStore.saveAsync(p.getUniqueId());
        }
    }

    private static boolean fail(Player p, String key) {
        Msg.send(p, key);
        p.playSound(p.getLocation(), "minecraft:entity.villager.no", 0.8f, 1.0f);
        return false;
    }

    private static void consume(Player p) {
        p.playSound(p.getLocation(), "minecraft:block.amethyst_block.chime", 0.9f, 1.5f);
        p.getWorld().spawnParticle(Particle.END_ROD,
                p.getLocation().add(0, 1.2, 0), 8, 0.3, 0.4, 0.3, 0.02);
    }

    private static int countMaterial(Player p, Material m) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && it.getType() == m) n += it.getAmount();
        }
        return n;
    }

    private static void removeMaterial(Player p, Material m, int qty) {
        p.getInventory().removeItem(new ItemStack(m, qty));
    }

    /** Синтетический BreakEvent-чек: защита регионов + логирование CoreProtect. */
    private static boolean breakAllowed(Player p, Block block) {
        BlockBreakEvent ev = new BlockBreakEvent(block, p);
        FIRING_INTERNAL = true;
        try {
            Bukkit.getPluginManager().callEvent(ev);
        } finally {
            FIRING_INTERNAL = false;
        }
        return !ev.isCancelled();
    }

    /** Синтетический PlaceEvent-чек (как safePlace в Actives). */
    private static boolean placeAllowed(Player p, Block block, Material mat) {
        BlockPlaceEvent ev = new BlockPlaceEvent(block, block.getState(),
                block.getRelative(BlockFace.DOWN), new ItemStack(mat), p,
                true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(ev);
        return !ev.isCancelled();
    }

    // ==================== ЛЕСОРУБ: Сучкорез ====================

    private static boolean pruner(Player p) {
        Block target = p.getTargetBlockExact(6);
        if (target == null) return fail(p, "gadget.need-target");
        Branch b = Blocks.branchOf(target.getType());
        if (b == null || b.section != Section.WOOD
                || !target.getType().name().endsWith("_LOG")) {
            return fail(p, "gadget.need-log");
        }
        int got = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block leaf = target.getRelative(dx, dy, dz);
                    if (!leaf.getType().name().endsWith("_LEAVES")) continue;
                    if (Placed.isMarked(leaf)) continue;
                    if (leaf.getState(false) instanceof org.bukkit.block.TileState) continue;
                    // v3.2: защита регионов + логирование через синтетический BreakEvent
                    if (!breakAllowed(p, leaf)) continue;
                    Branch lb = Blocks.branchOf(leaf.getType());
                    for (ItemStack d : leaf.getDrops()) {
                        if (d == null || d.getType().isAir()) continue;
                        Drills.give(p, leaf.getLocation(), d.getType(), d.getAmount());
                        if (Economy.tracked(d.getType())) {
                            Economy.mined(d.getType(), d.getAmount());
                        }
                    }
                    if (lb != null) Medals.progress(p, lb, 1);
                    leaf.getWorld().spawnParticle(Particle.CRIT,
                            leaf.getLocation().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0.05);
                    leaf.setType(Material.AIR, false);
                    Placed.unmark(leaf);
                    got++;
                }
            }
        }
        if (got == 0) return fail(p, "gadget.no-leaves");
        Msg.send(p, "gadget.pruned", "n", String.valueOf(got));
        p.playSound(p.getLocation(), "minecraft:block.wood.break", 1.0f, 1.3f);
        consume(p);
        return true;
    }

    // ==================== ЗЕМЛЕДЕЛЕЦ: Ручная сеялка ====================

    private static final Material[] SEED_TYPES = {
            Material.WHEAT_SEEDS, Material.CARROT, Material.POTATO,
            Material.matchMaterial("BEETROOT_SEEDS") == null
                    ? Material.WHEAT_SEEDS : Material.matchMaterial("BEETROOT_SEEDS")};

    private static boolean seeder(Player p) {
        Block target = p.getTargetBlockExact(6);
        if (target == null || !(target.getBlockData() instanceof Farmland)) {
            return fail(p, "gadget.need-farmland");
        }
        // Самый многочисленный тип семян в инвентаре
        Material seed = null;
        int best = 0;
        for (Material m : SEED_TYPES) {
            int n = countMaterial(p, m);
            if (n > best) { best = n; seed = m; }
        }
        if (seed == null || best == 0) return fail(p, "gadget.no-seeds");
        Material crop = seed == Material.WHEAT_SEEDS ? Material.WHEAT
                : seed == Material.CARROT ? Material.CARROTS
                : seed == Material.POTATO ? Material.POTATOES
                : Material.BEETROOTS;
        int planted = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block farm = target.getRelative(dx, 0, dz);
                if (!(farm.getBlockData() instanceof Farmland)) continue;
                Block above = farm.getRelative(BlockFace.UP);
                if (!above.getType().isAir()) continue;
                if (planted >= best) break;
                // v3.2: защита регионов через синтетический PlaceEvent (+ Placed-метка)
                if (!placeAllowed(p, above, crop)) continue;
                above.setType(crop, false);
                Placed.mark(above);
                removeMaterial(p, seed, 1);
                planted++;
            }
        }
        if (planted == 0) return fail(p, "gadget.no-farmland");
        Msg.send(p, "gadget.seeded", "n", String.valueOf(planted));
        p.getWorld().spawnParticle(Particle.COMPOSTER,
                target.getLocation().add(0.5, 1, 0.5), 12, 1.2, 0.3, 1.2, 0);
        consume(p);
        return true;
    }

    // ==================== ФЛОРИСТ: Костяной веер ====================

    private static boolean bonewand(Player p) {
        Block target = p.getTargetBlockExact(6);
        if (target == null) return fail(p, "gadget.need-target");
        if (countMaterial(p, Material.BONE_MEAL) < 3) return fail(p, "gadget.no-bone-meal");
        int grown = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 0; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block block = target.getRelative(dx, dy, dz);
                    // v3.2: Placed-метки не блокируем — рост не создаёт ресурсов,
                    // а связка «сеялка → веер» должна работать в той же сессии
                    if (!(block.getBlockData() instanceof Ageable ag)) continue;
                    if (ag.getAge() >= ag.getMaximumAge()) continue;
                    if (Blocks.branchOf(block.getType()) == null) continue;
                    ag.setAge(ag.getAge() + 1);
                    block.setBlockData(ag, false);
                    grown++;
                }
            }
        }
        if (grown == 0) return fail(p, "gadget.nothing");
        removeMaterial(p, Material.BONE_MEAL, 3);
        Msg.send(p, "gadget.bloomed", "n", String.valueOf(grown));
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                target.getLocation().add(0.5, 0.6, 0.5), 18, 1.2, 0.6, 1.2, 0);
        p.playSound(p.getLocation(), "minecraft:item.bone_meal.use", 1.0f, 1.2f);
        consume(p);
        return true;
    }

    // ==================== РЫБОЛОВ: Дыхательный баллон ====================

    private static boolean airTank(Player p) {
        ItemStack tank = p.getInventory().getItemInMainHand();
        var meta = tank.getItemMeta();
        if (meta == null) return false;
        int charges = meta.getPersistentDataContainer()
                .getOrDefault(Keys.PROBE_CHARGES,
                        org.bukkit.persistence.PersistentDataType.INTEGER, 0);
        if (charges <= 0) return fail(p, "probe.no-charges");
        charges--;
        meta.getPersistentDataContainer().set(Keys.PROBE_CHARGES,
                org.bukkit.persistence.PersistentDataType.INTEGER, charges);
        tank.setItemMeta(meta);
        potion(p, PotionEffectType.WATER_BREATHING, Cfg.airTankBreath(), 0);
        potion(p, PotionEffectType.DOLPHINS_GRACE, Cfg.airTankGrace(), 0);
        Msg.send(p, "gadget.air", "charges", String.valueOf(charges));
        p.playSound(p.getLocation(), "minecraft:entity.dolphin.splash", 0.9f, 1.2f);
        consume(p);
        return true;
    }

    // ==================== КАМЕНЩИК: Шпатель бетонщика ====================

    private static boolean trowel(Player p) {
        Block target = p.getTargetBlockExact(6);
        if (target == null || !target.getType().name().endsWith("_CONCRETE_POWDER")) {
            return fail(p, "gadget.need-powder");
        }
        int set = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block b = target.getRelative(dx, dy, dz);
                    String n = b.getType().name();
                    if (!n.endsWith("_CONCRETE_POWDER")) continue;
                    Material solid = Material.matchMaterial(
                            n.replace("_CONCRETE_POWDER", "_CONCRETE"));
                    if (solid == null) continue;
                    // v3.2: защита регионов через синтетический PlaceEvent
                    if (!placeAllowed(p, b, solid)) continue;
                    b.setType(solid, false);
                    set++;
                }
            }
        }
        if (set == 0) return fail(p, "gadget.nothing");
        Msg.send(p, "gadget.set-concrete", "n", String.valueOf(set));
        p.playSound(p.getLocation(), "minecraft:block.stone.place", 1.0f, 0.9f);
        consume(p);
        return true;
    }

    // ==================== СВЕТОВОД: Жезл световода ====================

    private static boolean lightwand(Player p) {
        var ray = p.rayTraceBlocks(12);
        if (ray == null || ray.getHitBlock() == null || ray.getHitBlockFace() == null) {
            return fail(p, "gadget.need-surface");
        }
        Material light = takeLight(p);
        if (light == null) return fail(p, "gadget.no-light");
        Block spot = ray.getHitBlock().getRelative(ray.getHitBlockFace());
        if (!spot.getType().isAir() && !spot.isLiquid()) return fail(p, "gadget.no-space");
        if (safePlace(p, spot, light)) {
            removeMaterial(p, light, 1);
            Msg.send(p, "gadget.light-placed");
            p.playSound(spot.getLocation(), "minecraft:block.amethyst_block.chime", 1.0f, 1.8f);
            consume(p);
            return true;
        }
        return fail(p, "gadget.protected");
    }

    private static Material takeLight(Player p) {
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it == null || it.getType().isAir()) continue;
            Material m = it.getType();
            if (!m.isBlock()) continue;
            String n = m.name();
            if (n.equals("GLOWSTONE") || n.equals("TORCH") || n.equals("LANTERN")
                    || n.equals("SOUL_LANTERN") || n.equals("SHROOMLIGHT")
                    || n.equals("SEA_LANTERN") || n.equals("END_ROD")
                    || n.equals("JACK_O_LANTERN") || n.equals("SEA_PICKLE")
                    || n.equals("CANDLE") || n.equals("GLOW_LICHEN")
                    || n.endsWith("_FROGLIGHT") || n.equals("COPPER_BULB")
                    || n.endsWith("_CANDLE")) return m;
        }
        return null;
    }

    /** Безопасная установка с совместимостью защитных плагинов (как в Actives). */
    private static boolean safePlace(Player p, Block block, Material mat) {
        BlockPlaceEvent ev = new BlockPlaceEvent(block, block.getState(),
                block.getRelative(BlockFace.DOWN),
                new ItemStack(mat), p, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) return false;
        block.setType(mat, false);
        Placed.onPlayerPlace(block, Blocks.branchOf(mat));
        return true;
    }

    // ==================== ИНЖЕНЕР: Инженерная маслёнка ====================

    private static boolean oiler(Player p) {
        Block target = p.getTargetBlockExact(5);
        if (target == null) return fail(p, "gadget.need-machine");
        String machine = ru.deepprogression.storage.Vaults.machineId(target);
        if (machine == null) return fail(p, "gadget.need-machine");
        // v3.2: у склада кулдауна нет — маслёнке нечего сбрасывать
        if (machine.equals("ore_vault")) return fail(p, "gadget.need-machine");
        // v3.2: Machines.setCooldown умеет и TileState, и PDC чанка
        Machines.setCooldown(target, 0L);
        String name = Tools.get(machine) != null ? Tools.get(machine).name : machine;
        Msg.send(p, "gadget.oiled", "machine", name);
        p.playSound(target.getLocation(), "minecraft:block.honey_block.break", 0.9f, 1.4f);
        target.getWorld().spawnParticle(Particle.ITEM_SLIME,
                target.getLocation().add(0.5, 1, 0.5), 10, 0.3, 0.2, 0.3, 0);
        consume(p);
        return true;
    }

    // ==================== НЕЗЕР: Магмовый термос ====================

    private static boolean thermos(Player p) {
        int cost = Cfg.thermosMagma();
        if (countMaterial(p, Material.MAGMA_BLOCK) < cost) {
            return fail(p, "gadget.no-magma");
        }
        removeMaterial(p, Material.MAGMA_BLOCK, cost);
        potion(p, PotionEffectType.FIRE_RESISTANCE, Cfg.thermosDuration(), 0);
        Msg.send(p, "gadget.thermos", "n", String.valueOf(cost),
                "d", String.valueOf(Cfg.thermosDuration()));
        p.playSound(p.getLocation(), "minecraft:block.lava.pop", 1.0f, 1.2f);
        consume(p);
        return true;
    }

    // ==================== КРАЙ: Парус пустоты ====================

    private static boolean voidsail(Player p) {
        if (countMaterial(p, Material.ENDER_PEARL) < 1) return fail(p, "gadget.no-pearl");
        removeMaterial(p, Material.ENDER_PEARL, 1);
        potion(p, PotionEffectType.SLOW_FALLING, Cfg.voidSailDuration(), 0);
        Msg.send(p, "gadget.sail", "d", String.valueOf(Cfg.voidSailDuration()));
        p.playSound(p.getLocation(), "minecraft:entity.enderman.teleport", 0.8f, 1.4f);
        consume(p);
        return true;
    }

    // ==================== АНОМАЛИИ: Глушитель эха ====================

    private static boolean jammer(Player p) {
        long until = System.currentTimeMillis() + Cfg.jammerDuration() * 1000L;
        JAMMED.put(p.getUniqueId(), until);
        Msg.send(p, "gadget.jammed", "n", String.valueOf(Cfg.jammerDuration()));
        p.playSound(p.getLocation(), "minecraft:block.sculk_sensor.clicking", 0.9f, 0.7f);
        p.getWorld().spawnParticle(Particle.SOUL,
                p.getLocation().add(0, 1, 0), 16, 0.4, 0.5, 0.4, 0.02);
        consume(p);
        return true;
    }

    // ---------- утилиты ----------

    private static void potion(Player p, PotionEffectType type, int seconds, int amp) {
        p.addPotionEffect(new PotionEffect(type, seconds * 20, amp, false, true, true));
    }
}
