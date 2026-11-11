package ru.deepevents.core;

/**
 * Чистая математика волн Кракена (без Bukkit — для юнит-тестов).
 *
 * Волна — фронт из колонн блоков воды без коллизии, движущийся со скоростью v.
 * Профиль колонны: eta_i(t) = A * sin(2π * x / λ + φ_i + t * ω_slow),
 * где x — пройденная дистанция, φ_i — фазовый сдвиг по ширине фронта.
 * Это бегущая волна: гребень прокатывается вдоль фронта по мере движения.
 */
public final class WaveMath {

    private WaveMath() {}

    public static final double TWO_PI = Math.PI * 2;

    /** Круговая частота из скорости и длины волны: ω = 2π·v/λ (фазовая скорость = v). */
    public static double omega(double speedPerTick, double wavelength) {
        return TWO_PI * speedPerTick / wavelength;
    }

    /** Волновое число: k = 2π/λ. */
    public static double waveNumber(double wavelength) {
        return TWO_PI / wavelength;
    }

    /**
     * Вертикальное смещение колонны i в момент t (в тиках) при пройденной дистанции x.
     * @param amplitude  амплитуда A (блоки)
     * @param wavelength длина волны λ (блоки)
     * @param phaseI     фазовый сдвиг колонны по ширине фронта
     */
    public static double eta(double x, long t, double amplitude, double wavelength,
                             double phaseI, double speedPerTick, double slowPeriodTicks) {
        double k = waveNumber(wavelength);
        double slowW = TWO_PI / Math.max(1.0, slowPeriodTicks);
        return amplitude * Math.sin(k * x + phaseI - slowW * t * 0.15);
    }

    /** Высота гребня колонны: базовый уровень + 1 блок + доля от положительной части профиля. */
    public static double crestY(double baseY, double etaValue, double amplitude) {
        return baseY + 1.0 + Math.max(0, etaValue) / amplitude * 0.8;
    }

    /** Высота подошвы колонны: базовый уровень + 60% профиля. */
    public static double troughY(double baseY, double etaValue) {
        return baseY + etaValue * 0.6;
    }

    /**
     * Проверка попадания игрока фронтом волны.
     * @param s        продольная координата игрока (проекция на направление волны)
     * @param frontX   текущая позиция фронта
     * @param u        поперечное отклонение игрока от оси фронта
     * @param halfWidth половина ширины фронта
     * @param playerY  высота игрока
     * @param baseY    базовая высота волны (поверхность воды)
     */
    public static boolean hits(double s, double frontX, double u, double halfWidth,
                               double playerY, double baseY) {
        if (Math.abs(s - frontX) > 1.8) return false;
        if (Math.abs(u) > halfWidth) return false;
        // под водой можно нырнуть под волну — это механика контрплея
        if (playerY < baseY - 1.0) return false;
        return playerY < baseY + 4.0;
    }

    /** Импульс игрока при попадании: горизонтальный по направлению волны + вертикальный. */
    public static double[] knockback(double damageFactor) {
        // damageFactor 0..1 — ближе к фронту сильнее
        double h = 0.9 + 0.6 * damageFactor;
        double v = 0.45 + 0.15 * damageFactor;
        return new double[]{h, v};
    }
}
