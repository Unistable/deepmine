package ru.deepprogression.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.deepprogression.model.Branch;
import ru.deepprogression.model.Section;
import ru.deepprogression.progress.PlayerProfile;
import ru.deepprogression.progress.ProfileStore;
import ru.deepprogression.recipe.Recipes;
import ru.deepprogression.storage.Vaults;
import ru.deepprogression.tools.Items;
import ru.deepprogression.tools.Tools;
import ru.deepprogression.util.Cfg;
import ru.deepprogression.util.Msg;
import ru.deepprogression.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** GUI-меню v3: секции → ветки → звания; крафты с пагинацией и фильтром секций. */
public final class Gui {

    public enum MenuType { MAIN, SECTION, BRANCH, CRAFTS, RECIPE, STATS, POUCH,
        PERKS, PERKS_SECTION, ECONOMY }

    public static final class Holder implements InventoryHolder {
        public final MenuType type;
        public Branch branch;
        public Section section;
        public String toolId;
        public int page;
        /** Цепочка «Назад» для вложенных рецептов (U52). */
        public final java.util.Deque<String> back = new java.util.ArrayDeque<>();
        public final Map<Integer, String> links = new HashMap<>();
        private Inventory inv;

        public Holder(MenuType type) { this.type = type; }

        public void bind(Inventory inv) { this.inv = inv; }

        @Override
        public Inventory getInventory() { return inv; }
    }

    private Gui() {}

    private static ItemStack filler() {
        ItemStack it = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(Text.color(Msg.plain("gui.filler")));
        it.setItemMeta(m);
        return it;
    }

    private static void fill(Inventory inv, int size) {
        ItemStack f = filler();
        for (int i = 0; i < size; i++) if (inv.getItem(i) == null) inv.setItem(i, f);
    }

    private static ItemStack button(Material mat, String name, List<String> lore) {
        // v3.2.1: GUI больше не может упасть на блоке без предметной формы
        ItemStack it;
        try {
            it = new ItemStack(mat == null || !ru.deepprogression.model.Blocks.isItemSafe(mat)
                    ? Material.PAPER : mat);
        } catch (IllegalArgumentException notAnItem) {
            it = new ItemStack(Material.PAPER);
        }
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(Text.color(name));
        if (lore != null) m.setLore(lore.stream().map(Text::color).toList());
        it.setItemMeta(m);
        return it;
    }

    /** Секция предмета каталога крафтов. */
    private static Section sectionOf(Tools.ToolSpec s) {
        if (s.type == Tools.ToolType.DRILL) return s.branch != null ? s.branch.section : null;
        if (s.type == Tools.ToolType.UNIVERSAL) return s.section;
        if (s.type == Tools.ToolType.GADGET) return s.section; // v3.2: приспособления секций
        if (s.type == Tools.ToolType.SECTION_SCANNER) return s.section; // v3.2: сканеры видны в своей секции
        if (s.type == Tools.ToolType.CRYSTAL) return s.section;         // v3.2: кристаллы видны в своей секции
        return switch (s.id) {
            case "geologist_pick", "prospector", "battery", "chunk_drill",
                    "charge_station", "geo_scanner" -> Section.MINING;
            case "sawmill", "resinworks" -> Section.WOOD;
            case "composter", "bakery" -> Section.FARM;
            case "herbarium", "nursery" -> Section.FLORA;
            case "dryer", "coral_farm" -> Section.SEA;
            case "glassworks", "stone_works" -> Section.MASON;
            case "luminaforge", "candleworks" -> Section.LIGHT;
            case "assembler", "rail_works" -> Section.ENGINE;
            case "altar", "foundry" -> Section.NETHER;
            case "chorus_reaper", "end_forge" -> Section.END;
            case "echo_chamber", "resonance_chamber" -> Section.ANOMALY;
            default -> null; // тайник/склад — общие
        };
    }

    /** Машина секции (для кнопки в меню секции). */
    private static String machineOfSection(Section s) {
        return switch (s) {
            case MINING -> "geo_scanner";
            case WOOD -> "sawmill";
            case FARM -> "composter";
            case FLORA -> "herbarium";
            case SEA -> "dryer";
            case MASON -> "glassworks";
            case LIGHT -> "luminaforge";
            case ENGINE -> "assembler";
            case NETHER -> "altar";
            case END -> "chorus_reaper";
            case ANOMALY -> "echo_chamber";
        };
    }

    /** Вторая машина секции (v3.2). */
    private static String machine2OfSection(Section s) {
        return switch (s) {
            case MINING -> null; // у шахтёров станция и геосканер
            case WOOD -> "resinworks";
            case FARM -> "bakery";
            case FLORA -> "nursery";
            case SEA -> "coral_farm";
            case MASON -> "stone_works";
            case LIGHT -> "candleworks";
            case ENGINE -> "rail_works";
            case NETHER -> "foundry";
            case END -> "end_forge";
            case ANOMALY -> "resonance_chamber";
        };
    }

    // ---------- Главное меню: сетка секций ----------
    public static final Map<Integer, Section> MAIN_SLOTS = new HashMap<>();

    public static void init() {
        MAIN_SLOTS.clear();
        int[] slots = {10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25};
        Section[] secs = Section.values();
        for (int i = 0; i < slots.length && i < secs.length; i++) {
            MAIN_SLOTS.put(slots[i], secs[i]);
        }
    }

    public static void openMain(Player p) {
        Holder h = new Holder(MenuType.MAIN);
        Inventory inv = Bukkit.createInventory(h, 54, Msg.plain("gui.main-title"));
        h.inv = inv;
        PlayerProfile prof = ProfileStore.get(p.getUniqueId());

        for (var e : MAIN_SLOTS.entrySet()) {
            Section s = e.getValue();
            long total = 0;
            int maxTier = 0;
            for (Branch b : Branch.ofSection(s)) {
                total += prof.getBlocks(b);
                maxTier = Math.max(maxTier, prof.getTier(b));
            }
            List<String> lore = new ArrayList<>();
            lore.add("&7Веток: &e" + Branch.ofSection(s).size()
                    + " &8· Добыто: &e" + Text.num(total) + " &7ед.");
            lore.add("&7Лучшее звание: &f" + (maxTier == 0 ? "—" : "тир " + maxTier));
            lore.add("&8ЛКМ — ветки секции");
            inv.setItem(e.getKey(), button(s.icon, s.color + s.display, lore));
        }

        String[] craftsTxt = Msg.plain("gui.crafts-item").split("\\|");
        List<String> craftsLore = new ArrayList<>();
        for (int i = 1; i < craftsTxt.length; i++) craftsLore.add(craftsTxt[i]);
        inv.setItem(40, button(Material.CRAFTING_TABLE, craftsTxt[0], craftsLore));
        String[] statsTxt = Msg.plain("gui.stats-item", "count", Text.num(prof.score())).split("\\|");
        List<String> statsLore = new ArrayList<>();
        for (int i = 1; i < statsTxt.length; i++) statsLore.add(statsTxt[i]);
        statsLore.add("&7Очки славы: &e" + Text.num(prof.score()));
        inv.setItem(42, button(Material.PLAYER_HEAD, statsTxt[0], statsLore));
        String[] perksTxt = Msg.plain("gui.perks-item").split("\\|");
        List<String> perksLore = new ArrayList<>();
        for (int i = 1; i < perksTxt.length; i++) perksLore.add(perksTxt[i]);
        inv.setItem(36, button(Material.AMETHYST_SHARD, perksTxt[0], perksLore));
        String[] econTxt = Msg.plain("gui.economy-item").split("\\|");
        List<String> econLore = new ArrayList<>();
        for (int i = 1; i < econTxt.length; i++) econLore.add(econTxt[i]);
        inv.setItem(38, button(Material.EMERALD, econTxt[0], econLore));
        inv.setItem(44, button(Material.BOOK,
                Msg.plain("gui.quests-item"),
                List.of("&7Ежедневные контракты профессий.", "&8ЛКМ — список в чате")));
        inv.setItem(49, button(Material.BARRIER, Msg.plain("gui.close-item"), null));
        fill(inv, 54);
        p.openInventory(inv);
    }

    // ---------- Секция: сетка веток ----------
    public static void openSection(Player p, Section s) {
        Holder h = new Holder(MenuType.SECTION);
        h.section = s;
        Inventory inv = Bukkit.createInventory(h, 54,
                Msg.plain("gui.section-title", "section", Text.color(s.color + s.display)));
        h.inv = inv;
        PlayerProfile prof = ProfileStore.get(p.getUniqueId());

        int slot = 0;
        for (Branch b : Branch.ofSection(s)) {
            long count = prof.getBlocks(b);
            int tier = prof.getTier(b);
            String tierName = tier == 0
                    ? "—" : (tier == 10 ? b.tierColored(10)
                    : b.tierColored(tier) + "&7 → " + b.tierColored(tier + 1));
            List<String> lore = new ArrayList<>();
            lore.add("&7Добыто: &e" + Text.num(count) + " &7ед.");
            lore.add("&7Звание: " + tierName);
            lore.add("&8ЛКМ — подробнее");
            inv.setItem(slot++, button(b.guiIcon, b.color + b.display, lore));
        }

        String machine = machineOfSection(s);
        if (machine != null) {
            Tools.ToolSpec ms = Tools.get(machine);
            if (ms != null) {
                inv.setItem(45, button(ms.icon, "&5" + ms.name,
                        List.of("&7Машина секции " + s.display + ".", "&8ЛКМ — рецепт")));
                h.links.put(45, machine);
            }
        }
        // v3.2: вторая машина секции (кнопка в слоте 46)
        String machine2 = machine2OfSection(s);
        if (machine2 != null) {
            Tools.ToolSpec ms2 = Tools.get(machine2);
            if (ms2 != null) {
                inv.setItem(46, button(ms2.icon, "&5" + ms2.name,
                        List.of("&7Машина секции " + s.display + ".", "&8ЛКМ — рецепт")));
                h.links.put(46, machine2);
            }
        }
        inv.setItem(49, button(Material.ARROW, Msg.plain("gui.back-item"), null));
        fill(inv, 54);
        p.openInventory(inv);
    }

    // ---------- Ветка: лестница званий ----------
    public static void openBranch(Player p, Branch b) {
        Holder h = new Holder(MenuType.BRANCH);
        h.branch = b;
        Inventory inv = Bukkit.createInventory(h, 54,
                Msg.plain("gui.branch-title", "branch", Text.color(b.color + b.display)));
        h.inv = inv;
        PlayerProfile prof = ProfileStore.get(p.getUniqueId());
        long count = prof.getBlocks(b);
        int achieved = prof.getTier(b);
        long[] th = Cfg.thresholds(b);

        List<String> infoLore = new ArrayList<>();
        infoLore.add("&7Секция: " + b.section.color + b.section.display);
        infoLore.add("&7Редкость: &f" + switch (b.rarity) {
            case COMMON -> "обычная";
            case MEDIUM -> "средняя";
            case RARE -> "редкая";
        });
        infoLore.add("&7Добыто: &e" + Text.num(count) + " &7ед.");
        infoLore.add("&7Звание: " + (achieved == 0 ? "&8—" : b.tierColored(achieved)));
        if (achieved < 10) {
            infoLore.add("&7До следующего: &e"
                    + Text.num(Math.max(0, th[achieved] - count)) + " &7ед.");
        } else {
            infoLore.add("&aМаксимальное звание достигнуто!");
        }
        inv.setItem(4, button(b.guiIcon, b.color + b.display + " &8· ветка", infoLore));

        for (int t = 1; t <= 10; t++) {
            int slot = 8 + t; // 9..18
            String tier = b.tierColored(t);
            if (t <= achieved) {
                String date = prof.getDate(b, t);
                inv.setItem(slot, button(b.guiIcon, "&a✔ " + tier,
                        List.of("&8Порог: " + Text.num(th[t - 1]) + " ед.",
                                "&8" + (date == null ? "" : date))));
            } else if (t == achieved + 1) {
                String bar = Text.bar(count, th[t - 1], 12);
                inv.setItem(slot, button(b.medalRawIcon, "&e▶ " + tier,
                        List.of("&7Порог: &e" + Text.num(th[t - 1]) + " &7ед.",
                                "&7" + Text.num(count) + "/" + Text.num(th[t - 1])
                                        + " " + bar + " " + Text.pct(count, th[t - 1]) + "%")));
            } else {
                inv.setItem(slot, button(Material.GRAY_DYE, "&8✖ " + tier,
                        List.of("&8Порог: " + Text.num(th[t - 1]) + " ед.")));
            }
        }
        inv.setItem(49, button(Material.ARROW, Msg.plain("gui.back-item"), null));
        fill(inv, 54);
        p.openInventory(inv);
    }

    // ---------- Каталог крафтов: пагинация + фильтр секции ----------
    public static void openCrafts(Player p) {
        openCrafts(p, null, 0);
    }

    public static void openCrafts(Player p, Section filter, int page) {
        Holder h = new Holder(MenuType.CRAFTS);
        h.section = filter;
        h.page = page;
        Inventory inv = Bukkit.createInventory(h, 54, Msg.plain("gui.crafts-title"));
        h.inv = inv;

        List<Tools.ToolSpec> items = new ArrayList<>();
        for (Tools.ToolSpec s : Tools.roots()) {
            Section sec = sectionOf(s);
            if (filter == null || sec == filter || (sec == null && filter == null)) {
                items.add(s);
            }
        }
        int perPage = 45;
        int maxPage = Math.max(0, (items.size() - 1) / perPage);
        page = Math.min(Math.max(0, page), maxPage);
        h.page = page;
        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < items.size(); i++) {
            Tools.ToolSpec s = items.get(start + i);
            ItemStack it = Items.build(s.id);
            if (it == null) continue;
            ItemMeta m = it.getItemMeta();
            List<String> lore = m.getLore() == null ? new ArrayList<>() : new ArrayList<>(m.getLore());
            lore.add(Text.color("&8ЛКМ — рецепт"));
            m.setLore(lore);
            it.setItemMeta(m);
            inv.setItem(i, it);
        }

        String filterName = filter == null ? "&aВСЕ" : filter.color + filter.display;
        List<Section> cycle = new ArrayList<>(List.of(Section.values()));
        String nextName;
        if (filter == null) nextName = cycle.get(0).color + cycle.get(0).display;
        else nextName = cycle.indexOf(filter) + 1 < cycle.size()
                ? cycle.get(cycle.indexOf(filter) + 1).color
                        + cycle.get(cycle.indexOf(filter) + 1).display
                : "&aВСЕ";
        inv.setItem(46, button(Material.SPYGLASS, "&fФильтр: " + filterName,
                List.of("&8ЛКМ — переключить (→ " + nextName + ")",
                        "&8Найдено: " + items.size())));
        if (page > 0) inv.setItem(45, button(Material.ARROW, "&e← Страница " + page, null));
        if (page < maxPage) inv.setItem(53, button(Material.ARROW,
                "&eСтраница " + (page + 2) + " →", null));
        inv.setItem(49, button(Material.ARROW, Msg.plain("gui.back-item"), null));
        fill(inv, 54);
        p.openInventory(inv);
    }

    // ---------- Рецепт (с навигацией по дереву) ----------
    public static void openRecipe(Player p, String toolId) {
        openRecipe(p, toolId, new java.util.ArrayDeque<>());
    }

    private static void openRecipe(Player p, String toolId,
                                   java.util.Deque<String> backChain) {
        Tools.ToolSpec s = Tools.get(toolId);
        String[] rows = Recipes.rows(toolId);
        if (s == null || rows == null) return;
        Holder h = new Holder(MenuType.RECIPE);
        h.toolId = toolId;
        h.back.addAll(backChain);
        Inventory inv = Bukkit.createInventory(h, 36,
                Msg.plain("gui.recipe-title", "tool", s.name));
        h.inv = inv;

        int[] grid = {10, 11, 12, 19, 20, 21, 28, 29, 30};
        int rowCount = rows.length;
        int rowOffset = (3 - rowCount) / 2;
        for (int r = 0; r < rowCount; r++) {
            String line = rows[r];
            int colOffset = (3 - line.length()) / 2;
            for (int c = 0; c < line.length(); c++) {
                char ch = line.charAt(c);
                if (ch == '.') continue;
                Recipes.Ing ing = Recipes.ingredient(toolId, ch);
                if (ing == null) continue;
                int gridRow = r + rowOffset;
                int gridCol = c + colOffset;
                int slotIdx = grid[gridRow * 3 + gridCol];

                ItemStack show;
                List<String> lore = new ArrayList<>();
                if (ing.mat() != null) {
                    show = button(ing.mat(), "&f" + Items.ruName(ing.mat()),
                            List.of("&8Ингредиент крафта"));
                } else if (ing.toolId() != null) {
                    show = Items.build(ing.toolId());
                    if (show == null) continue;
                    lore.add(Text.color("&8Ингредиент крафта"));
                    lore.add(Text.color("&eКлик — рецепт этого компонента"));
                } else {
                    Branch mb = Branch.byId(ing.medalBranch());
                    if (mb == null) continue;
                    show = Items.medalItem(mb, Math.min(10, ing.medalTier()));
                    lore.add(Text.color("&8Гейт прогрессии: звание «"
                            + mb.tierName(ing.medalTier()) + "» или выше"));
                    lore.add(Text.color("&8/dp medals " + mb.name() + " — пере-выдача"));
                }
                ItemMeta mm = show.getItemMeta();
                if (mm != null) {
                    List<String> existing = mm.getLore() == null
                            ? new ArrayList<>() : new ArrayList<>(mm.getLore());
                    existing.addAll(lore);
                    mm.setLore(existing);
                    show.setItemMeta(mm);
                }
                inv.setItem(slotIdx, show);
                if (ing.toolId() != null) {
                    h.links.put(slotIdx, ing.toolId());
                }
            }
        }
        inv.setItem(23, button(Material.ARROW, "&e→", List.of("&8Результат справа")));
        ItemStack result = Items.build(toolId);
        if (result == null) return;
        int cnt = Recipes.resultCount(toolId);
        if (cnt > 1) result.setAmount(cnt);
        ItemMeta rm = result.getItemMeta();
        List<String> lore = rm.getLore() == null ? new ArrayList<>() : new ArrayList<>(rm.getLore());
        for (String ln : Msg.plain("gui.recipe-lore", "tool", s.name).split("\\|")) {
            lore.add(ln);
        }
        rm.setLore(lore);
        result.setItemMeta(rm);
        inv.setItem(25, result);
        String backLabel = h.back.isEmpty()
                ? Msg.plain("gui.back-item")
                : Msg.plain("gui.back-recipe-item");
        inv.setItem(31, button(Material.ARROW, backLabel, null));
        fill(inv, 36);
        p.openInventory(inv);
    }

    // ---------- Статистика: сводка по секциям ----------
    public static void openStats(Player p) {
        Holder h = new Holder(MenuType.STATS);
        Inventory inv = Bukkit.createInventory(h, 54, Msg.plain("gui.stats-title"));
        h.inv = inv;
        PlayerProfile prof = ProfileStore.get(p.getUniqueId());
        inv.setItem(4, button(Material.PLAYER_HEAD, "&f" + p.getName(),
                List.of("&7Очки славы: &e" + Text.num(prof.score()),
                        "&7Всего единиц: &e" + Text.num(prof.totalBlocks()))));
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22};
        Section[] secs = Section.values();
        for (int i = 0; i < slots.length && i < secs.length; i++) {
            Section s = secs[i];
            long total = 0;
            int bestTier = 0;
            String bestBranch = "—";
            for (Branch b : Branch.ofSection(s)) {
                long v = prof.getBlocks(b);
                total += v;
                int t = prof.getTier(b);
                if (t > bestTier) { bestTier = t; bestBranch = b.display; }
            }
            inv.setItem(slots[i], button(s.icon, s.color + s.display,
                    List.of("&7Добыто: &e" + Text.num(total) + " &7ед.",
                            "&7Ведущая ветка: &f" + bestBranch,
                            "&7Лучший тир: &f" + (bestTier == 0 ? "—" : String.valueOf(bestTier)),
                            "&8Вес секции — см. конфиг")));
        }
        inv.setItem(49, button(Material.ARROW, Msg.plain("gui.back-item"), null));
        fill(inv, 54);
        p.openInventory(inv);
    }

    // ---------- Перки: выбор секции ----------
    public static void openPerks(Player p) {
        Holder h = new Holder(MenuType.PERKS);
        Inventory inv = Bukkit.createInventory(h, 54, Msg.plain("gui.perks-title"));
        h.inv = inv;
        PlayerProfile prof = ProfileStore.get(p.getUniqueId());
        for (var e : MAIN_SLOTS.entrySet()) {
            Section s = e.getValue();
            var list = ru.deepprogression.perks.Perks.ofSection(s);
            int unlocked = 0;
            for (var perk : list) {
                if (ru.deepprogression.perks.Perks.bestTier(prof, s) >= perk.tier()) unlocked++;
            }
            int best = ru.deepprogression.perks.Perks.bestTier(prof, s);
            List<String> lore = new ArrayList<>();
            lore.add("&7Открыто перков: &e" + unlocked + "&7/&e" + list.size());
            lore.add("&7Лучшее звание секции: &f" + (best == 0 ? "—" : "тир " + best));
            lore.add("&8ЛКМ — перки секции");
            inv.setItem(e.getKey(), button(s.icon, s.color + s.display, lore));
        }
        inv.setItem(49, button(Material.ARROW, Msg.plain("gui.back-item"), null));
        fill(inv, 54);
        p.openInventory(inv);
    }

    // ---------- Перки секции: пассивные + активный ----------
    public static void openPerksSection(Player p, Section s) {
        Holder h = new Holder(MenuType.PERKS_SECTION);
        h.section = s;
        Inventory inv = Bukkit.createInventory(h, 54,
                Msg.plain("gui.perks-section-title", "section", Text.color(s.color + s.display)));
        h.inv = inv;
        PlayerProfile prof = ProfileStore.get(p.getUniqueId());
        int best = ru.deepprogression.perks.Perks.bestTier(prof, s);
        inv.setItem(4, button(s.icon, s.color + s.display,
                List.of("&7Лучшее звание: &f" + (best == 0 ? "—" : "тир " + best),
                        "&7Перки открываются званиями веток секции")));
        int[] slots = {19, 21, 23, 25};
        List<ru.deepprogression.perks.Perks.Perk> list = ru.deepprogression.perks.Perks.ofSection(s);
        for (int i = 0; i < slots.length && i < list.size(); i++) {
            var perk = list.get(i);
            boolean unlocked = best >= perk.tier();
            boolean on = unlocked && !prof.isPerkOff(perk.id());
            Material icon = !unlocked ? Material.GRAY_DYE : on ? Material.LIME_DYE : Material.RED_DYE;
            List<String> lore = new ArrayList<>();
            lore.add("&7" + perk.desc());
            lore.add(on ? "&aВКЛЮЧЁН &8(клик — выключить)"
                    : unlocked ? "&cВЫКЛЮЧЕН &8(клик — включить)"
                    : "&8Откроется на тире " + perk.tier() + " &8любой ветки секции");
            inv.setItem(slots[i], button(icon,
                    (unlocked ? (on ? "&a" : "&c") : "&8") + perk.name(), lore));
            h.links.put(slots[i], perk.id());
        }
        // Активный кристалл секции
        String crystalId = "crystal_" + s.name().toLowerCase(java.util.Locale.ROOT);
        ItemStack crystal = Items.build(crystalId);
        if (crystal != null) {
            ItemMeta cm = crystal.getItemMeta();
            if (cm != null) {
                List<String> lore = cm.getLore() == null ? new ArrayList<>()
                        : new ArrayList<>(cm.getLore());
                long cdLeft = Math.max(0, prof.activeCooldown(crystalId)
                        + Cfg.activeCooldown(s) * 1000L - System.currentTimeMillis());
                lore.add(Text.color(cdLeft > 0
                        ? "&7Кулдаун: &c" + (cdLeft / 1000) + "с"
                        : "&aГотов к активации"));
                lore.add(Text.color("&8Крафтится на верстаке (каталог крафтов)"));
                cm.setLore(lore);
                crystal.setItemMeta(cm);
            }
            inv.setItem(40, crystal);
        }
        inv.setItem(49, button(Material.ARROW, Msg.plain("gui.back-item"), null));
        fill(inv, 54);
        p.openInventory(inv);
    }

    // ---------- Экономика: таблица индексов ----------
    public static void openEconomy(Player p, int page) {
        Holder h = new Holder(MenuType.ECONOMY);
        h.page = page;
        Inventory inv = Bukkit.createInventory(h, 54, Msg.plain("gui.economy-title"));
        h.inv = inv;
        List<Material> mats = ru.deepprogression.economy.Economy.trackedMaterials();
        int perPage = 45;
        int maxPage = Math.max(0, (mats.size() - 1) / perPage);
        page = Math.min(Math.max(0, page), maxPage);
        h.page = page;
        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < mats.size(); i++) {
            Material m = mats.get(start + i);
            int idx = ru.deepprogression.economy.Economy.index(m);
            int v = ru.deepprogression.economy.Economy.baseValue(m);
            int total = ru.deepprogression.economy.Economy.total(m);
            int week = ru.deepprogression.economy.Economy.week(m);
            String idxColor = idx >= 8 ? "&a" : idx >= 5 ? "&e" : "&c";
            String status = idx >= 8 ? "дефицит — награды повышены"
                    : idx >= 5 ? "стабильно"
                    : "перепроизводство — награды снижены";
            List<String> lore = new ArrayList<>();
            lore.add("&7Индекс: " + idxColor + idx + "&7/10 &8(ценность " + v + ")");
            lore.add("&7Добыто всего: &e" + Text.num(total));
            lore.add("&7За неделю: &e" + Text.num(week));
            lore.add("&8" + Text.bar(total, Math.max(1, Math.max(week * 5, total)), 10));
            lore.add("&8" + status);
            inv.setItem(i, button(m, "&f" + Items.ruName(m), lore));
        }
        if (page > 0) inv.setItem(45, button(Material.ARROW, "&e← Страница " + page, null));
        if (page < maxPage) inv.setItem(53, button(Material.ARROW,
                "&eСтраница " + (page + 2) + " →", null));
        inv.setItem(49, button(Material.ARROW, Msg.plain("gui.back-item"), null));
        fill(inv, 54);
        p.openInventory(inv);
    }

    // ---------- Клик ----------
    public static void handleClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        if (h.type == MenuType.POUCH) {
            Vaults.handlePouchClick(e);
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getRawSlot();

        switch (h.type) {
            case MAIN -> {
                Section s = MAIN_SLOTS.get(slot);
                if (s != null) { openSection(p, s); return; }
                switch (slot) {
                    case 36 -> openPerks(p);
                    case 38 -> openEconomy(p, 0);
                    case 40 -> openCrafts(p);
                    case 42 -> openStats(p);
                    case 44 -> ru.deepprogression.progress.Quests.sendList(p);
                    case 49 -> p.closeInventory();
                    default -> { }
                }
            }
            case PERKS -> {
                if (slot == 49) { openMain(p); return; }
                Section s = MAIN_SLOTS.get(slot);
                if (s != null) openPerksSection(p, s);
            }
            case PERKS_SECTION -> {
                if (slot == 49) { openPerks(p); return; }
                String perkId = h.links.get(slot);
                if (perkId != null) {
                    PlayerProfile prof = ProfileStore.get(p.getUniqueId());
                    prof.togglePerk(perkId);
                    ProfileStore.saveAsync(p.getUniqueId());
                    p.playSound(p.getLocation(), "minecraft:block.note_block.pling", 0.6f,
                            prof.isPerkOff(perkId) ? 0.8f : 1.4f);
                    openPerksSection(p, h.section);
                }
            }
            case ECONOMY -> {
                if (slot == 49) { openMain(p); return; }
                if (slot == 45 && h.page > 0) { openEconomy(p, h.page - 1); return; }
                if (slot == 53) { openEconomy(p, h.page + 1); return; }
            }
            case SECTION -> {
                if (slot == 49) { openMain(p); return; }
                if (slot == 45 && h.links.get(45) != null) {
                    openRecipe(p, h.links.get(45));
                    return;
                }
                if (slot == 46 && h.links.get(46) != null) {
                    openRecipe(p, h.links.get(46));
                    return;
                }
                List<Branch> branches = Branch.ofSection(h.section);
                if (slot < branches.size()) openBranch(p, branches.get(slot));
            }
            case BRANCH -> {
                if (slot == 49) openSection(p, h.branch.section);
            }
            case CRAFTS -> {
                if (slot == 49) { openMain(p); return; }
                if (slot == 46) {
                    // циклический фильтр секций
                    Section[] secs = Section.values();
                    Section next;
                    if (h.section == null) next = secs[0];
                    else {
                        int idx = h.section.ordinal() + 1;
                        next = idx < secs.length ? secs[idx] : null;
                    }
                    openCrafts(p, next, 0);
                    return;
                }
                if (slot == 45 && h.page > 0) { openCrafts(p, h.section, h.page - 1); return; }
                if (slot == 53) { openCrafts(p, h.section, h.page + 1); return; }
                if (slot >= 0 && slot < 45) {
                    int idx = h.page * 45 + slot;
                    List<Tools.ToolSpec> items = new ArrayList<>();
                    for (Tools.ToolSpec s : Tools.roots()) {
                        Section sec = sectionOf(s);
                        if (h.section == null || sec == h.section
                                || (sec == null && h.section == null)) items.add(s);
                    }
                    if (idx < items.size()) {
                        openRecipe(p, items.get(idx).id);
                    }
                }
            }
            case RECIPE -> {
                String link = h.links.get(slot);
                if (link != null) {
                    java.util.Deque<String> chain = new java.util.ArrayDeque<>(h.back);
                    chain.addLast(h.toolId);
                    openRecipe(p, link, chain);
                    return;
                }
                if (slot == 31) {
                    if (!h.back.isEmpty()) {
                        java.util.Deque<String> chain = new java.util.ArrayDeque<>(h.back);
                        String prev = chain.removeLast();
                        openRecipe(p, prev, chain);
                    } else {
                        Section sec = null;
                        Tools.ToolSpec spec = Tools.get(h.toolId);
                        if (spec != null) sec = sectionOf(spec);
                        openCrafts(p, sec, 0);
                    }
                }
            }
            case STATS -> {
                if (slot == 49) openMain(p);
            }
            default -> { }
        }
    }
}
