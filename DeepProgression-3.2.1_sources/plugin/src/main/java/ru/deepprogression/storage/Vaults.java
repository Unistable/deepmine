package ru.deepprogression.storage;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.deepprogression.gui.Gui;
import ru.deepprogression.model.Blocks;
import ru.deepprogression.tools.ChunkDrill;
import ru.deepprogression.tools.Items;
import ru.deepprogression.util.Cfg;
import ru.deepprogression.util.Keys;
import ru.deepprogression.util.Msg;
import ru.deepprogression.util.Text;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Хранилища руд v2.1:
 *  - Рудный тайник (портативный, PDC-строка "MATERIAL:count,...");
 *    Сессия GUI: предмет блокируется от перемещений (антидюп D2),
 *    сохранение строго в предмет с сессионным маркером.
 *  - Рудный склад (блок, ванильный инвентарь, фильтр хопперов — в Listeners).
 */
public final class Vaults {

    private Vaults() {}

    /** Открытые сессии: UUID игрока -> маркер сессии тайника. */
    private static final Map<UUID, String> OPEN_SESSIONS = new HashMap<>();
    /** Скрытый остаток, не поместившийся в окно GUI (страховка от потери). */
    private static final Map<UUID, Map<Material, Integer>> HIDDEN = new HashMap<>();

    // ===================== ТАЙНИК =====================

    public static ItemStack findPouch(Player p) {
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it == null || it.getType().isAir()) continue;
            if ("ore_pouch".equals(Items.toolId(it))) return it;
        }
        return null;
    }

    private static String sessionOf(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer()
                .get(Keys.POUCH_SESSION, PersistentDataType.STRING);
    }

    /** Читает содержимое тайника из PDC. */
    public static Map<Material, Integer> readContents(ItemStack pouch) {
        ItemMeta meta = pouch.getItemMeta();
        if (meta == null) return new LinkedHashMap<>();
        return ChunkDrill.deserialize(meta.getPersistentDataContainer()
                .getOrDefault(Keys.POUCH_CONTENTS, PersistentDataType.STRING, ""));
    }

    private static void writeContents(ItemStack pouch, Map<Material, Integer> map) {
        ItemMeta meta = pouch.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(Keys.POUCH_CONTENTS,
                PersistentDataType.STRING, ChunkDrill.serialize(map));
        pouch.setItemMeta(meta);
    }

    private static int totalUnits(Map<Material, Integer> map) {
        int t = 0;
        for (int v : map.values()) t += v;
        return t;
    }

    /**
     * Маршрут руды в тайники (v2.1: заполняет ВСЕ тайники в инвентаре по очереди).
     * @return остаток, не поместившийся в тайники.
     */
    public static int route(Player p, Material m, int count) {
        if (count <= 0) return 0;
        if (!Blocks.isVaultItem(m)) return count;
        int left = count;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it == null || it.getType().isAir()) continue;
            if (!"ore_pouch".equals(Items.toolId(it))) continue;
            Map<Material, Integer> map = readContents(it);
            int free = Cfg.pouchMaxUnits() - totalUnits(map);
            if (free <= 0) continue;
            int add = Math.min(left, free);
            map.merge(m, add, Integer::sum);
            writeContents(it, map);
            left -= add;
            if (left <= 0) return 0;
        }
        return left;
    }

    /** Shift+ПКМ по тайнику: ссыпка всей руды из инвентаря. */
    public static void sweepToPouch(Player p) {
        if (findPouch(p) == null) return;
        int moved = 0;
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType().isAir()) continue;
            if (!Blocks.isVaultItem(it.getType())) continue;
            int routed = route(p, it.getType(), it.getAmount());
            if (routed >= it.getAmount()) {
                moved += it.getAmount();
                p.getInventory().setItem(i, null);
            } else if (routed > 0) {
                moved += routed;
                it.setAmount(it.getAmount() - routed);
            }
        }
        if (moved > 0) {
            Msg.send(p, "pouch.swept", "n", Text.num(moved));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        } else {
            Msg.send(p, "pouch.nothing");
        }
    }

    /** ПКМ по тайнику: открыть GUI (27 слотов) на конкретном тайнике. */
    public static void openPouch(Player p, ItemStack hand) {
        ItemStack pouch = "ore_pouch".equals(Items.toolId(hand)) ? hand : findPouch(p);
        if (pouch == null) return;

        Map<Material, Integer> map = readContents(pouch);
        // Сессионный маркер — привязка GUI к конкретному предмету (антидюп D2)
        String session = UUID.randomUUID().toString();
        ItemMeta meta = pouch.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(Keys.POUCH_SESSION,
                PersistentDataType.STRING, session);
        pouch.setItemMeta(meta);
        OPEN_SESSIONS.put(p.getUniqueId(), session);

        render(p, map);
    }

    private static void render(Player p, Map<Material, Integer> map) {
        Gui.Holder h = new Gui.Holder(Gui.MenuType.POUCH);
        Inventory inv = Bukkit.createInventory(h, 27, Msg.plain("pouch.title"));
        h.bind(inv);
        // Единицы -> стаки
        int slot = 0;
        for (Map.Entry<Material, Integer> e : map.entrySet()) {
            int left = e.getValue();
            int max = Math.max(1, e.getKey().getMaxStackSize());
            while (left > 0 && slot < 27) {
                int stack = Math.min(max, left);
                left -= stack;
                inv.setItem(slot++, new ItemStack(e.getKey(), stack));
            }
            if (slot >= 27 && left > 0) {
                // не поместилось в окно — вернём при закрытии (страховка от потери)
                HIDDEN.computeIfAbsent(p.getUniqueId(), k -> new LinkedHashMap<>())
                        .merge(e.getKey(), left, Integer::sum);
            }
        }
        // Маркер пустых слотов со статусом заполненности
        int inPouch = Math.min(totalUnits(map), Cfg.pouchMaxUnits());
        ItemStack marker = markerItem(inPouch);
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, marker.clone());
        }
        p.openInventory(inv);
    }

    private static ItemStack markerItem(int inPouch) {
        ItemStack info = new ItemStack(Material.GRAY_DYE);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName(Text.color(Msg.plain("pouch.empty-slot")));
        String[] loreSrc = Msg.plain("pouch.empty-slot-lore").split("\\|");
        java.util.List<String> lore = new java.util.ArrayList<>();
        for (String l : loreSrc) lore.add(Text.color(l
                .replace("{n}", Text.num(inPouch))
                .replace("{max}", Text.num(Cfg.pouchMaxUnits()))));
        im.setLore(lore);
        info.setItemMeta(im);
        return info;
    }

    /** Перерисовка открытого тайника (после депозита из GUI-фильтра). */
    private static void refresh(Player p) {
        String session = OPEN_SESSIONS.get(p.getUniqueId());
        if (session == null) return;
        ItemStack pouch = findSessionPouch(p, session);
        if (pouch == null) return;
        Map<Material, Integer> map = readContents(pouch);
        Inventory top = p.getOpenInventory().getTopInventory();
        for (int i = 0; i < top.getSize() && i < 27; i++) top.setItem(i, null);
        renderInto(top, map);
        int inPouch = Math.min(totalUnits(map), Cfg.pouchMaxUnits());
        ItemStack marker = markerItem(inPouch);
        for (int i = 0; i < top.getSize() && i < 27; i++) {
            if (top.getItem(i) == null) top.setItem(i, marker.clone());
        }
    }

    private static void renderInto(Inventory inv, Map<Material, Integer> map) {
        int slot = 0;
        for (Map.Entry<Material, Integer> e : map.entrySet()) {
            int left = e.getValue();
            int max = Math.max(1, e.getKey().getMaxStackSize());
            while (left > 0 && slot < 27) {
                int stack = Math.min(max, left);
                left -= stack;
                inv.setItem(slot++, new ItemStack(e.getKey(), stack));
            }
            if (slot >= 27) break;
        }
    }

    /** Поиск тайника по сессионному маркеру: инвентарь + оффхенд + курсор. */
    private static ItemStack findSessionPouch(Player p, String session) {
        if (session == null) return null;
        ItemStack cur = p.getItemOnCursor();
        if (sessionOf(cur) != null && sessionOf(cur).equals(session)) return cur;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            String s = sessionOf(it);
            if (s != null && s.equals(session)) return it;
        }
        ItemStack off = p.getInventory().getItemInOffHand();
        String s = sessionOf(off);
        return s != null && s.equals(session) ? off : null;
    }

    /** Является ли предмет тайником текущей открытой сессии игрока (блокировка D2). */
    public static boolean isSessionPouch(Player p, ItemStack it) {
        String session = OPEN_SESSIONS.get(p.getUniqueId());
        if (session == null || it == null || it.getType().isAir() || !it.hasItemMeta()) return false;
        String s = sessionOf(it);
        return session.equals(s);
    }

    /** Сохранение тайника при закрытии GUI (строго в предмет сессии). */
    public static void savePouch(Player p, Inventory top) {
        String session = OPEN_SESSIONS.remove(p.getUniqueId());
        Map<Material, Integer> hidden = HIDDEN.remove(p.getUniqueId());
        if (session == null) return;
        ItemStack pouch = findSessionPouch(p, session);
        if (pouch == null) {
            // Предмет исчез из инвентаря (заблокировано фильтром кликов —
            // сюда попадаем только при форс-мажоре). Ничего не возвращаем:
            // содержимое PDC остаётся у предмета, где бы он ни был.
            pluginLog(p, "Тайник не найден при сохранении сессии; содержимое GUI не сохранено.");
            return;
        }
        Map<Material, Integer> map = new LinkedHashMap<>();
        for (ItemStack it : top.getContents()) {
            if (it == null || it.getType().isAir()) continue;
            if (it.getType() == Material.GRAY_DYE) continue;
            map.merge(it.getType(), it.getAmount(), Integer::sum);
        }
        // Скрытый остаток, не поместившийся в окно при открытии
        if (hidden != null) {
            for (var e : hidden.entrySet()) map.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        writeContents(pouch, map);
    }

    /** Принудительный сброс сессии (выход игрока). */
    public static void clearSession(UUID uuid) {
        OPEN_SESSIONS.remove(uuid);
        HIDDEN.remove(uuid);
    }

    private static void pluginLog(Player p, String msg) {
        var pl = ru.deepprogression.DeepProgressionPlugin.get();
        if (pl != null) pl.getLogger().warning("[pouch:" + p.getName() + "] " + msg);
    }

    /**
     * Фильтр кликов в GUI тайника v2.1:
     *  - тайник текущей сессии заблокирован от любых перемещений (антидюп);
     *  - Shift+клик по руде снизу — ручной депозит в PDC + перерисовка;
     *  - NUMBER_KEY поверх маркера — тоже депозит (маркер не попадает в хотбар);
     *  - кладка не-руды запрещена.
     */
    public static void handlePouchClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        boolean clickingTop = e.getClickedInventory() == top;
        ItemStack cur = e.getCurrentItem();
        ItemStack cursor = e.getCursor();

        // D2: тайник сессии нельзя трогать вообще (в любом инвентаре)
        if (isSessionPouch(p, cur) || isSessionPouch(p, cursor)) {
            e.setCancelled(true);
            return;
        }

        // Маркер пустого слота
        if (clickingTop && cur != null && cur.getType() == Material.GRAY_DYE) {
            boolean oreCursor = cursor != null && !cursor.getType().isAir()
                    && Blocks.isVaultItem(cursor.getType());
            if (e.getClick() == ClickType.NUMBER_KEY && oreCursor) {
                // цифра поверх маркера — депозит всего стека (U2)
                e.setCancelled(true);
                ItemStack hotbar = p.getInventory().getItem(e.getHotbarButton());
                if (hotbar != null && Blocks.isVaultItem(hotbar.getType())) {
                    depositFrom(p, e.getHotbarButton());
                    refresh(p);
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
                }
                return;
            }
            boolean placingOre = oreCursor
                    && (e.getClick() == ClickType.LEFT || e.getClick() == ClickType.RIGHT);
            if (!placingOre) e.setCancelled(true);
            return;
        }

        // Shift+клик из нижнего инвентаря: ручной депозит (маркеры не мешают, U1)
        if (!clickingTop && (e.getClick() == ClickType.SHIFT_LEFT
                || e.getClick() == ClickType.SHIFT_RIGHT)) {
            e.setCancelled(true);
            if (cur != null && !cur.getType().isAir() && Blocks.isVaultItem(cur.getType())) {
                depositFrom(p, e.getSlot());
                refresh(p);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
            }
            return;
        }

        switch (e.getClick()) {
            case SHIFT_LEFT, SHIFT_RIGHT -> {
                // Shift по верхнему инвентарю — забор в инвентарь (ванильно)
                if (!clickingTop) e.setCancelled(true);
                return;
            }
            case NUMBER_KEY -> {
                if (!clickingTop) return;
                ItemStack hotbar = p.getInventory().getItem(e.getHotbarButton());
                if (hotbar != null && !hotbar.getType().isAir()
                        && !Blocks.isVaultItem(hotbar.getType())) e.setCancelled(true);
                return;
            }
            default -> {
                if (!clickingTop) return;
                if (cursor != null && !cursor.getType().isAir()
                        && !Blocks.isVaultItem(cursor.getType())) e.setCancelled(true);
            }
        }
    }

    /** Ручной депозит: руда из слота инвентаря игрока -> PDC тайника сессии. */
    private static void depositFrom(Player p, int slot) {
        String session = OPEN_SESSIONS.get(p.getUniqueId());
        if (session == null) return;
        ItemStack pouch = findSessionPouch(p, session);
        ItemStack src = p.getInventory().getItem(slot);
        if (pouch == null || src == null || src.getType().isAir()
                || !Blocks.isVaultItem(src.getType())) return;
        int amount = src.getAmount();
        int routed = route(p, src.getType(), amount);
        if (routed >= amount) {
            p.getInventory().setItem(slot, null);
        } else if (routed > 0) {
            src.setAmount(amount - routed);
        } else {
            Msg.send(p, "pouch.nothing");
        }
    }

    // ===================== СКЛАД (блок) =====================

    /** Типы блоков машин — быстрый пре-чек перед TileState (perf U50). */
    public static final Set<Material> MACHINE_MATS = new HashSet<>(List.of(
            Material.BARREL, Material.FURNACE, Material.SMOKER,
            Material.STONECUTTER, Material.COMPOSTER, Material.BLAST_FURNACE,
            Material.GRINDSTONE, Material.LECTERN, Material.TRAPPED_CHEST,
            Material.BREWING_STAND, Material.ENCHANTING_TABLE,
            Material.DECORATED_POT,
            // v3.2: блоки вторых машин
            Material.LOOM, Material.BEEHIVE, Material.CHISELED_BOOKSHELF,
            Material.CONDUIT, Material.BELL, Material.DAYLIGHT_DETECTOR,
            Material.HOPPER, Material.RESPAWN_ANCHOR, Material.BEACON,
            Material.JUKEBOX, Material.matchMaterial("CALIBRATED_SCULK_SENSOR")));

    public static String machineId(Block b) {
        if (b == null || !MACHINE_MATS.contains(b.getType())) return null;
        String id;
        var state = b.getState(false); // Paper: без копии-снапшота
        if (state instanceof TileState ts) {
            id = ts.getPersistentDataContainer()
                    .get(Keys.MACHINE, PersistentDataType.STRING);
        } else if (CHUNK_MACHINE_MATS.contains(b.getType())) {
            // v3.2: машины на блоках без block-entity — тег в PDC чанка
            id = b.getChunk().getPersistentDataContainer()
                    .get(chunkMachineKey(b), PersistentDataType.STRING);
        } else {
            return null;
        }
        // v3.2: валидация тега (зачистка устаревших тегов после WorldEdit/setblock:
        // материал блока обязан совпадать с иконкой машины)
        if (id == null) return null;
        ru.deepprogression.tools.Tools.ToolSpec s = ru.deepprogression.tools.Tools.get(id);
        return (s != null && s.icon == b.getType()) ? id : null;
    }

    /** Блоки машин без block-entity (тег хранится в PDC чанка). */
    public static final Set<Material> CHUNK_MACHINE_MATS = new HashSet<>(List.of(
            Material.STONECUTTER, Material.COMPOSTER, Material.GRINDSTONE,
            Material.LOOM, Material.RESPAWN_ANCHOR));

    /** Ключ PDC чанка для тега машины по позиции. */
    public static org.bukkit.NamespacedKey chunkMachineKey(Block b) {
        return new org.bukkit.NamespacedKey(ru.deepprogression.DeepProgressionPlugin.get(),
                "m_" + b.getX() + "_" + b.getY() + "_" + b.getZ());
    }

    /** Ключ PDC чанка для кулдауна машины по позиции. */
    public static org.bukkit.NamespacedKey chunkCooldownKey(Block b) {
        return new org.bukkit.NamespacedKey(ru.deepprogression.DeepProgressionPlugin.get(),
                "c_" + b.getX() + "_" + b.getY() + "_" + b.getZ());
    }

    /** Shift+ПКМ по складу с рудой: ссыпка инвентаря в блок. */
    public static void sweepToVault(Player p, Block vault) {
        var state = vault.getState();
        if (!(state instanceof org.bukkit.block.Container container)) return;
        Inventory inv = container.getInventory();
        int moved = 0;
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType().isAir()) continue;
            if (!Blocks.isVaultItem(it.getType())) continue;
            var leftover = inv.addItem(it.clone());
            if (leftover.isEmpty()) {
                moved += it.getAmount();
                p.getInventory().setItem(i, null);
            } else {
                int notAdded = leftover.values().stream()
                        .mapToInt(ItemStack::getAmount).sum();
                moved += it.getAmount() - notAdded;
                it.setAmount(notAdded);
                break; // склад полон
            }
        }
        if (moved > 0) {
            Msg.send(p, "vault.swept", "n", Text.num(moved));
            p.playSound(vault.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.7f, 1.3f);
        } else {
            Msg.send(p, "vault.full-or-nothing");
        }
    }

    /** Очистка служебных структур (вызывается при отключении плагина). */
    public static void clearAll() {
        OPEN_SESSIONS.clear();
        HIDDEN.clear();
    }
}
