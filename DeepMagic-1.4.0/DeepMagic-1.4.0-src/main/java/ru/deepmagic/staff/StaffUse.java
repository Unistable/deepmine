package ru.deepmagic.staff;

import org.bukkit.entity.Player;
import ru.deepmagic.model.ItemDefs;
import ru.deepmagic.model.Magic;
import ru.deepmagic.synergy.Synergy;
import ru.deepmagic.util.Msg;
import ru.deepprogression.model.Branch;

/**
 * Применение посоха: кулдаун → ритуал (топливо бесплатно) → топливо → механика.
 */
public final class StaffUse {
    private StaffUse() {}

    public static void use(Player p, boolean shift) {
        org.bukkit.inventory.ItemStack item = p.getInventory().getItemInMainHand();
        Branch b = ItemDefs.staffBranch(item);
        if (b == null) return;
        Magic.StaffDef d = Magic.def(b);
        if (d == null) return;
        if (!ru.deepmagic.util.Conf.worldAllowed(p.getWorld())) return;
        // Святилище спавна: магия не работает (если стоит DeepSpawn)
        if (ru.deepmagic.util.SpawnGuard.staffBlocked(p.getLocation())) {
            Msg.dm(p, "&7Святилище спавна глушит магию. Отойдите дальше от врат.");
            return;
        }
        if (Synergy.raging(p)) {
            Msg.dm(p, "&cБагровое бешенство не даёт колдовать!");
            return;
        }
        int tier = ItemDefs.staffTier(item);
        // Угольная пыль (Shift у угольного посоха): отдельное топливо (4 угля)
        // и отдельный кулдаун — не смешивается с основным кастом.
        if (shift && b == Branch.COAL) {
            if (!Fuel.consume(p, b, 4)) {
                Fuel.missingMessage(p, b);
                return;
            }
            if (Mechanics.dustEmitter(p)) {
                return; // кулдаун и расход уже внутри dustEmitter
            }
            Fuel.refund(p, b, 4);
            return;
        }
        int cd = Cooldowns.forStaff(p.getUniqueId(), d);
        long left = Cooldowns.left(p.getUniqueId(), "staff_" + b.name());
        if (left > 0) {
            Msg.dm(p, "&7Посох восстановит силу через &e" + left + " с.");
            return;
        }
        // Ритуал свечей: топливо в зоне не тратится
        boolean freeFuel = Synergy.inRitual(p.getLocation());
        if (!freeFuel) {
            if (!Fuel.consume(p, b, d.fuelQty())) {
                Fuel.missingMessage(p, b);
                return;
            }
        }
        boolean ok = Mechanics.execute(p, d, tier, shift);
        if (ok) {
            Cooldowns.set(p.getUniqueId(), "staff_" + b.name(), cd);
        } else if (!freeFuel) {
            Fuel.refund(p, b, d.fuelQty()); // механика не удалась — топливо вернём
        }
    }
}
