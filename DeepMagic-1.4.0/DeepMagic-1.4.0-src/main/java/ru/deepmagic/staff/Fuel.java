package ru.deepmagic.staff;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.deepmagic.model.ItemDefs;
import ru.deepmagic.model.Magic;
import ru.deepmagic.util.Conf;
import ru.deepmagic.util.Msg;
import ru.deepprogression.model.Branch;
import ru.deepprogression.tools.ChunkDrill;
import ru.deepprogression.storage.Vaults;
import ru.deepprogression.util.Keys;

import java.util.Map;

/**
 * Топливо посохов — ресурс ветки (ман). Порядок: инвентарь → тайник ресурсов DP
 * (открытые сессии тайника не трогаем). Посохи жгут ресурсы = экономический sink.
 */
public final class Fuel {
    private Fuel() {}

    public static Material fuelOf(Branch b) {
        Magic.StaffDef d = Magic.def(b);
        return d == null ? null : d.fuel();
    }

    public static boolean consume(Player p, Branch b, int qty) {
        Material fuel = fuelOf(b);
        if (fuel == null) return false;
        int need = Math.min(64, Math.max(1, qty));
        if (takeMaterial(p.getInventory(), fuel, need)) {
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.5f, 1.6f);
            return true;
        }
        if (!Conf.usePouchFuel()) return false;
        ItemStack pouch = Vaults.findPouch(p);
        if (pouch == null || Vaults.isSessionPouch(p, pouch)) return false;
        Map<Material, Integer> map = Vaults.readContents(pouch);
        int have = map.getOrDefault(fuel, 0);
        if (have < need) return false;
        int left = have - need;
        if (left > 0) map.put(fuel, left);
        else map.remove(fuel);
        ItemMeta meta = pouch.getItemMeta();
        if (meta == null) return false;
        meta.getPersistentDataContainer().set(Keys.POUCH_CONTENTS,
                PersistentDataType.STRING, ChunkDrill.serialize(map));
        pouch.setItemMeta(meta);
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.5f, 1.6f);
        return true;
    }

    /** Возврат топлива (механика не удалась). */
    public static void refund(Player p, Branch b, int qty) {
        Material fuel = fuelOf(b);
        if (fuel == null || qty <= 0) return;
        var leftover = p.getInventory().addItem(new ItemStack(fuel, Math.min(64, qty)));
        for (ItemStack rest : leftover.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    /**
     * Достать need предметов материала fuel (мета не важна — наковальня/флаги
     * не спасают от расхода, но и не блокируют). false — если меньше need.
     */
    private static boolean takeMaterial(org.bukkit.inventory.PlayerInventory inv,
                                        Material fuel, int need) {
        int have = 0;
        for (ItemStack it : inv.getStorageContents()) {
            if (it != null && it.getType() == fuel) have += it.getAmount();
        }
        if (have < need) return false;
        int left = need;
        int size = Math.min(inv.getSize(), 36); // только основной инвентарь
        for (int i = 0; i < size && left > 0; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType() != fuel) continue;
            int take = Math.min(it.getAmount(), left);
            left -= take;
            if (take >= it.getAmount()) inv.setItem(i, null);
            else it.setAmount(it.getAmount() - take);
        }
        return true;
    }

    public static void missingMessage(Player p, Branch b) {
        Magic.StaffDef d = Magic.def(b);
        String name = d == null ? "?" : ItemDefs.ruName(d.fuel());
        Msg.dm(p, "&cНет топлива: &e" + name + "&c. Посох не сработал.");
    }
}
