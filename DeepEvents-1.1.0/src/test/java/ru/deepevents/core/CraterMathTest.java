package ru.deepevents.core;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

/** Тесты математики ядерного кратера. */
public class CraterMathTest {

    @Test
    public void depthIsMaxAtCenterAndZeroAtEdge() {
        assertEquals(13, CraterMath.columnDepth(0, 27, 13), "эпицентр: максимальная глубина");
        assertEquals(0, CraterMath.columnDepth(27, 27, 13), "кромка: нулевая глубина");
        assertEquals(0, CraterMath.columnDepth(30, 27, 13), "за кромкой: выемки нет");
    }

    @Test
    public void depthIsMonotonicNonIncreasing() {
        double prev = Double.MAX_VALUE;
        for (double r = 0; r <= 27; r += 0.25) {
            int d = CraterMath.columnDepth(r, 27, 13);
            assertTrue(d <= prev, "глубина должна не возрастать с радиусом");
            prev = d;
        }
    }

    @Test
    public void rimPeaksAtEffectiveRadius() {
        double peak = CraterMath.rimHeight(27, 27);
        assertTrue(CraterMath.rimHeight(24.7, 27) < peak, "вал растёт к кромке");
        assertTrue(CraterMath.rimHeight(29.3, 27) < peak, "вал спадает за кромкой");
        assertEquals(3.4, peak, 1e-9, "пик вала 3.4 блока");
        assertTrue(CraterMath.rimHeight(34, 27) < 0.1, "вал быстро гаснет");
    }

    @Test
    public void apronSlopesOutwardFromRim() {
        assertEquals(0, CraterMath.apronHeight(20, 27, 0.5), 1e-9, "внутри воронки апрона нет");
        assertEquals(0, CraterMath.apronHeight(40, 27, 0.5), 1e-9, "за пределами апрона нет");
        double near = CraterMath.apronHeight(28, 27, 1.0);
        double far = CraterMath.apronHeight(34, 27, 1.0);
        assertTrue(near > 0.3, "у вала апрон выше: " + near);
        assertTrue(far < near, "апрон спадает наружу");
        assertTrue(CraterMath.apronHeight(30, 27, 1.0) > CraterMath.apronHeight(30, 27, 0.1),
                "шум создаёт неровные груды породы");
    }

    @Test
    public void zonesAreOrdered() {
        Random rnd = new Random(3);
        assertEquals(CraterMath.Zone.TRINITITE, CraterMath.zone(0.1, rnd), "центр — тринитит");
        assertEquals(CraterMath.Zone.TRINITITE, CraterMath.zone(0.34, rnd));
        assertEquals(CraterMath.Zone.MAGMA, CraterMath.zone(0.5, rnd));
        assertEquals(CraterMath.Zone.COBBLE, CraterMath.zone(0.8, rnd));
        assertEquals(CraterMath.Zone.SCORCHED, CraterMath.zone(1.2, rnd));
        assertEquals(CraterMath.Zone.BURNT, CraterMath.zone(1.7, rnd));
    }

    @Test
    public void noiseIsContinuousAlongAzimuth() {
        // соседние углы не должны прыгать: сглаженная гармоника
        double p1 = 0.7, p2 = 2.1, p3 = 4.4;
        double maxJump = 0;
        for (int i = 0; i < 360; i++) {
            double a = CraterMath.azimuthNoise(Math.toRadians(i), p1, p2, p3);
            double b = CraterMath.azimuthNoise(Math.toRadians(i + 1), p1, p2, p3);
            maxJump = Math.max(maxJump, Math.abs(a - b));
        }
        assertTrue(maxJump < 0.12, "контур кратера должен быть гладким: maxJump=" + maxJump);
        // шум ограничен
        assertTrue(Math.abs(CraterMath.azimuthNoise(1.0, p1, p2, p3)) <= 1.0 + 1e-9);
    }

    @Test
    public void effectiveRadiusStaysWithinTenPercent() {
        double R = 27;
        assertTrue(CraterMath.effectiveRadius(R, 1.0) <= R * 1.1 + 1e-9);
        assertTrue(CraterMath.effectiveRadius(R, -1.0) >= R * 0.9 - 1e-9);
    }

    @Test
    public void shockDamageFallsLinearlyToMinimum() {
        assertEquals(12.0, CraterMath.shockDamage(0, 70, 12), 1e-9, "эпицентр");
        assertEquals(2.0, CraterMath.shockDamage(70, 70, 12), 1e-9, "граница: минимум 2");
        assertEquals(0.0, CraterMath.shockDamage(80, 70, 12), 1e-9, "за границей: ноль");
        assertTrue(CraterMath.shockDamage(20, 70, 12) > CraterMath.shockDamage(40, 70, 12));
    }

    @Test
    public void shockPushBounds() {
        assertTrue(CraterMath.shockPush(0, 70) > CraterMath.shockPush(60, 70));
        assertEquals(0, CraterMath.shockPush(80, 70), 1e-9);
    }
}
