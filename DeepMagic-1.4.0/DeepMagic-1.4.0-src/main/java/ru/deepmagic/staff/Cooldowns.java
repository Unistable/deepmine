package ru.deepmagic.staff;

import ru.deepmagic.util.Conf;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Кулдауны посохов (в памяти; посох тоже, кулдаун игрока на механику). */
public final class Cooldowns {
    private static final Map<String, Long> CD = new ConcurrentHashMap<>();

    private Cooldowns() {}

    private static String key(UUID u, String id) {
        return u + ":" + id;
    }

    /** Осталось секунд до готовности (0 = готов). */
    public static long left(UUID u, String id) {
        Long until = CD.get(key(u, id));
        if (until == null) return 0;
        long ms = until - System.currentTimeMillis();
        if (ms <= 0) {
            CD.remove(key(u, id));
            return 0;
        }
        return (ms + 999) / 1000;
    }

    public static void set(UUID u, String id, int seconds) {
        CD.put(key(u, id), System.currentTimeMillis() + seconds * 1000L);
    }

    /** Кулдаун посоха: из конфига (переопределения) или реестра. */
    public static int forStaff(UUID u, ru.deepmagic.model.Magic.StaffDef d) {
        return Conf.cooldown(d);
    }

    public static void trim() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> it = CD.entrySet().iterator();
        while (it.hasNext()) if (it.next().getValue() < now) it.remove();
    }
}
