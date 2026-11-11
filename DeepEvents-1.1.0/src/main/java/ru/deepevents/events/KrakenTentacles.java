package ru.deepevents.events;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.deepevents.core.ConfigManager;
import ru.deepevents.core.Fx;
import ru.deepevents.core.Msg;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Щупальца Кракена: анимированные колонны BlockDisplay (без коллизии),
 * плавно колышутся, затем целятся в игрока (красное кольцо-предупреждение)
 * и бьют слэмом: урон в радиусе, подброс, частицы удара.
 */
public final class KrakenTentacles {

    private static final int SEGMENTS = 7;

    private static final class Tentacle {
        BlockDisplay[] segs = new BlockDisplay[SEGMENTS];
        Location base;
        Vector out;
        Vector perp;
        double phase;
        int state = 0; // 0 sway, 1 warn, 2 slam, 3 retract
        int stateTicks;
        int warnTotal;
        int cooldown;
        Location slamTarget;
    }

    private final JavaPlugin plugin;
    private final ConfigManager cfg;
    private final List<Tentacle> tentacles = new ArrayList<>();
    private final Random rnd = new Random();
    private final Consumer<Location> impactCallback;
    private long swayTicks;

    public KrakenTentacles(JavaPlugin plugin, ConfigManager cfg, Consumer<Location> impactCallback) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.impactCallback = impactCallback;
    }

    /** Спавн щупалец вокруг центра арены. */
    public void spawn(Location center, int count) {
        World world = center.getWorld();
        BlockData body = Material.WARPED_WART_BLOCK.createBlockData();
        BlockData tip = Material.SEA_LANTERN.createBlockData();
        for (int t = 0; t < count; t++) {
            Tentacle te = new Tentacle();
            double angle = (Math.PI * 2 * t / count) + rnd.nextDouble() * 0.5;
            double radius = 10 + rnd.nextDouble() * 4;
            te.base = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            te.out = new Vector(Math.cos(angle), 0, Math.sin(angle)).normalize();
            te.perp = new Vector(-te.out.getZ(), 0, te.out.getX());
            te.phase = rnd.nextDouble() * Math.PI * 2;
            te.cooldown = 60 + rnd.nextInt(120);
            for (int i = 0; i < SEGMENTS; i++) {
                final int seg = i;
                // толще у основания, тоньше к кончику — «щупальце», а не колонна
                float scale = seg == SEGMENTS - 1 ? 0.45f : (float) (1.25 - seg * 0.13);
                te.segs[seg] = world.spawn(swayPos(te, seg), BlockDisplay.class, bd -> {
                    bd.setBlock(seg == SEGMENTS - 1 ? tip : body);
                    bd.setPersistent(false);
                    bd.setTeleportDuration(2);
                    bd.setInterpolationDuration(2);
                    bd.setBrightness(new org.bukkit.entity.Display.Brightness(
                            seg == SEGMENTS - 1 ? 15 : 8, seg == SEGMENTS - 1 ? 15 : 8));
                    bd.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                            new Vector3f(scale, scale, scale), new Quaternionf()));
                });
            }
            tentacles.add(te);
        }
    }

    private Location swayPos(Tentacle te, int i) {
        double t = swayTicks;
        double lean = 0.45 + i * 0.22;
        double wave = Math.sin(t * 0.05 + te.phase + i * 0.55) * 0.5 * (0.3 + i / 6.0);
        return te.base.clone()
                .add(te.out.clone().multiply(lean))
                .add(te.perp.clone().multiply(wave))
                .add(0, 0.8 + i * 1.25, 0)
                .add(0, Math.sin(t * 0.04 + te.phase) * 0.25, 0);
    }

    /** Тик анимации/атак (каждые 2 тика). */
    public void tick() {
        swayTicks += 2;
        for (Tentacle te : tentacles) {
            switch (te.state) {
                case 0 -> tickSway(te);
                case 1 -> tickWarn(te);
                case 2 -> tickSlam(te);
                case 3 -> tickRetract(te);
            }
        }
    }

    private void tickSway(Tentacle te) {
        for (int i = 0; i < SEGMENTS; i++) {
            if (te.segs[i].isValid()) te.segs[i].teleport(swayPos(te, i));
        }
        // всплеск и пузыри у основания — щупальце «живёт» в воде
        if ((swayTicks & 7) == 0 && Fx.near(te.base, 90)) {
            World w = te.base.getWorld();
            w.spawnParticle(Particle.SPLASH, te.base.clone().add(0, 0.2, 0), 3, 0.7, 0.15, 0.7, 0.02);
            w.spawnParticle(Particle.BUBBLE_COLUMN_UP, te.base.clone().add(0, 0.4, 0), 2, 0.5, 0.3, 0.5, 0.02);
        }
        if (--te.cooldown <= 0) {
            Player target = pickTarget(te);
            if (target != null) {
                te.state = 1;
                te.stateTicks = (int) (cfg.krakenSlamWarnSeconds() * 20);
                te.warnTotal = Math.max(1, te.stateTicks);
                te.slamTarget = target.getLocation().clone();
                Msg.sound(te.slamTarget, "entity.guardian.attack", 1.2f, 0.6f);
                Msg.actionbar(target, "<red>Щупальце целится в вас — уходите из кольца!</red>");
            } else {
                te.cooldown = 40;
            }
        }
    }

    private void tickWarn(Tentacle te) {
        World w = te.slamTarget.getWorld();
        // сужающееся красное кольцо: честный телеграф AoE
        if (te.stateTicks % 4 == 0) {
            double radius = 0.5 + (cfg.krakenSlamRadius() + 1.2) * ((double) te.stateTicks / te.warnTotal);
            for (int k = 0; k < 14; k++) {
                double a = Math.PI * 2 * k / 14;
                Fx.dust(w, te.slamTarget.clone().add(Math.cos(a) * radius, 0.3, Math.sin(a) * radius),
                        2, 0.05, 0.05, 0.05, 0, org.bukkit.Color.RED, 1.5f);
            }
            if (te.stateTicks == 10) {
                Msg.sound(te.slamTarget, "block.note_block.hat", 1.0f, 1.8f);
            }
        }
        if (--te.stateTicks <= 0) {
            te.state = 2;
            te.stateTicks = 8;
            Msg.sound(te.slamTarget, "entity.ghast.shoot", 1.4f, 0.5f);
        }
    }

    private void tickSlam(Tentacle te) {
        double progress = 1.0 - te.stateTicks / 8.0;
        for (int i = 0; i < SEGMENTS; i++) {
            Location pos = arcPos(te, i, progress);
            if (te.segs[i].isValid()) te.segs[i].teleport(pos);
        }
        // след движения за кончиком
        if ((te.stateTicks & 1) == 0) {
            World w = te.slamTarget.getWorld();
            w.spawnParticle(Particle.SWEEP_ATTACK, te.slamTarget.clone().add(0, 1.5, 0), 1, 0.6, 0.3, 0.6, 0);
        }
        if (--te.stateTicks <= 0) {
            impact(te);
            te.state = 3;
            te.stateTicks = 14;
        }
    }

    private void tickRetract(Tentacle te) {
        double progress = te.stateTicks / 14.0;
        for (int i = 0; i < SEGMENTS; i++) {
            Location pos = arcPos(te, i, progress);
            if (te.segs[i].isValid()) te.segs[i].teleport(pos);
        }
        if (--te.stateTicks <= 0) {
            te.state = 0;
            int[] range = cfg.krakenSlamInterval();
            te.cooldown = range[0] * 20 + rnd.nextInt(Math.max(1, (range[1] - range[0]) * 20));
        }
    }

    /**
     * Позиция сегмента i на дуге слэма (квадратичная Безье):
     * старт — покачивание, конец — точка удара, средняя контрольная
     * точка поднята — щупальце бьёт СВЕРХУ дугой, а не едет по прямой.
     */
    private Location arcPos(Tentacle te, int i, double progress) {
        Location start = swayPos(te, i);
        Location end = te.slamTarget.clone().add(0, 0.5 + i * 0.3, 0);
        Location mid = start.clone().add(end).multiply(0.5).add(0, 3.2 + i * 0.35, 0);
        double u = 1 - progress;
        double w0 = u * u, w1 = 2 * u * progress, w2 = progress * progress;
        return start.clone().multiply(w0)
                .add(mid.multiply(w1))
                .add(end.multiply(w2));
    }

    private void impact(Tentacle te) {
        World w = te.slamTarget.getWorld();
        double radius = cfg.krakenSlamRadius();
        w.spawnParticle(Particle.SWEEP_ATTACK, te.slamTarget.clone().add(0, 1, 0), 6, 1.4, 0.5, 1.4, 0);
        Fx.dust(w, te.slamTarget.clone().add(0, 0.6, 0),
                60, radius, 0.5, radius, 0, org.bukkit.Color.fromRGB(60, 20, 90), 2.6f);
        // ударное кольцо по земле — читаемый AoE
        Fx.shockRing(w, te.slamTarget, radius + 0.6, 14, org.bukkit.Color.fromRGB(168, 85, 247), 3.2f, 2);
        w.spawnParticle(Particle.POOF, te.slamTarget.clone().add(0, 0.8, 0), 25, radius, 0.6, radius, 0.05);
        w.spawnParticle(Particle.BUBBLE_POP, te.slamTarget.clone().add(0, 0.6, 0), 16, radius * 0.8, 0.5, radius * 0.8, 0.08);
        w.spawnParticle(Particle.SPLASH, te.slamTarget.clone().add(0, 1.1, 0), 20, radius * 0.8, 0.7, radius * 0.8, 0.1);
        Msg.sound(te.slamTarget, "block.anvil.land", 1.6f, 0.6f);
        Msg.sound(te.slamTarget, "entity.generic.explode", 1.1f, 0.7f);
        impactCallback.accept(te.slamTarget);
    }

    private Player pickTarget(Tentacle te) {
        Player best = null;
        double bestDist = 40 * 40;
        for (Player p : te.base.getWorld().getPlayers()) {
            if (p.isDead() || p.getGameMode() == org.bukkit.GameMode.CREATIVE
                    || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            double d = p.getLocation().distanceSquared(te.base);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    public void boostCooldowns(double factor) {
        for (Tentacle te : tentacles) {
            te.cooldown = (int) Math.max(20, te.cooldown * factor);
        }
    }

    public void stop() {
        for (Tentacle te : tentacles) {
            for (BlockDisplay seg : te.segs) {
                if (seg.isValid()) seg.remove();
            }
        }
        tentacles.clear();
    }
}
