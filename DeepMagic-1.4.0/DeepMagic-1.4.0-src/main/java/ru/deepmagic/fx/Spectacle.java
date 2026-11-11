package ru.deepmagic.fx;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.deepmagic.DeepMagicPlugin;
import ru.deepmagic.model.State;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spectacle — двигатель зрелищности DeepMagic 1.2.0.
 * Превращает «частицы и урон» в эпичные сцены:
 *  - физичные блоки-орбиты вокруг целей (BlockDisplay, БЕЗ коллизии),
 *    плавно исчезающие после действия состояния;
 *  - вспышки в стиле тотема бессмертия (ItemDisplay над головой, наклон и рост);
 *  - анимированные лучи, расширяющиеся кольца-шоквейвы, имплозии, спирали;
 *  - падающие блоки-призраки, материализация терраформа, разлёт предметов.
 *
 * Безопасность:
 *  - все дисплеи: setPersistent(false) — не сохраняются в мир;
 *  - PDC-тег dm_fx=1 — переап старта и /dmtest clean их убирают;
 *  - глобальный кап MAX_DISPLAYS и кап на владельца PER_OWNER — TPS защищён;
 *  - при переполнении FX тихо пропускается: геймплей не зависит от зрелищности.
 */
public final class Spectacle {

    private Spectacle() {}

    // ==================== реестр ====================

    private interface FXJob {
        /** Один шаг тикера (раз в 2 тика). false — работа завершена. */
        boolean step();
    }

    private static final List<FXJob> JOBS = new ArrayList<>();
    private static final AtomicInteger DISPLAYS = new AtomicInteger();
    private static final AtomicInteger ANON = new AtomicInteger(); // дисплеи без владельца
    private static final Set<String> BOUND = new HashSet<>(); // uuid+state дедупликация орбит
    private static final Map<UUID, AtomicInteger> PER_OWNER = new HashMap<>();
    private static org.bukkit.NamespacedKey KEY_FX;
    private static org.bukkit.NamespacedKey KEY_OWNER;

    static final int MAX_DISPLAYS = 480;
    static final int PER_OWNER_CAP = 48;
    static final int JOBS_CAP = 140;

    private static BukkitTask TICKER;

    // ==================== жизненный цикл ====================

    public static void init(JavaPlugin plugin) {
        KEY_FX = new org.bukkit.NamespacedKey(plugin, "dm_fx");
        KEY_OWNER = new org.bukkit.NamespacedKey(plugin, "dm_fx_owner");
        reapAllLoaded();
        TICKER = Bukkit.getScheduler().runTaskTimer(plugin, Spectacle::tick, 2L, 2L);
        plugin.getLogger().info("Spectacle: двигатель зрелищности активен (кап "
                + MAX_DISPLAYS + " дисплеев)");
    }

    public static void shutdown() {
        if (TICKER != null) TICKER.cancel();
        JOBS.clear();
        BOUND.clear();
        PER_OWNER.clear();
        DISPLAYS.set(0);
        ANON.set(0);
        reapAllLoaded();
    }

    /** Тикер: 1 раз в 2 тика. */
    private static void tick() {
        if (JOBS.isEmpty()) return;
        Iterator<FXJob> it = JOBS.iterator();
        while (it.hasNext()) {
            FXJob job = it.next();
            boolean alive;
            try {
                alive = job.step();
            } catch (Throwable t) {
                alive = false; // FX не должен ронять сервер
            }
            if (!alive) it.remove();
        }
    }

    /** Убрать все наши дисплеи в загруженных мирах (старт/выключение/чистка). */
    public static void reapAllLoaded() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (isOurFx(e)) e.remove();
            }
        }
    }

    public static boolean isOurFx(Entity e) {
        if (KEY_FX == null) return false;
        Byte v = e.getPersistentDataContainer().get(KEY_FX, PersistentDataType.BYTE);
        return v != null && v == 1;
    }

    /** Сводка для тестов и админ-команд. */
    public static String stats() {
        return "jobs=" + JOBS.size() + ", displays=" + DISPLAYS.get();
    }

    public static int activeDisplays() {
        return DISPLAYS.get();
    }

    // ==================== капы ====================

    private static boolean canSpawn(UUID owner) {
        if (DISPLAYS.get() >= MAX_DISPLAYS) return false;
        if (JOBS.size() >= JOBS_CAP) return false;
        if (owner == null) return ANON.get() < MAX_DISPLAYS;
        AtomicInteger n = PER_OWNER.get(owner);
        return n == null || n.get() < PER_OWNER_CAP;
    }

    private static void spawned(UUID owner) {
        DISPLAYS.incrementAndGet();
        if (owner == null) {
            ANON.incrementAndGet();
        } else {
            PER_OWNER.computeIfAbsent(owner, k -> new AtomicInteger()).incrementAndGet();
        }
    }

    private static void released(UUID owner) {
        DISPLAYS.decrementAndGet();
        if (owner == null) {
            ANON.decrementAndGet();
            return;
        }
        AtomicInteger n = PER_OWNER.get(owner);
        if (n != null && n.decrementAndGet() <= 0) PER_OWNER.remove(owner);
    }

    // ==================== фабрики дисплеев ====================

    private static BlockDisplay spawnBlock(Location loc, BlockData data, float scale,
                                           UUID owner) {
        if (!canSpawn(owner) || !loc.isChunkLoaded()) return null;
        return loc.getWorld().spawn(loc, BlockDisplay.class, d -> {
            d.setBlock(data);
            d.setPersistent(false);
            d.setInvulnerable(true);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(2);
            d.setViewRange(0.9f);
            d.getPersistentDataContainer().set(KEY_FX, PersistentDataType.BYTE, (byte) 1);
            if (owner != null) {
                d.getPersistentDataContainer().set(KEY_OWNER,
                        PersistentDataType.STRING, owner.toString());
            }
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0), new Quaternionf(),
                    new Vector3f(scale, scale, scale), new Quaternionf()));
            spawned(owner);
        });
    }

    private static ItemDisplay spawnItem(Location loc, Material item, float scale,
                                         UUID owner) {
        if (!canSpawn(owner) || !loc.isChunkLoaded()) return null;
        return loc.getWorld().spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(item));
            d.setPersistent(false);
            d.setInvulnerable(true);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(2);
            d.setViewRange(0.9f);
            d.getPersistentDataContainer().set(KEY_FX, PersistentDataType.BYTE, (byte) 1);
            if (owner != null) {
                d.getPersistentDataContainer().set(KEY_OWNER,
                        PersistentDataType.STRING, owner.toString());
            }
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0), new Quaternionf(),
                    new Vector3f(scale, scale, scale), new Quaternionf()));
            spawned(owner);
        });
    }

    /** Плавное сжатие в ноль + отложенное удаление (учёт капа — здесь и только здесь). */
    private static void vanish(Display d, UUID owner, int fadeSteps) {
        if (d == null || !d.isValid()) return;
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(Math.max(1, fadeSteps * 2));
        var t = d.getTransformation();
        d.setTransformation(new Transformation(t.getTranslation(), t.getLeftRotation(),
                new Vector3f(0.01f, 0.01f, 0.01f), t.getRightRotation()));
        released(owner);
        Bukkit.getScheduler().runTaskLater(DeepMagicPlugin.get(), () -> {
            if (d.isValid()) d.remove();
        }, Math.max(1, fadeSteps * 2L));
    }

    // ==================== примитивы частиц ====================

    public static void burst(Location c, Particle p, int count, double spread) {
        c.getWorld().spawnParticle(p, c, count, spread, spread, spread, 0.05);
    }

    public static void dust(Location c, Color color, float size, int count, double spread) {
        c.getWorld().spawnParticle(Particle.DUST, c, count, spread, spread, spread, 0,
                new Particle.DustOptions(color, size));
    }

    /** Всплеск частиц-предметов (Particle.ITEM требует ItemStack). */
    public static void itemFx(Location c, Material item, int count, double spread) {
        c.getWorld().spawnParticle(Particle.ITEM, c, count, spread, spread, spread, 0.1,
                new ItemStack(item));
    }

    public static void sound(Location c, Sound s, float vol, float pitch) {
        c.getWorld().playSound(c, s, vol, pitch);
    }

    /** Кольцо из частиц (мгновенное). */
    public static void ring(Location c, double radius, Particle p, double density) {
        int points = Math.max(14, (int) (2 * Math.PI * radius * density));
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            c.getWorld().spawnParticle(p,
                    c.clone().add(Math.cos(a) * radius, 0.05, Math.sin(a) * radius),
                    1, 0, 0, 0, 0);
        }
    }

    public static void ringDust(Location c, double radius, Color color, float size) {
        int points = Math.max(16, (int) (2 * Math.PI * radius * 1.2));
        Particle.DustOptions opt = new Particle.DustOptions(color, size);
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            c.getWorld().spawnParticle(Particle.DUST,
                    c.clone().add(Math.cos(a) * radius, 0.05, Math.sin(a) * radius),
                    1, 0, 0, 0, 0, opt);
        }
    }

    // ==================== анимационные работы ====================

    /** Расширяющееся кольцо-шоквейв. */
    public static void ringExpand(Location c, double maxR, int steps,
                                  Particle p, Color dust, float dustSize) {
        if (JOBS.size() >= JOBS_CAP) return;
        Location center = c.clone();
        JOBS.add(new FXJob() {
            int t = 0;
            @Override
            public boolean step() {
                t++;
                double r = maxR * t / (double) steps;
                if (dust != null) ringDust(center, r, dust, dustSize);
                if (p != null && t % 2 == 0) ring(center, r, p, 0.7);
                return t < steps;
            }
        });
    }

    /** Сходящееся кольцо-имплозия (втягивается в точку). */
    public static void implosion(Location c, double radius, int steps,
                                 Particle p, Color dust, float dustSize) {
        if (JOBS.size() >= JOBS_CAP) return;
        Location center = c.clone();
        JOBS.add(new FXJob() {
            int t = 0;
            @Override
            public boolean step() {
                t++;
                double r = radius * (1.0 - (double) t / steps);
                if (r <= 0.2) return false;
                if (dust != null) ringDust(center, r, dust, dustSize);
                if (p != null) ring(center, r, p, 0.7);
                return t < steps;
            }
        });
    }

    /** Восходящая спираль-столб. */
    public static void spiral(Location c, double radius, double height, int steps,
                              Particle p, Color dust, float dustSize) {
        if (JOBS.size() >= JOBS_CAP) return;
        Location base = c.clone();
        JOBS.add(new FXJob() {
            int t = 0;
            @Override
            public boolean step() {
                t++;
                double f = (double) t / steps;
                double y = height * f;
                double r = radius * (1.0 - 0.35 * f);
                double a = f * Math.PI * 6;
                Location at = base.clone().add(Math.cos(a) * r, y, Math.sin(a) * r);
                if (dust != null) {
                    base.getWorld().spawnParticle(Particle.DUST, at, 2, 0.05, 0.05,
                            0.05, 0, new Particle.DustOptions(dust, dustSize));
                }
                if (p != null) base.getWorld().spawnParticle(p, at, 1, 0, 0, 0, 0);
                return t < steps;
            }
        });
    }

    /** Анимированный луч A→B (живой, тянется N шагов). */
    public static void beam(Location from, Location to, Particle p,
                            Color dust, float dustSize, int steps, double gap) {
        if (JOBS.size() >= JOBS_CAP) return;
        World w = from.getWorld();
        org.bukkit.util.Vector v = to.toVector().subtract(from.toVector());
        double len = v.length();
        if (len < 0.1) return;
        org.bukkit.util.Vector dir = v.normalize();
        JOBS.add(new FXJob() {
            int t = 0;
            @Override
            public boolean step() {
                t++;
                double head = len * Math.min(1.0, t / (double) steps);
                for (double d = 0; d <= head; d += gap) {
                    Location at = from.clone().add(dir.clone().multiply(d));
                    if (dust != null) {
                        w.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0,
                                new Particle.DustOptions(dust, dustSize));
                    }
                    if (p != null) w.spawnParticle(p, at, 1, 0, 0, 0, 0);
                }
                return t < steps;
            }
        });
    }

    // ==================== орбиты блоков/предметов ====================

    /** Орбита блоков вокруг ТОЧКИ (не сущности) — вихри и якоря. */
    public static void orbitPoint(Location center, BlockData data, int count,
                                  double radius, double height, float scale,
                                  double speedDegPerStep, int seconds) {
        if (center == null) return;
        int n = Math.max(3, count);
        Display[] arr = new Display[n];
        Location base = center.clone().add(0, height, 0);
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            arr[i] = spawnBlock(base.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius),
                    data, scale, null);
        }
        JOBS.add(new OrbitJob(null, center, arr, radius, height, scale,
                Math.toRadians(speedDegPerStep), seconds * 10, null));
    }

    /** Орбита блоков вокруг сущности, живёт seconds. */
    public static void orbitBlocks(Entity owner, BlockData data, int count,
                                   double radius, double height, float scale,
                                   double speedDegPerStep, int seconds) {
        if (owner == null || owner.isDead()) return;
        UUID id = owner.getUniqueId();
        int n = Math.max(3, count);
        Display[] arr = new Display[n];
        Location base = owner.getLocation().add(0, height, 0);
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            arr[i] = spawnBlock(base.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius),
                    data, scale, id);
        }
        JOBS.add(new OrbitJob(owner, arr, radius, height, scale,
                Math.toRadians(speedDegPerStep), seconds * 10, null));
    }

    /** Орбита предметов вокруг сущности. */
    public static void orbitItems(Entity owner, Material item, int count,
                                 double radius, double height, float scale,
                                 double speedDegPerStep, int seconds) {
        if (owner == null || owner.isDead()) return;
        UUID id = owner.getUniqueId();
        int n = Math.max(3, count);
        Display[] arr = new Display[n];
        Location base = owner.getLocation().add(0, height, 0);
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            arr[i] = spawnItem(base.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius),
                    item, scale, id);
        }
        JOBS.add(new OrbitJob(owner, arr, radius, height, scale,
                Math.toRadians(speedDegPerStep), seconds * 10, null));
    }

    /**
     * Орбита, ПРИВЯЗАННАЯ К СОСТОЯНИЮ: живёт пока PDC-состояние активно
     * (пример ТЗ: угольное покрытие). По исчезновении состояния блоки
     * плавно сжимаются в ноль (fade) и удаляются.
     */
    public static void stateOrbitBlocks(LivingEntity owner, State state, BlockData data,
                                        int count, double radius, double height,
                                        float scale, double speedDegPerStep, int capSeconds) {
        String tag = owner.getUniqueId() + ":" + state.name();
        if (BOUND.contains(tag)) return;
        BOUND.add(tag);
        UUID id = owner.getUniqueId();
        int n = Math.max(3, count);
        Display[] arr = new Display[n];
        Location base = owner.getLocation().add(0, height, 0);
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            arr[i] = spawnBlock(base.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius),
                    data, scale, id);
        }
        JOBS.add(new OrbitJob(owner, arr, radius, height, scale,
                Math.toRadians(speedDegPerStep), capSeconds * 10, tag));
    }

    public static void stateOrbitItems(LivingEntity owner, State state, Material item,
                                       int count, double radius, double height,
                                       float scale, double speedDegPerStep, int capSeconds) {
        String tag = owner.getUniqueId() + ":" + state.name();
        if (BOUND.contains(tag)) return;
        BOUND.add(tag);
        UUID id = owner.getUniqueId();
        int n = Math.max(3, count);
        Display[] arr = new Display[n];
        Location base = owner.getLocation().add(0, height, 0);
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            arr[i] = spawnItem(base.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius),
                    item, scale, id);
        }
        JOBS.add(new OrbitJob(owner, arr, radius, height, scale,
                Math.toRadians(speedDegPerStep), capSeconds * 10, tag));
    }

    private static final class OrbitJob implements FXJob {
        final Entity owner;      // может быть null — тогда вращение вокруг center
        final Location center;   // фиксированный центр для orbitPoint
        final Display[] arr;
        final double radius, height;
        final float scale;
        final double speed;
        final int maxSteps;
        final String boundTag;   // != null → привязка к состоянию
        int step = 0;

        OrbitJob(Entity owner, Display[] arr, double radius, double height, float scale,
                 double speed, int maxSteps, String boundTag) {
            this(owner, null, arr, radius, height, scale, speed, maxSteps, boundTag);
        }

        OrbitJob(Entity owner, Location center, Display[] arr, double radius, double height,
                 float scale, double speed, int maxSteps, String boundTag) {
            this.owner = owner;
            this.center = center;
            this.arr = arr;
            this.radius = radius;
            this.height = height;
            this.scale = scale;
            this.speed = speed;
            this.maxSteps = maxSteps;
            this.boundTag = boundTag;
        }

        @Override
        public boolean step() {
            step++;
            if (owner != null && (!owner.isValid() || owner.isDead())) {
                kill();
                return false;
            }
            if (boundTag != null) {
                String[] parts = boundTag.split(":", 2);
                State s = State.valueOf(parts[1]);
                UUID u = UUID.fromString(parts[0]);
                Entity e = Bukkit.getEntity(u);
                if (!(e instanceof LivingEntity le) || !s.has(le)) {
                    kill(); // состояние кончилось — плавный fade
                    return false;
                }
            }
            if (step >= maxSteps) {
                kill();
                return false;
            }
            double ang = step * speed;
            double bob = Math.sin(step * 0.18) * 0.12;
            Location base = owner != null
                    ? owner.getLocation().add(0, height + bob, 0)
                    : center.clone().add(0, height + bob, 0);
            int n = arr.length;
            for (int i = 0; i < n; i++) {
                Display d = arr[i];
                if (d == null || !d.isValid()) continue;
                double a = ang + 2 * Math.PI * i / n;
                d.teleport(base.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius));
                float rot = (float) Math.toDegrees(ang * 0.5);
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(4);
                var t = d.getTransformation();
                d.setTransformation(new Transformation(t.getTranslation(),
                        new Quaternionf(new AxisAngle4f(rot, 0, 1, 0)),
                        t.getScale(), t.getRightRotation()));
            }
            return true;
        }

        private void kill() {
            UUID u = owner == null ? null : owner.getUniqueId();
            for (Display d : arr) vanish(d, u, 8);
            if (boundTag != null) BOUND.remove(boundTag);
        }
    }

    // ==================== вспышка тотема (пример ТЗ) ====================

    /**
     * Эффект тотема бессмертия над головой, но с любым предметом
     * (для угля — сам уголь): всплывает, наклоняется вперёд, растёт,
     * тает частицами и исчезает. Классический totem-момент.
     */
    public static void totemFlash(Entity owner, Material item, Particle accent,
                                  Color accentDust, int accentCount, float pitch) {
        if (owner == null || owner.isDead() || !canSpawn(owner.getUniqueId())) return;
        UUID id = owner.getUniqueId();
        Location start = owner.getLocation().add(0, owner.getHeight() + 0.4, 0);
        start.setYaw(owner.getLocation().getYaw());
        ItemDisplay d = spawnItem(start, item, 0.12f, id);
        if (d == null) return;
        d.setBillboard(Display.Billboard.FIXED);
        sound(start, Sound.ITEM_TOTEM_USE, 0.9f, pitch);
        JOBS.add(new FXJob() {
            int t = 0;
            @Override
            public boolean step() {
                t++;
                if (owner == null || !owner.isValid() || owner.isDead() || !d.isValid()) {
                    vanish(d, id, 2);
                    return false;
                }
                Location head = owner.getLocation().add(0, owner.getHeight() + 0.55, 0);
                head.setYaw(owner.getLocation().getYaw());
                if (t <= 12) d.teleport(head);
                if (t == 1) {
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(16);
                    d.setTransformation(new Transformation(
                            new Vector3f(0, 0.15f, 0),
                            new Quaternionf(new AxisAngle4f((float) Math.toRadians(-38), 1, 0, 0)),
                            new Vector3f(1.6f, 1.6f, 1.6f), new Quaternionf()));
                }
                if (t > 1 && t < 14) {
                    Location at = head.clone().add(0, 0.5, 0);
                    if (accentDust != null) {
                        at.getWorld().spawnParticle(Particle.DUST, at, accentCount / 2 + 2,
                                0.4, 0.4, 0.4, 0, new Particle.DustOptions(accentDust, 1.1f));
                    }
                    if (accent != null && t % 2 == 0) {
                        at.getWorld().spawnParticle(accent, at, 3, 0.35, 0.4, 0.35, 0.02);
                    }
                }
                if (t >= 22) {
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(14);
                    var tr = d.getTransformation();
                    d.setTransformation(new Transformation(tr.getTranslation(),
                            tr.getLeftRotation(), new Vector3f(0.01f, 0.01f, 0.01f),
                            tr.getRightRotation()));
                    if (t >= 30) {
                        released(id);
                        if (d.isValid()) d.remove();
                        return false;
                    }
                }
                return true;
            }
        });
    }

    // ==================== падающие блоки и разлёты предметов ====================

    /** Блоки-призраки: спавнятся выше и опускаются в целевую точку, тая (эхо реальной установки). */
    public static void blocksDescend(List<Location> spots, BlockData data,
                                     float scale, double fromHeight, int lifeSteps) {
        if (JOBS.size() >= JOBS_CAP) return;
        List<Display> list = new ArrayList<>();
        for (Location l : spots) {
            BlockDisplay d = spawnBlock(l.clone().add(0, fromHeight, 0), data, scale, null);
            if (d == null) continue;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(20);
            d.setTransformation(new Transformation(new Vector3f(0,
                    (float) -fromHeight, 0), new Quaternionf(),
                    new Vector3f(scale, scale, scale), new Quaternionf()));
            list.add(d);
        }
        if (list.isEmpty()) return;
        JOBS.add(new FadeOutJob(list.toArray(new Display[0]), lifeSteps, null));
    }

    /** Разлёт предметов из точки (монет, лепестков, осколков) с таянием. */
    public static void itemBurst(Location c, Material item, int count,
                                 double power, int lifeSteps) {
        if (JOBS.size() >= JOBS_CAP) return;
        List<Display> list = new ArrayList<>();
        org.bukkit.util.Vector[] dirs = new org.bukkit.util.Vector[count];
        for (int i = 0; i < count; i++) {
            double a = Math.random() * Math.PI * 2;
            double up = 0.4 + Math.random() * 0.6;
            dirs[i] = new org.bukkit.util.Vector(Math.cos(a) * (0.5 + Math.random() * 0.5),
                    up, Math.sin(a) * (0.5 + Math.random() * 0.5)).normalize().multiply(power);
            ItemDisplay d = spawnItem(c, item, 0.33f, null);
            if (d == null) continue;
            d.setBillboard(Display.Billboard.FIXED);
            list.add(d);
        }
        if (list.isEmpty()) return;
        JOBS.add(new ScatterJob(list, dirs, lifeSteps));
    }

    private static final class ScatterJob implements FXJob {
        final List<Display> list;
        final org.bukkit.util.Vector[] dirs;
        int t = 0;
        final int max;

        ScatterJob(List<Display> list, org.bukkit.util.Vector[] dirs, int max) {
            this.list = list;
            this.dirs = dirs;
            this.max = max;
        }

        @Override
        public boolean step() {
            t++;
            if (t >= max) {
                for (Display d : list) vanish(d, null, 6);
                return false;
            }
            for (int i = 0; i < list.size(); i++) {
                Display d = list.get(i);
                if (!d.isValid()) continue;
                org.bukkit.util.Vector v = dirs[i].clone()
                        .multiply(0.35).setY(dirs[i].getY() * 0.35 - 0.05 * t);
                d.teleport(d.getLocation().add(v));
            }
            return true;
        }
    }

    /** Полёт снаряда-предмета по дуге из A в B, финальный всплеск частиц. */
    public static void itemProjectile(Location from, Location to, Material item,
                                      Particle trail, Particle impact, int flightSteps) {
        if (JOBS.size() >= JOBS_CAP) return;
        World w = from.getWorld();
        ItemDisplay d = spawnItem(from, item, 0.5f, null);
        if (d == null) return;
        d.setBillboard(Display.Billboard.FIXED);
        org.bukkit.util.Vector v = to.toVector().subtract(from.toVector());
        JOBS.add(new FXJob() {
            int t = 0;
            @Override
            public boolean step() {
                t++;
                if (!d.isValid()) return false;
                double f = Math.min(1.0, t / (double) flightSteps);
                Location at = from.clone().add(v.clone().multiply(f))
                        .add(0, Math.sin(f * Math.PI) * 1.5, 0);
                d.teleport(at);
                if (trail != null) w.spawnParticle(trail, at, 2, 0.1, 0.1, 0.1, 0.01);
                if (f >= 1.0) {
                    if (impact != null) burst(to, impact, 24, 0.8);
                    vanish(d, null, 6);
                    return false;
                }
                return true;
            }
        });
    }

    /** Клетка-оболочка из блоков вокруг сущности (вспыхивает и тает). */
    public static void cage(Entity owner, BlockData data, float scale, int lifeSteps) {
        if (owner == null || owner.isDead()) return;
        UUID id = owner.getUniqueId();
        Location base = owner.getLocation();
        double[][] offs = {
                {0.65, 0.2, 0.65}, {-0.65, 0.2, 0.65}, {0.65, 0.2, -0.65},
                {-0.65, 0.2, -0.65}, {0.65, 1.3, 0.65}, {-0.65, 1.3, 0.65},
                {0.65, 1.3, -0.65}, {-0.65, 1.3, -0.65}, {0, 1.9, 0}};
        List<Display> list = new ArrayList<>();
        for (double[] o : offs) {
            Display d = spawnBlock(base.clone().add(o[0], o[1], o[2]), data, scale, id);
            if (d != null) list.add(d);
        }
        if (list.isEmpty()) return;
        JOBS.add(new FadeOutJob(list.toArray(new Display[0]), lifeSteps, id));
    }

    private static final class FadeOutJob implements FXJob {
        final Display[] arr;
        final UUID owner;
        int t = 0;
        final int max;

        FadeOutJob(Display[] arr, int max, UUID owner) {
            this.arr = arr;
            this.max = max;
            this.owner = owner;
        }

        @Override
        public boolean step() {
            t++;
            if (t >= max) {
                for (Display d : arr) vanish(d, owner, 8);
                return false;
            }
            return true;
        }
    }

    // ==================== автосостояния: визуалы 16 состояний ====================

    /**
     * ВЗРЫВ ОСКОЛКОВ — сигнатурный визуал GLASSED/BRITTLE.
     * Стеклянные блок-частицы + физичные осколки-ItemDisplay, разлетающиеся с гравитацией.
     * big=true — полное разбитие остеклённой цели, false — треск хрупкости.
     */
    public static void shatterFX(LivingEntity t, boolean big) {
        if (t == null || t.isDead()) return;
        Location c = t.getLocation().add(0, 1, 0);
        // стеклянная пыль: блок-частицы выглядят как настоящее разбитое стекло
        c.getWorld().spawnParticle(Particle.BLOCK, c, big ? 70 : 30,
                0.45, 0.65, 0.45, Material.GLASS.createBlockData());
        c.getWorld().spawnParticle(Particle.ITEM, c, big ? 14 : 8,
                0.4, 0.5, 0.4, 0.1, new ItemStack(Material.GLASS));
        // физичные осколки, летящие от цели (движок сам погасит и уберёт)
        itemBurst(c, Material.GLASS, big ? 9 : 5, big ? 1.0 : 0.7, big ? 16 : 12);
        // ударная волна трещины
        ringExpand(c, big ? 2.4 : 1.6, big ? 8 : 6, Particle.WAX_OFF, Color.fromRGB(0xF5F5F5), 0.9f);
        sound(c, Sound.BLOCK_GLASS_BREAK, 1f, big ? 0.8f : 1.3f);
        if (big) sound(c, Sound.BLOCK_GLASS_BREAK, 0.7f, 0.55f);
    }

    /** Наложить зрелищный визуал состояния (орбиты + вспышка) — вызывать при apply. */
    public static void stateApplied(LivingEntity t, State s) {
        if (t == null || t.isDead()) return;
        switch (s) {
            case CARBON_COATED -> {
                // Пример ТЗ: физичные угольные блоки без коллизии летают вокруг цели,
                // плавно исчезают при смыве; плюс эффект тотема с углём вместо тотема.
                stateOrbitBlocks(t, s, Material.COAL_BLOCK.createBlockData(),
                        7, 1.1, 1.0, 0.27f, 5.5, 130);
                totemFlash(t, Material.COAL, Particle.SMOKE,
                        Color.fromRGB(0x2B2B2B), 14, 0.7f);
                burst(t.getLocation().add(0, 1, 0), Particle.ASH, 26, 0.6);
            }
            case SOAKED -> {
                ringDust(t.getLocation(), 1.1, Color.fromRGB(0x64C8FF), 1.0f);
                burst(t.getLocation().add(0, 1, 0), Particle.SPLASH, 22, 0.5);
            }
            case FROSTED -> {
                stateOrbitBlocks(t, s, Material.PACKED_ICE.createBlockData(),
                        5, 1.0, 0.9, 0.2f, 4.5, 90);
                burst(t.getLocation().add(0, 1, 0), Particle.SNOWFLAKE, 20, 0.5);
            }
            case CHARGED -> {
                stateOrbitBlocks(t, s, Material.LIGHTNING_ROD.createBlockData(),
                        5, 1.0, 0.9, 0.18f, 7.5, 90);
                burst(t.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 18, 0.5);
            }
            case STICKY -> {
                stateOrbitBlocks(t, s, Material.HONEY_BLOCK.createBlockData(),
                        5, 1.0, 0.7, 0.17f, 4.0, 120);
                burst(t.getLocation().add(0, 1, 0), Particle.FALLING_HONEY, 14, 0.4);
            }
            case SPORED -> burst(t.getLocation().add(0, 1.2, 0),
                    Particle.SPORE_BLOSSOM_AIR, 18, 0.6);
            case BLOOMED -> {
                stateOrbitItems(t, s, Material.PINK_PETALS, 5, 0.9, 1.1, 0.35f, 5.0, 120);
                burst(t.getLocation().add(0, 1.3, 0), Particle.CHERRY_LEAVES, 16, 0.5);
            }
            case SOUL_MARKED -> {
                stateOrbitBlocks(t, s, Material.SOUL_LANTERN.createBlockData(),
                        4, 0.9, 1.0, 0.22f, 5.0, 120);
                spiral(t.getLocation(), 0.7, 2.2, 16, Particle.SOUL_FIRE_FLAME,
                        Color.fromRGB(0x4FD0C9), 0.9f);
            }
            case GILDED -> {
                stateOrbitItems(t, s, Material.GOLD_NUGGET, 6, 1.0, 1.0, 0.4f, 6.5, 180);
                totemFlash(t, Material.GOLD_INGOT, Particle.GLOW,
                        Color.fromRGB(0xFFD257), 12, 1.15f);
            }
            case GLASSED -> {
                // Остекление: стеклянные блоки-орбиты + тотем-вспышка стеклом.
                // Лор посоха: «бей — взорвутся осколки» (см. Synergy.onPhysicalHit).
                stateOrbitBlocks(t, s, Material.GLASS.createBlockData(),
                        6, 1.0, 0.85, 0.18f, 5.0, 120);
                totemFlash(t, Material.GLASS, Particle.WAX_OFF,
                        Color.fromRGB(0xF5F5F5), 12, 1.0f);
                burst(t.getLocation().add(0, 1, 0), Particle.WAX_OFF, 14, 0.5);
                sound(t.getLocation(), Sound.BLOCK_GLASS_PLACE, 0.9f, 0.7f);
            }
            case ECHO_TAGGED -> {
                stateOrbitItems(t, s, Material.AMETHYST_SHARD, 5, 0.95, 1.0, 0.4f, 6.0, 60);
                burst(t.getLocation().add(0, 1, 0), Particle.NOTE, 6, 0.5);
            }
            case NECROSIS -> {
                stateOrbitItems(t, s, Material.WITHER_ROSE, 4, 0.9, 0.6, 0.3f, 3.5, 48);
                burst(t.getLocation().add(0, 1, 0), Particle.SCULK_SOUL, 10, 0.5);
            }
            case FEAR -> burst(t.getLocation().add(0, 1.2, 0), Particle.SQUID_INK, 14, 0.6);
            case ROOTED -> {
                stateOrbitBlocks(t, s, Material.MANGROVE_ROOTS.createBlockData(),
                        5, 1.05, 0.4, 0.22f, 3.5, 60);
                burst(t.getLocation().add(0, 0.4, 0), Particle.COMPOSTER, 12, 0.5);
            }
            case ARCANE_MARK -> {
                stateOrbitItems(t, s, Material.LAPIS_LAZULI, 6, 1.05, 1.0, 0.4f, 6.0, 180);
                ringDust(t.getLocation(), 1.2, Color.fromRGB(0x2A5FDE), 1.2f);
                spiral(t.getLocation(), 0.8, 2.4, 18, Particle.ENCHANT, null, 0);
            }
            case BRITTLE -> {
                stateOrbitBlocks(t, s, Material.GLASS.createBlockData(),
                        4, 1.0, 0.9, 0.15f, 4.0, 48);
                burst(t.getLocation().add(0, 1, 0), Particle.ITEM_SNOWBALL, 12, 0.4);
            }
        }
    }

    /** Секундные акценты активных состояний (жизнь ауры). Дешёвые частицы. */
    public static void stateAccent(LivingEntity e) {
        if (e == null || !e.isValid() || e.isDead()) return;
        Location c = e.getLocation().add(0, 1, 0);
        if (State.CARBON_COATED.has(e)) {
            dust(c, Color.fromRGB(0x2B2B2B), 1.0f, 3, 0.5);
            c.getWorld().spawnParticle(Particle.SMOKE, c, 2, 0.4, 0.5, 0.4, 0.01);
        }
        if (State.SOAKED.has(e)) {
            c.getWorld().spawnParticle(Particle.DRIPPING_DRIPSTONE_WATER, c, 3,
                    0.4, 0.5, 0.4, 0);
        }
        if (State.FROSTED.has(e)) {
            c.getWorld().spawnParticle(Particle.SNOWFLAKE, c, 3, 0.4, 0.6, 0.4, 0.01);
        }
        if (State.CHARGED.has(e)) {
            c.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, c, 3, 0.4, 0.5, 0.4, 0.1);
        }
        if (State.STICKY.has(e)) {
            c.getWorld().spawnParticle(Particle.FALLING_HONEY, c, 2, 0.35, 0.4, 0.35, 0.01);
        }
        if (State.SPORED.has(e)) {
            c.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, c, 3, 0.5, 0.5, 0.5, 0.01);
        }
        if (State.BLOOMED.has(e)) {
            c.getWorld().spawnParticle(Particle.CHERRY_LEAVES, c.clone().add(0, 0.5, 0), 3,
                    0.4, 0.5, 0.4, 0.02);
        }
        if (State.SOUL_MARKED.has(e)) {
            c.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, c, 2, 0.35, 0.5, 0.35, 0.01);
        }
        if (State.GILDED.has(e)) {
            c.getWorld().spawnParticle(Particle.GLOW, c, 2, 0.4, 0.4, 0.4, 0);
        }
        if (State.GLASSED.has(e)) {
            dust(c, Color.fromRGB(0xDDEEFF), 0.8f, 2, 0.4);
        }
        if (State.ECHO_TAGGED.has(e)) {
            c.getWorld().spawnParticle(Particle.NOTE, c, 1, 0.4, 0.4, 0.4, 1);
        }
        if (State.NECROSIS.has(e)) {
            c.getWorld().spawnParticle(Particle.SCULK_SOUL, c, 2, 0.4, 0.4, 0.4, 0.01);
        }
        if (State.FEAR.has(e)) {
            c.getWorld().spawnParticle(Particle.SQUID_INK, c, 1, 0.4, 0.4, 0.4, 0.01);
        }
        if (State.ROOTED.has(e)) {
            dust(e.getLocation().add(0, 0.2, 0), Color.fromRGB(0x587B3A), 0.9f, 3, 0.4);
        }
        if (State.ARCANE_MARK.has(e)) {
            c.getWorld().spawnParticle(Particle.ENCHANT, c, 3, 0.4, 0.5, 0.4, 0.5);
        }
        if (State.BRITTLE.has(e)) {
            dust(c, Color.fromRGB(0xE8F4FF), 0.8f, 2, 0.4);
        }
    }

    // ==================== зональные и глобальные акценты ====================

    /** Мягкая разметка зон (мох/мёд/ритуал/обелиск) — вызывать раз в секунду. */
    public static void zoneRing(Location c, double radius, Color color) {
        if (c == null) return;
        boolean near = false;
        for (Player p : c.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(c) < 48 * 48) { near = true; break; }
        }
        if (!near) return;
        ringDust(c, radius, color, 1.0f);
    }

    /** Акцент вечного горения (раз в секунду). */
    public static void burnAccent(LivingEntity e) {
        if (e == null || !e.isValid() || e.isDead()) return;
        Location c = e.getLocation().add(0, 1, 0);
        c.getWorld().spawnParticle(Particle.FLAME, c, 4, 0.35, 0.5, 0.35, 0.01);
        c.getWorld().spawnParticle(Particle.LAVA, c, 1, 0.3, 0.3, 0.3, 0);
        if (Math.random() < 0.4) {
            c.getWorld().spawnParticle(Particle.ASH, c.clone().add(0, 0.6, 0), 6,
                    0.5, 0.6, 0.5, 0.01);
        }
    }

    /** Акцент угольной пыли вокруг эмиттера (вызывается из dustPulse). */
    public static void dustAccent(Player p, double radius) {
        Location c = p.getLocation().add(0, 1, 0);
        dust(c, Color.fromRGB(0x2B2B2B), 1.4f, 10, radius * 0.5);
        ringDust(p.getLocation(), radius * 0.8, Color.fromRGB(0x1A1A1A), 0.9f);
    }
}
