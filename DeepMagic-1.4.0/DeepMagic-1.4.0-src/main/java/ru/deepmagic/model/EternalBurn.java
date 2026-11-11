package ru.deepmagic.model;

import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import ru.deepmagic.DeepMagicPlugin;

/**
 * Вечное горение (уголь + магмовый жар): флаг PDC eternal_burn.
 * 1 урон/с, игнорирует огнестойкость. Гасится: вода, дождь, влажность,
 * берёзовый сок, бетон (wipeAll), арбуз.
 */
public final class EternalBurn {
    public static final String KEY = "dm_eternal_burn";

    private EternalBurn() {}

    private static org.bukkit.NamespacedKey key() {
        return new org.bukkit.NamespacedKey(DeepMagicPlugin.get(), KEY);
    }

    public static void ignite(LivingEntity e) {
        if (e == null || e.isDead()) return;
        e.getPersistentDataContainer().set(key(), PersistentDataType.BYTE, (byte) 1);
        e.setFireTicks(Math.max(e.getFireTicks(), 20 * 60));
    }

    public static boolean burning(LivingEntity e) {
        if (e == null || !e.isValid()) return false;
        Byte v = e.getPersistentDataContainer().get(key(), PersistentDataType.BYTE);
        return v != null && v == 1;
    }

    public static void quench(LivingEntity e) {
        if (e == null) return;
        e.getPersistentDataContainer().remove(key());
        e.setFireTicks(0);
    }

    /** Секундный тик вечного огня. */
    public static void tickSecond(LivingEntity e) {
        if (!burning(e)) return;
        // Растворение: вода (НЕ лава — она лишь и так жжёт) / дождь / влажность
        if (e.isInsideVehicle() && e.getVehicle().isInWater()) { quench(e); return; }
        if (inWater(e) || e.isInWaterOrRain() || State.SOAKED.has(e)) {
            quench(e);
            State.CARBON_COATED.remove(e);
            return;
        }
        if (e.getFireTicks() < 40) e.setFireTicks(40); // визуал держится с флагом
        if (!ru.deepmagic.util.Conf.dotAllowed(e)) return; // мирные зоны/PvP-выключатель
        double dps = ru.deepmagic.util.Conf.eternalDps();
        e.damage(dps);
        // Зрелищный акцент вечного огня (искры, лава, пепел)
        ru.deepmagic.fx.Spectacle.burnAccent(e);
        if (e instanceof org.bukkit.entity.Player p) {
            p.sendActionBar(ru.deepmagic.util.T.color(
                    "&cВЕЧНОЕ ГОРЕНИЕ &8— спасает только вода!"));
        }
    }

    /** Цельно водная проверка: лава НЕ гасит вечное горение (лор: спасает только вода). */
    private static boolean inWater(LivingEntity e) {
        org.bukkit.Material feet = e.getLocation().getBlock().getType();
        org.bukkit.Material head = e.getLocation().clone().add(0, 1, 0).getBlock().getType();
        return feet == org.bukkit.Material.WATER || head == org.bukkit.Material.WATER
                || feet == org.bukkit.Material.BUBBLE_COLUMN
                || head == org.bukkit.Material.BUBBLE_COLUMN;
    }
}
