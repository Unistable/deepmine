package ru.deepevents.core;

import java.util.Random;

/**
 * Чистая математика ядерного кратера (без Bukkit — для юнит-тестов).
 *
 * Форма v2 (кинематографичная воронка):
 *  - профиль глубины: D * (1 - q^1.8)^1.15 — крутые стены у кромки, «чаша»;
 *  - value-noise по азимуту (неровный контур);
 *  - высокий вал (гауссов бамп h=3.4, σ=2.3) на кромке;
 *  - апрон выбросов за валом (пологий конус из гравия/крупняка);
 *  - зоны обжига за пределами воронки.
 * Материалы по зонам: тринитит (стекло) -> магма/базальт -> булыжник/туф.
 */
public final class CraterMath {

    private CraterMath() {}

    /** Сглаженный noise по азимуту: сумма гармоник с ФИКСИРОВАННЫМИ фазами (одни на кратер). */
    public static double azimuthNoise(double theta, double p1, double p2, double p3) {
        return (Math.sin(3 * theta + p1) + 0.5 * Math.sin(7 * theta + p2) + 0.25 * Math.sin(13 * theta + p3)) / 1.75;
    }

    /** Эффективный радиус воронки в направлении theta: R * (1 + 0.10 * noise). */
    public static double effectiveRadius(double R, double noise) {
        return R * (1.0 + 0.10 * noise);
    }

    /**
     * Глубина выемки колонны на радиусе r (0 внутри воронки, 0 вне).
     * Профиль «чаши»: d = D * (1 - (r/Re)^1.8)^1.15 — стены круто падают
     * у кромки, дно широкое и пологое. Монотонно не возрастает.
     */
    public static int columnDepth(double r, double Re, double depthMax) {
        if (r >= Re) return 0;
        double q = r / Re;
        double base = 1.0 - Math.pow(q, 1.8);
        if (base <= 0) return 0;
        double d = depthMax * Math.pow(base, 1.15);
        return (int) Math.floor(d);
    }

    /** Высота вала на кромке: гауссов бамп с центром в Re (пик 3.4, σ=2.3). */
    public static double rimHeight(double r, double Re) {
        double d = (r - Re) / 2.3;
        return 3.4 * Math.exp(-d * d);
    }

    /**
     * Высота апрона выбросов за валом: пологий конус от Re до Re*1.32,
     * промодулированный шумом азимута (груды выброшенной породы).
     */
    public static double apronHeight(double r, double Re, double noise) {
        double out = Re * 1.32;
        if (r <= Re || r >= out) return 0;
        double t = (r - Re) / (out - Re); // 0..1
        double h = 1.9 * (1.0 - t) * (1.0 - 0.4 * t);
        return Math.max(0, h * (0.55 + 0.45 * Math.abs(noise)));
    }

    public enum Zone { TRINITITE, MAGMA, COBBLE, SCORCHED, BURNT, OUTER }

    /** Зона по нормированному радиусу q = r/Re. */
    public static Zone zone(double q, Random rnd) {
        if (q < 0.35) return Zone.TRINITITE;
        if (q < 0.62) return Zone.MAGMA;
        if (q <= 1.0) return Zone.COBBLE;
        if (q <= 1.35) return Zone.SCORCHED;
        if (q <= 2.0) return Zone.BURNT;
        return Zone.OUTER;
    }

    /** Материал пола воронки по зоне. */
    public static MaterialClass floorMaterial(Zone zone, double q, Random rnd) {
        return switch (zone) {
            case TRINITITE -> pick(rnd, MaterialClass.LIME_GLASS, MaterialClass.CYAN_GLASS, MaterialClass.GLASS);
            case MAGMA -> rnd.nextDouble() < 0.35 ? MaterialClass.MAGMA : (rnd.nextDouble() < 0.7 ? MaterialClass.BLACKSTONE : MaterialClass.OBSIDIAN);
            case COBBLE -> pick(rnd, MaterialClass.COBBLESTONE, MaterialClass.ANDESITE, MaterialClass.TUFF, MaterialClass.DEEPSLATE_COBBLE);
            default -> MaterialClass.NONE;
        };
    }

    /** Материал вала. */
    public static MaterialClass rimMaterial(Random rnd) {
        return pick(rnd, MaterialClass.BLACKSTONE, MaterialClass.BASALT, MaterialClass.COBBLESTONE, MaterialClass.TUFF);
    }

    /** Материал апрона (выброшенная порода). */
    public static MaterialClass apronMaterial(Random rnd) {
        return pick(rnd, MaterialClass.GRAVEL, MaterialClass.COBBLESTONE, MaterialClass.TUFF, MaterialClass.ANDESITE);
    }

    public enum MaterialClass {
        NONE, LIME_GLASS, CYAN_GLASS, GLASS, MAGMA, BLACKSTONE, OBSIDIAN,
        COBBLESTONE, ANDESITE, TUFF, DEEPSLATE_COBBLE, BASALT,
        GRAVEL
    }

    private static MaterialClass pick(Random rnd, MaterialClass... options) {
        return options[rnd.nextInt(options.length)];
    }

    /** Урон ударной волны по дистанции: линейный спад от maxDamage в эпицентре до 2 на границе. */
    public static double shockDamage(double dist, double range, double maxDamage) {
        if (dist > range) return 0;
        double q = 1.0 - dist / range;
        return Math.max(2.0, maxDamage * q);
    }

    /** Импульс отбрасывания ударной волной. */
    public static double shockPush(double dist, double range) {
        if (dist > range) return 0;
        return 1.2 * (1.0 - dist / range) + 0.5;
    }
}
