package ru.deepevents.core;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Асинхронный поиск локаций: глубокий океан (Кракен) и суша (Ядерный взрыв).
 * Чанки грузятся через Paper getChunkAtAsync, проверки — на главном потоке.
 */
public final class LocationFinder {

    public static final Set<Biome> OCEAN_BIOMES = Set.of(
            Biome.OCEAN, Biome.DEEP_OCEAN, Biome.WARM_OCEAN, Biome.DEEP_LUKEWARM_OCEAN,
            Biome.COLD_OCEAN, Biome.DEEP_COLD_OCEAN, Biome.FROZEN_OCEAN, Biome.DEEP_FROZEN_OCEAN);

    private LocationFinder() {}

    /**
     * Поиск глубокой воды: кольца радиусов от 150 до maxRadius, 16 проб на кольцо.
     * @param minWaterDepth минимальная глубина воды (блоки) в колонне
     */
    public static void findOcean(JavaPlugin plugin, World world, Location origin, int maxRadius,
                                 int minWaterDepth, Consumer<Location> onFound, Runnable onFail) {
        Random rnd = new Random();
        List<int[]> candidates = new ArrayList<>();
        for (int radius = 150; radius <= maxRadius; radius += 100) {
            for (int i = 0; i < 16; i++) {
                double angle = rnd.nextDouble() * Math.PI * 2;
                candidates.add(new int[]{
                        origin.getBlockX() + (int) (Math.cos(angle) * radius),
                        origin.getBlockZ() + (int) (Math.sin(angle) * radius)});
            }
        }
        probe(plugin, world, candidates, 0, loc -> isDeepWater(world, loc, minWaterDepth), onFound, onFail);
    }

    /**
     * Поиск точки на суше: случайные пробы вокруг origin с проверками
     * (не океан, не регион WG, дистанция до игроков и спавна).
     */
    public static void findLand(JavaPlugin plugin, World world, Location origin, int minPlayerDistance,
                                int minSpawnDistance, int attempts, Consumer<Location> onFound, Runnable onFail) {
        Random rnd = new Random();
        List<int[]> candidates = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            int dx = (int) ((rnd.nextDouble() * 2 - 1) * 600);
            int dz = (int) ((rnd.nextDouble() * 2 - 1) * 600);
            candidates.add(new int[]{origin.getBlockX() + dx, origin.getBlockZ() + dz});
        }
        probe(plugin, world, candidates, 0, loc -> isSafeLand(world, loc, minPlayerDistance, minSpawnDistance),
                onFound, onFail);
    }

    private static void probe(JavaPlugin plugin, World world, List<int[]> candidates, int index,
                              java.util.function.Predicate<Location> check,
                              Consumer<Location> onFound, Runnable onFail) {
        if (index >= candidates.size()) {
            Bukkit.getScheduler().runTask(plugin, onFail);
            return;
        }
        int[] c = candidates.get(index);
        int cx = c[0] >> 4, cz = c[1] >> 4;
        world.getChunkAtAsync(cx, cz).thenAccept(chunk ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // ФИКС: раньше возвращался Y=0 (дно мира) — из-за этого шалкер
                    // оказывался под землёй, радиация не работала, координаты врали.
                    // Теперь Y = реальная поверхность (или поверхность воды).
                    Location surface = atSurface(world, c[0], c[1]);
                    if (check.test(surface)) {
                        onFound.accept(surface);
                    } else {
                        probe(plugin, world, candidates, index + 1, check, onFound, onFail);
                    }
                }));
    }

    /** Точка НА поверхности: верхний твёрдый блок + 1 (или поверхность воды). */
    private static Location atSurface(World world, int x, int z) {
        int y = waterSurface(world, x, z);
        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }

    /** Проверка колонны: океанский биом + глубина воды >= minWaterDepth. */
    private static boolean isDeepWater(World world, Location loc, int minWaterDepth) {
        int x = loc.getBlockX(), z = loc.getBlockZ();
        int y = world.getHighestBlockYAt(x, z, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Biome biome = world.getBiome(x, Math.max(world.getMinHeight(), y - 8), z);
        if (!OCEAN_BIOMES.contains(biome)) return false;
        if (world.getBlockAt(x, y, z).getType() != Material.WATER) return false;
        int depth = 0;
        for (int yy = y; yy > world.getMinHeight() && world.getBlockAt(x, yy, z).getType() == Material.WATER; yy--) depth++;
        return depth >= minWaterDepth;
    }

    /** Проверка точки: суша, не WG-регион, дистанция от игроков и спавна. */
    private static boolean isSafeLand(World world, Location loc, int minPlayerDistance, int minSpawnDistance) {
        int x = loc.getBlockX(), z = loc.getBlockZ();
        int y = world.getHighestBlockYAt(x, z, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Material top = world.getBlockAt(x, y, z).getType();
        if (top == Material.WATER || top == Material.LAVA || top == Material.AIR) return false;
        if (OCEAN_BIOMES.contains(world.getBiome(x, Math.max(world.getMinHeight(), y - 4), z))) return false;

        Location real = new Location(world, x + 0.5, y + 1, z + 0.5);
        if (ProtectionAdapter.isProtected(real)) return false;

        if (world.getSpawnLocation() != null && real.distanceSquared(world.getSpawnLocation()) < (double) minSpawnDistance * minSpawnDistance) {
            return false;
        }
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(world) && p.getLocation().distanceSquared(real) < (double) minPlayerDistance * minPlayerDistance) {
                return false;
            }
        }
        return true;
    }

    /** Поверхность воды (y верхнего водного блока) в колонне. */
    public static int waterSurface(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z);
        for (int yy = y; yy > world.getMinHeight(); yy--) {
            if (world.getBlockAt(x, yy, z).getType() == Material.WATER) return yy;
        }
        return y;
    }
}
