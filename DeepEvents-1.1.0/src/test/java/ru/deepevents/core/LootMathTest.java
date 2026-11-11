package ru.deepevents.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

/** Тесты равного дележа лута. */
public class LootMathTest {

    @Test
    public void equalSharesForEqualContributors() {
        Random rnd = new Random(42);
        List<Integer> pool = List.of(1, 1, 1, 1, 1, 1); // 6 предметов
        int[] shares = LootMath.deal(pool, 3, rnd);
        assertEquals(2, shares[0]);
        assertEquals(2, shares[1]);
        assertEquals(2, shares[2]);
    }

    @Test
    public void remainderGoesOneMax() {
        Random rnd = new Random(7);
        // 10 предметов на 3 игроков: 3/3/4
        List<Integer> pool = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) pool.add(1);
        int[] shares = LootMath.deal(pool, 3, rnd);
        int sum = shares[0] + shares[1] + shares[2];
        assertEquals(10, sum);
        int min = Math.min(shares[0], Math.min(shares[1], shares[2]));
        int max = Math.max(shares[0], Math.max(shares[1], shares[2]));
        assertTrue(max - min <= 1, "разброс долей не должен превышать 1: " + java.util.Arrays.toString(shares));
    }

    @Test
    public void everyDamagerGetsAtLeastOneWhenPoolIsBig() {
        Random rnd = new Random(1);
        List<Integer> pool = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) pool.add(1);
        int[] shares = LootMath.deal(pool, 5, rnd);
        for (int s : shares) assertTrue(s >= 1, "каждый наносивший урон должен получить добычу");
    }

    @Test
    public void deterministicWithSameSeed() {
        List<Integer> pool = List.of(1, 1, 1, 1, 1, 1, 1, 1, 1);
        int[] a = LootMath.deal(pool, 4, new Random(99));
        int[] b = LootMath.deal(pool, 4, new Random(99));
        assertArrayEquals(a, b, "одинаковый seed -> одинаковый делёж");
    }

    @Test
    public void zeroPlayersIsSafe() {
        assertEquals(0, LootMath.deal(List.of(1, 2, 3), 0, new Random()).length);
    }

    @Test
    public void emptyPoolIsSafe() {
        int[] shares = LootMath.deal(List.of(), 3, new Random());
        assertEquals(3, shares.length);
        assertEquals(0, shares[0]);
    }

    @Test
    public void rollPoolRespectsChanceAndCount() {
        // chance 0% — никогда; chance 100% — всегда в диапазоне min..max
        Random rnd = new Random(5);
        List<int[]> entries = List.of(
                new int[]{2, 2, 0},   // никогда
                new int[]{3, 3, 100}, // всегда 3 предмета
                new int[]{1, 5, 100}  // всегда 1..5 предметов
        );
        List<Integer> pool = LootMath.rollPool(entries, rnd);
        int size = pool.size();
        // вторая запись даёт ровно 3, третья 1..5: итого 4..8 предметов
        assertTrue(size >= 4 && size <= 8, "пул = 3 + 1..5 предметов, размер=" + size);
    }

    @Test
    public void totalOfAddsUp() {
        assertEquals(7, LootMath.totalOf(List.of(1, 2, 4)));
        assertEquals(0, LootMath.totalOf(List.of()));
    }
}
