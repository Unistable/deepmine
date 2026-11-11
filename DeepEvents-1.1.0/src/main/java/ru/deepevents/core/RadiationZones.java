package ru.deepevents.core;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Зоны радиации. Кратер остаётся в мире — радиация тоже (permanent),
 * но зоны можно снять откатом (/deepevents undo) или очисткой при выключении.
 */
public final class RadiationZones {

    public static final class Zone {
        public final Location center;
        public final double radius;
        public final double falloutRadius;
        public final boolean permanent;
        public final List<ConfigManager.RadEffect> effects;

        public Zone(Location center, double radius, double falloutRadius, boolean permanent,
                    List<ConfigManager.RadEffect> effects) {
            this.center = center;
            this.radius = radius;
            this.falloutRadius = falloutRadius;
            this.permanent = permanent;
            this.effects = effects;
        }
    }

    private static final PotionEffectType[] HOT = {
            PotionEffectType.POISON, PotionEffectType.NAUSEA, PotionEffectType.HUNGER, PotionEffectType.SLOWNESS};

    private final JavaPlugin plugin;
    private final List<Zone> zones = new ArrayList<>();
    private final Random rnd = new Random();
    private static final int MAX_ZONES = 5;

    public RadiationZones(JavaPlugin plugin, int periodTicks) {
        this.plugin = plugin;
        // Глобальный тик радиации — работает всегда, дёшево при пустом списке
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, Math.max(20, periodTicks));
    }

    public void add(Zone zone) {
        // кап: не накапливаем бесконечно старые постоянные зоны
        while (zones.size() >= MAX_ZONES) {
            zones.remove(0);
        }
        zones.add(zone);
        Msg.log("Радиационная зона: " + zone.center.getBlockX() + " " + zone.center.getBlockZ()
                + " R=" + (int) zone.radius + (zone.permanent ? " (постоянная)" : "")
                + " эффектов: " + zone.effects.size());
    }

    public boolean removeNear(Location loc, double tolerance) {
        return zones.removeIf(z -> z.center.getWorld().equals(loc.getWorld())
                // сравнение по горизонтали (см. tick): зона висит на уровне дна воронки
                && Math.hypot(z.center.getX() - loc.getX(), z.center.getZ() - loc.getZ()) <= tolerance);
    }

    public void clearTransient() {
        zones.removeIf(z -> !z.permanent);
    }

    public void clearAll() { zones.clear(); }

    public int count() { return zones.size(); }

    private void tick() {
        if (zones.isEmpty()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (Zone z : zones) {
                if (!p.getWorld().equals(z.center.getWorld())) continue;
                // 2D-дистанция по горизонтали: центр зоны лежит на дне воронки,
                // 3D-дистанция оттуда никогда не достаёт до игрока на поверхности
                double dx = p.getLocation().getX() - z.center.getX();
                double dz = p.getLocation().getZ() - z.center.getZ();
                double dy = Math.abs(p.getLocation().getY() - z.center.getY());
                double d = Math.hypot(dx, dz);
                if (d <= z.radius && dy <= 28) {
                    applyInside(p, z);
                } else if (d <= z.falloutRadius && dy <= 48) {
                    applyFallout(p, z);
                }
            }
        }
    }

    private void applyInside(Player p, Zone z) {
        // Случайный набор негативных эффектов (по ТЗ: «все негативные эффекты рандомно»)
        List<ConfigManager.RadEffect> pool = z.effects;
        if (pool.isEmpty()) return;
        int count = 2 + rnd.nextInt(2);
        for (int i = 0; i < count; i++) {
            ConfigManager.RadEffect e = pool.get(rnd.nextInt(pool.size()));
            p.addPotionEffect(new PotionEffect(e.type(), e.duration(), rnd.nextInt(2), true, true, true));
        }
        Msg.actionbar(p, "<gradient:#facc15:#ef4444>Радиация: 3.7 Зв/ч — выходите из зоны!</gradient>");
        if (rnd.nextDouble() < 0.35) {
            p.playSound(p.getLocation(), "block.note_block.hat", 0.4f, 1.6f + rnd.nextFloat() * 0.8f);
        }
        if (rnd.nextDouble() < 0.3) {
            p.getWorld().spawnParticle(Particle.ASH, p.getLocation().add(0, 1, 0), 6, 0.5, 0.6, 0.5, 0.01);
        }
    }

    private void applyFallout(Player p, Zone z) {
        if (rnd.nextDouble() < 0.25) {
            p.addPotionEffect(new PotionEffect(HOT[rnd.nextInt(HOT.length)], 120, 0, true, true, true));
            Msg.actionbar(p, "<gray>Радиационный след: держитесь дальше от воронки</gray>");
        }
    }
}
