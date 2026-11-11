package ru.deepmagic.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.deepmagic.model.ItemDefs;
import ru.deepmagic.model.Magic;
import ru.deepmagic.util.Conf;
import ru.deepmagic.util.T;
import ru.deepprogression.model.Branch;
import ru.deepprogression.model.Section;
import ru.deepprogression.progress.PlayerProfile;
import ru.deepprogression.progress.ProfileStore;
import ru.deepprogression.tools.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * GUI-меню DeepMagic v1 — стиль DeepProgression (Gui v3):
 *   MAIN    — сетка секций (10-16/19-25) + навигация; /mage
 *   SECTION — ветки секции (слоты 0..N-1); назад = 49
 *   BRANCH  — витрина посоха (4), лестница тиров (20/22/24), топливо (40)
 *   RECIPE  — 36 слотов: грид {10,11,12,19,20,21,28,29,30}, стрелка 23,
 *             результат 25, назад 31 (точная копия вида рецепта DP)
 *   GUIDE   — «Путь мага» (6 шагов + топливо + PvP)
 *   RULES   — матрица синергий (12 правил)
 *   CATALOG — каталог 73 посохов с фильтром секций (46) и пагинацией (45/53)
 *   PROFILE — профиль мага: гейты, посохи в инвентаре
 * Все клики гасятся; звуки — UI; тексты через T.color (сырой '&' исключён).
 */
public final class MenuGui {

    public enum MenuType { MAIN, SECTION, BRANCH, RECIPE, GUIDE, RULES, CATALOG, PROFILE }

    public static final class Holder implements InventoryHolder {
        public final MenuType type;
        public Branch branch;
        public Section section;
        public int page;
        public int tier;
        public boolean compressorRecipe;
        public final Map<Integer, String> links = new HashMap<>();
        private final Inventory inv;

        public Holder(MenuType type, Component title) {
            this.type = type;
            this.inv = Bukkit.createInventory(this, type == MenuType.RECIPE ? 36 : 54, title);
        }

        @Override public Inventory getInventory() { return inv; }
    }

    // Сетка секций главного меню — как у DeepProgression
    public static final int[] MAIN_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    /** Грид рецепта 3×3 (36-слотовое окно) — как у DeepProgression. */
    private static final int[] RECIPE_GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};

    private MenuGui() {}

    // ---------- инфраструктура ----------

    private static ItemStack filler() {
        ItemStack it = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(T.color("&r"));
        it.setItemMeta(m);
        return it;
    }

    private static void fill(Inventory inv) {
        ItemStack f = filler();
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, f);
    }

    private static ItemStack button(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat == null || !mat.isItem() ? Material.PAPER : mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(T.color(name));
        if (lore != null) m.setLore(lore.stream().map(T::color).toList());
        it.setItemMeta(m);
        return it;
    }

    private static Component title(String mini) {
        return MiniMessage.miniMessage().deserialize(mini);
    }

    private static void clickSound(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 0.9f);
    }

    private static void openSound(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.6f, 1.3f);
    }

    // ---------- вход ----------

    public static void open(Player p) {
        openMain(p);
    }

    // ---------- MAIN ----------

    public static void openMain(Player p) {
        Holder h = new Holder(MenuType.MAIN, title("<gradient:#c77dff:#f0abfc><bold>☠ Магический Арсенал</bold></gradient>"
                + " <dark_gray>· DeepMagic"));
        Inventory inv = h.getInventory();
        PlayerProfile prof = ProfileStore.get(p.getUniqueId());

        Section[] secs = Section.values();
        for (int i = 0; i < MAIN_SLOTS.length && i < secs.length; i++) {
            Section s = secs[i];
            int gates = 0;
            for (Branch b : Branch.ofSection(s)) if (prof.getTier(b) >= Conf.craftGateTier()) gates++;
            List<String> lore = new ArrayList<>();
            lore.add("&7Веток: &e" + Branch.ofSection(s).size()
                    + " &8· Посохов: &e" + Branch.ofSection(s).size());
            lore.add("&7Гейт пройден: " + (gates == Branch.ofSection(s).size() ? "&a" : gates > 0 ? "&e" : "&c")
                    + gates + "&7/" + Branch.ofSection(s).size() + " &8(медаль 3+)");
            lore.add("");
            lore.add("&8ЛКМ — ветки секции и их посохи");
            inv.setItem(MAIN_SLOTS[i], button(s.icon, s.color + "&l" + s.display, lore));
        }

        inv.setItem(36, button(Material.BOOK, "&e&lПуть мага",
                List.of("&7Как получить посох за 6 шагов:", "&8добыча → компрессор → сжатие",
                        "&8→ медаль → крафт → применение", "", "&8ЛКМ — открыть гайд")));
        inv.setItem(38, button(Material.PISTON, "&d&lМагический Компрессор",
                List.of("&7Машина, прессующая ресурсы в вещества.", "&7Топливо: &eмагмовый крем",
                        "&7Гейт: &eмедаль ветки 3+", "", "&8ЛКМ — рецепт машины")));
        inv.setItem(40, button(Material.ENCHANTED_BOOK, "&5&lКаталог посохов",
                List.of("&7Все &e" + Magic.all().size() + " посохов &7сервера:",
                        "&8стихия, роль, механика", "", "&8ЛКМ — открыть каталог")));
        inv.setItem(42, button(Material.BLAZE_POWDER, "&c&lМатрица синергий",
                List.of("&7Законы взаимодействий стихий:", "&8уголь+огонь, вода, лёд, молния…",
                        "", "&8ЛКМ — правила магии")));
        int staffsInv = 0, subsInv = 0, gates = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null) continue;
            if (ItemDefs.isStaff(it)) staffsInv++;
            if (ItemDefs.isSubstance(it)) subsInv++;
        }
        for (Branch b : Branch.values()) if (prof.getTier(b) >= Conf.craftGateTier()) gates++;
        inv.setItem(44, button(Material.PLAYER_HEAD, "&f&lПрофиль мага",
                List.of("&7Посохов в инвентаре: &e" + staffsInv,
                        "&7Веществ в инвентаре: &e" + subsInv,
                        "&7Гейтов пройдено: &e" + gates + "&7/&e" + Branch.values().length,
                        "", "&8ЛКМ — подробнее по секциям")));
        inv.setItem(49, button(Material.BARRIER, "&cЗакрыть", null));
        fill(inv);
        p.openInventory(inv);
        openSound(p);
    }

    // ---------- SECTION ----------

    public static void openSection(Player p, Section s) {
        Holder h = new Holder(MenuType.SECTION, title("<gradient:#c77dff:#f0abfc><bold>☠ " + s.display + "</bold></gradient>"
                + " <dark_gray>· ветки и посохи"));
        h.section = s;
        Inventory inv = h.getInventory();
        PlayerProfile prof = ProfileStore.get(p.getUniqueId());

        int slot = 0;
        for (Branch b : Branch.ofSection(s)) {
            Magic.StaffDef d = Magic.def(b);
            if (d == null) continue;
            int tier = prof.getTier(b);
            boolean gate = tier >= Conf.craftGateTier();
            List<String> lore = new ArrayList<>();
            lore.add("&7Вещество: " + b.color + d.substance());
            lore.add("&7Посох: " + b.color + d.staff());
            lore.add("&7Стихия: " + d.element().color + d.element().display
                    + " &8· Роль: &e" + d.role().display);
            lore.add("&7Редкость: " + ItemDefs.rarityColor(b.rarity) + ItemDefs.rarityName(b.rarity));
            lore.add("");
            lore.add((gate ? "&a✔ " : "&c✖ ") + "&7Ваше звание: " + (tier == 0 ? "&8—" : b.tierColored(tier))
                    + " &8(гейт: медаль " + Conf.craftGateTier() + "+)");
            lore.add("&8ЛКМ — посох и крафт");
            inv.setItem(slot++, button(b.guiIcon, b.color + "&l" + b.display, lore));
        }
        inv.setItem(49, button(Material.ARROW, "&e← Назад", null));
        fill(inv);
        p.openInventory(inv);
        clickSound(p);
    }

    // ---------- BRANCH ----------

    public static void openBranch(Player p, Branch b) {
        Magic.StaffDef d = Magic.def(b);
        if (d == null) return;
        Holder h = new Holder(MenuType.BRANCH, title("<gradient:" + hexOf(b) + ":#f0abfc><bold>☠ " + b.display + "</bold></gradient>"
                + " <dark_gray>· посох"));
        h.branch = b;
        Inventory inv = h.getInventory();
        PlayerProfile prof = ProfileStore.get(p.getUniqueId());
        int tier = prof.getTier(b);
        boolean gate = tier >= Conf.craftGateTier();

        List<String> infoLore = new ArrayList<>();
        infoLore.add("&7Вещество: " + b.color + d.substance());
        infoLore.add("&8  " + d.substanceLore());
        infoLore.add("");
        infoLore.add("&7Посох: " + b.color + d.staff());
        infoLore.add("&f  " + d.staffLore());
        infoLore.add("");
        infoLore.add("&7Стихия: " + d.element().color + d.element().display
                + " &8· Роль: &e" + d.role().display);
        infoLore.add("&7Топливо: &e" + d.fuelQty() + "× " + ItemDefs.ruName(d.fuel())
                + " &7за применение");
        infoLore.add("&7Кулдаун: &e" + Conf.cooldown(d) + " с");
        infoLore.add("");
        infoLore.add((gate ? "&a✔ Гейт пройден: звание " + b.tierColored(tier)
                : "&c✖ Гейт не пройден: нужно звание " + b.tierColored(Conf.craftGateTier()) + "+")
                + " &8(у вас " + (tier == 0 ? "—" : "тир " + tier) + ")");
        infoLore.add("&7Сжатие: &e" + T.num(Conf.unitsFor(b.rarity)) + " ед. ресурса → вещество I");
        ItemStack info = staffDisplay(b, 1, " &8· ветка");
        ItemMeta im = info.getItemMeta();
        if (im != null) {
            List<String> lore = im.getLore() == null ? new ArrayList<>() : new ArrayList<>(im.getLore());
            for (String l : infoLore) lore.add(T.color(l));
            im.setLore(lore);
            info.setItemMeta(im);
        }
        inv.setItem(4, info);

        // Лестница тиров: I / II / III — слоты 20/22/24
        int[] slots = {20, 22, 24};
        String[] tiers = {"I", "II", "III"};
        for (int t = 1; t <= 3; t++) {
            ItemStack st = staffDisplay(b, t, "");
            ItemMeta tm = st.getItemMeta();
            List<String> lore = tm.getLore() == null ? new ArrayList<>() : new ArrayList<>(tm.getLore());
            lore.add(T.color(""));
            lore.add(T.color("&7Крафт: &eкомпонент (" + Conf.component(t)
                    + ") + вещество " + T.roman(t) + " + медаль ветки"));
            if (t > 1) lore.add(T.color("&7Пересжатие: &e" + T.roman(t - 1) + " → " + T.roman(t)
                    + " &8(медаль " + (t == 2 ? Conf.recompressGate2() : Conf.recompressGate3()) + "+)"));
            lore.add(T.color("&8ЛКМ — рецепт посоха " + tiers[t - 1]));
            tm.setLore(lore);
            st.setItemMeta(tm);
            inv.setItem(slots[t - 1], st);
        }

        // Топливо посоха
        inv.setItem(40, button(d.fuel(), "&6Топливо посоха",
                List.of("&e" + d.fuelQty() + "× " + ItemDefs.ruName(d.fuel()) + " &7за каждое применение",
                        "&7Берётся из инвентаря, иначе — из тайника ресурсов DP.",
                        "&8use_pouch_fuel: " + (Conf.usePouchFuel() ? "вкл" : "выкл"))));

        inv.setItem(49, button(Material.ARROW, "&e← Назад &8(секция)", null));
        fill(inv);
        p.openInventory(inv);
        clickSound(p);
    }

    /** Витринный предмет посоха (без PDC — нельзя вынести из меню как артефакт). */
    private static ItemStack staffDisplay(Branch b, int tier, String suffix) {
        ItemStack it = ItemDefs.staff(b, tier);
        if (it == null) return button(Material.BREEZE_ROD, "&c" + b.display, null);
        ItemDefs.clearIds(it);
        if (suffix != null && !suffix.isEmpty()) {
            ItemMeta m = it.getItemMeta();
            if (m != null) {
                m.setDisplayName(m.getDisplayName() + T.color(suffix));
                it.setItemMeta(m);
            }
        }
        return it;
    }

    private static String hexOf(Branch b) {
        // Приблизительный hex по редкости ветки — заголовок-градиент
        return switch (b.rarity) {
            case COMMON -> "#9aa5b1";
            case MEDIUM -> "#e0b34c";
            case RARE -> "#7dd3fc";
        };
    }

    // ---------- RECIPE (36 слотов, вид как у DP) ----------

    public static void openRecipe(Player p, Branch b, int tier) {
        Magic.StaffDef d = Magic.def(b);
        if (d == null) return;
        Holder h = new Holder(MenuType.RECIPE, title("<gradient:#c77dff:#f0abfc><bold>Рецепт · " + b.display + " "
                + T.roman(tier) + "</bold></gradient>"));
        h.branch = b;
        h.tier = tier;
        h.compressorRecipe = false;
        Inventory inv = h.getInventory();

        // Крафт-сетка посоха: компонент (верх), вещество (центр), медаль (низ)
        String comp = Conf.component(tier);
        ItemStack compIt = Items.build(comp);
        if (compIt == null) compIt = button(Material.PAPER, "&c" + comp, null);
        ItemMeta cm = compIt.getItemMeta();
        if (cm != null) {
            List<String> lore = cm.getLore() == null ? new ArrayList<>() : new ArrayList<>(cm.getLore());
            lore.add(T.color(""));
            lore.add(T.color("&7Компонент DeepProgression &8(тир вещества " + T.roman(tier) + ")"));
            lore.add(T.color("&8Крафтится в каталоге крафтов DP (&f/dp crafts&8)"));
            cm.setLore(lore);
            compIt.setItemMeta(cm);
        }
        inv.setItem(RECIPE_GRID[1], compIt); // верх-центр

        ItemStack sub = ItemDefs.substance(b, tier);
        if (sub != null) {
            ItemDefs.clearIds(sub);
            ItemMeta sm = sub.getItemMeta();
            if (sm != null) {
                List<String> lore = sm.getLore() == null ? new ArrayList<>() : new ArrayList<>(sm.getLore());
                lore.add(T.color(""));
                lore.add(T.color("&7Сжать &e" + T.num(Conf.unitsFor(b.rarity))
                        + " ед. " + b.display + " &7в компрессоре"));
                if (tier > 1) lore.add(T.color("&7Пересжатие: &e" + T.roman(tier - 1) + " → " + T.roman(tier)));
                sm.setLore(lore);
                sub.setItemMeta(sm);
            }
            inv.setItem(RECIPE_GRID[4], sub); // центр
        }

        ItemStack medal = Items.medalItem(b, Math.min(10, Conf.craftGateTier()));
        if (medal != null) {
            ItemMeta mm = medal.getItemMeta();
            if (mm != null) {
                List<String> lore = mm.getLore() == null ? new ArrayList<>() : new ArrayList<>(mm.getLore());
                lore.add(T.color(""));
                lore.add(T.color("&7Гейт прогрессии: звание &e" + b.tierName(Conf.craftGateTier()) + "+"));
                lore.add(T.color("&8Расходуется при крафте; &f/dp medals " + b.name().toLowerCase(Locale.ROOT)
                        + " &8вернёт"));
                mm.setLore(lore);
                medal.setItemMeta(mm);
            }
            inv.setItem(RECIPE_GRID[7], medal); // низ-центр
        }

        inv.setItem(23, button(Material.ARROW, "&e→", List.of("&8Результат справа")));
        ItemStack result = staffDisplay(b, tier, "");
        ItemMeta rm = result.getItemMeta();
        if (rm != null) {
            List<String> lore = rm.getLore() == null ? new ArrayList<>() : new ArrayList<>(rm.getLore());
            lore.add(T.color(""));
            lore.add(T.color("&aКликните по результату в верстаке, чтобы забрать посох."));
            lore.add(T.color("&8Shift-клик — крафт циклом (несколько штук)."));
            rm.setLore(lore);
            result.setItemMeta(rm);
        }
        inv.setItem(25, result);
        inv.setItem(31, button(Material.ARROW, "&e← Назад &8(посох)", null));
        fill(inv);
        p.openInventory(inv);
        clickSound(p);
    }

    /** Рецепт магического компрессора (тот же 36-слотовый вид). */
    public static void openCompressorRecipe(Player p) {
        Holder h = new Holder(MenuType.RECIPE, title("<gradient:#c77dff:#f0abfc><bold>Рецепт · Магический Компрессор</bold></gradient>"));
        h.compressorRecipe = true;
        Inventory inv = h.getInventory();

        // .P. / N F P / .M.  → 11=поршень, 19=node_power, 20=рама, 21=поршень, 29=медаль меди
        inv.setItem(RECIPE_GRID[1], button(Material.PISTON, "&fПоршень",
                List.of("&8Обычный ванильный поршень")));
        putComponent(inv, RECIPE_GRID[3], Conf.component(2));
        putComponent(inv, RECIPE_GRID[4], "reinforced_frame");
        inv.setItem(RECIPE_GRID[5], button(Material.PISTON, "&fПоршень",
                List.of("&8Обычный ванильный поршень")));
        ItemStack medal = Items.medalItem(Branch.COPPER, Math.min(10, Conf.craftGateTier()));
        if (medal != null) {
            ItemMeta mm = medal.getItemMeta();
            if (mm != null) {
                List<String> lore = mm.getLore() == null ? new ArrayList<>() : new ArrayList<>(mm.getLore());
                lore.add(T.color(""));
                lore.add(T.color("&7Гейт: звание &e" + Branch.COPPER.tierName(Conf.craftGateTier()) + "+ &7(медь)"));
                lore.add(T.color("&8Расходуется; &f/dp medals copper &8вернёт"));
                mm.setLore(lore);
                medal.setItemMeta(mm);
            }
            inv.setItem(RECIPE_GRID[7], medal);
        }

        inv.setItem(23, button(Material.ARROW, "&e→", List.of("&8Результат справа")));
        ItemStack result = ItemDefs.compressorItem();
        if (result != null) {
            ItemDefs.clearIds(result);
            ItemMeta rm = result.getItemMeta();
            if (rm != null) {
                List<String> lore = rm.getLore() == null ? new ArrayList<>() : new ArrayList<>(rm.getLore());
                lore.add(T.color(""));
                lore.add(T.color("&7После установки: ПКМ по блоку — окно машины,"));
                lore.add(T.color("&7Shift+ПКМ с ресурсом — закладка партии."));
                rm.setLore(lore);
                result.setItemMeta(rm);
            }
        }
        inv.setItem(25, result);
        inv.setItem(31, button(Material.ARROW, "&e← Назад", null));
        fill(inv);
        p.openInventory(inv);
        clickSound(p);
    }

    private static void putComponent(Inventory inv, int slot, String toolId) {
        ItemStack it = Items.build(toolId);
        if (it == null) {
            inv.setItem(slot, button(Material.PAPER, "&c" + toolId, null));
            return;
        }
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            List<String> lore = m.getLore() == null ? new ArrayList<>() : new ArrayList<>(m.getLore());
            lore.add(T.color(""));
            lore.add(T.color("&7Компонент DeepProgression"));
            lore.add(T.color("&8Крафтится в каталоге крафтов DP (&f/dp crafts&8)"));
            m.setLore(lore);
            it.setItemMeta(m);
        }
        inv.setItem(slot, it);
    }

    // ---------- GUIDE ----------

    public static void openGuide(Player p) {
        Holder h = new Holder(MenuType.GUIDE, title("<gradient:#c77dff:#f0abfc><bold>Путь мага</bold></gradient>"
                + " <dark_gray>· от добычи до посоха"));
        Inventory inv = h.getInventory();

        inv.setItem(4, button(Material.NETHER_STAR, "&d&lИтог: посох своей ветки",
                List.of("&73 посоха на ветку &8(I/II/III — сила ×1/×1.5/×2)",
                        "&7Всего посохов: &e" + Magic.all().size() + " &8— по числу веток DP",
                        "&8Посох живёт на топливе ветки и кулдаунах")));

        inv.setItem(19, button(Material.IRON_PICKAXE, "&eШаг 1 · Добыча",
                List.of("&7Добывайте ресурс нужной ветки (руда, растение, дары…)",
                        "&7До звания &eмедаль тир 3+ &7— пороги в &f/dp progress",
                        "&8Медаль ветки нужна и компрессору, и крафту посоха")));
        inv.setItem(20, button(Material.PISTON, "&eШаг 2 · Компрессор",
                List.of("&7Скрафтите машину: &f2 поршня + узел питания + усиленная рама",
                        "&7+ медаль меди тир 3+", "&7Топливо машины: &eмагмовый крем",
                        "", "&8ЛКМ — рецепт компрессора")));
        h.links.put(20, "compressor");
        inv.setItem(21, button(Material.HEAVY_CORE, "&eШаг 3 · Сжатие",
                List.of("&7ПКМ по компрессору — окно машины.",
                        "&7Shift+ПКМ с ресурсом — заложить партию.",
                        "&7Например: &e64 000 ед. угля &7→ вещество «Спрессованное» (I)")));
        inv.setItem(22, button(Material.ANVIL, "&eШаг 4 · Пересжатие",
                List.of("&7Вещество I → II: &eмедаль ветки 6+ &7(сила посоха ×1.5)",
                        "&7Вещество II → III: &eмедаль ветки 9+ &7(сила посоха ×2)",
                        "&8Пересжатие — Shift+ПКМ веществом по компрессору")));
        inv.setItem(23, button(Material.LIME_DYE, "&eШаг 5 · Крафт посоха",
                List.of("&7Верстак: &fкомпонент (верх) + вещество (центр) + медаль (низ)",
                        "&7Клик по результату — посох ваш. Медаль расходуется.",
                        "&8/dp medals <ветка> — пере-выдача медалей")));
        inv.setItem(24, button(Material.BREEZE_ROD, "&eШаг 6 · Применение",
                List.of("&7ПКМ с посохом — применить (съест топливо).",
                        "&7Shift+ПКМ — вариация (у угольного — удушающая пыль).",
                        "&8Сила посоха = ступень сжатия вещества")));

        inv.setItem(38, button(Material.MAGMA_CREAM, "&6Топливо и мана",
                List.of("&7Каждый посох ест топливо СВОЕЙ ветки:",
                        "&8угольный — уголь, магмовый — крем, водный — и т.д.",
                        "&7Нет в инвентаре — берётся из тайника ресурсов DP.",
                        "&8В зоне ритуала свечей топливо не тратится")));
        inv.setItem(40, button(Material.IRON_CHESTPLATE, "&bPvP-гигиена",
                List.of("&7Кулдауны 2–120 с на каждый посох.",
                        "&7Радиус &e64 блока &7от спавна безопасен.",
                        "&7Вечное горение по игрокам: &e1 ♥/с&7, гасится водой/дождём/арбузом.",
                        "&8Смерть снимает ВСЕ состояния")));
        inv.setItem(42, button(Material.BLAZE_POWDER, "&cМатрица синергий",
                List.of("&7Посохи взаимодействуют: уголь+огонь = вечное пламя,",
                        "&7лёд по мокрым — корка, молния ×2 по заряженным…",
                        "", "&8ЛКМ — правила магии")));
        h.links.put(42, "rules");
        inv.setItem(49, button(Material.ARROW, "&e← Назад", null));
        fill(inv);
        p.openInventory(inv);
        clickSound(p);
    }

    // ---------- RULES ----------

    private record Rule(Material mat, String name, String a, String b) {}

    public static void openRules(Player p) {
        Holder h = new Holder(MenuType.RULES, title("<gradient:#c77dff:#f0abfc><bold>Матрица синергий</bold></gradient>"
                + " <dark_gray>· законы магии"));
        Inventory inv = h.getInventory();

        int[][] slots = {{10, 11, 12, 13, 14, 15, 16}, {19, 20, 21, 22, 23, 24, 25}};
        List<Rule> rules = List.of(
                new Rule(Material.BLAZE_POWDER, "&cОгонь × Уголь",
                        "Угольное покрытие + любой огонь = ВЕЧНОЕ горение до воды.",
                        "На незерраке вечный огонь возникает даже без магмы."),
                new Rule(Material.WATER_BUCKET, "&bВода",
                        "Гасит вечное горение, снимает камуфляж и невидимость.",
                        "По заряженным — цепной электрический шок."),
                new Rule(Material.PACKED_ICE, "&fЛёд",
                        "По влажным — ледяная корка; по смоле — хрупкость.",
                        "Обмороженные получают ×1.5 от физики."),
                new Rule(Material.LIGHTNING_ROD, "&eМолния",
                        "×2 по заряженным и позолоченным.",
                        "По обмороженным — шок-заморозка (цель хрупка)."),
                new Rule(Material.ANVIL, "&6Физика",
                        "Удар по остеклённой цели — осколки по соседям.",
                        "По обмороженным — SHATTER ×1.5."),
                new Rule(Material.ECHO_SHARD, "&9Эхо",
                        "Резонанс и гниль продлевают состояния ×2.",
                        "Эхо-разряд накладывает гниль."),
                new Rule(Material.GLOWSTONE, "&eСвет",
                        "Стирает печать души, гниль и негативный камуфляж.",
                        "Дождь гасит вечное горение во всём мире."),
                new Rule(Material.GRAY_CONCRETE, "&8Бетон",
                        "Стирает ВСЕ состояния — общий сброс.",
                        "Заливка бетоном смывает и защиты, и угрозы."),
                new Rule(Material.BIRCH_SAPLING, "&aБерёза",
                        "Снимает негативные состояния (обаяние остаётся).",
                        "Анти-контроль против страха и печати."),
                new Rule(Material.MELON_SLICE, "&2Арбуз",
                        "Тушит ВСЕ огни в радиусе 6 блоков.",
                        "Единственная боевая контрмера вечному огню."),
                new Rule(Material.CANDLE, "&6Ритуал свечей",
                        "Сила всех посохов в зоне +25%.",
                        "Топливо в зоне ритуала не тратится."),
                new Rule(Material.IRON_CHESTPLATE, "&bГигиена боя",
                        "Кулдаун у каждого посоха свой (2–120 с).",
                        "Спавн-радиус 64 — безопасная зона; смерть сбрасывает всё."));
        for (int i = 0; i < rules.size() && i < 14; i++) {
            Rule r = rules.get(i);
            int slot = slots[i / 7][i % 7];
            inv.setItem(slot, button(r.mat, r.name, List.of("&f" + r.a, "&f" + r.b)));
        }
        inv.setItem(49, button(Material.ARROW, "&e← Назад", null));
        fill(inv);
        p.openInventory(inv);
        clickSound(p);
    }

    // ---------- CATALOG ----------

    public static void openCatalog(Player p) {
        openCatalog(p, null, 0);
    }

    public static void openCatalog(Player p, Section filter, int page) {
        Holder h = new Holder(MenuType.CATALOG, title("<gradient:#c77dff:#f0abfc><bold>Каталог посохов</bold></gradient>"
                + " <dark_gray>· " + Magic.all().size()));
        h.section = filter;
        Inventory inv = h.getInventory();

        List<Magic.StaffDef> items = new ArrayList<>();
        for (Magic.StaffDef d : Magic.all()) {
            Section sec = d.branch().section;
            if (filter == null || sec == filter) items.add(d);
        }
        int perPage = 45;
        int maxPage = Math.max(0, (items.size() - 1) / perPage);
        page = Math.min(Math.max(0, page), maxPage);
        h.page = page;
        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < items.size(); i++) {
            Magic.StaffDef d = items.get(start + i);
            Branch b = d.branch();
            ItemStack it = staffDisplay(b, 1, "");
            ItemMeta m = it.getItemMeta();
            if (m != null) {
                List<String> lore = m.getLore() == null ? new ArrayList<>() : new ArrayList<>(m.getLore());
                int cut = d.staffLore().indexOf(';');
                String mech = cut > 0 ? d.staffLore().substring(0, cut) : d.staffLore();
                if (mech.length() > 60) mech = mech.substring(0, 60) + "…";
                lore.add(T.color("&7" + mech));
                lore.add(T.color("&8ЛКМ — ветка и крафт"));
                m.setLore(lore);
                it.setItemMeta(m);
            }
            inv.setItem(i, it);
        }

        // Фильтр-цикл по секциям — как у DP
        Section[] secs = Section.values();
        String filterName = filter == null ? "&aВСЕ" : filter.color + filter.display;
        String nextName;
        if (filter == null) nextName = secs[0].color + secs[0].display;
        else nextName = filter.ordinal() + 1 < secs.length
                ? secs[filter.ordinal() + 1].color + secs[filter.ordinal() + 1].display
                : "&aВСЕ";
        inv.setItem(46, button(Material.SPYGLASS, "&fФильтр: " + filterName,
                List.of("&8ЛКМ — переключить (→ " + nextName + ")",
                        "&8Найдено посохов: &e" + items.size())));
        if (page > 0) inv.setItem(45, button(Material.ARROW, "&e← Страница " + page, null));
        if (page < maxPage) inv.setItem(53, button(Material.ARROW,
                "&eСтраница " + (page + 2) + " →", null));
        inv.setItem(49, button(Material.ARROW, "&e← Назад", null));
        fill(inv);
        p.openInventory(inv);
        clickSound(p);
    }

    // ---------- PROFILE ----------

    public static void openProfile(Player p) {
        Holder h = new Holder(MenuType.PROFILE, title("<gradient:#c77dff:#f0abfc><bold>Профиль мага</bold></gradient>"
                + " <dark_gray>· " + p.getName()));
        Inventory inv = h.getInventory();
        PlayerProfile prof = ProfileStore.get(p.getUniqueId());

        int staffsInv = 0, subsInv = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null) continue;
            if (ItemDefs.isStaff(it)) staffsInv++;
            if (ItemDefs.isSubstance(it)) subsInv++;
        }
        int gates = 0;
        for (Branch b : Branch.values()) if (prof.getTier(b) >= Conf.craftGateTier()) gates++;
        inv.setItem(4, button(Material.PLAYER_HEAD, "&f&l" + p.getName(),
                List.of("&7Посохов в инвентаре: &e" + staffsInv,
                        "&7Веществ в инвентаре: &e" + subsInv,
                        "&7Гейтов пройдено: &e" + gates + "&7/&e" + Branch.values().length,
                        "&7Порог гейта: медаль ветки тир &e" + Conf.craftGateTier() + "+",
                        "&8Медали расходуются в крафте — /dp medals вернёт")));

        Section[] secs = Section.values();
        for (int i = 0; i < MAIN_SLOTS.length && i < secs.length; i++) {
            Section s = secs[i];
            List<Branch> bs = Branch.ofSection(s);
            int g = 0;
            List<String> missing = new ArrayList<>();
            for (Branch b : bs) {
                if (prof.getTier(b) >= Conf.craftGateTier()) g++;
                else if (missing.size() < 5) missing.add(b.display);
            }
            List<String> lore = new ArrayList<>();
            lore.add("&7Гейт пройден: " + (g == bs.size() ? "&a" : "&e") + g + "&7/&7" + bs.size());
            if (!missing.isEmpty()) {
                lore.add("&7Ждут гейта: &f" + String.join(", ", missing)
                        + (bs.size() - g > missing.size() ? "…" : ""));
            } else if (g > 0) {
                lore.add("&aВсе ветки секции открыты для крафта!");
            }
            lore.add("");
            lore.add("&8ЛКМ — ветки секции");
            inv.setItem(MAIN_SLOTS[i], button(s.icon, s.color + "&l" + s.display, lore));
        }
        inv.setItem(49, button(Material.ARROW, "&e← Назад", null));
        fill(inv);
        p.openInventory(inv);
        clickSound(p);
    }

    // ---------- клики ----------

    public static void handleClick(Player p, Holder h, int slot) {
        switch (h.type) {
            case MAIN -> {
                Section[] secs = Section.values();
                for (int i = 0; i < MAIN_SLOTS.length && i < secs.length; i++) {
                    if (MAIN_SLOTS[i] == slot) { openSection(p, secs[i]); return; }
                }
                switch (slot) {
                    case 36 -> openGuide(p);
                    case 38 -> openCompressorRecipe(p);
                    case 40 -> openCatalog(p);
                    case 42 -> openRules(p);
                    case 44 -> openProfile(p);
                    case 49 -> p.closeInventory();
                    default -> { }
                }
            }
            case SECTION -> {
                if (slot == 49) { openMain(p); return; }
                List<Branch> branches = Branch.ofSection(h.section);
                if (slot >= 0 && slot < branches.size()) openBranch(p, branches.get(slot));
            }
            case BRANCH -> {
                if (slot == 49) { openSection(p, h.branch.section); return; }
                if (slot == 20) { openRecipe(p, h.branch, 1); return; }
                if (slot == 22) { openRecipe(p, h.branch, 2); return; }
                if (slot == 24) { openRecipe(p, h.branch, 3); }
            }
            case RECIPE -> {
                if (slot == 31) {
                    if (h.compressorRecipe) openMain(p);
                    else openBranch(p, h.branch);
                }
            }
            case GUIDE -> {
                if (slot == 49) { openMain(p); return; }
                if (slot == 20) { openCompressorRecipe(p); return; }
                if (slot == 42) openRules(p);
            }
            case RULES -> { if (slot == 49) openMain(p); }
            case CATALOG -> {
                if (slot == 49) { openMain(p); return; }
                if (slot == 46) {
                    Section[] secs = Section.values();
                    Section next;
                    if (h.section == null) next = secs[0];
                    else {
                        int idx = h.section.ordinal() + 1;
                        next = idx < secs.length ? secs[idx] : null;
                    }
                    openCatalog(p, next, 0);
                    return;
                }
                if (slot == 45 && h.page > 0) { openCatalog(p, h.section, h.page - 1); return; }
                if (slot == 53) { openCatalog(p, h.section, h.page + 1); return; }
                if (slot >= 0 && slot < 45) {
                    List<Magic.StaffDef> items = new ArrayList<>();
                    for (Magic.StaffDef d : Magic.all()) {
                        Section sec = d.branch().section;
                        if (h.section == null || sec == h.section) items.add(d);
                    }
                    int idx = h.page * 45 + slot;
                    if (idx < items.size()) openBranch(p, items.get(idx).branch());
                }
            }
            case PROFILE -> { if (slot == 49) openMain(p); }
        }
    }

    // ---------- selftest ----------

    /** Инварианты меню: иконки, предметы, рецепты, сетки. Возвращает число FAIL. */
    public static int selftest(StringBuilder log) {
        int fail = 0;
        for (Magic.StaffDef d : Magic.all()) {
            Branch b = d.branch();
            if (b.section == null) { fail++; log.append("FAIL: ветка ").append(b.name()).append(" без секции\n"); }
            if (b.guiIcon == null || !b.guiIcon.isItem()) {
                fail++; log.append("FAIL: иконка ветки ").append(b.name()).append(" не предмет\n");
            }
            for (int tier = 1; tier <= 3; tier++) {
                if (ItemDefs.staff(b, tier) == null) {
                    fail++; log.append("FAIL: витрина посоха ").append(b.name()).append(':').append(tier).append('\n');
                }
                if (ItemDefs.substance(b, tier) == null) {
                    fail++; log.append("FAIL: витрина вещества ").append(b.name()).append(':').append(tier).append('\n');
                }
            }
            if (Items.medalItem(b, Conf.craftGateTier()) == null) {
                fail++; log.append("FAIL: медаль ").append(b.name()).append(" не строится\n");
            }
        }
        for (int tier = 1; tier <= 3; tier++) {
            if (Items.build(Conf.component(tier)) == null) {
                fail++; log.append("FAIL: компонент тира ").append(tier).append(" не строится\n");
            }
        }
        if (ItemDefs.compressorItem() == null) { fail++; log.append("FAIL: витрина компрессора\n"); }
        if (RECIPE_GRID.length != 9) { fail++; log.append("FAIL: грид рецепта ≠ 9\n"); }
        // Каталог: 73 посоха должны давать ≥2 страницы
        int pages = (Magic.all().size() + 44) / 45;
        if (pages < 2) { fail++; log.append("FAIL: каталог должен иметь ≥2 страницы\n"); }
        return fail;
    }
}
