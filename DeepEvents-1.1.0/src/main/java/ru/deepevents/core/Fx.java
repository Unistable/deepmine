package ru.deepevents.core;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Адаптер частиц DUST: в Paper 1.21.11 тип данных DUST в рантайме сервера —
 * Color, хотя compile-time API объявляет DustOptions. Определяем режим
 * при первом спавне и далее используем рабочий вариант.
 *
 * v2: добавлены бюджетные помощники — гейт по близости игроков (не тратить
 * пакеты, когда рядом никого), кольца, лучи и градиентные дуст-полосы.
 */
public final class Fx {

    private static volatile Boolean colorMode = null; // null = не определён

    private Fx() {}

    /** Есть ли игроки в радиусе r от точки (дешёвый гейт для тяжёлых всплесков). */
    public static boolean near(Location loc, double r) {
        World w = loc.getWorld();
        if (w == null) return false;
        double r2 = r * r;
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= r2) return true;
        }
        return false;
    }

    /** Цветной дуст-партайкл с деградацией размера в Color-режиме. */
    public static void dust(World world, Location loc, int count,
                            double dx, double dy, double dz, double extra,
                            Color color, float size) {
        Boolean mode = detect(world, loc, count, dx, dy, dz, extra, color, size);
        if (mode) {
            world.spawnParticle(Particle.DUST, loc, count, dx, dy, dz, extra, color);
        } else {
            world.spawnParticle(Particle.DUST, loc, count, dx, dy, dz, extra,
                    new Particle.DustOptions(color, size));
        }
    }

    /** Вариант для персональных частиц игрока (аврора). */
    public static void dust(Player p, double x, double y, double z, int count,
                            double dx, double dy, double dz, double extra,
                            Color color, float size) {
        Boolean mode = colorMode;
        if (mode == null) detect(p.getWorld(), p.getLocation().add(0, 30, 0), 0, 0, 0, 0, 0, color, size);
        if (colorMode) {
            p.spawnParticle(Particle.DUST, x, y, z, count, dx, dy, dz, extra, color);
        } else {
            p.spawnParticle(Particle.DUST, x, y, z, count, dx, dy, dz, extra,
                    new Particle.DustOptions(color, size));
        }
    }

    /**
     * Кольцо из дуста: n точек по окружности радиуса r в горизонтали.
     * Экономно: count частиц на точку, лёгкий вертикальный разброс.
     */
    public static void dustRing(World world, Location center, double r, int points,
                                Color color, float size, int countPerPoint) {
        for (int k = 0; k < points; k++) {
            double a = Math.PI * 2 * k / points;
            Location p = center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            dust(world, p, countPerPoint, 0.08, 0.25, 0.08, 0, color, size);
        }
    }

    /** Вертикальный световой луч из дуста (столб от y0 до y0+height). */
    public static void dustBeam(World world, Location base, double height, int points,
                                Color color, float size) {
        for (int k = 0; k < points; k++) {
            double t = (double) k / Math.max(1, points - 1);
            dust(world, base.clone().add(0, height * t, 0), 1, 0.06, 0.06, 0.06, 0, color, size);
        }
    }

    /** Расширяющееся кольцо по земле (ударные волны, слэмы): y берётся с карты. */
    public static void shockRing(World world, Location center, double r, int points,
                                 Color color, float size, int dustPerPoint) {
        for (int k = 0; k < points; k++) {
            double a = Math.PI * 2 * k / points;
            Location p = center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            int y = world.getHighestBlockYAt(p.getBlockX(), p.getBlockZ());
            p.setY(y + 1.1);
            dust(world, p, dustPerPoint, 0.3, 0.6, 0.3, 0.02, color, size);
        }
    }

    private static boolean detect(World world, Location loc, int count,
                                  double dx, double dy, double dz, double extra,
                                  Color color, float size) {
        Boolean mode = colorMode;
        if (mode != null) return mode;
        try {
            world.spawnParticle(Particle.DUST, loc, count, dx, dy, dz, extra,
                    new Particle.DustOptions(color, size));
            colorMode = Boolean.FALSE;
            return false;
        } catch (IllegalArgumentException e) {
            colorMode = Boolean.TRUE;
            return true;
        }
    }
}
