package ru.deepevents.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Тесты математики волн Кракена. */
public class WaveMathTest {

    private static final double EPS = 1e-9;

    @Test
    public void etaNeverExceedsAmplitude() {
        for (double x = -50; x < 50; x += 0.7) {
            for (long t = 0; t < 400; t += 7) {
                double eta = WaveMath.eta(x, t, 1.7, 7.0, x * 0.1, 0.85, 400);
                assertTrue(Math.abs(eta) <= 1.7 + EPS, "eta=" + eta + " при x=" + x);
            }
        }
    }

    @Test
    public void waveIsPeriodicInX() {
        double a = WaveMath.eta(0, 100, 1.5, 7.0, 0.3, 0.85, 400);
        double b = WaveMath.eta(7.0, 100, 1.5, 7.0, 0.3, 0.85, 400);
        assertEquals(a, b, 1e-9, "период волны по X должен равняться длине волны");
    }

    @Test
    public void omegaMatchesPhaseVelocity() {
        // ω = 2π v / λ; за λ/v тиков фаза должна пройти 2π
        double v = 0.85, lambda = 7.0;
        double omega = WaveMath.omega(v, lambda);
        double ticks = lambda / v;
        assertEquals(2 * Math.PI, omega * ticks, 1e-6);
    }

    @Test
    public void divingUnderWaveSaves() {
        // игрок под базовым уровнем на 1+ блок — промах
        assertFalse(WaveMath.hits(10, 10.5, 0, 5.5, 61.0, 63.0), "нырок под волну должен спасать");
        // игрок на поверхности — попадание
        assertTrue(WaveMath.hits(10, 10.5, 0, 5.5, 63.5, 63.0));
        // игрок слишком высоко (прыжок выше 4 блоков) — промах
        assertFalse(WaveMath.hits(10, 10.5, 0, 5.5, 68.0, 63.0));
        // сбоку за фронтом по ширине — промах
        assertFalse(WaveMath.hits(10, 10.5, 7.0, 5.5, 63.5, 63.0));
        // далеко впереди фронта — промах
        assertFalse(WaveMath.hits(25, 10.5, 0, 5.5, 63.5, 63.0));
    }

    @Test
    public void crestAboveTrough() {
        double baseY = 62;
        for (double x = 0; x < 30; x += 0.5) {
            double eta = WaveMath.eta(x, 50, 1.7, 7.0, x, 0.85, 400);
            assertTrue(WaveMath.crestY(baseY, eta, 1.7) > WaveMath.troughY(baseY, eta),
                    "гребень всегда выше подошвы");
        }
    }

    @Test
    public void knockbackIsSane() {
        double[] kb = WaveMath.knockback(1.0);
        assertTrue(kb[0] > 0.5 && kb[0] < 2.5);
        assertTrue(kb[1] > 0.2 && kb[1] < 1.0);
    }
}
