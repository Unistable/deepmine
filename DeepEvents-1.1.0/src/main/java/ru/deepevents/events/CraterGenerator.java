package ru.deepevents.events;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import ru.deepevents.core.CraterMath;
import ru.deepevents.core.Msg;
import ru.deepevents.core.RestoreService;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Генератор детализированного ядерного кратера (v2, кинематографичный).
 *
 * Воронка «чаши» с крутыми стенами (CraterMath.columnDepth), ВЫСОКИЙ вал
 * (до 3-4 блоков) и апрон выбросов за валом. Зоны: лава-ядро в центре ->
 * тринитит (стекло) -> магма/базальт/обсидиан -> булыжник/туф, кольцо
 * выжженной земли (coarse dirt, гравий), вырубленный/обугленный лес
 * (листва и стволы убираются целиком). Вода у кромки витрифицируется.
 *
 * Оптимизация: колонны префильтруются по радиусу (не O(span^2) пустых),
 * сортируются от центра к краю (воронка раскрывается на глазах),
 * бюджет 12мс/тик — без фризов. Все изменения пишутся в RestoreSession.
 */
public final class CraterGenerator {

    /** Бюджет работы на один тик (мс). 12мс = 24% тика в худшем кадре. */
    private static final long BUDGET_MS = 12;

    private final JavaPlugin plugin;
    private final Random rnd;
    private final double p1, p2, p3;
    private volatile boolean cancelled;

    /** Реальный Y дна воронки в центре (заполняется при обработке колонны 0,0).
     *  Использовать getHighestBlockYAt ПОСЛЕ генерации нельзя: heightmap
     *  обновляется лениво и вернёт устаревшее значение. */
    private volatile int floorYCenter = Integer.MIN_VALUE;

    public CraterGenerator(JavaPlugin plugin, long seed) {
        this.plugin = plugin;
        this.rnd = new Random(seed);
        this.p1 = rnd.nextDouble() * Math.PI * 2;
        this.p2 = rnd.nextDouble() * Math.PI * 2;
        this.p3 = rnd.nextDouble() * Math.PI * 2;
    }

    /** Прекратить генерацию (вызывается при остановке ивента). */
    public void cancel() {
        cancelled = true;
    }

    /** Y дна воронки в центре (валиден после завершения генерации). */
    public int craterFloorY() {
        return floorYCenter;
    }

    /**
     * Порционная генерация (бюджет 12мс за тик).
     * @param session сессия отката (все изменённые блоки)
     * @param onDone  вызов по завершении (главный поток)
     */
    public void generate(Location center, int R, int depthMax,
                         RestoreService.RestoreSession session, Runnable onDone) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();

        // префильтр: держим только колонны, которые реально трогаем
        // (воронка R*1.1 + апрон 1.32*Re + хвост вала ~6 блоков)
        int rmax = (int) Math.ceil(R * 1.1 * 1.32) + 6;
        List<int[]> columns = new ArrayList<>((int) (Math.PI * rmax * rmax) + 16);
        for (int dx = -rmax; dx <= rmax; dx++) {
            for (int dz = -rmax; dz <= rmax; dz++) {
                if (dx * dx + dz * dz <= rmax * (long) rmax) {
                    columns.add(new int[]{cx + dx, cz + dz, dx, dz});
                }
            }
        }
        // сортировка от центра: кратер раскрывается на глазах + равномерная нагрузка
        columns.sort((a, b) -> Integer.compare(a[2] * a[2] + a[3] * a[3], b[2] * b[2] + b[3] * b[3]));

        // обеспечиваем прогрузку чанков заранее
        List<long[]> chunkKeys = new ArrayList<>();
        for (int ccx = (cx - rmax) >> 4; ccx <= (cx + rmax) >> 4; ccx++) {
            for (int ccz = (cz - rmax) >> 4; ccz <= (cz + rmax) >> 4; ccz++) {
                chunkKeys.add(new long[]{ccx, ccz});
            }
        }
        final int totalChunks = chunkKeys.size();
        final int[] loaded = {0};
        final boolean[] failed = {false};
        for (long[] key : chunkKeys) {
            world.getChunkAtAsync((int) key[0], (int) key[1]).whenComplete((ch, err) -> {
                if (err != null) failed[0] = true;
                loaded[0]++;
                if (loaded[0] >= totalChunks) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (cancelled) return;
                        if (failed[0]) {
                            Msg.warn("Кратер: часть чанков не прогрузилась — продолжаем с синхронной прогрузкой.");
                        }
                        processColumns(world, columns, session, cx, cz, R, depthMax, onDone);
                    });
                }
            });
        }
    }

    private void processColumns(World world, List<int[]> columns, RestoreService.RestoreSession session,
                                int cx, int cz, int R, int depthMax, Runnable onDone) {
        final int[] index = {0};
        final int total = columns.size();
        new Object() {
            void step() {
                if (cancelled) {
                    Msg.log("Кратер: генерация прервана, откат снапшота возможен через /deepevents undo.");
                    return;
                }
                long start = System.currentTimeMillis();
                while (index[0] < total && System.currentTimeMillis() - start < BUDGET_MS) {
                    int[] col = columns.get(index[0]++);
                    processColumn(world, session, col[0], col[1], col[2], col[3], cx, cz, R, depthMax);
                }
                int done = index[0];
                if (done % 1200 < 40 || done == total) {
                    int pct = done * 100 / total;
                    Msg.actionbarAll("<#facc15>Формирование кратера… <b>" + pct + "%</b></#facc15>");
                }
                if (done < total) {
                    Bukkit.getScheduler().runTask(plugin, this::step);
                } else {
                    Msg.actionbarAll("<#22c55e>Кратер сформирован.</#22c55e>");
                    onDone.run();
                }
            }
        }.step();
    }

    private void processColumn(World world, RestoreService.RestoreSession session,
                               int x, int z, int dx, int dz, int cx, int cz, int R, int depthMax) {
        double theta = Math.atan2(dz, dx);
        double r = Math.hypot(dx, dz);
        double noise = CraterMath.azimuthNoise(theta, p1, p2, p3);
        double Re = CraterMath.effectiveRadius(R, noise);

        int surfY = world.getHighestBlockYAt(x, z, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (surfY <= world.getMinHeight()) return;

        if (r <= Re) {
            // ---- воронка ----
            int depth = CraterMath.columnDepth(r, Re, depthMax);
            boolean steam = false;
            for (int y = surfY; y > surfY - depth && y > world.getMinHeight(); y--) {
                Block b = world.getBlockAt(x, y, z);
                if (b.getType() == Material.WATER || b.getType() == Material.LAVA) steam = true;
                session.capture(b);
                b.setType(Material.AIR, false);
            }
            // снос листвы/стволов НАД воронкой (иначе деревья висят в воздухе)
            clearVegetationAbove(world, session, x, surfY + 1, z, 26);

            int floorY = surfY - depth;
            if (floorY <= world.getMinHeight()) return;
            // запоминаем реальный пол в центре — heightmap после генерации врёт
            if (dx == 0 && dz == 0) {
                floorYCenter = floorY;
            }
            Block floor = world.getBlockAt(x, floorY, z);
            session.capture(floor);
            floor.setType(floorMaterial(r / Re, world, x, floorY, z, cx, cz), false);
            if (steam) {
                world.spawnParticle(Particle.CLOUD, new Location(world, x + 0.5, floorY + 2, z + 0.5), 8, 0.5, 1.0, 0.5, 0.02);
            }
            // ---- вал ----
            double rh = CraterMath.rimHeight(r, Re) * (0.72 + 0.28 * Math.abs(noise));
            int layers = (int) Math.ceil(rh);
            for (int ly = 1; ly <= layers; ly++) {
                Block rim = world.getBlockAt(x, surfY + ly, z);
                if (rim.getType() == Material.AIR || rim.isReplaceable()) {
                    session.capture(rim);
                    rim.setType(rimMaterial(), false);
                }
            }
        } else {
            double q = r / Re;
            // ---- апрон выбросов за валом (Re..Re*1.32) ----
            double ah = CraterMath.apronHeight(r, Re, noise);
            if (ah > 0) {
                clearVegetationAbove(world, session, x, surfY + 1, z, 14);
                int layers = (int) Math.ceil(ah);
                for (int ly = 1; ly <= layers; ly++) {
                    Block eb = world.getBlockAt(x, surfY + ly, z);
                    if (eb.getType() == Material.AIR || eb.isReplaceable()) {
                        session.capture(eb);
                        eb.setType(apronMaterial(), false);
                    }
                }
                // верхний слой апрона — выжженный грунт
                scorchGround(world, session, x, z);
            } else if (q <= 1.45) {
                // ---- выжженное кольцо: деревья без листвы (обугленные стволы) ----
                clearVegetationAbove(world, session, x, surfY + 1, z, 22);
                scorchGround(world, session, x, z);
            } else if (q <= 2.0) {
                // ---- дальний обжиг: редкий пал, пятна грунта ----
                if (rnd.nextDouble() < 0.30) {
                    clearVegetationAbove(world, session, x, surfY + 1, z, 22);
                }
                if (rnd.nextDouble() < 0.15) {
                    scorchGround(world, session, x, z);
                }
            }
        }
    }

    /**
     * Выжечь верхний блок грунта в колонне. Если верх — ствол дерева,
     * спускаемся по стволу до земли и выжигаем грунт под ним
     * (ствол остаётся стоять — эффект обугленного леса).
     */
    private void scorchGround(World world, RestoreService.RestoreSession session, int x, int z) {
        int y = world.getHighestBlockYAt(x, z, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (y <= world.getMinHeight()) return;
        Block top = world.getBlockAt(x, y, z);
        Material m = top.getType();
        // ствол — ищем грунт под ним
        while (m.name().endsWith("_LOG") && y > world.getMinHeight() + 1) {
            y--;
            m = world.getBlockAt(x, y, z).getType();
        }
        if (m == Material.GRASS_BLOCK || m == Material.DIRT || m == Material.SAND
                || m == Material.GRAVEL || m == Material.PODZOL || m == Material.MOSS_BLOCK
                || m == Material.MYCELIUM) {
            Block g = world.getBlockAt(x, y, z);
            session.capture(g);
            g.setType(rnd.nextDouble() < 0.7 ? Material.COARSE_DIRT : Material.GRAVEL, false);
        }
    }

    /**
     * Снос растительности над колонной (листва + стволы): деревья не должны
     * висеть в воздухе над воронкой и в зоне пала. Ограничение по высоте.
     */
    private void clearVegetationAbove(World world, RestoreService.RestoreSession session,
                                      int x, int fromY, int z, int maxUp) {
        int top = Math.min(fromY + maxUp, world.getMaxHeight() - 1);
        for (int y = fromY; y <= top; y++) {
            Block b = world.getBlockAt(x, y, z);
            Material m = b.getType();
            if (m == Material.AIR) {
                // досматриваем чуть выше первого воздуха: листва может быть прерывистой
                boolean foundAbove = false;
                for (int yy = y + 1; yy <= Math.min(y + 4, top); yy++) {
                    Material mm = world.getBlockAt(x, yy, z).getType();
                    if (mm.name().endsWith("_LEAVES") || mm.name().endsWith("_LOG")) { foundAbove = true; break; }
                }
                if (!foundAbove) break;
                continue;
            }
            if (m.name().endsWith("_LEAVES") || m.name().endsWith("_LOG")) {
                session.capture(b);
                b.setType(Material.AIR, false);
            } else {
                break;
            }
        }
    }

    /** Материал дна воронки: лава-ядро -> тринитит -> магма -> булыжник. С витрификацией у воды. */
    private Material floorMaterial(double q, World world, int x, int y, int z, int cx, int cz) {
        // расплавленное ядро в эпицентре
        double dr = Math.hypot(x - cx, z - cz);
        if (dr <= 2.2) return Material.LAVA;
        if (dr <= 3.4) return Material.MAGMA_BLOCK;

        // витрификация у кромки с водой
        if (q > 0.75) {
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                if (world.getBlockAt(x + d[0], y + 1, z + d[1]).getType() == Material.WATER
                        || world.getBlockAt(x + d[0], y, z + d[1]).getType() == Material.WATER) {
                    return rnd.nextDouble() < 0.5 ? Material.CYAN_STAINED_GLASS : Material.LIME_STAINED_GLASS;
                }
            }
        }
        CraterMath.Zone zone = CraterMath.zone(q, rnd);
        return switch (CraterMath.floorMaterial(zone, q, rnd)) {
            case LIME_GLASS -> Material.LIME_STAINED_GLASS;
            case CYAN_GLASS -> Material.CYAN_STAINED_GLASS;
            case GLASS -> Material.GLASS;
            case MAGMA -> Material.MAGMA_BLOCK;
            case BLACKSTONE -> Material.BLACKSTONE;
            case OBSIDIAN -> Material.OBSIDIAN;
            case COBBLESTONE -> Material.COBBLESTONE;
            case ANDESITE -> Material.ANDESITE;
            case TUFF -> Material.TUFF;
            case DEEPSLATE_COBBLE -> Material.COBBLED_DEEPSLATE;
            case BASALT -> Material.BASALT;
            default -> Material.COBBLESTONE;
        };
    }

    private Material rimMaterial() {
        return switch (CraterMath.rimMaterial(rnd)) {
            case BLACKSTONE -> Material.BLACKSTONE;
            case BASALT -> Material.BASALT;
            case COBBLESTONE -> Material.COBBLESTONE;
            default -> Material.TUFF;
        };
    }

    private Material apronMaterial() {
        return switch (CraterMath.apronMaterial(rnd)) {
            case GRAVEL -> Material.GRAVEL;
            case COBBLESTONE -> Material.COBBLESTONE;
            case TUFF -> Material.TUFF;
            case ANDESITE -> Material.ANDESITE;
            default -> Material.COBBLESTONE;
        };
    }
}
