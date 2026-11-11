package ru.deepevents.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Чистая математика раздачи лута (без Bukkit — для юнит-тестов).
 *
 * Требование ТЗ: лут делится РАВНОМЕРНО между всеми, кто наносил урон боссу,
 * независимо от величины урона. Пул генерируется, перемешивается и раздаётся
 * в round-robin: каждый охотник получает floor(total/k) предметов,
 * остаток (total mod k) уходит случайным охотникам.
 */
public final class LootMath {

    private LootMath() {}

    /**
     * @param amounts список количеств по каждой позиции пула (уже после бросков на шанс/количество)
     * @param players количество участников
     * @param rnd     источник случайности (для перемешивания пула)
     * @return массив [players] — сколько предметов получит каждый
     */
    public static int[] deal(List<Integer> amounts, int players, Random rnd) {
        if (players <= 0) return new int[0];
        int[] out = new int[players];
        if (amounts.isEmpty()) return out;

        List<Integer> pool = new ArrayList<>(amounts);
        // перемешивание пула: Fisher-Yates, чтобы состав у каждого был случайным
        for (int i = pool.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int tmp = pool.get(i);
            pool.set(i, pool.get(j));
            pool.set(j, tmp);
        }

        int total = 0;
        for (int n : pool) total += n;

        int base = total / players;
        int remainder = total % players;

        for (int p = 0; p < players; p++) out[p] = base;

        // остаток раздаётся по одному случайным игрокам
        List<Integer> idx = new ArrayList<>();
        for (int p = 0; p < players; p++) idx.add(p);
        for (int i = 0; i < remainder && !idx.isEmpty(); i++) {
            int j = rnd.nextInt(idx.size());
            out[idx.remove(j)] += 1;
        }
        return out;
    }

    /** Сумма пула. */
    public static int totalOf(List<Integer> amounts) {
        int t = 0;
        for (int n : amounts) t += n;
        return t;
    }

    /**
     * Превращает таблицу лута в конкретный пул количеств:
     * на каждую запись бросается chance, затем min..max.
     * @return список количеств (по одной ячейке на каждый выпавший предмет)
     */
    public static List<Integer> rollPool(List<int[]> entries, Random rnd) {
        // entries: {min, max, chancePercent}
        List<Integer> out = new ArrayList<>();
        for (int[] e : entries) {
            if (rnd.nextDouble() * 100.0 >= e[2]) continue;
            int count = e[0] + rnd.nextInt(Math.max(1, e[1] - e[0] + 1));
            for (int i = 0; i < count; i++) out.add(1);
        }
        return out;
    }
}
