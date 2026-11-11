package ru.deepmagic.model;

import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import ru.deepmagic.DeepMagicPlugin;
import ru.deepmagic.fx.Spectacle;

/**
 * Состояния сущностей (ядро матрицы синергий). Хранение: PDC сущности,
 * ключ dm_state_<ID> = остаток в тиках. Все ID — строго ASCII (§25 аудита).
 * Длительности: 8–120 с.
 */
public enum State {
    CARBON_COATED("Угольное покрытие", 60, "&8"),
    SOAKED("Пропитан влагой", 30, "&b"),
    FROSTED("Обморожен", 15, "&b"),
    CHARGED("Наэлектризован", 15, "&e"),
    STICKY("Липкий", 20, "&6"),
    SPORED("В спорах", 12, "&c"),
    BLOOMED("Очарован", 10, "&d"),
    SOUL_MARKED("Отмечен душой", 20, "&5"),
    GILDED("Позолочен", 30, "&6"),
    GLASSED("Остеклён", 20, "&f"),
    ECHO_TAGGED("Резонирует", 10, "&9"),
    NECROSIS("Гниль", 8, "&2"),
    FEAR("В страхе", 8, "&8"),
    ROOTED("В корнях", 10, "&2"),
    ARCANE_MARK("Лазурная печать", 30, "&9"),
    BRITTLE("Хрупкий", 8, "&f");

    public final String display;
    public final int defaultSeconds;
    public final String color;

    State(String display, int defaultSeconds, String color) {
        this.display = display;
        this.defaultSeconds = defaultSeconds;
        this.color = color;
    }

    public String key() {
        return "dm_state_" + name();
    }

    /** Наложить/продлить состояние. Возвращает фактическую длительность в тиках. */
    public int apply(LivingEntity e, int seconds) {
        if (e == null || e.isDead()) return 0;
        boolean fresh;
        int ticks = seconds * 20;
        var pdc = e.getPersistentDataContainer();
        Integer cur = pdc.get(
                new org.bukkit.NamespacedKey(DeepMagicPlugin.get(), key()),
                PersistentDataType.INTEGER);
        fresh = cur == null || cur <= 0;
        int next = (cur == null ? 0 : cur) + ticks;
        // Кап: 2×дефолта, максимум 240 с — защита от бесконечного стека
        int cap = Math.max(defaultSeconds * 2 * 20, 20);
        cap = Math.min(cap, 240 * 20);
        if (next > cap) next = cap;
        pdc.set(new org.bukkit.NamespacedKey(DeepMagicPlugin.get(), key()),
                PersistentDataType.INTEGER, next);
        if (fresh) Spectacle.stateApplied(e, this); // зрелищное наложение (орбиты/тотем)
        return next;
    }

    public void set(LivingEntity e, int seconds) {
        if (e == null || e.isDead()) return;
        boolean fresh = !has(e);
        e.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(DeepMagicPlugin.get(), key()),
                PersistentDataType.INTEGER, seconds * 20);
        if (fresh) Spectacle.stateApplied(e, this); // зрелищное наложение (орбиты/тотем)
    }

    public boolean has(LivingEntity e) {
        if (e == null || !e.isValid()) return false;
        Integer v = e.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(DeepMagicPlugin.get(), key()),
                PersistentDataType.INTEGER);
        return v != null && v > 0;
    }

    public int ticksLeft(LivingEntity e) {
        if (e == null || !e.isValid()) return 0;
        Integer v = e.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(DeepMagicPlugin.get(), key()),
                PersistentDataType.INTEGER);
        return v == null ? 0 : Math.max(0, v);
    }

    public void remove(LivingEntity e) {
        if (e == null) return;
        e.getPersistentDataContainer().remove(
                new org.bukkit.NamespacedKey(DeepMagicPlugin.get(), key()));
    }

    /** Тик задач состояний — вызывается раз в секунду из главного таска. */
    public static void tickSecond(LivingEntity e) {
        if (e == null || !e.isValid() || e.isDead()) return;
        var pdc = e.getPersistentDataContainer();
        for (State s : values()) {
            var key = new org.bukkit.NamespacedKey(DeepMagicPlugin.get(), s.key());
            Integer v = pdc.get(key, PersistentDataType.INTEGER);
            if (v == null) continue;
            v -= 20;
            if (v <= 0) pdc.remove(key);
            else pdc.set(key, PersistentDataType.INTEGER, v);
        }
        // ГНИЛЬ: урон увядания (не в мирных зонах и при выключенном PvP)
        if (NECROSIS.has(e) && ru.deepmagic.util.Conf.dotAllowed(e)) {
            e.damage(1.0);
        }
        // Жизнь аур: секундные акценты активных состояний
        Spectacle.stateAccent(e);
    }

    /** Стереть негативные состояния (берёзовый сок): полезные остаются. */
    public static void wipeNegative(LivingEntity e) {
        if (e == null) return;
        for (State s : values()) {
            if (s != BLOOMED) s.remove(e); // обаяние — благотворное, не трогаем
        }
        EternalBurn.quench(e);
    }

    /** Стереть все состояния (бетонное стирание, контрмеры). */
    public static void wipeAll(LivingEntity e) {
        if (e == null) return;
        for (State s : values()) s.remove(e);
        EternalBurn.quench(e);
    }

    /** Активные состояния списком (для лора/сообщений). */
    public static StringBuilder describe(LivingEntity e) {
        StringBuilder sb = new StringBuilder();
        for (State s : values()) {
            if (s.has(e)) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(s.color).append(s.display);
            }
        }
        return sb;
    }
}
