package ru.deepmagic.craft;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import ru.deepmagic.DeepMagicPlugin;
import ru.deepmagic.model.ItemDefs;
import ru.deepmagic.model.Magic;
import ru.deepmagic.util.Conf;
import ru.deepmagic.util.Msg;
import ru.deepprogression.model.Branch;
import ru.deepprogression.tools.Items;

/**
 * Крафты DeepMagic: магический компрессор + 73 посоха.
 * Матчинг ручной (кастомные ингредиенты: вещества, медали DP, компоненты DP).
 * Фазы:
 *  1) PrepareItemCraftEvent (HIGHEST): ставим превью результата
 *     (глушитель-защита DP срабатывает раньше, на NORMAL, и мы её перекрываем);
 *  2) клик по результату: списываем ровно одну сетку, выдаём предмет
 *     (shift — цикл). Дюп невозможен: списание до выдачи.
 * Гейт крафта посоха: медаль ветки тир ≥ 3 (расходуется, /dp medals возвращает).
 */
public final class Crafts {

    private Crafts() {}

    // ---------- превью (Prepare) ----------

    public static void handlePrepare(PrepareItemCraftEvent e) {
        CraftingInventory inv = e.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        if (matrix == null || matrix.length != 9) return;

        String match = matchMatrix(matrix);
        if (match == null) {
            // Защита: DM-предметы не должны крафтиться в ванильные вещи (мейс и т.п.)
            for (ItemStack it : matrix) {
                if (ItemDefs.isOurs(it)) {
                    inv.setResult(null);
                    return;
                }
            }
            return;
        }
        ItemStack res;
        if ("__compressor__".equals(match)) {
            res = ItemDefs.compressorItem();
        } else {
            String[] spec = match.split(":");
            Branch b = Branch.byId(spec[0]);
            if (b == null) return;
            res = ItemDefs.staff(b, Integer.parseInt(spec[1]));
        }
        if (res != null) inv.setResult(res);
    }

    /**
     * Матчинг сетки. Возвращает "__compressor__", "BRANCH:tier" или null.
     * Вещество обязано быть в центре; медаль — снизу; компонент — сверху.
     * Остальные клетки пусты. Тир компонента обязан соответствовать тиру вещества.
     */
    public static String matchMatrix(ItemStack[] m) {
        // --- посох: .C. / .S. / .M. ---
        ItemStack center = m[4];
        if (ItemDefs.isSubstance(center)) {
            Branch b = ItemDefs.substanceBranch(center);
            int tier = ItemDefs.substanceTier(center);
            if (b != null && tier >= 1) {
                boolean ok = isEmpty(m[0]) && isEmpty(m[2])
                        && isEmpty(m[3]) && isEmpty(m[5])
                        && isEmpty(m[6]) && isEmpty(m[8]);
                if (ok) {
                    // компонент сверху — по тиру вещества
                    ItemStack comp = m[1];
                    String needComp = Conf.component(tier);
                    if (comp != null && needComp.equals(Items.toolId(comp))) {
                        // медаль снизу — ветка ≥ craftGateTier
                        ItemStack medal = m[7];
                        String mb = Items.medalBranch(medal);
                        int mt = Items.medalTier(medal);
                        if (b.name().equals(mb) && mt >= Conf.craftGateTier()) {
                            return b.name() + ":" + tier;
                        }
                    }
                }
            }
            return null;
        }
        // --- компрессор: .P. / N F P / .M. ---
        if (m[1] != null && m[1].getType() == Material.PISTON && Items.toolId(m[1]) == null
                && Items.toolId(m[3]) != null && Items.toolId(m[3]).equals(Conf.component(2))
                && Items.toolId(m[4]) != null && Items.toolId(m[4]).equals("reinforced_frame")
                && m[5] != null && m[5].getType() == Material.PISTON && Items.toolId(m[5]) == null
                && isEmpty(m[0]) && isEmpty(m[2]) && isEmpty(m[6]) && isEmpty(m[8])) {
            ItemStack medal = m[7];
            String mb = Items.medalBranch(medal);
            int mt = Items.medalTier(medal);
            if ("COPPER".equals(mb) && mt >= Conf.craftGateTier()) {
                return "__compressor__";
            }
        }
        return null;
    }

    private static boolean isEmpty(ItemStack it) {
        return it == null || it.getType().isAir();
    }

    // ---------- списание (клик по результату) ----------

    public static void handleClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory() instanceof CraftingInventory inv)) return;
        ItemStack preview = inv.getResult();
        boolean ours = preview != null && (ItemDefs.isStaff(preview) || ItemDefs.isCompressor(preview));
        // Наш результат в верстаке: блокируем сбор двойным кликом/перетаскивание превью
        if (ours && e.getRawSlot() != 0) {
            if (e.getClick().name().contains("DOUBLE")
                    || e.getClick().name().contains("COLLECT")
                    || e.getClick().isShiftClick()
                    || e.getClick().isKeyboardClick()) {
                e.setCancelled(true);
            }
            return;
        }
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (e.getRawSlot() != 0) return;
        ItemStack current = e.getCurrentItem();
        if (current == null || current.getType().isAir()) return;
        if (!(current.getItemMeta() != null && current.getItemMeta().getPersistentDataContainer()
                .has(ItemDefs.key(ItemDefs.STAFF_KEY), org.bukkit.persistence.PersistentDataType.STRING))
                && !(current.getItemMeta() != null && current.getItemMeta().getPersistentDataContainer()
                .has(ItemDefs.key(ItemDefs.COMPRESSOR_KEY), org.bukkit.persistence.PersistentDataType.BYTE))) {
            return; // наш результат? нет — не вмешиваемся
        }
        if (!(e.getWhoClicked() instanceof Player p)) return;
        e.setCancelled(true);
        boolean shift = e.getClick().isShiftClick();
        int maxCycles = shift ? 64 : 1;
        int done = 0;
        for (int i = 0; i < maxCycles; i++) {
            ItemStack[] matrix = inv.getMatrix();
            String match = matchMatrix(matrix);
            if (match == null) break;
            // D1: сначала списываем, потом выдаём (анти-дюп)
            consume(inv);
            ItemStack res;
            if ("__compressor__".equals(match)) {
                res = ItemDefs.compressorItem();
            } else {
                String[] spec = match.split(":");
                res = ItemDefs.staff(Branch.byId(spec[0]), Integer.parseInt(spec[1]));
            }
            if (res == null) break;
            var leftover = p.getInventory().addItem(res);
            for (ItemStack rest : leftover.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), rest);
            }
            done++;
            if (!leftover.isEmpty()) break;
        }
        if (done > 0) {
            inv.setResult(null);
            p.updateInventory();
            Msg.dm(p, "&aКрафт выполнен: &e×" + done);
            p.playSound(p.getLocation(), "minecraft:entity.experience_orb.pickup", 0.7f, 1.4f);
        } else {
            Msg.dm(p, "&cНет места в инвентаре.");
        }
    }

    private static void consume(CraftingInventory inv) {
        ItemStack[] matrix = inv.getMatrix();
        for (int i = 0; i < matrix.length; i++) {
            ItemStack it = matrix[i];
            if (it == null || it.getType().isAir()) continue;
            if (it.getAmount() > 1) {
                it.setAmount(it.getAmount() - 1);
                inv.setItem(i + 1, it);
            } else {
                inv.setItem(i + 1, null);
            }
        }
    }

    /** Самопроверка: все рецепты строим матрицами и матчим (без коллизий). */
    public static int selftest(StringBuilder log) {
        int fail = 0;
        // компрессор
        ItemStack[] m = new ItemStack[9];
        m[1] = new ItemStack(Material.PISTON);
        m[3] = Items.build("node_power");
        m[4] = Items.build("reinforced_frame");
        m[5] = new ItemStack(Material.PISTON);
        m[7] = ru.deepprogression.tools.Items.medalItem(Branch.COPPER, 3);
        if (!"__compressor__".equals(matchMatrix(m))) { fail++; log.append("FAIL: компрессор не матчится\n"); }
        // подмена медали — не матч
        m[7] = Items.medalItem(Branch.IRON, 3);
        if (matchMatrix(m) != null) { fail++; log.append("FAIL: компрессор матчится с чужой медалью\n"); }
        // посохи всех веток, все тиры
        for (Magic.StaffDef d : Magic.all()) {
            Branch b = d.branch();
            for (int tier = 1; tier <= 3; tier++) {
                ItemStack[] sm = new ItemStack[9];
                sm[1] = Items.build(Conf.component(tier));
                sm[4] = ItemDefs.substance(b, tier);
                sm[7] = Items.medalItem(b, Conf.craftGateTier());
                String expect = b.name() + ":" + tier;
                String got = matchMatrix(sm);
                if (!expect.equals(got)) {
                    fail++;
                    log.append("FAIL: посох ").append(b.name()).append(" тир ").append(tier)
                            .append(" матч: ").append(got).append('\n');
                }
                // чужая медаль — не матч (медаль заведомо другой ветки)
                Branch wrong = b == Branch.IRON ? Branch.COAL : Branch.IRON;
                sm[7] = Items.medalItem(wrong, 3);
                if (matchMatrix(sm) != null) {
                    fail++;
                    log.append("FAIL: посох ").append(b.name()).append(" крафтится с чужой медалью\n");
                }
            }
        }
        return fail;
    }
}
