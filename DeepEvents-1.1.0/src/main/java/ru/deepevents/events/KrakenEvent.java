package ru.deepevents.events;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.ElderGuardian;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.World;
import ru.deepevents.DeepEventsPlugin;
import ru.deepevents.core.AbstractEvent;
import ru.deepevents.core.ConfigManager;
import ru.deepevents.core.Fx;
import ru.deepevents.core.GlobalListeners;
import ru.deepevents.core.LocationFinder;
import ru.deepevents.core.Msg;
import ru.deepevents.core.RestoreService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * КРАКЕН БЕЗДНЫ — кинематографичный босс-ивент.
 *
 * Цикл боя (не «приди и закликай»):
 *  1. Щит Бездны: Кракен неуязвим, пока живы 3 Аколита на алтарях вокруг арены.
 *  2. Убийство всех аколитов открывает ядро на vulnerable-seconds — окно урона.
 *  3. Параллельно: щупальца целит и бьёт слэмами, цунами-волны (физичные блоки
 *     воды без коллизии, по формуле бегущей волны) накрывают арену, водовороты
 *     затягивают, отродья отвлекают.
 *  4. Лут делится ПОРОВНУ между всеми, кто наносил урон боссу.
 */
public final class KrakenEvent extends AbstractEvent {

    private final DeepEventsPlugin plugin;
    private final ConfigManager cfg;
    private final Random rnd = new Random();

    private Location center;
    private ElderGuardian boss;
    private BossBar bossBar;

    private KrakenTentacles tentacles;
    private KrakenWaves waves;
    private KrakenBody body;
    private BukkitTask acolyteFxTask;

    private final List<Location> altars = new ArrayList<>();
    private final List<LivingEntity> acolytes = new ArrayList<>();
    private final List<LivingEntity> minions = new ArrayList<>();

    private final List<Whirlpool> whirlpools = new ArrayList<>();

    private final Map<UUID, Double> damageMap = new HashMap<>();
    private final java.util.Set<UUID> bossBarViewers = new java.util.HashSet<>();
    private RestoreService.RestoreSession arenaSession;

    private boolean shieldClosed = true;
    private int shieldOpenTicksLeft = 0;
    private int phase = 1;
    private long lastDamageAt;
    private long startedAt;
    private boolean despawnWarned;
    private boolean finished;
    private BukkitTask closeShieldTask;

    private final class Whirlpool {
        final Location center;
        int ticksLeft;

        Whirlpool(Location center) {
            this.center = center;
            this.ticksLeft = (int) (cfg.whirlLifetimeSeconds() * 20);
        }
    }

    public KrakenEvent(DeepEventsPlugin plugin, ConfigManager cfg) {
        super(plugin);
        this.plugin = plugin;
        this.cfg = cfg;
    }

    @Override
    public String displayName() {
        return "Кракен Бездны";
    }

    @Override
    public void launch() {
        // Точка отсчёта: случайный игрок в мире ивента, иначе спавн
        var worlds = Bukkit.getWorlds().stream()
                .filter(w -> cfg.isEventWorld(w)).toList();
        if (worlds.isEmpty()) {
            Msg.warn("Кракен: нет мира из конфига general.worlds.");
            plugin.events().clearActive(this);
            stop("нет мира");
            return;
        }
        var world = worlds.get(0);
        Location origin = world.getSpawnLocation();
        List<Player> inWorld = world.getPlayers();
        if (!inWorld.isEmpty()) origin = inWorld.get(rnd.nextInt(inWorld.size())).getLocation();

        Msg.broadcast("<dark_purple>Глубины дрожат… что-то огромное просыпается под водой…</dark_purple>");
        Msg.soundAll("ambient.underwater.loop", 1.5f, 0.4f);

        int radius = cfg.krakenSearchRadius();
        final Location from = origin.clone();
        LocationFinder.findOcean(plugin, world, from, radius, 8,
                this::setupArena,
                () -> {
                    Msg.broadcast("<gray>Океан отступил… Кракен ушёл в бездну (подходящая вода не найдена в радиусе "
                            + radius + " блоков).</gray>");
                    plugin.events().clearActive(this);
                    stop("океан не найден");
                });
    }

    private void setupArena(Location loc) {
        if (isStopped()) return;
        this.center = loc;
        var world = loc.getWorld();
        int surface = LocationFinder.waterSurface(world, loc.getBlockX(), loc.getBlockZ());
        center.setY(surface);

        // Сессия отката арены (алтари)
        arenaSession = plugin.restores().beginSession(world, "kraken_arena");

        buildAltars(world, surface);

        tentacles = new KrakenTentacles(plugin, cfg, this::slamImpact);
        tentacles.spawn(center, cfg.krakenTentacleCount());
        waves = new KrakenWaves(plugin, cfg);
        body = new KrakenBody(plugin, center);

        bossBar = BossBar.bossBar(Msg.mm("<gradient:#a855f7:#312e81><bold>КРАКЕН БЕЗДНЫ</bold></gradient> — щит Бездны активен"),
                1.0f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);

        int[] slamRange = cfg.krakenSlamInterval();
        int[] waveRange = cfg.waveInterval();
        nextWaveIn = waveRange[0] * 20 + rnd.nextInt(Math.max(1, (waveRange[1] - waveRange[0]) * 20));

        startedAt = world.getGameTime();
        lastDamageAt = startedAt;

        // Задачи (босс появится после интро — combatTick защищён boss==null)
        every(20L, 20L, this::combatTick);
        every(4L, 2L, () -> tentacles.tick());
        every(6L, 2L, () -> waves.tick());
        every(10L, 10L, this::whirlpoolTick);
        every(cfg.minionIntervalSeconds() * 20L, cfg.minionIntervalSeconds() * 20L, this::spawnMinions);
        // ---- КИНОШНЫЙ ВХОД: вода бурлит, волны расходятся, босс поднимается из бездны ----
        Msg.broadcastRaw("""
                <gradient:#a855f7:#5eead4><bold>            ☢ КРАКЕН ПРОБУДИЛСЯ ☢</bold></gradient>""");
        Msg.broadcast("Координаты арены: <aqua>" + center.getBlockX() + ", " + center.getBlockZ()
                + "</aqua>. <gray>Снимите 3 Аколита Бездны, чтобы открыть ядро!</gray>");
        Msg.titleAll("<gradient:#a855f7:#312e81><bold>КРАКЕН БЕЗДНЫ</bold></gradient>",
                "<gray>Океан вспух…</gray>", 300, 2500, 700);
        Msg.soundAll("entity.ender_dragon.growl", 1.0f, 0.45f);
        Msg.log("Кракен: интро на " + center.getBlockX() + " " + center.getBlockY() + " " + center.getBlockZ());

        // водоворот пузырей в центре + расходящиеся волны + водяной столб
        world.spawnParticle(Particle.BUBBLE_COLUMN_UP, center.clone(), 200, 2.5, 3.0, 2.5, 0.08);
        world.spawnParticle(Particle.NAUTILUS, center.clone(), 120, 3.0, 2.0, 3.0, 0.4);
        world.spawnParticle(Particle.GUST_EMITTER_LARGE, center.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
        // водяной столб: поднимающаяся струя (12 секунд интро)
        final int[] spout = {0};
        every(2L, 2L, () -> {
            spout[0] += 2;
            if (spout[0] > 240) return;
            double k = Math.min(1.0, spout[0] / 160.0);
            double h = 26 * k;
            if (!Fx.near(center, 120)) return;
            for (int i = 0; i < 6; i++) {
                double frac = (double) i / 6;
                Fx.dust(world, center.clone().add(0, h * frac, 0), 2, 1.1 * (1 - frac * 0.6), 0.5, 1.1 * (1 - frac * 0.6), 0.05,
                        org.bukkit.Color.fromRGB(96, 165, 250), 3.6f);
            }
            world.spawnParticle(Particle.BUBBLE_COLUMN_UP, center.clone().add(0, h * 0.6, 0), 8, 1.4, h / 3, 1.4, 0.06);
            world.spawnParticle(Particle.SPLASH, center.clone().add(0, h, 0), 6, 1.2, 0.6, 1.2, 0.05);
        });
        for (int k = 0; k < 3; k++) {
            double ang = Math.PI * 2 * k / 3;
            waves.spawnWave(center.clone(), new Vector(Math.cos(ang), 0, Math.sin(ang)));
        }
        for (int k = 0; k < 4; k++) {
            final int kk = k;
            once(kk * 15L, () -> Msg.soundNear(center, 96, "entity.guardian.attack", 1.4f, 0.4f + kk * 0.15f));
        }

        // босс поднимается из бездны через 3 секунды
        once(60L, () -> {
            if (isStopped()) return;
            spawnBoss(world, surface);
            spawnAcolytes();
            for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(bossBar);
            world.strikeLightningEffect(center);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0, 1, 0), 2, 1.5, 2, 1.5, 0);
            world.spawnParticle(Particle.END_ROD, center.clone().add(0, 2, 0), 100, 2.5, 2.5, 2.5, 0.25);
            world.spawnParticle(Particle.SPLASH, center.clone().add(0, 0.5, 0), 160, 3.5, 1, 3.5, 0.1);
            Msg.soundAll("entity.wither.spawn", 1.2f, 1.4f);
            Msg.titleAll("<gradient:#a855f7:#312e81><bold>КРАКЕН БЕЗДНЫ</bold></gradient>",
                    "<gray>Щит держат Аколиты…</gray>", 200, 1500, 500);
            Msg.log("Кракен заспавнен: " + center.getBlockX() + " " + center.getBlockY() + " " + center.getBlockZ());
        });
    }

    private void buildAltars(org.bukkit.World world, int surface) {
        int count = Math.max(1, cfg.krakenAcolytes());
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * i / count;
            int ax = center.getBlockX() + (int) (Math.cos(angle) * 22);
            int az = center.getBlockZ() + (int) (Math.sin(angle) * 22);
            int ay = LocationFinder.waterSurface(world, ax, az);
            Location altar = new Location(world, ax + 0.5, ay + 1, az + 0.5);
            altars.add(altar);
            // платформа 3x3: центр — морской фонарь, кольцо — призмарин
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    var b = world.getBlockAt(ax + dx, ay, az + dz);
                    arenaSession.capture(b);
                    b.setType(dx == 0 && dz == 0 ? Material.SEA_LANTERN : Material.PRISMARINE_BRICKS, false);
                }
            }
        }
    }

    private void spawnBoss(org.bukkit.World world, int surface) {
        // босс внутри головы Кракена: не уплывает (AI off, гравитация off)
        Location bossLoc = center.clone().add(0, 1.5, 0);
        boss = world.spawn(bossLoc, ElderGuardian.class, g -> {
            g.setAI(false);
            g.setGravity(false);
            g.setSilent(true);
            g.setCustomNameVisible(true);
            g.setPersistent(true);
            g.setRemoveWhenFarAway(false);
            g.customName(Msg.mm("<gradient:#a855f7:#312e81><bold>КРАКЕН БЕЗДНЫ</bold></gradient>"));
            var attr = g.getAttribute(org.bukkit.attribute.Attribute.SCALE);
            if (attr != null) attr.setBaseValue(cfg.krakenScale());
            var hp = g.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (hp != null) hp.setBaseValue(cfg.krakenHp());
            var kb = g.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE);
            if (kb != null) kb.setBaseValue(1.0);
            var fr = g.getAttribute(org.bukkit.attribute.Attribute.FOLLOW_RANGE);
            if (fr != null) fr.setBaseValue(64);
            g.setHealth(cfg.krakenHp());
        });
        track(boss);
    }

    private void spawnAcolytes() {
        acolytes.clear();
        for (Location altar : altars) {
            var world = altar.getWorld();
            Drowned d = world.spawn(altar.clone(), Drowned.class, dr -> {
                // ФИКС: аколиты больше не уплывают/не разбегаются с алтарей:
                // AI выключен, отбрасывание погашено, позиция якорится
                dr.setAI(false);
                dr.setGravity(false);
                dr.setRemoveWhenFarAway(false);
                dr.setPersistent(false);
                dr.setCustomNameVisible(true);
                dr.customName(Msg.mm("<gradient:#5eead4:#0ea5e9><bold>Аколит Бездны</bold></gradient>"));
                dr.getEquipment().setItemInMainHand(new ItemStack(Material.TRIDENT));
                var hp = dr.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (hp != null) hp.setBaseValue(cfg.krakenAcolyteHp());
                dr.setHealth(cfg.krakenAcolyteHp());
                dr.setGlowing(true);
                var scale = dr.getAttribute(org.bukkit.attribute.Attribute.SCALE);
                if (scale != null) scale.setBaseValue(1.15);
                var kb = dr.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE);
                if (kb != null) kb.setBaseValue(0.85);
            });
            track(d);
            acolytes.add(d);
        }
        startAcolyteRitualFx();
        Msg.broadcast("Аколиты Бездны заняли алтары: <aqua>" + acolytes.size() + "</aqua>. Уничтожьте их!");
    }

    /** Ритуальные круги + световые столбы над живыми аколитами (каждые 8 тиков). */
    private void startAcolyteRitualFx() {
        if (acolyteFxTask != null) acolyteFxTask.cancel();
        acolyteFxTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (acolytes.isEmpty() || !Fx.near(center, 128)) return;
            for (LivingEntity a : acolytes) {
                if (!a.isValid()) continue;
                Location loc = a.getLocation();
                World w = loc.getWorld();
                double t = (w.getGameTime() % 2000) * 0.12;
                // вращающийся ритуальный круг под ногами
                for (int k = 0; k < 10; k++) {
                    double ang = t + Math.PI * 2 * k / 10;
                    Fx.dust(w, loc.clone().add(Math.cos(ang) * 1.7, 0.25, Math.sin(ang) * 1.7),
                            1, 0.03, 0.03, 0.03, 0, org.bukkit.Color.fromRGB(168, 85, 247), 3.0f);
                }
                // световой столб над аколитом (виден издалека)
                Fx.dustBeam(w, loc.clone().add(0, 1.8, 0), 7.5, 6, org.bukkit.Color.fromRGB(168, 85, 247), 2.2f);
                w.spawnParticle(Particle.BUBBLE_COLUMN_UP, loc.clone().add(0, 0.5, 0), 3, 0.5, 0.4, 0.5, 0.02);
                // якорь: если аколит сместился — вернуть на алтарь
            }
        }, 20L, 8L);
        // якорение позиций — отдельная дешёвая задача (каждые 10 тиков)
        every(10L, 10L, () -> {
            for (int i = 0; i < acolytes.size() && i < altars.size(); i++) {
                LivingEntity a = acolytes.get(i);
                Location altar = altars.get(i);
                if (!a.isValid()) continue;
                Location al = a.getLocation();
                if (al.distanceSquared(altar) > 2.25) {
                    a.teleport(altar);
                }
            }
        });
    }

    // ---------- бой ----------

    /** Урон по боссу/аколитам (маршрутизация из GlobalListeners). */
    public void handleDamage(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        if (victim.equals(boss)) {
            if (shieldClosed) {
                e.setCancelled(true);
                // реакция щита
            if (e.getDamager() instanceof Player p) {
                    Msg.actionbar(p, "<#a855f7>Щит Бездны поглощает урон! Убейте Аколитов.</#a855f7>");
                }
                ru.deepevents.core.Fx.dust(victim.getWorld(), victim.getLocation().add(0, 1.5, 0),
                        14, 1.6, 1.2, 1.6, 0, Color.fromRGB(168, 85, 247), 1.4f);
                return;
            }
            Player attacker = GlobalListeners.shooter(e.getDamager());
            if (attacker != null) {
                damageMap.merge(attacker.getUniqueId(), e.getFinalDamage(), Double::sum);
                lastDamageAt = victim.getWorld().getGameTime();
            }
            return;
        }
        if (acolytes.contains(victim)) {
            Player attacker = GlobalListeners.shooter(e.getDamager());
            if (attacker != null) {
                damageMap.computeIfAbsent(attacker.getUniqueId(), k -> 0.0);
            }
        }
    }

    /** Смерть аколита или босса (маршрутизация из GlobalListeners). */
    public void handleDeath(EntityDeathEvent e) {
        Entity dead = e.getEntity();
        if (dead.equals(boss)) {
            onBossKilled(e);
            return;
        }
        if (acolytes.contains(dead)) {
            acolytes.remove(dead);
            Msg.broadcast("<#5eead4>Аколит Бездны повержен! Щит слабеет: <b>"
                    + (cfg.krakenAcolytes() - acolytes.size()) + "/" + cfg.krakenAcolytes() + "</b></#5eead4>");
            Msg.sound(center, "block.beacon.deactivate", 1.4f, 0.8f);
            center.getWorld().strikeLightningEffect(dead.getLocation());
            if (acolytes.isEmpty() && shieldClosed) {
                openShield();
            }
        }
    }

    private void openShield() {
        shieldClosed = false;
        shieldOpenTicksLeft = cfg.krakenVulnerableSeconds() * 20;
        bossBar.name(Msg.mm("<gradient:#5eead4:#22c55e><bold>ЯДРО КРАКЕНА ОТКРЫТО!</bold></gradient> — бейте!"));
        bossBar.color(BossBar.Color.GREEN);
        Msg.broadcast("<green><bold>ЩИТ ПАЛ!</bold></green> Ядро Кракена открыто на <b>" + cfg.krakenVulnerableSeconds()
                + " сек.</b> — уничтожьте его!");
        Msg.titleAll("<green><bold>ЯДРО ОТКРЫТО</bold></green>", "<gray>Бейте по Кракену!</gray>", 100, 1200, 300);
        Msg.soundAll("entity.wither.spawn", 1.0f, 1.6f);
        if (body != null) body.setCoreOpen(true);
        if (boss != null && boss.isValid()) {
            center.getWorld().spawnParticle(Particle.END_ROD, center.clone().add(0, 2, 0), 80, 2.5, 2, 2.5, 0.15);
            center.getWorld().spawnParticle(Particle.GUST_EMITTER_SMALL, center.clone().add(0, 2, 0), 1, 0, 0, 0, 0);
        }
        if (closeShieldTask != null) closeShieldTask.cancel();
        closeShieldTask = Bukkit.getScheduler().runTaskLater(plugin, this::closeShield, shieldOpenTicksLeft);
    }

    private void closeShield() {
        if (isStopped() || finished || !shieldClosed) return;
        shieldClosed = true;
        bossBar.name(Msg.mm("<gradient:#a855f7:#312e81><bold>КРАКЕН БЕЗДНЫ</bold></gradient> — щит восстановлен"));
        bossBar.color(BossBar.Color.PURPLE);
        Msg.broadcast("<#a855f7>Кракен восстановил Щит Бездны. Новые Аколиты занимают алтары…</#a855f7>");
        if (body != null) body.setCoreOpen(false);
        spawnAcolytes();
    }

    private void slamImpact(Location loc) {
        double damage = cfg.krakenSlamDamage();
        double radius = cfg.krakenSlamRadius();
        for (Player p : loc.getWorld().getNearbyPlayers(loc, radius)) {
            if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            p.damage(damage);
            p.setVelocity(new Vector(0, 0.75, 0));
            Msg.actionbar(p, "<red>Щупальце Кракена обрушилось на вас!</red>");
        }
    }

    private void whirlpoolTick() {
        if (phase < 2 || center == null) return;
        // спавн новых
        if (rnd.nextInt(100) < 18) {
            Player p = randomTarget();
            if (p != null && p.getWorld().equals(center.getWorld())
                    && p.getLocation().distanceSquared(center) < 64 * 64) {
                Location wl = p.getLocation().clone();
                whirlpools.add(new Whirlpool(wl));
                Msg.soundNear(wl, 64, "block.conduit.ambient", 1.2f, 0.6f);
                Msg.broadcastNear(wl, 64, "<#60a5fa>Водоворот всасывает воду — расходимся!</#60a5fa>");
            }
        }
        // тик существующих
        List<Whirlpool> dead = new ArrayList<>();
        for (Whirlpool wp : whirlpools) {
            wp.ticksLeft -= 10;
            if (wp.ticksLeft <= 0) {
                dead.add(wp);
                continue;
            }
            double radius = cfg.whirlRadius();
            for (Player p : wp.center.getWorld().getNearbyPlayers(wp.center, radius)) {
                if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                Vector toCenter = wp.center.clone().subtract(p.getLocation()).toVector().setY(0);
                if (toCenter.lengthSquared() < 0.04) {
                    p.damage(cfg.whirlDamage());
                    p.setVelocity(new Vector(rnd.nextDouble() - 0.5, 0.4, rnd.nextDouble() - 0.5).multiply(0.8));
                } else {
                    p.setVelocity(toCenter.normalize().multiply(0.45).setY(0.08));
                }
            }
            // спираль частиц
            for (int a = 0; a < 3; a++) {
                double ang = (wp.ticksLeft * 0.25) + a * (Math.PI * 2 / 3);
                double r = 1.5 + (wp.ticksLeft % 20) * 0.2;
                Location pp = wp.center.clone().add(Math.cos(ang) * r, (wp.ticksLeft % 14) * 0.12, Math.sin(ang) * r);
                wp.center.getWorld().spawnParticle(Particle.NAUTILUS, pp, 2, 0, 0, 0, 0);
                wp.center.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, pp, 2, 0.05, 0.05, 0.05, 0.01);
            }
        }
        whirlpools.removeAll(dead);
    }

    private void spawnMinions() {
        if (boss == null || !boss.isValid()) return;
        minions.removeIf(m -> !m.isValid());
        if (minions.size() >= cfg.minionCap()) return;
        for (int i = 0; i < cfg.minionGroupSize(); i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            Location ml = center.clone().add(Math.cos(ang) * 8, 0, Math.sin(ang) * 8);
            Guardian g = center.getWorld().spawn(ml, Guardian.class, gd -> {
                gd.setCustomNameVisible(true);
                gd.customName(Msg.mm("<dark_purple>Отродье Бездны</dark_purple>"));
                gd.setRemoveWhenFarAway(true);
                gd.setPersistent(false);
            });
            track(g);
            minions.add(g);
        }
    }

    private long nextWaveIn = 0;

    private void combatTick() {
        if (boss == null || !boss.isValid()) return;
        var world = center.getWorld();
        long now = world.getGameTime();

        // боссбар: показываем только тем, кто рядом (антиспам для дальних)
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean near = p.getWorld().equals(world) && p.getLocation().distanceSquared(center) < 80 * 80;
            boolean viewing = bossBarViewers.contains(p.getUniqueId());
            if (near && !viewing) {
                p.showBossBar(bossBar);
                bossBarViewers.add(p.getUniqueId());
            } else if (!near && viewing) {
                p.hideBossBar(bossBar);
                bossBarViewers.remove(p.getUniqueId());
            }
        }

        // боссбар
        double hpFrac = Math.max(0, boss.getHealth() / boss.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        bossBar.progress((float) Math.min(1.0, hpFrac));

        // регенерация под щитом
        if (shieldClosed) {
            double maxHp = boss.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            boss.setHealth(Math.min(maxHp, boss.getHealth() + cfg.krakenRegenPerSecond()));
            // пузырь щита
            for (int a = 0; a < 4; a++) {
                double ang = now * 0.08 + a * Math.PI / 2;
                Location sp = center.clone().add(Math.cos(ang) * 3.2, 1 + Math.sin(now * 0.05 + a) * 1.2, Math.sin(ang) * 3.2);
                ru.deepevents.core.Fx.dust(world, sp, 2, 0, 0, 0, 0, Color.fromRGB(168, 85, 247), 1.3f);
            }
        } else if (shieldOpenTicksLeft > 0) {
            shieldOpenTicksLeft -= 20;
            if (shieldOpenTicksLeft == 60) {
                Msg.broadcast("<yellow>Ядро закрывается через 3 секунды!</yellow>");
            }
        }

        // фазы
        int newPhase = hpFrac > 0.66 ? 1 : (hpFrac > 0.33 ? 2 : 3);
        if (newPhase != phase) {
            phase = newPhase;
            if (phase == 2) {
                Msg.broadcast("<#a855f7><bold>Кракен в ярости!</bold> Появляются водовороты…</#a855f7>");
                Msg.soundAll("entity.elder_guardian.curse", 1.2f, 0.7f);
            } else if (phase == 3) {
                Msg.broadcast("<red><bold>Кракен в отчаянии!</bold> Волны бьют чаще!</red>");
                Msg.soundAll("entity.elder_guardian.curse", 1.4f, 0.5f);
                if (tentacles != null) tentacles.boostCooldowns(0.6);
            }
        }

        // волны по расписанию
        if (--nextWaveIn <= 0) {
            Player target = randomTarget();
            Vector dir;
            if (target != null && target.getWorld().equals(center.getWorld())) {
                dir = target.getLocation().toVector().subtract(center.toVector());
            } else {
                dir = new Vector(rnd.nextDouble() - 0.5, 0, rnd.nextDouble() - 0.5);
            }
            waves.spawnWave(center.clone(), dir);
            Msg.broadcastNear(center, 96, "<#60a5fa><bold>ЦУНАМИ!</bold> Волна Бездны накрывает арену — ныряйте под гребень!</#60a5fa>");
            Msg.soundNear(center, 96, "entity.player.splash", 2.0f, 0.6f);
            int[] range = cfg.waveInterval();
            double mult = phase >= 3 ? 0.6 : 1.0;
            nextWaveIn = (int) ((range[0] * 20 + rnd.nextInt(Math.max(1, (range[1] - range[0]) * 20))) * mult);
        }

        // отвлечение и уход
        if (now - lastDamageAt > cfg.krakenDisengageSeconds() * 20L) {
            boolean anyoneNear = center.getWorld().getPlayers().stream()
                    .anyMatch(p -> p.getLocation().distanceSquared(center) < 48 * 48);
            if (!anyoneNear) {
                Msg.broadcast("<gray>Кракен потерял интерес и ушёл в бездну…</gray>");
                plugin.events().clearActive(this);
                stop("потерял интерес");
                return;
            }
        }
        long age = now - startedAt;
        if (age > (cfg.krakenDespawnSeconds() - 60) * 20L && !despawnWarned) {
            despawnWarned = true;
            Msg.broadcast("<gray>Воды успокаиваются. У Кракена осталась минута!</gray>");
        }
        if (age > cfg.krakenDespawnSeconds() * 20L) {
            Msg.broadcast("<gray>Кракен скрылся в глубинах.</gray>");
            plugin.events().clearActive(this);
            stop("время вышло");
        }
    }

    private Player randomTarget() {
        List<Player> candidates = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (center != null && p.getWorld().equals(center.getWorld())
                    && p.getLocation().distanceSquared(center) < 64 * 64
                    && p.getGameMode() != org.bukkit.GameMode.CREATIVE
                    && p.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                candidates.add(p);
            }
        }
        return candidates.isEmpty() ? null : candidates.get(rnd.nextInt(candidates.size()));
    }

    // ---------- смерть босса и лут ----------

    private void onBossKilled(EntityDeathEvent e) {
        if (finished) return;
        finished = true;
        if (closeShieldTask != null) closeShieldTask.cancel();

        // киношный финал: тело распадается, взрывы, столб света
        var world = center.getWorld();
        if (body != null) {
            body.setCoreOpen(true);
        }
        for (int i = 0; i < 3; i++) {
            final int k = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isStopped()) return;
                world.spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(
                        rnd.nextDouble() * 4 - 2, 1 + k * 1.4, rnd.nextDouble() * 4 - 2), 2, 0.2, 0.2, 0.2, 0);
                world.spawnParticle(Particle.END_ROD, center.clone().add(0, 1 + k, 0), 60, 2.5, 1.5, 2.5, 0.2);
                world.spawnParticle(Particle.NAUTILUS, center.clone().add(0, 1 + k, 0), 40, 3, 1.5, 3, 0.3);
                world.spawnParticle(Particle.SPLASH, center.clone().add(0, 0.5, 0), 80, 3, 0.8, 3, 0.12);
                Msg.soundAll("entity.generic.explode", 1.4f, 0.5f + k * 0.2f);
            }, 4L + i * 8L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isStopped()) return;
            world.spawnParticle(Particle.GUST_EMITTER_LARGE, center.clone().add(0, 1.5, 0), 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.FLASH, center.clone().add(0, 3, 0), 2, 1.5, 1.5, 1.5, 0, Color.WHITE);
        }, 26L);
        Msg.titleAll("<green><bold>КРАКЕН ПОВЕРЖЕН!</bold></green>", "<gray>Бездна отступает…</gray>", 200, 2500, 800);
        Msg.soundAll("ui.toast.challenge_complete", 1.0f, 1.0f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            world.strikeLightningEffect(center);
        }, 6L);

        distributeLoot();

        bossBar.name(Msg.mm("<green><bold>КРАКЕН ПОВЕРЖЕН!</bold></green>"));
        bossBar.progress(1.0f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bossBar);
            plugin.events().clearActive(this);
            stop("повержен");
        }, 100L);
    }

    /** Лут делится РАВНОМЕРНО между всеми, кто нанёс урон (требование ТЗ).
     *  Весь пул перемешивается и раздаётся по одному предмету round-robin. */
    private void distributeLoot() {
        List<UUID> contributors = new ArrayList<>();
        for (var entry : damageMap.entrySet()) {
            if (entry.getValue() > 0 && Bukkit.getPlayer(entry.getKey()) != null) contributors.add(entry.getKey());
        }
        if (contributors.isEmpty()) {
            Msg.broadcast("<gray>Никто не повредил Кракену — добыча ушла в бездну.</gray>");
            return;
        }

        // броски пула по таблице: одна единица = один ItemStack
        var table = cfg.loot("kraken");
        List<ItemStack> pool = new ArrayList<>();
        for (ConfigManager.LootEntry le : table) {
            if (rnd.nextDouble() * 100.0 >= le.chance()) continue;
            int count = le.min() + rnd.nextInt(Math.max(1, le.max() - le.min() + 1));
            for (int i = 0; i < count; i++) {
                pool.add(le.material() == Material.ENCHANTED_BOOK ? randomEnchantedBook()
                        : new ItemStack(le.material()));
            }
        }
        java.util.Collections.shuffle(pool, rnd);

        // round-robin по очереди: каждый наносивший урон получает равную долю
        int n = contributors.size();
        java.util.Map<UUID, java.util.Map<Material, Integer>> haul = new HashMap<>();
        for (int i = 0; i < pool.size(); i++) {
            UUID who = contributors.get(i % n);
            Player p = Bukkit.getPlayer(who);
            if (p == null) continue; // вышел во время финала — его доля сгорает честно
            ItemStack item = pool.get(i);
            var overflow = p.getInventory().addItem(item);
            for (var rest : overflow.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), rest);
            }
            haul.computeIfAbsent(who, k -> new HashMap<>())
                    .merge(item.getType(), 1, Integer::sum);
            p.giveExp(60 + rnd.nextInt(80));
        }
        // компактная сводка каждому
        for (var e : haul.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            StringBuilder sb = new StringBuilder();
            for (var me : e.getValue().entrySet()) {
                if (sb.length() > 0) sb.append("<gray>, </gray>");
                sb.append("<aqua>").append(ru(minecraftName(me.getKey())))
                        .append(" ×").append(me.getValue()).append("</aqua>");
            }
            p.sendMessage(Msg.prefixed("<gray>Ваша добыча:</gray> " + sb));
        }

        // трофей — случайному из наносивших урон
        Player trophyOwner = Bukkit.getPlayer(contributors.get(rnd.nextInt(contributors.size())));
        if (trophyOwner != null && cfg.trophyMaterial() != null) {
            ItemStack trophy = new ItemStack(cfg.trophyMaterial());
            ItemMeta meta = trophy.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§d" + cfg.trophyName());
                meta.setLore(List.of("§7Трофей за победу над Кракеном Бездны", "§8DeepEvents 1.0"));
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                trophy.setItemMeta(meta);
            }
            var overflow = trophyOwner.getInventory().addItem(trophy);
            for (var rest : overflow.values()) trophyOwner.getWorld().dropItemNaturally(trophyOwner.getLocation(), rest);
            Msg.broadcast("<#facc15>" + trophyOwner.getName() + " получает трофей: " + cfg.trophyName() + "!</#facc15>");
        }

        Msg.broadcast("<green>Добыча (" + pool.size() + " предметов) разделена ПОРОВНУ между <b>" + n
                + "</b> охотник(ами), наносившими урон Кракену.</green>");
    }

    private String minecraftName(Material m) {
        return m.getKey().getKey(); // например heart_of_the_sea
    }

    private String ru(String key) {
        return switch (key) {
            case "heart_of_the_sea" -> "Сердце моря";
            case "nautilus_shell" -> "Раковина наутилуса";
            case "diamond" -> "Алмаз";
            case "emerald" -> "Изумруд";
            case "gold_ingot" -> "Золотой слиток";
            case "prismarine_crystals" -> "Кристаллы призмарина";
            case "experience_bottle" -> "Бутылек опыта";
            case "enchanted_book" -> "Зачарованная книга";
            case "trident" -> "Трезубец";
            default -> key;
        };
    }

    private ItemStack randomEnchantedBook() {
        Enchantment[] pool = {
                Enchantment.IMPALING, Enchantment.LOYALTY, Enchantment.RIPTIDE, Enchantment.LUCK_OF_THE_SEA,
                Enchantment.LURE, Enchantment.RESPIRATION, Enchantment.AQUA_AFFINITY, Enchantment.DEPTH_STRIDER,
                Enchantment.UNBREAKING, Enchantment.THORNS, Enchantment.MENDING};
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        if (meta != null) {
            Enchantment ench = pool[rnd.nextInt(pool.length)];
            int lvl = ench.getMaxLevel() > 1 ? 1 + rnd.nextInt(ench.getMaxLevel()) : 1;
            meta.addStoredEnchant(ench, lvl, true);
            book.setItemMeta(meta);
        }
        return book;
    }

    private void give(Player p, ItemStack item, boolean announce) {
        var overflow = p.getInventory().addItem(item);
        for (var rest : overflow.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
        if (announce) {
            p.sendMessage(Msg.prefixed("<gray>Добыча:</gray> <aqua>" + item.getType().name().toLowerCase()
                    + " ×" + item.getAmount() + "</aqua>"));
        }
    }

    // ---------- очистка ----------

    @Override
    protected void cleanup() {
        if (acolyteFxTask != null) acolyteFxTask.cancel();
        if (tentacles != null) tentacles.stop();
        if (waves != null) waves.stop();
        if (body != null) body.stop();
        if (bossBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bossBar);
            bossBarViewers.clear();
        }
        if (closeShieldTask != null) closeShieldTask.cancel();
        whirlpools.clear();
        damageMap.clear();
        if (arenaSession != null) arenaSession.restore();
        // вьюер для звука центра
        if (center != null && !finished) {
            center.getWorld().spawnParticle(Particle.POOF, center.clone(), 40, 2, 1.5, 2, 0.05);
        }
    }
}
