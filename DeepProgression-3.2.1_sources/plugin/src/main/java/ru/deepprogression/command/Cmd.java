package ru.deepprogression.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.deepprogression.DeepProgressionPlugin;
import ru.deepprogression.gui.Gui;
import ru.deepprogression.model.Blocks;
import ru.deepprogression.model.Branch;
import ru.deepprogression.model.Section;
import ru.deepprogression.progress.Medals;
import ru.deepprogression.progress.PlayerProfile;
import ru.deepprogression.progress.ProfileStore;
import ru.deepprogression.tools.Items;
import ru.deepprogression.tools.Tools;
import ru.deepprogression.util.Cfg;
import ru.deepprogression.util.Msg;
import ru.deepprogression.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /dp, /deepprogression — все подкоманды. */
public final class Cmd implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "menu", "progress", "crafts", "stats", "top", "medals", "quests",
            "perks", "economy", "give", "reload", "selftest", "help");

    private final DeepProgressionPlugin plugin;

    public Cmd(DeepProgressionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) Gui.openMain(p);
            else Msg.sendList(sender, "cmd.help");
            return true;
        }
        String sub = Text.lower(args[0]);
        switch (sub) {
            case "menu" -> {
                if (!(sender instanceof Player p)) { Msg.send(sender, "cmd.player-only"); return true; }
                Gui.openMain(p);
            }
            case "progress" -> {
                if (!(sender instanceof Player p)) { Msg.send(sender, "cmd.player-only"); return true; }
                if (args.length >= 2) {
                    Branch b = Branch.byId(args[1]);
                    if (b == null) { Msg.send(sender, "cmd.unknown"); return true; }
                    Gui.openBranch(p, b);
                } else {
                    printProgress(p);
                }
            }
            case "crafts" -> {
                if (!(sender instanceof Player p)) { Msg.send(sender, "cmd.player-only"); return true; }
                if (args.length >= 2 && Tools.exists(args[1].toLowerCase(Locale.ROOT))) {
                    Gui.openRecipe(p, Text.lower(args[1]));
                } else {
                    Gui.openCrafts(p);
                }
            }
            case "stats" -> {
                if (!(sender instanceof Player p)) { Msg.send(sender, "cmd.player-only"); return true; }
                Gui.openStats(p);
            }
            case "top" -> {
                Branch tb = null;
                if (args.length >= 2) {
                    tb = Branch.byId(args[1]);
                    if (tb == null) { Msg.send(sender, "cmd.unknown"); return true; }
                }
                Branch branch = tb;
                if (branch == null) {
                    Msg.sendList(sender, "cmd.top-header", "n", "10");
                    ProfileStore.topAsync(10, (lines, any) -> {
                        if (sender instanceof Player pl && !pl.isOnline()) return;
                        if (!any) { Msg.send(sender, "cmd.top-empty"); return; }
                        int pos = 1;
                        for (String line : lines) {
                            int i = line.lastIndexOf(';');
                            if (i <= 0) continue;
                            Msg.send(sender, "cmd.top-line",
                                    "pos", String.valueOf(pos++),
                                    "player", line.substring(0, i),
                                    "count", Text.num(Long.parseLong(line.substring(i + 1))));
                        }
                    });
                } else {
                    final Branch fb = branch;
                    Msg.sendList(sender, "cmd.top-branch-header",
                            "branch", Text.color(fb.color + fb.display), "n", "10");
                    ProfileStore.topBranchAsync(fb, 10, (lines, any) -> {
                        if (sender instanceof Player pl && !pl.isOnline()) return;
                        if (!any) { Msg.send(sender, "cmd.top-empty"); return; }
                        int pos = 1;
                        for (String line : lines) {
                            int i = line.lastIndexOf(';');
                            if (i <= 0) continue;
                            Msg.send(sender, "cmd.top-branch-line",
                                    "pos", String.valueOf(pos++),
                                    "player", line.substring(0, i),
                                    "count", Text.num(Long.parseLong(line.substring(i + 1))));
                        }
                    });
                }
            }
            case "quests" -> {
                if (!(sender instanceof Player p)) { Msg.send(sender, "cmd.player-only"); return true; }
                ru.deepprogression.progress.Quests.sendList(p);
            }
            case "perks" -> {
                if (!(sender instanceof Player p)) { Msg.send(sender, "cmd.player-only"); return true; }
                Gui.openPerks(p);
            }
            case "economy" -> {
                int page = args.length >= 2 ? Math.max(0, parseIntSafe(args[1]) - 1) : 0;
                if (sender instanceof Player p) Gui.openEconomy(p, page);
                else printEconomy(sender);
            }
            case "medals" -> {
                if (!(sender instanceof Player p)) { Msg.send(sender, "cmd.player-only"); return true; }
                int total = 0;
                if (args.length >= 2) {
                    Branch b = Branch.byId(args[1]);
                    if (b == null) { Msg.send(sender, "cmd.unknown"); return true; }
                    total = Medals.reissue(p, b);
                    if (total == 0) Msg.send(p, "medal.medals-none");
                    else Msg.send(p, "medal.medals-given", "n", String.valueOf(total),
                            "branch", Text.color(b.color + b.display));
                } else {
                    for (Branch b : Branch.values()) total += Medals.reissue(p, b);
                    Msg.send(p, "medal.medals-given", "n", String.valueOf(total),
                            "branch", "все ветки");
                }
            }
            case "give" -> {
                if (!sender.hasPermission("deepprogression.admin")) { Msg.send(sender, "cmd.no-perm"); return true; }
                if (args.length < 3) { Msg.send(sender, "cmd.give-usage"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { Msg.send(sender, "cmd.give-unknown-player", "player", args[1]); return true; }
                String id = Text.lower(args[2]);
                // v3.2: кап количества — защита от опечатки админа (watchdog-килл)
                int qty = args.length >= 4 ? Math.max(1, Math.min(2304, parseIntSafe(args[3]))) : 1;

                ItemStack item;
                boolean isMedal = id.startsWith("medal_");
                Branch medalBranch = null;
                int medalTier = 0;
                if (isMedal) {
                    // v3.2: ветки с «_» в имени (DARK_OAK, SUGAR_CANE…) — разбор
                    // по последнему подчёркиванию: medal_<ветка…>_<тир>
                    int last = id.lastIndexOf('_');
                    if (last <= 6 || last == id.length() - 1) {
                        Msg.send(sender, "cmd.give-unknown-tool", "tool", id); return true;
                    }
                    medalBranch = Branch.byId(id.substring(6, last));
                    medalTier = parseIntSafe(id.substring(last + 1));
                    if (medalBranch == null || medalTier < 1 || medalTier > 10) { Msg.send(sender, "cmd.give-unknown-tool", "tool", id); return true; }
                    item = Items.medalItem(medalBranch, medalTier);
                } else {
                    if (!Tools.exists(id)) { Msg.send(sender, "cmd.give-unknown-tool", "tool", id); return true; }
                    item = Items.build(id);
                }
                if (item == null) { Msg.send(sender, "cmd.give-unknown-tool", "tool", id); return true; }
                // v3.2: выдача по одному предмету — предметы с лимитом стака 1
                // (зонды, сканеры, дыхательный баллон) иначе молча терялись
                int given = 0;
                for (int i = 0; i < qty; i++) {
                    ItemStack single = isMedal
                            ? Items.medalItem(medalBranch, medalTier)
                            : Items.build(id);
                    if (single == null) break;
                    var leftover = target.getInventory().addItem(single);
                    for (ItemStack rest : leftover.values()) {
                        target.getWorld().dropItemNaturally(target.getLocation(), rest);
                    }
                    given++;
                }
                if (given == 0) { Msg.send(sender, "cmd.give-unknown-tool", "tool", id); return true; }
                Msg.send(sender, "cmd.give-done",
                        "item", item.getItemMeta() != null ? item.getItemMeta().getDisplayName() : id,
                        "player", target.getName());
            }
            case "reload" -> {
                if (!sender.hasPermission("deepprogression.admin")) { Msg.send(sender, "cmd.no-perm"); return true; }
                Cfg.reload(plugin);
                Msg.reload(plugin);
                Msg.send(sender, "cmd.reload-done");
            }
            case "selftest" -> {
                if (!sender.hasPermission("deepprogression.admin")) { Msg.send(sender, "cmd.no-perm"); return true; }
                selfTest(sender);
            }
            case "help" -> Msg.sendList(sender, "cmd.help");
            default -> Msg.send(sender, "cmd.unknown");
        }
        return true;
    }

    private int ok = 0;
    private int fail = 0;

    private void ok(CommandSender s, String label) {
        Text.send(s, "&a✔ " + label);
        ok++;
    }

    private void fail(CommandSender s, String label, String why) {
        Text.send(s, "&c✖ " + label + " &8(" + why + ")");
        fail++;
    }

    /** Самопроверка реестра, матчера крафтов и полноты покрытия. */
    private void selfTest(CommandSender sender) {
        ok = 0;
        fail = 0;
        Text.send(sender, "&6=== Selftest DeepProgression ===");

        // 1) Сборка представительных предметов
        for (String id : List.of("steel_ingot", "steel_plate", "board_basic",
                "board_elite", "node_power", "node_control", "node_drive",
                "tool_body", "bit_coal", "bit_oak", "bit_sculk",
                "tool_coal_1", "tool_coal_3", "tool_oak_2", "tool_sculk_3",
                "universal_mining_1", "universal_wood_2",
                "chunk_drill", "ore_pouch", "ore_vault",
                "sawmill", "composter", "dryer", "glassworks",
                "luminaforge", "assembler", "altar", "chorus_reaper",
                "prospector", "battery", "geologist_pick", "geo_scanner")) {
            if (Items.build(id) != null) ok++;
            else fail(sender, "build " + id, "null");
        }

        // 2) Полное покрытие: каждая ветка — 3 инструмента + насадка + ресурс в реестре
        for (Branch b : Branch.values()) {
            String low = b.name().toLowerCase(Locale.ROOT);
            boolean fine = true;
            for (int t = 1; t <= 3; t++) {
                if (!Tools.exists("tool_" + low + "_" + t)) fine = false;
            }
            if (!Tools.exists("bit_" + low)) fine = false;
            if (Blocks.branchOfItem(b.medalRawIcon) == null) fine = false;
            if (fine) ok++;
            else fail(sender, "coverage " + low, "инструменты/насадка/реестр");
        }

        // 3) Покрытие секций: 3 универсала + 2 машины + сканер (кроме шахтёров) + кристалл + гаджет
        for (Section s : Section.values()) {
            String low = s.name().toLowerCase(Locale.ROOT);
            boolean fine = true;
            for (int t = 1; t <= 3; t++) {
                if (!Tools.exists("universal_" + low + "_" + t)) fine = false;
            }
            if (!Tools.exists("crystal_" + low)) fine = false;
            if (ru.deepprogression.recipe.Recipes.rows(machineOf(s)) == null) fine = false;
            // v3.2: у шахтёров приспособления другого класса (зонд/батарея/бур) —
            // ручной гаджет обязателен только для остальных секций
            if (s != Section.MINING
                    && Tools.gadgets().stream().noneMatch(g -> g.section == s)) fine = false;
            if (!Tools.exists(machineOf(s))) fine = false;
            if (fine) ok++;
            else fail(sender, "section " + low, "универсалы/машины/кристалл/гаджет");
        }

        // 3a) Секционные сканеры (все, кроме шахтёрской) и эхо-камера
        for (Section s : Section.values()) {
            if (s == Section.MINING) continue;
            String id = "scanner_" + s.name().toLowerCase(Locale.ROOT);
            if (Items.build(id) != null) ok++;
            else fail(sender, "build " + id, "null");
        }
        for (Section s : Section.values()) {
            String id = "crystal_" + s.name().toLowerCase(Locale.ROOT);
            if (Items.build(id) != null) ok++;
            else fail(sender, "build " + id, "null");
        }
        if (Items.build("echo_chamber") != null) ok++;
        else fail(sender, "build echo_chamber", "null");
        for (String gid : List.of("resinworks", "bakery", "nursery", "coral_farm",
                "stone_works", "candleworks", "rail_works", "foundry", "end_forge",
                "resonance_chamber", "pruner", "seeder", "bonewand", "air_tank",
                "trowel", "lightwand", "oiler", "thermos", "voidsail", "jammer")) {
            if (Items.build(gid) != null) ok++;
            else fail(sender, "build " + gid, "null");
        }

        // 3a-2) v3.2: иконка каждой ставящейся машины — блок
        // (машины на блоках с block-entity тегируются в блоке, остальные — в PDC чанка;
        // ловит класс бага 3.1: echo_chamber с иконкой-предметом ECHO_SHARD не ставился)
        for (String mid : ru.deepprogression.tools.Machines.PLACEABLE) {
            Tools.ToolSpec s = Tools.get(mid);
            if (s == null) { fail(sender, "машина в реестре " + mid, "null"); continue; }
            if (s.icon.isBlock()) ok++;
            else fail(sender, "иконка машины не блок: " + mid, s.icon.name());
        }

        // 3a-3) v3.2: иконка каждого ручного приспособления — существующий предмет
        for (Tools.ToolSpec g : Tools.gadgets()) {
            if (g.icon.isItem()) ok++;
            else fail(sender, "иконка гаджета не предмет: " + g.id, g.icon.name());
        }

        // 3b) Перки и экономика
        if (ru.deepprogression.perks.Perks.count() == Section.values().length * 4) ok++;
        else fail(sender, "перки", "ожидалось " + Section.values().length * 4
                + ", есть " + ru.deepprogression.perks.Perks.count());
        if (ru.deepprogression.economy.Economy.trackedCount() >= 50) ok++;
        else fail(sender, "экономика", "материалов меньше 50: "
                + ru.deepprogression.economy.Economy.trackedCount());

        // 4) Матчинг: tool_coal_2 (.C./PNM/.B. — медаль тир 2)
        ItemStack[] matrix = new ItemStack[9];
        matrix[1] = Items.build("bit_coal");
        matrix[3] = Items.build("node_power");
        matrix[4] = Items.build("node_control");
        matrix[5] = Items.medalItem(Branch.COAL, 2);
        matrix[7] = Items.build("tool_body");
        check(sender, "tool_coal_2", "tool_coal_2",
                ru.deepprogression.recipe.Recipes.matchMatrix(matrix));

        // 5) Медаль выше требуемой — матчится
        matrix[5] = Items.medalItem(Branch.COAL, 5);
        check(sender, "tool_coal_2(медаль 5)", "tool_coal_2",
                ru.deepprogression.recipe.Recipes.matchMatrix(matrix));

        // 6) Чужая ветка медали — не матчится
        matrix[5] = Items.medalItem(Branch.IRON, 5);
        check(sender, "tool_coal_2(чужая медаль)", null,
                ru.deepprogression.recipe.Recipes.matchMatrix(matrix));

        // 7) Тир III с медалью 2 — не матчится (требуется 4)
        matrix = new ItemStack[9];
        matrix[1] = Items.build("bit_diamond");
        matrix[3] = Items.build("node_power");
        matrix[4] = Items.build("node_control");
        matrix[5] = Items.medalItem(Branch.DIAMOND, 2);
        matrix[7] = Items.build("reinforced_frame");
        check(sender, "tool_diamond_3(медаль 2<4)", null,
                ru.deepprogression.recipe.Recipes.matchMatrix(matrix));

        // 8) tool_oak_1: вертикальный 3×1
        matrix = new ItemStack[9];
        matrix[0] = Items.build("bit_oak");
        matrix[3] = Items.build("iron_gear");
        matrix[6] = Items.build("steel_plate");
        check(sender, "tool_oak_1", "tool_oak_1",
                ru.deepprogression.recipe.Recipes.matchMatrix(matrix));

        // 9) Пилорама: рама + пильный диск + медаль дуба 3 + плата II
        matrix = new ItemStack[9];
        matrix[1] = Items.build("reinforced_frame");
        matrix[3] = Items.build("saw_blade");
        matrix[4] = Items.medalItem(Branch.OAK, 3);
        matrix[7] = Items.build("board_advanced");
        check(sender, "sawmill", "sawmill",
                ru.deepprogression.recipe.Recipes.matchMatrix(matrix));

        // 10) Универсал лесоруба I: сборная насадка + шестерня + сырьё флагмана
        matrix = new ItemStack[9];
        matrix[1] = Items.build("bit_universal");
        matrix[4] = Items.build("iron_gear");
        matrix[7] = new ItemStack(org.bukkit.Material.OAK_PLANKS);
        check(sender, "universal_wood_1", "universal_wood_1",
                ru.deepprogression.recipe.Recipes.matchMatrix(matrix));
        matrix[7] = new ItemStack(org.bukkit.Material.RAW_IRON);
        check(sender, "universal_mining_1", "universal_mining_1",
                ru.deepprogression.recipe.Recipes.matchMatrix(matrix));

        // 11) Плата улучшенная
        matrix = new ItemStack[9];
        matrix[0] = Items.build("quartz_chip");
        matrix[1] = Items.build("copper_coil");
        matrix[2] = Items.build("quartz_chip");
        matrix[4] = Items.build("board_basic");
        check(sender, "board_advanced", "board_advanced",
                ru.deepprogression.recipe.Recipes.matchMatrix(matrix));

        // 12) Подмена ингредиента — не матчится
        matrix = new ItemStack[9];
        matrix[0] = Items.build("quartz_chip");
        matrix[1] = Items.build("copper_coil");
        matrix[2] = Items.build("quartz_chip");
        matrix[4] = Items.build("quartz_chip");
        check(sender, "board_advanced(подмена)", null,
                ru.deepprogression.recipe.Recipes.matchMatrix(matrix));

        // 13) Связность дерева: каждый компонент где-то используется
        java.util.Set<String> used = ru.deepprogression.recipe.Recipes.usedToolIds();
        java.util.Set<String> roots = ru.deepprogression.recipe.Recipes.rootIds();
        for (Tools.ToolSpec s : Tools.all()) {
            if (s.type != Tools.ToolType.COMPONENT) continue;
            if (used.contains(s.id)) continue;
            if (!roots.contains(s.id)) {
                fail(sender, "бесполезный предмет " + s.id, "не в рецептах и не корневой");
            }
        }
        // 14) Каждый ингредиент-предмет имеет собственный рецепт
        for (String ing : used) {
            if (ru.deepprogression.recipe.Recipes.rows(ing) == null) {
                fail(sender, "без рецепта " + ing, "дерево разорвано");
            }
        }

        // 15) v3.2: каждый РУЧНОЙ рецепт уникально матчится сам собой (без коллизий)
        // (ванильные проверяются самой регистрацией Bukkit.addRecipe)
        int collisions = 0;
        for (String rid : ru.deepprogression.recipe.Recipes.specIds()) {
            String[] rows = ru.deepprogression.recipe.Recipes.rows(rid);
            if (rows == null) continue;
            ItemStack[] selfMatrix = new ItemStack[9];
            boolean built = true;
            for (int r = 0; r < rows.length && built; r++) {
                for (int c = 0; c < rows[r].length() && built; c++) {
                    char ch = rows[r].charAt(c);
                    if (ch == '.') continue;
                    ru.deepprogression.recipe.Recipes.Ing ing =
                            ru.deepprogression.recipe.Recipes.ingredient(rid, ch);
                    if (ing == null) { built = false; break; }
                    ItemStack it;
                    if (ing.mat() != null) it = new ItemStack(ing.mat());
                    else if (ing.toolId() != null) {
                        it = Items.build(ing.toolId());
                        if (it == null) { built = false; break; }
                    } else {
                        Branch mb = Branch.byId(ing.medalBranch());
                        if (mb == null) { built = false; break; }
                        it = Items.medalItem(mb, Math.min(10, ing.medalTier()));
                    }
                    selfMatrix[r * 3 + c] = it;
                }
            }
            if (!built) { fail(sender, "рецепт не строится " + rid, "ингредиент null"); continue; }
            String m = ru.deepprogression.recipe.Recipes.matchMatrix(selfMatrix);
            if (!rid.equals(m)) {
                collisions++;
                fail(sender, "коллизия/несоответствие рецепта " + rid,
                        "матч: " + m);
            }
        }
        if (collisions == 0) ok(sender, "уникальность всех рецептов");

        Text.send(sender, "&6=== Результат: &a" + ok + " OK&6, &c" + fail + " FAIL &6(" +
                ru.deepprogression.recipe.Recipes.count() + " крафтов зарегистрировано) ===");
    }

    private String machineOf(Section s) {
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

    private void check(CommandSender sender, String label, String expect, String actual) {
        boolean pass;
        if (expect == null) pass = actual == null;
        else pass = expect.equals(actual);
        if (pass) ok(sender, label);
        else fail(sender, label, "ожидалось: " + expect + ", получено: " + actual);
    }

    private void printProgress(Player p) {
        PlayerProfile prof = ProfileStore.get(p.getUniqueId());
        Msg.sendList(p, "cmd.progress-header");
        int shown = 0;
        for (Section s : Section.values()) {
            for (Branch b : Branch.ofSection(s)) {
                long count = prof.getBlocks(b);
                int tier = prof.getTier(b);
                if (count == 0 && tier == 0) continue; // компактный вывод
                shown++;
                String name = Text.color(b.color + b.display);
                if (tier >= 10) {
                    Msg.send(p, "cmd.progress-max", "branch", name, "tier", b.tierColored(10));
                } else {
                    long[] th = Cfg.thresholds(b);
                    long cur = th[tier];
                    String bar = Text.bar(count, cur, 10);
                    Msg.send(p, "cmd.progress-line",
                            "branch", name,
                            "tier", tier == 0 ? "—" : b.tierColored(tier),
                            "count", Text.num(count), "max", Text.num(cur),
                            "bar", bar, "percent", Text.pct(count, cur));
                }
            }
        }
        if (shown == 0) Msg.send(p, "cmd.progress-empty");
    }

    private int parseIntSafe(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 1; }
    }

    /** /dp economy для консоли — сводка в чат. */
    private void printEconomy(CommandSender sender) {
        Msg.sendList(sender, "cmd.economy-header");
        var mats = ru.deepprogression.economy.Economy.trackedMaterials();
        int shown = 0;
        for (var m : mats) {
            if (shown++ >= 15) break;
            int idx = ru.deepprogression.economy.Economy.index(m);
            Msg.send(sender, "cmd.economy-line",
                    "item", Items.ruName(m),
                    "index", String.valueOf(idx),
                    "value", String.valueOf(ru.deepprogression.economy.Economy.baseValue(m)),
                    "total", Text.num(ru.deepprogression.economy.Economy.total(m)),
                    "week", Text.num(ru.deepprogression.economy.Economy.week(m)));
        }
        Msg.send(sender, "cmd.economy-more", "n", String.valueOf(mats.size()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(Text.lower(args[0]))) out.add(s);
            return out;
        }
        String sub = Text.lower(args[0]);
        if (args.length == 2) {
            switch (sub) {
                case "progress", "medals", "top" -> {
                    for (Branch b : Branch.values())
                        if (b.name().toLowerCase(Locale.ROOT).startsWith(Text.lower(args[1])))
                            out.add(b.name());
                }
                case "crafts" -> {
                    for (var s : Tools.all())
                        if (s.id.startsWith(Text.lower(args[1]))) out.add(s.id);
                }
                case "give" -> {
                    for (var pl : Bukkit.getOnlinePlayers())
                        if (pl.getName().toLowerCase(Locale.ROOT).startsWith(Text.lower(args[1])))
                            out.add(pl.getName());
                }
                case "economy" -> {
                    for (int i = 1; i <= 5; i++) out.add(String.valueOf(i));
                }
                default -> { }
            }
            return out;
        }
        if (args.length == 3 && sub.equals("give")) {
            String pref = Text.lower(args[2]);
            for (var s : Tools.all()) if (s.id.startsWith(pref)) out.add(s.id);
            for (Branch b : Branch.values()) {
                String m = ("medal_" + b.name()).toLowerCase(Locale.ROOT);
                if (m.startsWith(pref)) out.add("medal_" + b.name() + "_10");
            }
            return out;
        }
        return out;
    }
}
