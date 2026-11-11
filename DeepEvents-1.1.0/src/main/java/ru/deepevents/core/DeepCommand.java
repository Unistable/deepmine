package ru.deepevents.core;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import ru.deepevents.DeepEventsPlugin;

import java.util.ArrayList;
import java.util.List;

/** /deepevents — статус, триггер, стоп, undo, selftest, reload. */
public final class DeepCommand implements CommandExecutor, TabCompleter {

    private final DeepEventsPlugin plugin;
    private final ConfigManager config;
    private final EventManager manager;
    private final EventScheduler scheduler;
    private final RestoreService restores;
    private final RadiationZones radiation;

    public DeepCommand(DeepEventsPlugin plugin, ConfigManager config, EventManager manager,
                       EventScheduler scheduler, RestoreService restores, RadiationZones radiation) {
        this.plugin = plugin;
        this.config = config;
        this.manager = manager;
        this.scheduler = scheduler;
        this.restores = restores;
        this.radiation = radiation;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "status" -> {
                long min = scheduler.minutesToNext();
                String next = min >= 0 ? (min + " мин.") : "не запланирован";
                sender.sendMessage(Msg.prefixed("Статус: " + manager.statusLine()
                        + "<gray> | следующий случайный ивент через:</gray> " + next
                        + "<gray> | радиационных зон:</gray> " + radiation.count()));
            }
            case "trigger" -> {
                if (!admin(sender)) return true;
                if (args.length < 2) {
                    sender.sendMessage(Msg.prefixed("<red>Использование: /deepevents trigger <kraken|nuke|starfall|random></red>"));
                    return true;
                }
                EventManager.EventType t = switch (args[1].toLowerCase()) {
                    case "kraken" -> EventManager.EventType.KRAKEN;
                    case "nuke", "nuclear" -> EventManager.EventType.NUKE;
                    case "starfall" -> EventManager.EventType.STARFALL;
                    case "random" -> null;
                    default -> EventManager.EventType.KRAKEN;
                };
                sender.sendMessage(Msg.prefixed("<aqua>Запускаю: <b>" + (t == null ? "случайный" : t) + "</b>…</aqua>"));
                if (t == null) manager.startRandom(sender);
                else manager.start(t, sender);
            }
            case "stop" -> {
                if (!admin(sender)) return true;
                manager.stopActive(sender);
            }
            case "undo" -> {
                if (!admin(sender)) return true;
                RestoreService.RestoreSession s = restores.lastSession();
                if (s == null) {
                    // попытка после рестарта
                    if (!restores.restoreFromDisk("nuke")) {
                        sender.sendMessage(Msg.prefixed("<yellow>Нет снапшота для отката.</yellow>"));
                        return true;
                    }
                    s = restores.lastSession();
                }
                boolean ok = s.restore();
                if (!ok) {
                    // последняя сессия — не кратер (или уже откачена) — берём снапшот кратера с диска
                    ok = restores.restoreFromDisk("nuke");
                    s = restores.lastSession();
                }
                sender.sendMessage(Msg.prefixed(ok && s != null
                        ? "<green>Откат '" + s.tag() + "' запущен: " + s.size() + " блоков.</green>"
                        : "<yellow>Снапшот пуст или уже восстановлен.</yellow>"));
                // радиационная зона снимается вместе с кратером
                if (ok && s.origin() != null) {
                    boolean removed = radiation.removeNear(s.origin(), 80);
                    if (removed) sender.sendMessage(Msg.prefixed("<green>Радиационная зона снята вместе с кратером.</green>"));
                }
            }
            case "selftest" -> {
                if (!admin(sender)) return true;
                selftest(sender);
            }
            case "reload" -> {
                if (!admin(sender)) return true;
                config.reload();
                scheduler.start();
                sender.sendMessage(Msg.prefixed("<green>Конфиг перезагружен.</green>"));
            }
            default -> help(sender);
        }
        return true;
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("deepevents.admin")) return true;
        s.sendMessage(Msg.mm("<red>Нужно право deepevents.admin</red>"));
        return false;
    }

    private void help(CommandSender s) {
        s.sendMessage(Msg.mm("""
                <gradient:#5eead4:#0ea5e9><bold>DeepEvents</bold></gradient> <gray>— случайные ивенты</gray>
                <aqua>/deepevents status</aqua> <gray>- что происходит и когда следующий</gray>
                <aqua>/deepevents trigger <kraken|nuke|starfall|random></aqua> <gray>- запустить (админ)</gray>
                <aqua>/deepevents stop</aqua> <gray>- остановить текущий ивент (админ)</gray>
                <aqua>/deepevents undo</aqua> <gray>- откатить кратер ядерного взрыва (админ)</gray>
                <aqua>/deepevents selftest</aqua> <gray>- внутренние проверки (админ)</gray>
                <aqua>/deepevents reload</aqua> <gray>- перечитать конфиг (админ)</gray>"""));
    }

    /** Самопроверка: конфиг, лут, математика, снапшот. */
    private void selftest(CommandSender s) {
        List<String> fail = new ArrayList<>();
        List<String> ok = new ArrayList<>();

        // 1. Миры из конфига существуют
        for (String w : config.worlds()) {
            if (plugin.getServer().getWorld(w) != null) ok.add("мир " + w);
            else fail.add("мир не найден: " + w);
        }
        // 2. Лут-таблицы непусты
        if (!config.loot("kraken").isEmpty()) ok.add("лут кракена (" + config.loot("kraken").size() + ")");
        else fail.add("лут кракена пуст");
        if (!config.loot("nuke").isEmpty()) ok.add("лут ядерки (" + config.loot("nuke").size() + ")");
        else fail.add("лут ядерки пуст");
        // 3. Благословения валидны
        if (!config.blessings().isEmpty()) ok.add("благословения (" + config.blessings().size() + ")");
        else fail.add("благословения пусты");
        // 4. Материалы трофея
        if (config.trophyMaterial() != null) ok.add("трофей кракена");
        else fail.add("материал трофея невалиден");
        // 5. Волны: амплитуда > 0, скорость > 0
        if (config.waveAmplitude() > 0 && config.waveSpeed() > 0) ok.add("волны: параметры");
        else fail.add("волны: нулевые параметры");
        // 6. Лут-математика: справедливость
        try {
            java.util.Random rnd = new java.util.Random(42);
            int[] shares = LootMath.deal(java.util.List.of(1, 1, 1, 1, 1), 3, rnd);
            int sum = shares[0] + shares[1] + shares[2];
            if (sum == 5 && Math.max(shares[0], Math.max(shares[1], shares[2])) - Math.min(shares[0], Math.min(shares[1], shares[2])) <= 1) {
                ok.add("лут-математика: справедливый делёж");
            } else fail.add("лут-математика: делёж несправедлив");
        } catch (Exception e) {
            fail.add("лут-математика: исключение " + e.getMessage());
        }
        // 7. Волны: амплитуда ограничена
        double eta = WaveMath.eta(3.5, 10, 1.7, 7.0, 0, 0.85, 400);
        if (Math.abs(eta) <= 1.7 + 1e-9) ok.add("волны: |eta| <= A");
        else fail.add("волны: eta вне амплитуды");
        // 8. Кратер: глубина в границах
        int depth = CraterMath.columnDepth(0, 27, 13);
        if (depth == 13) ok.add("кратер: эпицентр = макс. глубина");
        else fail.add("кратер: глубина эпицентра != " + 13);
        // 9. Радиационные эффекты парсятся
        if (!config.radiationEffects().isEmpty()) ok.add("радиация: эффекты (" + config.radiationEffects().size() + ")");
        else fail.add("радиация: эффекты не парсятся");

        s.sendMessage(Msg.prefixed("SelfTest: <green>" + ok.size() + " ✓</green> <red>" + fail.size() + " ✗</red>"));
        for (String o : ok) s.sendMessage(Msg.mm("  <dark_green>✓ " + o + "</dark_green>"));
        for (String f : fail) s.sendMessage(Msg.mm("  <red>✗ " + f + "</red>"));
        Msg.log("SelfTest: ok=" + ok.size() + " fail=" + fail.size());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("status", "trigger", "stop", "undo", "selftest", "reload", "help"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("trigger")) {
            out.addAll(List.of("kraken", "nuke", "starfall", "random"));
        }
        String prefix = args[args.length - 1].toLowerCase();
        out.removeIf(s -> !s.toLowerCase().startsWith(prefix));
        return out;
    }
}
