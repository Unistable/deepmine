package ru.deepprogression.tools;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
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
import ru.deepprogression.util.Msg;
import ru.deepprogression.util.Text;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Активные перки DeepProgression 3.1: «Кристалл <секции>».
 * Активация: Shift+ПКМ с кристаллом в руке. Кулдаун — в профиле игрока
 * (сохраняется между сессиями). Все способности: лимиты, только естественные
 * блоки (Placed-защита), прогрессия и экономика начисляются как за обычную добычу.
 */
public final class Actives {

    private static final Set<Material> LIGHT_SOURCES = EnumSet.of(
            Material.GLOWSTONE, Material.TORCH, Material.LANTERN, Material.SOUL_LANTERN,
            Material.SHROOMLIGHT, Material.SEA_LANTERN, Material.END_ROD,
            Material.JACK_O_LANTERN, Material.SEA_PICKLE,
            Material.matchMaterial("OCHRE_FROGLIGHT"),
            Material.matchMaterial("VERDANT_FROGLIGHT"),
            Material.matchMaterial("PEARLESCENT_FROGLIGHT"),
            Material.matchMaterial("GLOW_LICHEN"),
            Material.matchMaterial("COPPER_BULB"));

    private Actives() {}

    // ---------- активация ----------

    public static void use(Player p) {
        ItemStack item = p.getInventory().getItemInMainHand();
        String id = Items.toolId(item);
        if (id == null || !id.startsWith("crystal_")) return;
        Section s = sectionOfCrystal(id);
        if (s == null) return;

        PlayerProfile prof = ProfileStore.get(p.getUniqueId());
        long now = System.currentTimeMillis();
        long cd = Cfg.activeCooldown(s) * 1000L;
        long last = prof.activeCooldown(id);
        if (now - last < cd) {
            Msg.send(p, "active.cooldown", "n", String.valueOf((cd - (now - last) + 999) / 1000));
            return;
        }
        boolean ok = switch (s) {
            case MINING -> rush(p);
            case WOOD -> fellTree(p);
            case FARM -> harvest(p);
            case FLORA -> bloom(p);
            case SEA -> tide(p);
            case MASON -> bridge(p);
            case LIGHT -> chandelier(p);
            case ENGINE -> overdrive(p);
            case NETHER -> heatShield(p);
            case END -> phaseStep(p);
            case ANOMALY -> echoLocate(p);
        };
        if (ok) {
            prof.setActiveCooldown(id, now);
            ProfileStore.saveAsync(p.getUniqueId());
        }
    }

    public static Section sectionOfCrystal(String toolId) {
        if (toolId == null || !toolId.startsWith("crystal_")) return null;
        try {
            return Section.valueOf(toolId.substring("crystal_".length())
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void consume(Player p) {
        p.playSound(p.getLocation(), "minecraft:block.amethyst_block.chime", 1.0f, 1.4f);
        p.getWorld().spawnParticle(Particle.END_ROD,
                p.getLocation().add(0, 1.2, 0), 12, 0.3, 0.4, 0.3, 0.02);
    }

    // ==================== ШАХТЁР: Жиловой раж ====================
    private static boolean rush(Player p) {
        potion(p, PotionEffectType.HASTE, 90, 1);
        potion(p, PotionEffectType.NIGHT_VISION, 90, 0);
        Msg.send(p, "active.mining");
        consume(p);
        return true;
    }

    // ==================== ЛЕСОРУБ: Валка ====================
    private static boolean fellTree(Player p) {
        Block target = p.getTargetBlockExact(6);
        if (target == null) return fail(p, "active.need-target");
        Branch b = Blocks.branchOf(target.getType());
        if (b == null || b.section != Section.WOOD
                || !target.getType().name().endsWith("_LOG")) {
            return fail(p, "active.need-log");
        }
        if (Placed.isMarked(target)) return fail(p, "active.placed");

        // BFS вверх по брёвнам той же ветки (лимит 64)
        Set<Block> logs = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        queue.add(target);
        logs.add(target);
        while (!queue.isEmpty() && logs.size() < 64) {
            Block cur = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block next = cur.getRelative(dx, dy, dz);
                        if (next.getType() != target.getType()) continue;
                        if (logs.contains(next)) continue;
                        if (Placed.isMarked(next)) continue;
                        logs.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        World w = target.getWorld();
        int got = 0;
        for (Block log : logs) {
            if (log.getState(false) instanceof org.bukkit.block.TileState) continue;
            for (ItemStack d : log.getDrops()) {
                if (d == null || d.getType().isAir()) continue;
                Drills.give(p, log.getLocation(), d.getType(), d.getAmount());
                Economy.mined(d.getType(), d.getAmount());
            }
            w.spawnParticle(Particle.CRIT, log.getLocation().add(0.5, 0.5, 0.5), 4, 0.2, 0.2, 0.2, 0.05);
            log.setType(Material.AIR, false);
            Placed.unmark(log);
            got++;
        }
        Medals.progress(p, b, got);
        // Подсадка-перк: саженец у корня
        if (ru.deepprogression.perks.Perks.has(p, "wood_replant")
                && ThreadLocalRandom.current().nextDouble() < 0.5) {
            String base = target.getType().name().replace("_LOG", "");
            Material sap = base.equals("MANGROVE")
                    ? Material.matchMaterial("MANGROVE_PROPAGULE")
                    : Material.matchMaterial(base + "_SAPLING");
            Block ground = target.getRelative(BlockFace.DOWN);
            Block at = target;
            if (sap != null && at.getType().isAir()) at.setType(sap, false);
            else if (sap != null && ground.getType().isSolid()
                    && ground.getRelative(BlockFace.UP).getType().isAir()) {
                ground.getRelative(BlockFace.UP).setType(sap, false);
            }
        }
        Msg.send(p, "active.wood", "n", String.valueOf(got));
        p.playSound(p.getLocation(), "minecraft:block.wood.break", 1.0f, 0.8f);
        consume(p);
        return true;
    }

    // ==================== ЗЕМЛЕДЕЛЕЦ: Жатва ====================
    private static boolean harvest(Player p) {
        Location base = p.getLocation();
        World w = base.getWorld();
        int count = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Block block = base.getBlock().getRelative(dx, dy, dz);
                    Material m = block.getType();
                    if (Placed.isMarked(block)) continue;
                    var data = block.getBlockData();
                    if (!(data instanceof Ageable ag)) continue;
                    if (ag.getAge() < ag.getMaximumAge()) continue;
                    Branch b = Blocks.branchOf(m);
                    if (b == null) continue;
                    if (block.getState(false) instanceof org.bukkit.block.TileState) continue;
                    boolean doubleDrop = ThreadLocalRandom.current().nextDouble() < 0.10;
                    for (ItemStack d : block.getDrops()) {
                        if (d == null || d.getType().isAir()) continue;
                        int amt = d.getAmount() * (doubleDrop ? 2 : 1);
                        Drills.give(p, block.getLocation(), d.getType(), amt);
                        Economy.mined(d.getType(), d.getAmount());
                    }
                    block.setType(Material.AIR, false);
                    Placed.unmark(block);
                    if (b != null) Medals.progress(p, b, 1);
                    count++;
                }
            }
        }
        if (count == 0) return fail(p, "active.nothing");
        w.spawnParticle(Particle.COMPOSTER, base.clone().add(0, 0.5, 0), 25, 2, 0.5, 2, 0);
        Msg.send(p, "active.farm", "n", String.valueOf(count));
        consume(p);
        return true;
    }

    // ==================== ФЛОРИСТ: Цветение ====================
    private static boolean bloom(Player p) {
        Location base = p.getLocation();
        int count = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Block block = base.getBlock().getRelative(dx, dy, dz);
                    var data = block.getBlockData();
                    if (data instanceof Ageable ag && ag.getAge() < ag.getMaximumAge()) {
                        Branch b = Blocks.branchOf(block.getType());
                        if (b == null) continue;
                        ag.setAge(Math.min(ag.getMaximumAge(), ag.getAge() + 1));
                        block.setBlockData(ag, false);
                        count++;
                    } else if (block.getType() == Material.CACTUS
                            || block.getType().name().contains("AZALEA")) {
                        continue;
                    }
                }
            }
        }
        if (count == 0) return fail(p, "active.nothing");
        base.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                base.clone().add(0, 0.6, 0), 30, 2, 0.6, 2, 0);
        Msg.send(p, "active.flora", "n", String.valueOf(count));
        p.playSound(base, "minecraft:item.bone_meal.use", 1.0f, 1.2f);
        consume(p);
        return true;
    }

    // ==================== РЫБОЛОВ: Прилив ====================
    private static boolean tide(Player p) {
        int pulled = 0;
        for (Item it : p.getNearbyEntities(8, 8, 8).stream()
                .filter(e -> e instanceof Item).map(e -> (Item) e).toList()) {
            ItemStack stack = it.getItemStack();
            if (!Blocks.isVaultItem(stack.getType())) continue;
            it.teleport(p.getLocation().add(0, 0.5, 0));
            it.setPickupDelay(0);
            pulled++;
        }
        if (pulled == 0) return fail(p, "active.nothing");
        potion(p, PotionEffectType.WATER_BREATHING, 60, 0);
        Msg.send(p, "active.sea", "n", String.valueOf(pulled));
        p.playSound(p.getLocation(), "minecraft:entity.dolphin.play", 1.0f, 1.2f);
        consume(p);
        return true;
    }

    // ==================== КАМЕНЩИК: Кладка ====================
    private static boolean bridge(Player p) {
        BlockFace dir = cardinal(p);
        Block feet = p.getLocation().getBlock();
        int placed = 0;
        for (int i = 1; i <= 5; i++) {
            Block target = feet.getRelative(dir, i);
            Block ground = target.getRelative(BlockFace.DOWN);
            if (!ground.getType().isAir() && !ground.isLiquid()) continue;
            Material mat = takeMasonBlock(p);
            if (mat == null) break;
            if (safePlace(p, ground, mat)) placed++;
        }
        if (placed == 0) return fail(p, "active.no-mason");
        Msg.send(p, "active.mason", "n", String.valueOf(placed));
        p.playSound(p.getLocation(), "minecraft:block.stone.place", 1.0f, 1.0f);
        consume(p);
        return true;
    }

    /** Первый материал веток каменщика в инвентаре. */
    private static Material takeMasonBlock(Player p) {
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it == null || it.getType().isAir()) continue;
            Branch b = Blocks.branchOf(it.getType());
            if (b != null && b.section == Section.MASON) return it.getType();
        }
        return null;
    }

    /** Безопасная установка с совместимостью защитных плагинов. */
    private static boolean safePlace(Player p, Block block, Material mat) {
        BlockPlaceEvent ev = new BlockPlaceEvent(block, block.getState(),
                block.getRelative(BlockFace.DOWN),
                new ItemStack(mat), p, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) return false;
        block.setType(mat, false);
        p.getInventory().removeItem(new ItemStack(mat, 1));
        Placed.onPlayerPlace(block, Blocks.branchOf(mat));
        return true;
    }

    private static BlockFace cardinal(Player p) {
        float yaw = p.getLocation().getYaw();
        yaw = (yaw % 360 + 360) % 360;
        if (yaw >= 315 || yaw < 45) return BlockFace.SOUTH;
        if (yaw < 135) return BlockFace.WEST;
        if (yaw < 225) return BlockFace.NORTH;
        return BlockFace.EAST;
    }

    // ==================== СВЕТОВОД: Люстра ====================
    private static boolean chandelier(Player p) {
        Location center = p.getLocation().add(0, 2.5, 0);
        int[][] ring = {{3, 0}, {2, 2}, {0, 3}, {-2, 2}, {-3, 0}, {-2, -2}, {0, -3}, {2, -2}};
        int placed = 0;
        for (int[] off : ring) {
            Block target = center.clone().add(off[0], 0, off[1]).getBlock();
            if (!target.getType().isAir()) continue;
            Material light = takeLight(p);
            if (light == null) break;
            if (safePlace(p, target, light)) placed++;
        }
        if (placed == 0) return fail(p, "active.no-light");
        Msg.send(p, "active.light", "n", String.valueOf(placed));
        p.playSound(p.getLocation(), "minecraft:block.amethyst_block.chime", 1.0f, 1.8f);
        consume(p);
        return true;
    }

    private static Material takeLight(Player p) {
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it == null || it.getType().isAir()) continue;
            if (LIGHT_SOURCES.contains(it.getType()) && it.getType().isBlock()) return it.getType();
        }
        return null;
    }

    // ==================== ИНЖЕНЕР: Форсаж ====================
    private static boolean overdrive(Player p) {
        potion(p, PotionEffectType.SPEED, 60, 1);
        potion(p, PotionEffectType.HASTE, 60, 1);
        Msg.send(p, "active.engine");
        consume(p);
        return true;
    }

    // ==================== НЕЗЕР: Жаровой щит ====================
    private static boolean heatShield(Player p) {
        potion(p, PotionEffectType.FIRE_RESISTANCE, 120, 0);
        Msg.send(p, "active.nether");
        consume(p);
        return true;
    }

    // ==================== КРАЙ: Фазовый шаг ====================
    private static boolean phaseStep(Player p) {
        Location eyes = p.getEyeLocation();
        org.bukkit.util.Vector dir = eyes.getDirection().normalize();
        Location dest = null;
        for (int i = 2; i <= 8; i++) {
            Location probe = eyes.clone().add(dir.clone().multiply(i));
            Block body = probe.getBlock();
            Block head = probe.clone().add(0, 1, 0).getBlock();
            Block ground = probe.clone().add(0, -1, 0).getBlock();
            if (body.isPassable() && head.isPassable() && !ground.isPassable()) {
                dest = probe;
            }
        }
        if (dest == null) return fail(p, "active.no-space");
        Location from = p.getLocation().clone();
        p.setFallDistance(0);
        p.teleport(new Location(dest.getWorld(), dest.getX(), dest.getY(), dest.getZ(),
                p.getLocation().getYaw(), p.getLocation().getPitch()));
        p.setFallDistance(0);
        p.getWorld().spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.5);
        p.playSound(p.getLocation(), "minecraft:entity.enderman.teleport", 1.0f, 1.1f);
        Msg.send(p, "active.end");
        consume(p);
        return true;
    }

    // ==================== АНОМАЛИИ: Эхолокация ====================
    private static boolean echoLocate(Player p) {
        Location loc = p.getLocation();
        World w = loc.getWorld();
        int cx = loc.getChunk().getX();
        int cz = loc.getChunk().getZ();
        java.util.List<ChunkSnapshot> snaps = new java.util.ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (w.isChunkLoaded(cx + dx, cz + dz)) {
                    snaps.add(w.getChunkAt(cx + dx, cz + dz).getChunkSnapshot(false, false, false));
                }
            }
        }
        int baseX = loc.getBlockX(), baseY = loc.getBlockY(), baseZ = loc.getBlockZ();
        String worldName = w.getName();
        Bukkit.getScheduler().runTaskAsynchronously(DeepProgressionPlugin.get(), () -> {
            int total = 0;
            double nearest = Double.MAX_VALUE;
            int minY = w.getMinHeight(), maxY = w.getMaxHeight();
            for (ChunkSnapshot snap : snaps) {
                int ox = snap.getX() * 16, oz = snap.getZ() * 16;
                for (int y = minY; y < maxY; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            Material m = snap.getBlockType(x, y, z);
                            if (Blocks.branchOf(m) != Branch.SCULK) continue;
                            total++;
                            double dx = ox + x - baseX, dy = (double) y - baseY, dz = oz + z - baseZ;
                            nearest = Math.min(nearest, Math.sqrt(dx * dx + dy * dy + dz * dz));
                        }
                    }
                }
            }
            final int fTotal = total;
            final double fNearest = nearest;
            Bukkit.getScheduler().runTask(DeepProgressionPlugin.get(), () -> {
                if (!p.isOnline()) return;
                // Три эхо-волны
                for (int wave = 0; wave < 3; wave++) {
                    final int wv = wave;
                    final int r = 4 + wave * 4;
                    Bukkit.getScheduler().runTaskLater(DeepProgressionPlugin.get(), () -> {
                        loc.getWorld().spawnParticle(Particle.SOUL,
                                loc.clone().add(0, 1, 0), 40, r / 2.0, 1.5, r / 2.0, 0.02);
                        loc.getWorld().playSound(loc, "minecraft:block.sculk_sensor.clicking",
                                0.8f, 0.8f + wv * 0.2f);
                    }, wave * 12L);
                }
                Msg.send(p, "active.echo", "n", Text.num(fTotal),
                        "dist", fNearest == Double.MAX_VALUE ? "—" : String.valueOf((int) fNearest),
                        "world", worldName);
            });
        });
        consume(p);
        return true;
    }

    // ---------- утилиты ----------

    private static void potion(Player p, PotionEffectType type, int seconds, int amp) {
        p.addPotionEffect(new PotionEffect(type, seconds * 20, amp, false, true, true));
    }

    private static boolean fail(Player p, String key) {
        Msg.send(p, key);
        p.playSound(p.getLocation(), "minecraft:entity.villager.no", 0.8f, 1.0f);
        return false;
    }

    /** Очистка (выход игрока). */
    public static void clear(java.util.UUID uuid) { }

    /** Число способностей (selftest). */
    public static int count() {
        return Section.values().length;
    }
}
