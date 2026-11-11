package ru.deepmagic.cmd;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.deepmagic.DeepMagicPlugin;
import ru.deepmagic.menu.MenuGui;
import ru.deepmagic.model.ItemDefs;
import ru.deepmagic.model.Magic;
import ru.deepmagic.util.Conf;
import ru.deepmagic.util.Msg;
import ru.deepmagic.util.T;
import ru.deepprogression.model.Branch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /mage — вход в DeepMagic. Информационные команды УДАЛЕНЫ и заменены меню:
 *   /mage            — открыть «Магический Арсенал» (меню крафтов и справки)
 * Административные подкоманды (deepmagic.admin): give / selftest / reload.
 */
public final class MageCmd implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("give", "selftest", "reload");

    private final DeepMagicPlugin plugin;

    public MageCmd(DeepMagicPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Без аргументов: игроку — меню; консоли — краткая админ-справка.
        if (args.length == 0) {
            if (sender instanceof Player p) {
                MenuGui.open(p);
            } else {
                help(sender);
            }
            return true;
        }
        switch (T.lower(args[0])) {
            case "menu" -> {
                if (sender instanceof Player p) MenuGui.open(p);
                else Msg.dm(sender, "&cМеню доступно только игрокам.");
            }
            case "selftest" -> {
                if (!sender.hasPermission("deepmagic.admin")) {
                    Msg.dm(sender, "&cНет прав.");
                    return true;
                }
                selftest(sender);
            }
            case "give" -> {
                if (!sender.hasPermission("deepmagic.admin")) {
                    Msg.dm(sender, "&cНет прав.");
                    return true;
                }
                give(sender, args);
            }
            case "reload" -> {
                if (!sender.hasPermission("deepmagic.admin")) {
                    Msg.dm(sender, "&cНет прав.");
                    return true;
                }
                Conf.reload(plugin);
                Msg.dm(sender, "&aКонфиг DeepMagic перезагружен.");
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender s) {
        Msg.dm(s, "&5&lDeepMagic &7— магический арсенал DeepProgression");
        Msg.dm(s, "&e/mage &8— меню магии: крафты, каталог, синергии, профиль");
        if (s.hasPermission("deepmagic.admin")) {
            Msg.dm(s, "&e/mage give <игрок> <staff|substance> <ветка> [тир 1-3]");
            Msg.dm(s, "&e/mage selftest &8— самопроверка &8· &e/mage reload");
        }
    }

    private void give(CommandSender s, String[] args) {
        if (args.length < 4) {
            Msg.dm(s, "&e/mage give <игрок> <staff|substance> <ветка> [тир 1-3]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Msg.dm(s, "&cИгрок не в сети: &f" + args[1]);
            return;
        }
        Branch b = Branch.byId(args[3]);
        if (b == null || Magic.def(b) == null) {
            Msg.dm(s, "&cНеизвестная ветка: &f" + args[3]);
            return;
        }
        int tier = 1;
        if (args.length >= 5) {
            try {
                tier = Math.max(1, Math.min(3, Integer.parseInt(args[4])));
            } catch (NumberFormatException ignored) {
            }
        }
        ItemStack it;
        if (T.lower(args[2]).startsWith("staff")) {
            it = ItemDefs.staff(b, tier);
        } else if (T.lower(args[2]).startsWith("subst")) {
            it = ItemDefs.substance(b, tier);
        } else {
            Msg.dm(s, "&cТип: staff | substance");
            return;
        }
        if (it == null) {
            Msg.dm(s, "&cНе удалось построить предмет.");
            return;
        }
        var leftover = target.getInventory().addItem(it);
        for (ItemStack rest : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), rest);
        }
        Msg.dm(s, "&aВыдано: " + it.getItemMeta().getDisplayName());
        Msg.dm(target, "&aПолучен артефакт магии: " + it.getItemMeta().getDisplayName());
    }

    // ==================== selftest ====================

    private void selftest(CommandSender sender) {
        int ok = 0, fail = 0;
        StringBuilder log = new StringBuilder();
        T.send(sender, "&6=== Selftest DeepMagic ===");

        // 1) Реестр полон и совпадает с DeepProgression
        if (Magic.ready() && Magic.all().size() == Branch.values().length) {
            ok++;
        } else {
            fail++;
            log.append("FAIL: реестр ").append(Magic.all().size())
                    .append(" ≠ веток DP ").append(Branch.values().length).append('\n');
        }

        // 2) Вещества и посохи строятся (все ветки × 3 тира)
        for (Magic.StaffDef d : Magic.all()) {
            for (int tier = 1; tier <= 3; tier++) {
                if (ItemDefs.substance(d.branch(), tier) != null) ok++;
                else {
                    fail++;
                    log.append("FAIL: вещество ").append(d.branch().name()).append(':').append(tier).append('\n');
                }
            }
            if (ItemDefs.staff(d.branch(), 1) != null) ok++;
            else {
                fail++;
                log.append("FAIL: посох ").append(d.branch().name()).append('\n');
            }
        }
        if (ItemDefs.compressorItem() != null) ok++;
        else {
            fail++;
            log.append("FAIL: компрессор не строится\n");
        }

        // 3) Анти-копипаста: mechId уникальны
        java.util.Set<Magic.Mech> seen = new java.util.HashSet<>();
        for (Magic.StaffDef d : Magic.all()) {
            if (!seen.add(d.mech())) {
                fail++;
                log.append("FAIL: дубликат mechId ").append(d.mech()).append('\n');
            }
        }

        // 4) Топливо — vault-предмет СВОЕЙ ветки
        for (Magic.StaffDef d : Magic.all()) {
            if (ru.deepprogression.model.Blocks.branchOfItem(d.fuel()) == d.branch()) ok++;
            else {
                fail++;
                log.append("FAIL: топливо ").append(d.branch().name())
                        .append(" = ").append(d.fuel()).append(" не из ветки\n");
            }
        }

        // 5) Кулдауны и цены в допустимых пределах
        for (Magic.StaffDef d : Magic.all()) {
            int cd = Conf.cooldown(d);
            if (cd >= 2 && cd <= 120) ok++;
            else {
                fail++;
                log.append("FAIL: кулдаун ").append(d.branch().name()).append(" = ").append(cd).append('\n');
            }
            long units = Conf.unitsFor(d.branch().rarity);
            if (units >= 64 && units <= 64000) ok++;
            else {
                fail++;
                log.append("FAIL: цена вещества ").append(d.branch().name()).append(" = ").append(units).append('\n');
            }
        }

        // 6) Крафты: компрессор + 73 посоха × 3 тира, без коллизий и чужих медалей
        int craftFails = ru.deepmagic.craft.Crafts.selftest(log);
        fail += craftFails;
        ok += 1 + Magic.all().size() * 3 - craftFails;

        // 7) Состояния: ID — ASCII (гигиена PDC)
        boolean ascii = true;
        for (ru.deepmagic.model.State st : ru.deepmagic.model.State.values()) {
            if (!st.name().matches("[A-Z0-9_]+")) ascii = false;
        }
        if (ascii) ok++;
        else {
            fail++;
            log.append("FAIL: не-ASCII ID состояний\n");
        }

        // 8) Гигиена имён: ни одного сырого '&' в отображаемых текстах
        boolean rawAmp = false;
        for (Magic.StaffDef d : Magic.all()) {
            for (int tier = 1; tier <= 3; tier++) {
                ItemStack s = ItemDefs.substance(d.branch(), tier);
                if (s == null || hasRawAmp(s)) rawAmp = true;
            }
            ItemStack stf = ItemDefs.staff(d.branch(), 1);
            if (stf == null || hasRawAmp(stf)) rawAmp = true;
        }
        ItemStack comp = ItemDefs.compressorItem();
        if (comp == null || hasRawAmp(comp)) rawAmp = true;
        if (!rawAmp) ok++;
        else {
            fail++;
            log.append("FAIL: сырой '&' в названиях/лоре предметов (путь мимо T.color)\n");
        }

        // 9) Меню: иконки, витрины, рецепты, каталог
        int menuFails = MenuGui.selftest(log);
        fail += menuFails;
        ok += Magic.all().size() + 3 + 1 - menuFails;

        // 10) Сводка топлива
        if (log.length() == 0) {
            T.send(sender, "&7Топливо 73 посохов: " + fuelSummary());
        }

        T.send(sender, "&6=== Результат: &a" + ok + " OK&6, &c" + fail
                + " FAIL &8(73 ветки, " + Magic.all().size() * 3 + " веществ, 74 крафта, меню) ===");
        if (log.length() > 0) {
            for (String line : log.toString().split("\n")) {
                if (!line.isBlank()) T.send(sender, "&c" + line);
            }
        }
    }

    /** true — в отображаемом имени/лоре есть сырой '&' (не переведён через T.color). */
    private boolean hasRawAmp(ItemStack it) {
        if (it == null || !it.hasItemMeta() || it.getItemMeta() == null) return true;
        ItemMeta meta = it.getItemMeta();
        String name = meta.getDisplayName();
        if (name != null && name.indexOf('&') >= 0) return true;
        List<String> lore = meta.getLore();
        if (lore != null) {
            for (String line : lore) {
                if (line != null && line.indexOf('&') >= 0) return true;
            }
        }
        return false;
    }

    private String fuelSummary() {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Magic.StaffDef d : Magic.all()) {
            if (i++ > 0) sb.append(", ");
            sb.append(d.branch().name().toLowerCase(Locale.ROOT)).append('=')
                    .append(d.fuelQty()).append('×').append(d.fuel().name().toLowerCase(Locale.ROOT));
            if (sb.length() > 1600) {
                sb.append(" …");
                break;
            }
        }
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("deepmagic.admin")) return out;
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(SUBS);
            subs.add("menu");
            for (String s : subs) if (s.startsWith(T.lower(args[0]))) out.add(s);
            return out;
        }
        if (args.length == 2 && T.lower(args[0]).equals("give")) {
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(T.lower(args[1])))
                    out.add(p.getName());
            return out;
        }
        if (T.lower(args[0]).equals("give") && args.length == 3) {
            for (String t : List.of("staff", "substance"))
                if (t.startsWith(T.lower(args[2]))) out.add(t);
            return out;
        }
        if (T.lower(args[0]).equals("give") && args.length == 4) {
            for (Branch b : Branch.values())
                if (b.name().toLowerCase(Locale.ROOT).startsWith(T.lower(args[3])))
                    out.add(b.name());
            return out;
        }
        if (T.lower(args[0]).equals("give") && args.length == 5) {
            out.addAll(List.of("1", "2", "3"));
        }
        return out;
    }
}
