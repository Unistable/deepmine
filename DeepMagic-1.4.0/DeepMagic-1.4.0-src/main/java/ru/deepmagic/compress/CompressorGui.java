package ru.deepmagic.compress;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.deepmagic.model.ItemDefs;
import ru.deepmagic.util.Conf;
import ru.deepmagic.util.Msg;
import ru.deepmagic.util.T;
import ru.deepprogression.model.Branch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GUI компрессора v2 (54 слота, 6×9):
 *   ряд 0   — корпус (пурпурная рамка) + метки зон (2 — вход, 6 — выход)
 *             + книга-справка (4);
 *   ряды 1–4 — ВХОД (колонки 1–3) | ЦЕНТР (топливо 13, ход сжатия 22,
 *              индикатор 31, режим 40) | ВЫХОД (колонки 5–7);
 *   ряд 5   — корпус + «Забрать всё» (47) и «Закрыть» (49).
 * Живая перерисовка: Compressors.tick() вызывает refresh(block) — прогресс,
 * лампа и стеки обновляются каждые 2 с без переоткрытия.
 * Закладка — снаружи (Shift+ПКМ по блоку), внутри GUI только забор.
 * Все видимые строки — через T.color: сырых '&' быть не может.
 */
public final class CompressorGui {

    /** Позиции зон (ряд-главный порядок). */
    private static final int[] INPUT_SLOTS =
            {10, 11, 12, 19, 20, 21, 28, 29, 30, 37, 38, 39};
    private static final int[] OUTPUT_SLOTS =
            {14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43};
    private static final int SLOT_FUEL = 13;
    private static final int SLOT_PROGRESS = 22;
    private static final int SLOT_LAMP = 31;
    private static final int SLOT_MODE = 40;
    private static final int SLOT_TAKE_ALL = 47;
    private static final int SLOT_CLOSE = 49;

    /** Записи входа: обычный ресурс или вещество на пересжатие. */
    record InEntry(Material res, Branch branch, Long qty, String subKey, Integer subTier) {}

    public static final class Holder implements InventoryHolder {
        public final Block block;
        public final Inventory inv;
        /** Снимок раскладки входа на момент рендера (клик валидируется по нему —
         *  анти-гонка: между рендером и кликом тик мог израсходовать ресурс). */
        public List<InEntry> inSnapshot = List.of();
        /** Снимок порядка ключей выхода ("BRANCH:tier"). */
        public List<String> outSnapshot = List.of();

        public Holder(Block block) {
            this.block = block;
            this.inv = Bukkit.createInventory(this, 54, titleFor(block));
        }
        @Override public Inventory getInventory() { return inv; }
    }

    /** Открытые GUI: ключ компрессора → держатели (для живой перерисовки). */
    private static final Map<String, List<Holder>> OPEN = new LinkedHashMap<>();

    private CompressorGui() {}

    /** Заголовок окна: градиент MiniMessage + координаты машины. */
    public static Component titleFor(Block b) {
        return MiniMessage.miniMessage().deserialize(
                "<gradient:#c77dff:#f0abfc><bold>☠ Магический Компрессор</bold></gradient>"
                        + " <dark_gray>· " + b.getX() + "," + b.getY() + "," + b.getZ());
    }

    // ---------- открытие/закрытие/перерисовка ----------

    public static void open(Player p, Block b) {
        Holder h = new Holder(b);
        render(h, b);
        OPEN.computeIfAbsent(Compressors.keyOf(b), k -> new ArrayList<>()).add(h);
        p.playSound(p.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.5f, 1.4f);
        p.openInventory(h.inv);
    }

    /** Вызывается из InventoryCloseEvent — снимаем с живой перерисовки. */
    public static void closed(Holder h) {
        List<Holder> list = OPEN.remove(Compressors.keyOf(h.block));
        if (list != null) {
            list.remove(h);
            if (!list.isEmpty()) OPEN.put(Compressors.keyOf(h.block), list);
        }
    }

    /** Живое обновление открытых GUI компрессора (вызывается из тика). */
    public static void refresh(Block b) {
        List<Holder> list = OPEN.get(Compressors.keyOf(b));
        if (list == null || list.isEmpty()) return;
        list.removeIf(h -> h.inv.getViewers().isEmpty());
        if (list.isEmpty()) {
            OPEN.remove(Compressors.keyOf(b));
            return;
        }
        for (Holder h : list) render(h, b);
    }

    /** Закрыть все окна этого компрессора (поломка блока — анти-«призрак»). */
    public static void closeAll(Block b) {
        List<Holder> list = OPEN.remove(Compressors.keyOf(b));
        if (list == null) return;
        for (Holder h : list) {
            // копия: closeInventory() модифицирует живой список viewers → CME
            for (org.bukkit.entity.HumanEntity v : List.copyOf(h.inv.getViewers())) {
                v.closeInventory();
            }
        }
        list.clear();
    }

    // ---------- данные ----------

    /** Вход: материалы-ресурсы (крем отдельно) + вещества на пересжатие. */
    private static List<InEntry> inputEntries(Block b) {
        List<InEntry> list = new ArrayList<>();
        for (Map.Entry<Material, Long> e : Compressors.readMap(b, Compressors.TAG_IN).entrySet()) {
            if (e.getKey() == Material.MAGMA_CREAM) continue;
            Branch br = ru.deepprogression.model.Blocks.branchOfItem(e.getKey());
            if (br == null) continue;
            list.add(new InEntry(e.getKey(), br, e.getValue(), null, null));
        }
        for (Map.Entry<String, Long> e : Compressors.readTriple(b, Compressors.TAG_SUBIN).entrySet()) {
            String[] spec = e.getKey().split(":");
            Branch br = Branch.byId(spec[0]);
            int tier;
            try {
                tier = Integer.parseInt(spec[1]);
            } catch (NumberFormatException ex) {
                continue;
            }
            if (br != null) {
                list.add(new InEntry(null, br, e.getValue(), e.getKey(), tier));
            }
        }
        return list;
    }

    private static long fuelOf(Block b) {
        return Compressors.readMap(b, Compressors.TAG_IN)
                .getOrDefault(Material.MAGMA_CREAM, 0L);
    }

    // ---------- рендер ----------

    public static void render(Holder h, Block b) {
        h.inv.clear();

        List<InEntry> in = inputEntries(b);
        Map<String, Long> subprog = Compressors.readTriple(b, Compressors.TAG_SUBPROG);
        Map<Material, Long> prog = Compressors.readMap(b, Compressors.TAG_PROG);
        Map<String, Long> out = Compressors.readTriple(b, Compressors.TAG_OUT);
        long fuel = fuelOf(b);

        // Снимки раскладки ДЛЯ КЛИКОВ: порядок и состав ровно как на витрине
        List<String> validOut = new ArrayList<>();
        for (String k : out.keySet()) {
            Branch bb = Branch.byId(k.split(":")[0]);
            if (bb != null && ru.deepmagic.model.Magic.def(bb) != null) validOut.add(k);
        }
        h.inSnapshot = List.copyOf(in);
        h.outSnapshot = List.copyOf(validOut);

        // ---- корпус и шапка ----
        for (int slot = 0; slot < 54; slot++) {
            if (isBorder(slot)) h.inv.setItem(slot, pane(Material.PURPLE_STAINED_GLASS_PANE, ""));
        }
        h.inv.setItem(2, pane(Material.LIME_STAINED_GLASS_PANE, "&a▼ Вход",
                "&7Материалы и вещества на пересжатии.",
                "&8Клик по предмету — забрать обратно."));
        h.inv.setItem(6, pane(Material.YELLOW_STAINED_GLASS_PANE, "&e▼ Выход",
                "&7Готовые сверхспрессованные вещества.",
                "&8Клик — забрать &7(Shift — пачку&8)."));
        h.inv.setItem(4, info(Material.ENCHANTED_BOOK, "&5&l☠ Магический Компрессор",
                "&7Место: &f" + b.getX() + ", " + b.getY() + ", " + b.getZ(),
                "",
                "&7Закладка ресурса: &fShift+ПКМ &7по компрессору",
                "&7с ресурсом DeepProgression в руке.",
                "&7Топливо: &6магмовый крем &8(" + Conf.fuelPerCycle()
                        + " шт/" + T.sec(Conf.cycleTicks()) + ")",
                "",
                "&dВещество → верстак → посох. &d/mage handbook <ветка>"));

        // ---- вход ----
        int shown = 0;
        for (InEntry e : in) {
            if (shown >= INPUT_SLOTS.length - 1) break;
            h.inv.setItem(INPUT_SLOTS[shown++], inputItem(e, b, prog, subprog));
        }
        if (in.size() > shown) {
            List<String> more = new ArrayList<>();
            more.add(T.color("&7Ещё в буфере:"));
            int extra = 0;
            for (int i = shown; i < in.size() && extra < 4; i++, extra++) {
                InEntry e = in.get(i);
                more.add(T.color("&8 · " + zoneName(e)));
            }
            int hidden = in.size() - shown;
            more.add(T.color("&8Клик по предметам выше —"));
            more.add(T.color("&8забор пачками."));
            h.inv.setItem(INPUT_SLOTS[INPUT_SLOTS.length - 1],
                    info(Material.PAPER, "&e+ещё " + hidden, more.toArray(new String[0])));
        } else {
            boolean hintPlaced = false;
            for (int slot : INPUT_SLOTS) {
                if (h.inv.getItem(slot) != null) continue;
                if (!hintPlaced) {
                    h.inv.setItem(slot, pane(Material.GRAY_STAINED_GLASS_PANE, "&8Пусто",
                            "&7Заложите ресурс: &fShift+ПКМ", "&7по компрессору с предметом."));
                    hintPlaced = true;
                } else {
                    h.inv.setItem(slot, pane(Material.GRAY_STAINED_GLASS_PANE, "&8·"));
                }
            }
        }

        // ---- центр: топливо ----
        if (fuel > 0) {
            ItemStack f = info(Material.MAGMA_CREAM, "&6Топливо: магмовый крем",
                    "&7В буфере: &e" + T.num(fuel) + " шт",
                    "&7Расход: &e" + Conf.fuelPerCycle() + " шт &7за цикл (&e"
                            + T.sec(Conf.cycleTicks()) + "&7)",
                    "",
                    "&eЛКМ — забрать крем обратно");
            f.setAmount(Math.max(1, (int) Math.min(f.getMaxStackSize(), fuel)));
            h.inv.setItem(SLOT_FUEL, f);
        } else {
            h.inv.setItem(SLOT_FUEL, pane(Material.GRAY_STAINED_GLASS_PANE, "&8Топлива нет",
                    "&7Shift+ПКМ по компрессору", "&7с магмовым кремом в руке."));
        }

        // ---- центр: ход сжатия ----
        h.inv.setItem(SLOT_PROGRESS, progressItem(in, prog, subprog));

        // ---- центр: индикатор ----
        boolean hasJobs = !in.isEmpty();
        boolean fuelOk = fuel >= Conf.fuelPerCycle();
        String lampName;
        List<String> lampLore = new ArrayList<>();
        if (hasJobs && fuelOk) {
            lampName = "&a✔ Работа идёт";
            lampLore.add("&7Цикл: &e" + T.sec(Conf.cycleTicks()) + " &8· &e"
                    + T.num(Conf.unitsPerCycle()) + " ед. сжатия");
            lampLore.add("&7Заданий в буфере: &e" + in.size());
        } else if (hasJobs) {
            lampName = "&c⛔ Нет топлива";
            lampLore.add("&7Конвейер стоит: нужен магмовый крем");
            lampLore.add("&7(" + Conf.fuelPerCycle() + " шт на цикл).");
        } else {
            lampName = "&8◇ Простой";
            lampLore.add("&7Буфер входа пуст.");
            lampLore.add("&7Закладка: &fShift+ПКМ &7по компрессору.");
        }
        Material lampMat = hasJobs && fuelOk ? Material.LIME_CONCRETE
                : hasJobs ? Material.RED_CONCRETE : Material.GRAY_CONCRETE;
        h.inv.setItem(SLOT_LAMP, info(lampMat, lampName, lampLore.toArray(new String[0])));

        // ---- центр: режим ----
        h.inv.setItem(SLOT_MODE, info(Material.PISTON, "&5Режим сжатия",
                "&7Цикл: &e" + T.num(Conf.unitsPerCycle()) + " ед. &7за &e"
                        + T.sec(Conf.cycleTicks()) + "",
                "&7Партия: &e" + T.num(Conf.unitsFor(Branch.Rarity.COMMON))
                        + " &8обычная · &e" + T.num(Conf.unitsFor(Branch.Rarity.MEDIUM))
                        + " &8средняя · &e" + T.num(Conf.unitsFor(Branch.Rarity.RARE)) + " &8редкая",
                "&7Пересжатие: &e3 вещества &7→ следующий тир",
                "&7Гейты: медаль ветки &e3+&7, пересжатие &e6/9"));

        // ---- выход ----
        int o = 0;
        int hiddenOut = 0;
        List<String> outMoreNames = new ArrayList<>();
        for (String key : validOut) {
            String[] spec = key.split(":");
            Branch br = Branch.byId(spec[0]);
            long cnt = out.getOrDefault(key, 0L);
            if (o >= OUTPUT_SLOTS.length - 1) {
                hiddenOut++;
                if (outMoreNames.size() < 4) {
                    outMoreNames.add("&8 · " + zoneName(br, safeTier(spec[1])));
                }
                continue;
            }
            ItemStack st = ItemDefs.substance(br, safeTier(spec[1]));
            if (st == null) continue;
            ItemMeta meta = st.getItemMeta();
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            lore.add(T.color(""));
            lore.add(T.color("&6В компрессоре: &e" + T.num(cnt) + " шт"));
            lore.add(T.color("&eЛКМ — забрать &8· &eShift — пачку"));
            meta.setLore(lore);
            st.setItemMeta(meta);
            showQty(st, cnt);
            h.inv.setItem(OUTPUT_SLOTS[o++], st);
        }
        if (hiddenOut > 0) {
            List<String> more = new ArrayList<>();
            more.add(T.color("&7Ещё в выходе:"));
            more.addAll(outMoreNames);
            h.inv.setItem(OUTPUT_SLOTS[OUTPUT_SLOTS.length - 1],
                    info(Material.PAPER, "&e+ещё " + hiddenOut, more.toArray(new String[0])));
        } else {
            boolean hint = false;
            for (int slot : OUTPUT_SLOTS) {
                if (h.inv.getItem(slot) != null) continue;
                h.inv.setItem(slot, pane(Material.GRAY_STAINED_GLASS_PANE,
                        hint ? "&8·" : "&8Выход пуст"));
                hint = true;
            }
        }

        // ---- кнопки ----
        h.inv.setItem(SLOT_TAKE_ALL, info(Material.HOPPER, "&6Забрать всё",
                "&7Весь выход — в инвентарь одним кликом.",
                "&8Лишнее упадёт к ногам."));
        h.inv.setItem(SLOT_CLOSE, info(Material.BARRIER, "&cЗакрыть", "&8ESC тоже работает"));
    }

    /** Имя записи входа (для списков «ещё N»). */
    private static String zoneName(InEntry e) {
        if (e.subKey() == null) return e.branch().color + e.branch().display;
        return e.branch().color + e.branch().display + " " + T.roman(e.subTier());
    }

    private static String zoneName(Branch br, int tier) {
        return br.color + br.display + " " + T.roman(tier);
    }

    /** Предмет входной зоны: ресурс или вещество (клик — забрать). */
    private static ItemStack inputItem(InEntry e, Block b, Map<Material, Long> prog,
                                       Map<String, Long> subprog) {
        if (e.subKey() == null) {
            long need = Compressors.batchNeed(e.branch());
            long done = prog.getOrDefault(e.res(), 0L);
            ItemStack it = info(e.res(),
                    e.branch.color + e.branch.display + " &8(вход)",
                    "&7Количество: &e" + T.num(e.qty()) + " шт",
                    "&7Партия: &e" + T.num(done) + "&7/&e" + T.num(need) + " &8("
                            + T.bar(done, need, 16) + "&8)",
                    "",
                    "&eЛКМ — забрать всё обратно");
            showQty(it, e.qty());
            return it;
        }
        long done = subprog.getOrDefault(e.subKey(), 0L);
        ItemStack it = info(Material.HEAVY_CORE,
                e.branch.color + e.branch.display + " " + T.roman(e.subTier())
                        + " &8(пересжатие " + T.roman(e.subTier()) + "→" + T.roman(e.subTier() + 1) + ")",
                "&7Количество: &e" + T.num(e.qty()) + " шт &8(серия 3 шт)",
                "&7Прогресс серии: &e" + done + "&7/3 " + T.bar(done, 3, 16),
                "",
                "&eЛКМ — забрать вещество обратно");
        showQty(it, e.qty());
        return it;
    }

    /** Ход сжатия: до 3 активных заданий полосами (6 строк лора). */
    private static ItemStack progressItem(List<InEntry> in, Map<Material, Long> prog,
                                          Map<String, Long> subprog) {
        if (in.isEmpty()) {
            return info(Material.ARROW, "&8⌛ Сжатие: нет заданий",
                    "&7Заложите ресурс — здесь появится", "&7живой прогресс партий.");
        }
        List<String> lore = new ArrayList<>();
        int shownEntries = 0;
        double bestFrac = -1;
        String bestName = null;
        for (InEntry e : in) {
            if (shownEntries >= 3) break;
            if (e.subKey() == null) {
                long need = Compressors.batchNeed(e.branch());
                long done = prog.getOrDefault(e.res(), 0L);
                lore.add(e.branch.color + e.branch.display + " &8· &e"
                        + T.num(done) + "&7/&e" + T.num(need));
                lore.add("&8" + T.bar(done, need, 20));
                double frac = need > 0 ? (double) done / need : 0;
                if (frac > bestFrac) {
                    bestFrac = frac;
                    bestName = e.branch.display;
                }
            } else {
                long done = subprog.getOrDefault(e.subKey(), 0L);
                lore.add(e.branch.color + e.branch.display + " " + T.roman(e.subTier())
                        + "→" + T.roman(e.subTier() + 1) + " &8· &e" + done + "&7/&e3");
                lore.add("&8" + T.bar(done, 3, 20));
                double frac = done / 3.0;
                if (frac > bestFrac) {
                    bestFrac = frac;
                    bestName = e.branch.display + " " + T.roman(e.subTier());
                }
            }
            shownEntries++;
        }
        int more = in.size() - shownEntries;
        if (more > 0) lore.add("&8…и ещё " + more);
        String name = bestName == null ? "&e⌛ Сжатие…"
                : "&e⌛ Сжатие: " + bestName;
        return info(Material.SPECTRAL_ARROW, name, lore.toArray(new String[0]));
    }

    // ---------- клики ----------

    /** Клик по слоту GUI (верхний инвентарь уже отменён слушателем). */
    public static boolean handleClick(Player p, Holder h, int rawSlot, boolean shift) {
        Block b = h.block;
        // Топливо: забрать крем
        if (rawSlot == SLOT_FUEL) {
            long f = fuelOf(b);
            if (f > 0) {
                Map<Material, Long> in = Compressors.readMap(b, Compressors.TAG_IN);
                in.remove(Material.MAGMA_CREAM);
                Compressors.writeMap(b, Compressors.TAG_IN, in);
                giveOrDrop(p, Material.MAGMA_CREAM, f);
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.1f);
                render(h, b);
            }
            return true;
        }
        // Забрать всё из выхода
        if (rawSlot == SLOT_TAKE_ALL) {
            takeAllOutput(p, h, b);
            return true;
        }
        // Закрыть
        if (rawSlot == SLOT_CLOSE) {
            p.closeInventory();
            return true;
        }
        // Выход: забрать вещество (по снимку рендера — анти-гонка с тиком)
        Integer outIdx = zoneIndex(rawSlot, OUTPUT_SLOTS);
        if (outIdx != null) {
            Map<String, Long> out = Compressors.readTriple(b, Compressors.TAG_OUT);
            List<String> snap = h.outSnapshot;
            if (outIdx >= snap.size()) return true;
            if (outIdx == OUTPUT_SLOTS.length - 1 && snap.size() > OUTPUT_SLOTS.length - 1) {
                return true; // это бумага «+ещё N», а не элемент выхода
            }
            String key = snap.get(outIdx);
            Long count = out.get(key);
            if (count == null) {
                render(h, b); // партия ушла между рендером и кликом
                return true;
            }
            String[] spec = key.split(":");
            Branch br = Branch.byId(spec[0]);
            int tier = safeTier(spec[1]);
            if (br == null) return true;
            long take = shift ? Math.min(count, 9L * 9) : 1;
            long taken = 0;
            boolean full = false;
            for (long i = 0; i < take; i++) {
                ItemStack st = ItemDefs.substance(br, tier);
                if (st == null) break;
                var leftover = p.getInventory().addItem(st);
                if (!leftover.isEmpty()) {
                    full = true; // инвентарь полон — предмет ОСТАЁТСЯ в компрессоре
                    break;
                }
                taken++;
            }
            if (taken > 0) {
                out.put(key, count - taken);
                if (out.get(key) <= 0) out.remove(key);
                Compressors.writeTriple(b, Compressors.TAG_OUT, out);
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.3f);
            }
            if (full) Msg.dm(p, "&7Инвентарь полон — вещество осталось в компрессоре.");
            render(h, b);
            return true;
        }
        // Вход: забрать материал/вещество (по снимку рендера)
        Integer inIdx = zoneIndex(rawSlot, INPUT_SLOTS);
        if (inIdx != null) {
            List<InEntry> snap = h.inSnapshot;
            if (inIdx >= snap.size()) return true;
            if (inIdx == INPUT_SLOTS.length - 1 && snap.size() > INPUT_SLOTS.length - 1) {
                return true; // бумага «+ещё N» — не элемент входа
            }
            InEntry e = snap.get(inIdx);
            if (e.subKey() == null) {
                Map<Material, Long> map = Compressors.readMap(b, Compressors.TAG_IN);
                Long qty = map.remove(e.res());
                if (qty != null) {
                    Compressors.writeMap(b, Compressors.TAG_IN, map);
                    giveOrDrop(p, e.res(), qty);
                }
            } else {
                // анти-потеря: возвращаем и очередь, и уже потраченное серии
                Map<String, Long> subin = Compressors.readTriple(b, Compressors.TAG_SUBIN);
                Long qty = subin.remove(e.subKey());
                Map<String, Long> subprog2 = Compressors.readTriple(b, Compressors.TAG_SUBPROG);
                Long spent = subprog2.remove(e.subKey());
                long total = (qty == null ? 0 : qty) + (spent == null ? 0 : spent);
                if (total > 0) {
                    Compressors.writeTriple(b, Compressors.TAG_SUBIN, subin);
                    Compressors.writeTriple(b, Compressors.TAG_SUBPROG, subprog2);
                    long back = 0;
                    for (long i = 0; i < total; i++) {
                        ItemStack st = ItemDefs.substance(e.branch(), e.subTier());
                        if (st == null) break;
                        var leftover = p.getInventory().addItem(st);
                        if (!leftover.isEmpty()) break;
                        back++;
                    }
                    long rest = total - back;
                    if (rest > 0) {
                        // не влезло в инвентарь — честно возвращаем в очередь
                        subin.merge(e.subKey(), rest, Long::sum);
                        Compressors.writeTriple(b, Compressors.TAG_SUBIN, subin);
                        Msg.dm(p, "&7Инвентарь полон: &e" + T.num(rest)
                                + " вещ. &7осталось в компрессоре.");
                    }
                }
            }
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.1f);
            render(h, b);
            return true;
        }
        return true; // корпус/витрина — только отмена (слушатель уже отменил)
    }

    /** «Забрать всё»: выгружаем выход в инвентарь (кап на клик; при полном
     *  инвентаре остаток ОСТАЁТСЯ в компрессоре — анти-потеря). */
    private static void takeAllOutput(Player p, Holder h, Block b) {
        Map<String, Long> out = Compressors.readTriple(b, Compressors.TAG_OUT);
        long taken = 0;
        long cap = 9L * 9 * 9; // 729 предметов за клик — больше не накапливается за циклы
        boolean full = false;
        java.util.Iterator<Map.Entry<String, Long>> it = out.entrySet().iterator();
        while (it.hasNext() && taken < cap && !full) {
            Map.Entry<String, Long> e = it.next();
            String[] spec = e.getKey().split(":");
            Branch br = Branch.byId(spec[0]);
            if (br == null) {
                it.remove();
                continue;
            }
            int tier = safeTier(spec[1]);
            while (e.getValue() > 0 && taken < cap && !full) {
                ItemStack st = ItemDefs.substance(br, tier);
                if (st == null) break;
                var leftover = p.getInventory().addItem(st);
                if (!leftover.isEmpty()) {
                    full = true; // предмет не выдан — счётчик не трогаем
                    break;
                }
                e.setValue(e.getValue() - 1);
                taken++;
            }
            if (e.getValue() <= 0) it.remove();
        }
        Compressors.writeTriple(b, Compressors.TAG_OUT, out);
        if (full) Msg.dm(p, "&7Инвентарь полон — остальное дожидается в компрессоре.");
        if (taken > 0) {
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.5f);
        }
        render(h, b);
    }

    // ---------- утилиты ----------

    private static int safeTier(String s) {
        try {
            return Math.max(1, Math.min(3, Integer.parseInt(s)));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static Integer zoneIndex(int rawSlot, int[] zone) {
        for (int i = 0; i < zone.length; i++) {
            if (zone[i] == rawSlot) return i;
        }
        return null;
    }

    private static boolean isBorder(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        if (row == 0) return slot != 2 && slot != 4 && slot != 6;
        if (row == 5) return slot != SLOT_TAKE_ALL && slot != SLOT_CLOSE;
        return col == 0 || col == 8;
    }

    /** Витринное количество: не выше maxStackSize материала (HEAVY_CORE = 1). */
    private static void showQty(ItemStack it, long qty) {
        it.setAmount((int) Math.max(1, Math.min(it.getMaxStackSize(), qty)));
    }

    /** Выдать totalQty штук СКЛАДИРУЕМОГО материала. addItem() мутирует переданный
     *  стек (частичное слияние/переполнение) — поэтому каждый заход СВЕЖИЙ стек,
     *  а реально добавленное считаем как «amt − возвращённое». Возвращённое (дропнутое)
     *  списывается из квоты ДО fallback — двойного дропа нет. */
    private static void giveOrDrop(Player p, Material m, long totalQty) {
        long left = totalQty;
        int max = Math.max(1, m.getMaxStackSize());
        while (left > 0) {
            int amt = (int) Math.min(max, left);
            ItemStack stack = new ItemStack(m, amt);
            var leftover = p.getInventory().addItem(stack);
            long returned = 0;
            for (ItemStack rest : leftover.values()) {
                if (rest.getAmount() > 0) {
                    p.getWorld().dropItemNaturally(p.getLocation(), rest);
                    returned += rest.getAmount();
                }
            }
            left -= returned; // вернувшееся уже на земле — из квоты списано
            long added = amt - returned;
            if (added <= 0) {
                // инвентарь полон: остаток квоты вываливаем стеками
                while (left > 0) {
                    int a2 = (int) Math.min(max, left);
                    p.getWorld().dropItemNaturally(p.getLocation(), new ItemStack(m, a2));
                    left -= a2;
                }
                return;
            }
            left -= added;
        }
    }

    private static ItemStack pane(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name.isEmpty() ? " " : T.color(name));
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(T.color(s));
        if (!l.isEmpty()) meta.setLore(l);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack info(Material m, String name, String... lore) {
        return pane(m, name, lore);
    }
}
