package ru.deepevents.events;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.deepevents.DeepEventsPlugin;
import ru.deepevents.core.AbstractEvent;
import ru.deepevents.core.ConfigManager;
import ru.deepevents.core.CraterMath;
import ru.deepevents.core.Fx;
import ru.deepevents.core.LocationFinder;
import ru.deepevents.core.Msg;
import ru.deepevents.core.RadiationZones;
import ru.deepevents.core.RestoreService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * ЯДЕРНЫЙ ВЗРЫВ v2 — кинематографичная последовательность:
 * сирена -> ВСПЫШКА (белая стена) -> ОГНЕННЫЙ ШАР (подъём с раскалённой
 * корой, искры, лава-капли) -> УДАРНАЯ ВОЛНА (плотное кольцо, отбрасывание)
 * -> СТРУКТУРНЫЙ ГРИБ (MushroomCloud v2) -> КРАТЕР (чаша, высокий вал,
 * апрон, лава-ядро) -> РАДИАЦИЯ -> ШАЛКЕР С ГРУЗОМ НА ДНЕ ВОРОНКИ
 * (постамент + световой луч-маркер, видимый издалека) -> ПАЛЬБА/ПЕПЕЛ.
 * Откат мира: /deepevents undo.
 */
public final class NukeEvent extends AbstractEvent {

    private final DeepEventsPlugin plugin;
    private final ConfigManager cfg;
    private final RestoreService restores;
    private final RadiationZones radiation;
    private final Random rnd = new Random();

    private Location target;
    private BukkitTask sirenTask;
    private BukkitTask shockTask;
    private BukkitTask fireballTask;
    private BukkitTask ashTask;
    private BukkitTask aftermathTask;
    private BukkitTask beamTask;
    private MushroomCloud cloud;
    private RestoreService.RestoreSession session;
    private boolean craterDone;
    private CraterGenerator generator;
    private int shockRadius;
    private boolean detonated;
    private final java.util.Set<java.util.UUID> shockHit = new java.util.HashSet<>();

    public NukeEvent(DeepEventsPlugin plugin, ConfigManager cfg, RestoreService restores, RadiationZones radiation) {
        super(plugin);
        this.plugin = plugin;
        this.cfg = cfg;
        this.restores = restores;
        this.radiation = radiation;
    }

    @Override
    public String displayName() {
        return "Ядерный взрыв";
    }

    @Override
    public void launch() {
        var world = Bukkit.getWorlds().stream().filter(w -> cfg.isEventWorld(w)).findFirst().orElse(null);
        if (world == null) {
            Msg.warn("Ядерный взрыв: нет мира из конфига.");
            plugin.events().clearActive(this);
            stop("нет мира");
            return;
        }
        Location origin = world.getSpawnLocation();
        if (!world.getPlayers().isEmpty()) {
            origin = world.getPlayers().get(rnd.nextInt(world.getPlayers().size())).getLocation();
        }
        Msg.broadcast("<red>Сирены воют на горизонте…</red>");
        Location from = origin.clone();
        LocationFinder.findLand(plugin, world, from, cfg.minDistanceFromPlayers(), cfg.minDistanceFromSpawn(),
                40, this::beginSiren,
                () -> {
                    Msg.broadcast("<gray>Воздух стих… точка удара не найдена (всё слишком близко к людям).</gray>");
                    plugin.events().clearActive(this);
                    stop("локация не найдена");
                });
    }

    private void beginSiren(Location loc) {
        if (isStopped()) return;
        this.target = loc;
        int seconds = cfg.nukeSirenSeconds();
        Msg.titleAll("<red><bold>ЯДЕРНАЯ ТРЕВОГА</bold></red>",
                "<gray>Координаты вспышки: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "</gray>",
                300, (seconds + 3) * 1000, 500);

        final int[] step = {0};
        sirenTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            step[0]++;
            // тревожный вой: низкий барабан + глиссандо колокольчика
            float pitch = 0.4f + 0.22f * (step[0] % 6);
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), "block.note_block.basedrum", 1.6f, pitch);
                if (step[0] % 2 == 0) {
                    p.playSound(p.getLocation(), "block.note_block.chime", 0.9f, pitch * 2f);
                }
            }
            if (step[0] % 3 == 0) {
                Msg.soundAll("ambient.underwater.loop", 0.8f, 0.5f);
            }
        }, 0L, 20L);

        once(seconds * 20L, this::detonate);
        Msg.log("Ядерный взрыв: точка удара " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()
                + ", сирена " + seconds + "с");
    }

    // =====================================================================
    // ДЕТОНАЦИЯ: вспышка -> огненный шар -> ударная волна -> гриб -> кратер
    // =====================================================================

    private void detonate() {
        if (isStopped()) return;
        if (sirenTask != null) sirenTask.cancel();
        detonated = true;
        World world = target.getWorld();
        Location base = target.clone(); // Y = поверхность земли

        // ---- 1. БЕЛАЯ ВСПЫШКА (момент 0) ----
        world.spawnParticle(Particle.FLASH, base.clone().add(0, 5, 0), 4, 2.5, 2.5, 2.5, 0, Color.WHITE);
        world.spawnParticle(Particle.FLASH, base.clone().add(0, 12, 0), 2, 3, 2, 3, 0, Color.WHITE);
        // белая стена света (тело вспышки)
        Fx.dust(world, base.clone().add(0, 6, 0), 260, 7, 7, 7, 0.1, Color.WHITE, 4.0f);
        Fx.dust(world, base.clone().add(0, 3, 0), 120, 5, 2, 5, 0.05, Color.fromRGB(255, 240, 200), 4.0f);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, base.clone().add(0, 1, 0), 4, 3, 1, 3, 0);
        world.spawnParticle(Particle.EXPLOSION, base.clone().add(0, 3, 0), 40, 8, 4, 8, 0.15);
        world.spawnParticle(Particle.ELECTRIC_SPARK, base.clone().add(0, 4, 0), 120, 6, 4, 6, 0.35);

        Msg.soundAll("entity.generic.explode", 4.0f, 0.5f);
        Msg.soundAll("entity.wither.spawn", 3.0f, 0.5f);
        Msg.soundAll("block.respawn_anchor.deplete", 2.5f, 0.7f);
        Msg.soundAll("entity.lightning_bolt.thunder", 3.0f, 0.6f);
        Msg.titleAll("<#ef4444><bold>ДЕТОНАЦИЯ</bold></#ef4444>", "<gray>Ударная волна!</gray>", 50, 900, 400);

        // мобы в эпицентре гибнут
        for (LivingEntity le : world.getNearbyLivingEntities(base, 30)) {
            if (le instanceof Player) continue;
            le.damage(1000);
        }

        // ---- 2. ОГНЕННЫЙ ШАР: подъём ~3.5 сек (каждый тик) ----
        // сфера огня поднимается и остывает; капли лавы сыплются вниз
        final int[] fb = {0};
        fireballTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            fb[0]++;
            int tt = fb[0];
            if (tt > 70 || !Fx.near(base, 200)) {
                if (fireballTask != null) fireballTask.cancel();
                return;
            }
            double k = Math.min(1.0, tt / 55.0);
            double h = 3 + 22 * (1 - Math.pow(1 - k, 1.8));      // высота центра шара
            double r = 9 - 4.5 * k;                               // радиус шара (сжимается)
            double cool = k;                                       // 0 = белый огонь, 1 = тёмный дым

            Location c = base.clone().add(0, h, 0);
            // огненная кора шара
            world.spawnParticle(Particle.FLAME, c, (int) (26 * (1 - cool * 0.5)), r * 0.8, r * 0.8, r * 0.8, 0.02);
            world.spawnParticle(Particle.SMALL_FLAME, c, (int) (20 * (1 - cool * 0.4)), r * 0.9, r * 0.9, r * 0.9, 0.05);
            // раскалённое ядро в первые секунды
            if (cool < 0.45) {
                Fx.dust(world, c, 14, r * 0.5, r * 0.5, r * 0.5, 0, Color.fromRGB(255, 200, 90), 4.0f);
            }
            // тёмный дым сверху (остывание)
            world.spawnParticle(Particle.LARGE_SMOKE, c.clone().add(0, r * 0.6, 0),
                    (int) (8 + 6 * cool), r * 0.7, r * 0.5, r * 0.7, 0.02);
            // капли лавы и искры вниз
            world.spawnParticle(Particle.LAVA, c, 3, r * 0.7, r * 0.6, r * 0.7, 0);
            world.spawnParticle(Particle.FALLING_LAVA, c.clone().add(0, r * 0.4, 0), 6, r * 0.8, 1, r * 0.8, 0);
            // огненное кольцо у земли первые 2 сек
            if (tt < 40) {
                Fx.shockRing(world, base, 6 + tt * 0.35, 22, Color.fromRGB(255, 120, 40), 3.4f, 1);
                world.spawnParticle(Particle.FLAME, base.clone().add(0, 1.5, 0), 12, 5, 1, 5, 0.04);
            }
        }, 1L, 1L);

        // ---- 3. УДАРНАЯ ВОЛНА ----
        shockRadius = 6;
        shockTask = Bukkit.getScheduler().runTaskTimer(plugin, this::shockTick, 2L, 1L);

        // ---- 4. ГРИБ (структурный, стартует сразу) ----
        cloud = new MushroomCloud(plugin, base);

        // ---- 5. КРАТЕР (через 15 тиков после вспышки) ----
        session = restores.beginSession(world, "nuke");
        session.setOrigin(base.clone());
        generator = new CraterGenerator(plugin, rnd.nextLong());
        once(15L, () -> generator.generate(base, cfg.nukeCraterRadius(), cfg.nukeCraterDepth(), session, this::onCraterDone));
    }

    private void shockTick() {
        World world = target.getWorld();
        double range = cfg.shockRange();
        if (shockRadius > range) {
            if (shockTask != null) shockTask.cancel();
            return;
        }
        if (Fx.near(target, range + 40)) {
            // плотное кольцо фронта: пыль по земле + редкие SONIC_BOOM + пар
            int pts = 26;
            for (int k = 0; k < pts; k++) {
                double a = Math.PI * 2 * k / pts + (shockRadius % 2) * 0.12;
                Location p = target.clone().add(Math.cos(a) * shockRadius, 0, Math.sin(a) * shockRadius);
                int y = world.getHighestBlockYAt(p.getBlockX(), p.getBlockZ());
                p.setY(y + 1.0);
                Fx.dust(world, p, 2, 0.35, 0.9, 0.35, 0.03, Color.fromRGB(180, 160, 130), 3.2f);
                if (k % 6 == 0) {
                    world.spawnParticle(Particle.SONIC_BOOM, p.clone().add(0, 0.4, 0), 1, 0, 0, 0, 0);
                }
                if (k % 3 == 0) {
                    world.spawnParticle(Particle.CLOUD, p.clone().add(0, 0.6, 0), 2, 0.4, 0.3, 0.4, 0.02);
                }
            }
        }
        // игроки на фронте волны
        for (Player p : world.getPlayers()) {
            double d = p.getLocation().toVector().setY(0).distance(target.toVector().setY(0));
            if (Math.abs(d - shockRadius) < 2.5 && d <= range) {
                if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                if (shockHit.contains(p.getUniqueId())) continue;
                shockHit.add(p.getUniqueId());
                double dmg = CraterMath.shockDamage(d, range, cfg.shockMaxDamage());
                double push = CraterMath.shockPush(d, range);
                Vector away = p.getLocation().toVector().subtract(target.toVector()).setY(0);
                if (away.lengthSquared() < 0.01) away = new Vector(0.5, 0, 0.5);
                away.normalize().multiply(push).setY(Math.min(0.9, push * 0.45));
                p.setVelocity(away);
                p.damage(dmg);
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, 60, 1, true, false, true));
                Msg.actionbar(p, "<#ef4444>Ударная волна сбила вас с ног!</#ef4444>");
                Msg.sound(p.getLocation(), "entity.generic.explode", 0.8f, 1.4f);
            }
        }
        // мобы на фронте
        for (LivingEntity le : world.getNearbyLivingEntities(target, shockRadius + 3)) {
            if (le instanceof Player) continue;
            double d = le.getLocation().toVector().setY(0).distance(target.toVector().setY(0));
            if (Math.abs(d - shockRadius) < 2.5) {
                le.damage(8);
                Vector away = le.getLocation().toVector().subtract(target.toVector()).setY(0);
                if (away.lengthSquared() > 0.01) le.setVelocity(away.normalize().multiply(1.2).setY(0.5));
            }
        }
        shockRadius += cfg.shockSpeed();
    }

    private void onCraterDone() {
        if (isStopped()) return;
        craterDone = true;
        World world = target.getWorld();
        int R = cfg.nukeCraterRadius();

        // проверка: footprint кратера не должен задевать защищённые регионы (8 периметровых точек)
        for (int k = 0; k < 8; k++) {
            double a = Math.PI * 2 * k / 8;
            Location edge = target.clone().add(Math.cos(a) * R, 0, Math.sin(a) * R);
            edge.setY(world.getHighestBlockYAt(edge.getBlockX(), edge.getBlockZ()));
            if (ru.deepevents.core.ProtectionAdapter.isProtected(edge)) {
                Msg.broadcast("<red>Взрыв оказался на защищённой территории — земля восстановлена.</red>");
                session.saveToDisk();
                session.restore();
                plugin.events().clearActive(this);
                stop("пересечение с регионом");
                return;
            }
        }

        // ---- ШАЛКЕР НА ДНЕ ВОРОНКИ (Y из генератора — heightmap после генерации врёт) ----
        int cx = target.getBlockX(), cz = target.getBlockZ();
        int floorY = generator.craterFloorY();
        if (floorY == Integer.MIN_VALUE || floorY <= world.getMinHeight()) {
            // фолбэк: поверхность минус глубина конфига
            floorY = Math.max(world.getMinHeight() + 1, target.getBlockY() - 1 - cfg.nukeCraterDepth());
        }

        Block pedestal = world.getBlockAt(cx, floorY, cz);
        session.capture(pedestal);
        pedestal.setType(Material.OBSIDIAN, false);
        Block shulker = world.getBlockAt(cx, floorY + 1, cz);
        session.capture(shulker);
        shulker.setType(Material.SHULKER_BOX, false);
        var state = shulker.getState();
        if (state instanceof ShulkerBox box) {
            box.customName(Msg.mm("<#ef4444><bold>Секретный груз</bold></#ef4444>"));
            var inv = box.getInventory();
            for (ItemStack item : rollNukeLoot()) {
                inv.addItem(item);
            }
            box.update();
        }
        // декор: обломки у постамента
        for (int[] d : new int[][]{{2, 0}, {-2, 1}, {1, -2}, {-1, -2}}) {
            Block wreck = world.getBlockAt(cx + d[0], floorY + 1, cz + d[1]);
            if (wreck.getType() == Material.AIR) {
                session.capture(wreck);
                wreck.setType(rnd.nextDouble() < 0.5 ? Material.IRON_BARS : Material.IRON_CHAIN, false);
            }
        }

        // ---- ЛУЧ-МАРКЕР ГРУЗА: столб света, видимый издалека (10 минут) ----
        Location beamBase = new Location(world, cx + 0.5, floorY + 2, cz + 0.5);
        final long[] beamTicks = {0};
        beamTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            beamTicks[0] += 40;
            if (beamTicks[0] > 12000) { // 10 минут
                if (beamTask != null) beamTask.cancel();
                return;
            }
            if (!Fx.near(beamBase, 140)) return;
            Fx.dustBeam(world, beamBase, 14 + 2 * Math.sin(beamTicks[0] * 0.1), 11,
                    Color.fromRGB(250, 204, 21), 3.0f);
            world.spawnParticle(Particle.GLOW, beamBase.clone(), 6, 0.8, 1.2, 0.8, 0.04);
            world.spawnParticle(Particle.END_ROD, beamBase.clone().add(0, 3, 0), 3, 0.5, 0.8, 0.5, 0.02);
        }, 60L, 40L);

        world.spawnParticle(Particle.GLOW, new Location(world, cx + 0.5, floorY + 2, cz + 0.5), 30, 1.5, 1.5, 1.5, 0.05);

        // радиационная зона: центр на дне воронки (2D-дистанция внутри RadiationZones)
        double fallout = R * cfg.nukeFalloutMult();
        radiation.add(new RadiationZones.Zone(new Location(world, cx + 0.5, floorY, cz + 0.5),
                R, fallout, cfg.radiationPermanent(), cfg.radiationEffects()));

        session.saveToDisk();

        Msg.broadcastRaw("<gradient:#ef4444:#facc15><bold>ЯДЕРНЫЙ ВЗРЫВ</bold></gradient><gray> — кратер сформирован.</gray>");
        Msg.broadcast("Координаты воронки: <aqua>" + cx + ", " + floorY + ", " + cz
                + "</aqua>. <gray>В центре на дне — <#facc15>Секретный груз</#facc15> (жёлтый луч света). Радиация!</gray>");
        Msg.log("Кратер готов: " + cx + " " + floorY + " " + cz
                + " R=" + R + " снапшот=" + session.size() + " блоков");

        // ---- ПОСЛЕСВИТИЕ: дымные столбы, угли, пар из воронки (5 минут) ----
        final int fY = floorY;
        final int[] am = {0};
        aftermathTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            am[0] += 30;
            if (am[0] > 6000) {
                if (aftermathTask != null) aftermathTask.cancel();
                return;
            }
            if (!Fx.near(target, 120)) return;
            // 3-4 дымных точки внутри воронки
            for (int k = 0; k < 4; k++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double rr = rnd.nextDouble() * R * 0.6;
                int hx = cx + (int) (Math.cos(a) * rr);
                int hz = cz + (int) (Math.sin(a) * rr);
                int hy = world.getHighestBlockYAt(hx, hz);
                Location hot = new Location(world, hx + 0.5, hy + 0.5, hz + 0.5);
                world.spawnParticle(Particle.LARGE_SMOKE, hot, 4, 0.5, 0.8, 0.5, 0.012);
                if (rnd.nextDouble() < 0.5) {
                    world.spawnParticle(Particle.SMALL_FLAME, hot, 3, 0.4, 0.4, 0.4, 0.01);
                }
                if (rnd.nextDouble() < 0.3) {
                    world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, hot, 1, 0.2, 0.2, 0.2, 0.004);
                }
            }
            // редкие угли над центром
            world.spawnParticle(Particle.LAVA, new Location(world, cx + 0.5, fY + 2.5, cz + 0.5),
                    2, 1.6, 0.8, 1.6, 0);
        }, 120L, 30L);

        // пепел над зоной выпадений на 5 минут (только близким игрокам)
        final int[] ashTicks = {0};
        ashTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ashTicks[0] += 30;
            if (ashTicks[0] > 6000) {
                if (ashTask != null) ashTask.cancel();
                return;
            }
            for (Player p : world.getPlayers()) {
                if (p.getLocation().distance(target) > fallout * 1.2) continue;
                world.spawnParticle(Particle.ASH, p.getLocation().add(0, 2, 0), 6, 3, 2, 3, 0.01);
                world.spawnParticle(Particle.WHITE_ASH, p.getLocation().add(0, 3, 0), 5, 4, 2, 4, 0.01);
            }
        }, 100L, 30L);

        // завершение ивента (кратер, луч и радиация остаются)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.events().clearActive(this);
            stop("завершён");
        }, 200L);
    }

    private List<ItemStack> rollNukeLoot() {
        List<ItemStack> out = new ArrayList<>();
        for (ConfigManager.LootEntry le : cfg.loot("nuke")) {
            if (rnd.nextDouble() * 100.0 >= le.chance()) continue;
            int count = le.min() + rnd.nextInt(Math.max(1, le.max() - le.min() + 1));
            int left = count;
            while (left > 0) {
                int stack = Math.min(le.material().getMaxStackSize(), left);
                out.add(new ItemStack(le.material(), stack));
                left -= stack;
            }
        }
        return out;
    }

    @Override
    protected void cleanup() {
        if (sirenTask != null) sirenTask.cancel();
        if (shockTask != null) shockTask.cancel();
        if (ashTask != null) ashTask.cancel();
        if (fireballTask != null) fireballTask.cancel();
        if (aftermathTask != null) aftermathTask.cancel();
        if (beamTask != null) beamTask.cancel();
        if (cloud != null) cloud.stop();
        if (generator != null) generator.cancel();
        // если взрыв не состоялся или кратер не достроен — спасти снапшот на диск и откатить
        if (session != null && !craterDone && detonated) {
            session.saveToDisk();
            session.restore();
        }
        if (target != null && detonated && craterDone) {
            target.getWorld().spawnParticle(Particle.LARGE_SMOKE, target.clone().add(0, 4, 0), 30, 3, 2, 3, 0.02);
        }
    }
}
