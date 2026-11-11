package ru.deepevents.events;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;
import ru.deepevents.core.ConfigManager;
import ru.deepevents.core.Msg;
import ru.deepevents.core.WaveMath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Система цунами-волн Кракена.
 *
 * Волна — фронт из колонн BlockDisplay (блоки воды БЕЗ коллизии), движущийся
 * со скоростью v к игрокам. Профиль колонн по формуле бегущей волны
 * eta_i(t) = A·sin(2π·x/λ + φ_i + медленный дрейф), гребень с пеной из частиц.
 * Попадание: отбрасывание + урон + краткая дезориентация. Нырок под волну спасает.
 */
public final class KrakenWaves {

    private static final class Wave {
        BlockDisplay[] bottom;
        BlockDisplay[] top;
        Location origin;
        Vector dir;
        Vector perp;
        double baseY;
        double x;
        long ticks;
        Map<UUID, Long> hitAt = new HashMap<>();
    }

    private final JavaPlugin plugin;
    private final ConfigManager cfg;
    private final List<Wave> waves = new ArrayList<>();

    public KrakenWaves(JavaPlugin plugin, ConfigManager cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    public int activeWaves() { return waves.size(); }

    /** Спавн волны из точки на поверхности воды в направлении dir. */
    public void spawnWave(Location origin, Vector dir) {
        World world = origin.getWorld();
        if (world == null || waves.size() >= 3) return;

        Vector d = dir.clone().setY(0).normalize();
        Vector perp = new Vector(-d.getZ(), 0, d.getX());

        int cols = Math.max(3, cfg.waveColumns());
        double spacing = cfg.waveColumnSpacing();
        BlockData water = Material.WATER.createBlockData();

        Wave w = new Wave();
        w.origin = origin.clone();
        w.dir = d;
        w.perp = perp;
        w.baseY = origin.getY();
        w.bottom = new BlockDisplay[cols];
        w.top = new BlockDisplay[cols];

        for (int i = 0; i < cols; i++) {
            Vector off = perp.clone().multiply((i - (cols - 1) / 2.0) * spacing);
            Location col = origin.clone().add(off);
            w.bottom[i] = world.spawn(col, BlockDisplay.class, bd -> {
                bd.setBlock(water);
                bd.setPersistent(false);
                bd.setTeleportDuration(2);
                bd.setInterpolationDuration(2);
                bd.setTransformation(new Transformation(new Vector3f(), new org.joml.Quaternionf(),
                        new Vector3f(1f, 1f, 1f), new org.joml.Quaternionf()));
            });
            w.top[i] = world.spawn(col, BlockDisplay.class, bd -> {
                bd.setBlock(water);
                bd.setPersistent(false);
                bd.setTeleportDuration(2);
                bd.setInterpolationDuration(2);
                bd.setTransformation(new Transformation(new Vector3f(), new org.joml.Quaternionf(),
                        new Vector3f(1f, 1f, 1f), new org.joml.Quaternionf()));
            });
        }
        waves.add(w);
        Msg.sound(origin, "entity.player.splash", 1.6f, 0.7f);
    }

    /** Обновление всех волн (вызывается каждые 2 тика). */
    public void tick() {
        if (waves.isEmpty()) return;
        double A = cfg.waveAmplitude();
        double lambda = cfg.waveLength();
        double speed = cfg.waveSpeed();
        double maxDist = cfg.waveMaxDistance();
        double spacing = cfg.waveColumnSpacing();

        List<Wave> dead = new ArrayList<>();
        for (Wave w : waves) {
            w.ticks += 2;
            w.x += speed * 2;
            if (w.x > maxDist) {
                dead.add(w);
                continue;
            }

            int cols = w.bottom.length;
            double halfWidth = cols * spacing / 2.0;
            for (int i = 0; i < cols; i++) {
                double eta = WaveMath.eta(w.x, w.ticks, A, lambda, i * 0.55, speed, 400);
                double off = (i - (cols - 1) / 2.0) * spacing;
                Location base = w.origin.clone().add(w.dir.clone().multiply(w.x)).add(w.perp.clone().multiply(off));

                double yBottom = WaveMath.troughY(w.baseY, eta);
                double yTop = WaveMath.crestY(w.baseY, eta, A);
                World wWorld = w.origin.getWorld();
                if (w.bottom[i].isValid()) {
                    w.bottom[i].teleport(new Location(wWorld, base.getX(), yBottom, base.getZ()));
                }
                if (w.top[i].isValid()) {
                    w.top[i].teleport(new Location(wWorld, base.getX(), yTop, base.getZ()));
                }
                // пена на гребне
                if (eta > A * 0.72) {
                    worldSpawnFoam(new Location(wWorld, base.getX(), yTop + 0.6, base.getZ()));
                }
            }
            handleHits(w, halfWidth);
        }
        for (Wave w : dead) {
            removeWave(w, true);
        }
        waves.removeAll(dead);
    }

    private void worldSpawnFoam(Location loc) {
        loc.getWorld().spawnParticle(Particle.SPLASH, loc, 6, 0.4, 0.2, 0.4, 0.05);
        loc.getWorld().spawnParticle(Particle.BUBBLE, loc, 4, 0.5, 0.3, 0.5, 0.02);
    }

    private void handleHits(Wave w, double halfWidth) {
        World world = w.origin.getWorld();
        if (world == null) return;
        double damage = cfg.waveDamage();
        for (Player p : world.getPlayers()) {
            if (p.isDead() || p.getGameMode() == org.bukkit.GameMode.CREATIVE
                    || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            Location pl = p.getLocation();
            Vector rel = pl.clone().subtract(w.origin).toVector();
            double s = rel.dot(w.dir);
            double u = rel.dot(w.perp);
            if (!WaveMath.hits(s, w.x, u, halfWidth, pl.getY(), w.baseY)) continue;

            Long last = w.hitAt.get(p.getUniqueId());
            if (last != null && w.ticks - last < 40) continue;
            w.hitAt.put(p.getUniqueId(), w.ticks);

            double[] kb = WaveMath.knockback(1.0 - Math.abs(s - w.x) / 1.8);
            Vector push = w.dir.clone().multiply(kb[0]).setY(kb[1]);
            p.setVelocity(push);
            p.damage(damage);
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0, true, false, true));
            world.spawnParticle(Particle.SPLASH, pl.clone().add(0, 1, 0), 20, 0.6, 0.8, 0.6, 0.1);
            world.spawnParticle(Particle.BUBBLE_POP, pl.clone().add(0, 1, 0), 12, 0.5, 0.7, 0.5, 0.05);
            Msg.sound(pl, "entity.generic.splash", 1.2f, 0.9f);
            Msg.actionbar(p, "<#60a5fa>Вас накрыло волной! Ныряйте под гребень.</#60a5fa>");
        }
    }

    private void removeWave(Wave w, boolean poof) {
        for (int i = 0; i < w.bottom.length; i++) {
            if (w.bottom[i].isValid()) {
                if (poof) w.bottom[i].getWorld().spawnParticle(Particle.POOF, w.bottom[i].getLocation(), 3, 0.2, 0.3, 0.2, 0.01);
                w.bottom[i].remove();
            }
            if (w.top[i].isValid()) w.top[i].remove();
        }
    }

    public void stop() {
        for (Wave w : waves) removeWave(w, false);
        waves.clear();
    }
}
