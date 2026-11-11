package ru.deepevents.events;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.deepevents.core.Fx;
import ru.deepevents.core.Msg;

/**
 * Грибовидное облако v2 — СТРУКТУРНОЕ, а не пылевое пятно:
 *
 *  - СТВОЛ: вращающаяся колонна из тёмного дыма и пыли (радиус ~2.6),
 *    поднимается за ~6 секунд до высоты 30, потом стоит и клубится;
 *  - ШЛЯПА: тор из пыли — внешнее тёмное кольцо, коричневая середина,
 *    бело-раскалённое ядро снизу; медленно вращается и расширяется;
 *  - ЮБКА: нижние кромки шляпы заворачиваются вниз (кольцо ниже шляпы);
 *  - ПРИЗЕМНЫЙ ВАЛ (base surge): расширяющееся пылевое кольцо по земле
 *    в первые ~5 секунд;
 *  - ИСКРЫ: редкие LAVA/ELECTRIC_SPARK в стволе.
 *
 * Бюджет: ~180-220 частиц за апдейт (каждые 2 тика), спавн только если
 * в радиусе 160 блоков есть игроки.
 */
public final class MushroomCloud {

    private static final long MAX_TICKS = 480;

    private final JavaPlugin plugin;
    private final Location center;
    private final BukkitTask task;
    private int t;

    // палитра
    private static final Color DARK = Color.fromRGB(52, 48, 54);
    private static final Color GRAY = Color.fromRGB(96, 88, 86);
    private static final Color BROWN = Color.fromRGB(140, 100, 62);
    private static final Color HOT = Color.fromRGB(255, 196, 120);
    private static final Color FIRE = Color.fromRGB(255, 120, 40);

    public MushroomCloud(JavaPlugin plugin, Location center) {
        this.plugin = plugin;
        this.center = center.clone();
        final World world = center.getWorld();
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 3L, 2L);
    }

    private void tick() {
        t += 2;
        World world = center.getWorld();
        if (world == null || t > MAX_TICKS) {
            stop();
            return;
        }
        // спавним только если кому-то это видно
        if (!Fx.near(center, 170)) return;

        // фазы: рост (0..120), зрелость (120..320), деградация (320..480)
        double rise = Math.min(1.0, t / 120.0);
        double h = 6 + 26 * (1 - Math.pow(1 - rise, 2.2));      // высота ствола
        double capR = 4 + 13 * Math.min(1.0, t / 150.0);        // радиус шляпы
        double capY = center.getY() + h + 2.2;
        double fade = t < 340 ? 1.0 : Math.max(0.0, 1.0 - (t - 340) / 130.0);
        if (fade <= 0.02) {
            stop();
            return;
        }
        double churn = t * 0.045; // медленное вращение структуры

        // ---- СТВОЛ: спиральный дуст + дым (стабильно по высоте) ----
        int stemPts = 16;
        for (int k = 0; k < stemPts; k++) {
            double a = churn * 1.4 + Math.PI * 2 * k / stemPts;
            double rr = 2.3 + 0.5 * Math.sin(t * 0.11 + k);
            Location p = center.clone().add(Math.cos(a) * rr, 0, Math.sin(a) * rr);
            // дуст по высоте ствола в 3 уровнях
            Fx.dust(world, p.clone().add(0, h * 0.25, 0), 1, 0.5, h * 0.16, 0.5, 0.02, BROWN, 3.4f);
            Fx.dust(world, p.clone().add(0, h * 0.62, 0), 1, 0.45, h * 0.12, 0.45, 0.02, DARK, 3.4f);
        }
        world.spawnParticle(Particle.LARGE_SMOKE, center.clone().add(0, h * 0.5, 0),
                (int) (7 * fade), 1.6, h / 2.6, 1.6, 0.015);
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center.clone().add(0, h + 1, 0),
                2, 0.3, 0.4, 0.3, 0.006);

        // ---- ШЛЯПА: тор из 3 концентрических колец ----
        // внешнее тёмное кольцо
        capRing(world, capY, capR, churn, 26, DARK, 4.0f);
        // коричневая середина
        capRing(world, capY + 1.0, capR * 0.74, churn * -0.8, 20, BROWN, 3.6f);
        // раскалённое ядро снизу (видно снизу — контраст с тёмной шляпой)
        capRing(world, capY - 1.4, capR * 0.42, churn * 1.6, 14, HOT, 3.2f);

        // ---- ЮБКА: заворот кромки вниз ----
        capRing(world, capY - 2.8, capR * 1.12, churn * 0.5, 22, GRAY, 3.4f);
        world.spawnParticle(Particle.LARGE_SMOKE, center.clone().add(0, capY - 2, 0),
                (int) (9 * fade), capR * 0.8, 1.6, capR * 0.8, 0.01);

        // ---- ПРИЗЕМНЫЙ ВАЛ (base surge): расширяющееся пылевое кольцо ----
        if (t < 110) {
            double ringR = 9 + t * 0.30;
            double alpha = 1.0 - t / 110.0;
            int pts = (int) (30 * (0.5 + alpha / 2));
            for (int k = 0; k < pts; k++) {
                double a = Math.PI * 2 * k / pts + churn * 0.4;
                Location p = center.clone().add(Math.cos(a) * ringR, 0, Math.sin(a) * ringR);
                int y = world.getHighestBlockYAt(p.getBlockX(), p.getBlockZ());
                p.setY(y + 1.4);
                Fx.dust(world, p, 2, 0.5, 1.1, 0.5, 0.03, BROWN, 3.6f);
            }
        }

        // ---- искры в стволе в фазе роста ----
        if (t < 90) {
            world.spawnParticle(Particle.LAVA, center.clone().add(0, h * 0.35, 0),
                    2, 1.8, h / 3.4, 1.8, 0);
            world.spawnParticle(Particle.SMALL_FLAME, center.clone().add(0, h * 0.22, 0),
                    6, 2.2, h / 4.0, 2.2, 0.02);
        }
        // пепел под шляпой в зрелости
        if (t >= 120 && t % 6 == 0) {
            world.spawnParticle(Particle.WHITE_ASH, center.clone().add(0, capY - 4, 0),
                    12, capR * 0.9, 3.0, capR * 0.9, 0.01);
        }
    }

    /** Одно кольцо тора: точки по окружности + небольшой вертикальный разброс. */
    private void capRing(World world, double y, double r, double rot, int points, Color c, float size) {
        for (int k = 0; k < points; k++) {
            double a = rot + Math.PI * 2 * k / points;
            double rr = r * (1.0 + 0.06 * Math.sin(3 * a + rot));
            Location p = center.clone().add(Math.cos(a) * rr, y - center.getY(), Math.sin(a) * rr);
            Fx.dust(world, p, 1, 0.35, 0.55, 0.35, 0, c, size);
        }
    }

    public void stop() {
        task.cancel();
    }
}
