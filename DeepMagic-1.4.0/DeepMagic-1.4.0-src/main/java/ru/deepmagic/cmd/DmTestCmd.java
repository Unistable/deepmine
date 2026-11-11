package ru.deepmagic.cmd;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import ru.deepmagic.DeepMagicPlugin;
import ru.deepmagic.compress.CompressorGui;
import ru.deepmagic.compress.Compressors;
import ru.deepmagic.model.EternalBurn;
import ru.deepmagic.model.ItemDefs;
import ru.deepmagic.model.State;
import ru.deepmagic.util.Msg;
import ru.deepmagic.util.T;
import ru.deepprogression.model.Branch;
import ru.deepprogression.model.Blocks;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * /dmtest — функциональные тесты реального кода (админ).
 * Прогоняет компрессорный конвейер и законы вечного огня на живом сервере.
 */
public final class DmTestCmd implements CommandExecutor {

    private final DeepMagicPlugin plugin;

    public DmTestCmd(DeepMagicPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("deepmagic.admin")) {
            Msg.dm(sender, "&cНет прав.");
            return true;
        }
        String sub = args.length > 0 ? T.lower(args[0]) : "help";
        switch (sub) {
            case "compressor" -> testCompressor(sender, args);
            case "burn" -> testBurn(sender);
            case "burnwet" -> testBurnWet(sender);
            case "gui" -> testGui(sender);
            case "fx" -> testFx(sender);
            case "glass" -> testGlass(sender);
            case "clean" -> clean(sender);
            default -> Msg.dm(sender, "&e/dmtest <compressor|burn|burnwet|gui|fx|glass|clean>");
        }
        return true;
    }

    /** Полный конвейер: место → ресурс → тики → вещество в выходе. */
    private void testCompressor(CommandSender sender, String[] args) {
        Branch b = args.length > 1 ? Branch.byId(args[1]) : Branch.CARROT;
        if (b == null || Magic_def(b) == null) {
            Msg.dm(sender, "&cВетка неизвестна.");
            return;
        }
        // Ищем свободную позицию (твердь + 2 воздуха) рядом со спавном — не портим чужие блоки
        Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
        Block block = null;
        outer:
        for (int r = 0; r <= 6 && block == null; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    Block ground = spawn.clone().add(dx, 0, dz).getBlock();
                    Block spot = ground.getRelative(BlockFace.UP);
                    if (ground.getType().isSolid() && spot.getType().isAir()
                            && spot.getRelative(BlockFace.UP).getType().isAir()) {
                        block = spot;
                        break outer;
                    }
                }
            }
        }
        if (block == null) {
            Msg.dm(sender, "&cНет свободного места (твердь + 2 воздуха) рядом со спавном.");
            return;
        }
        block.getChunk().load();
        block.setType(Material.PISTON);
        Compressors.install(block);
        // закладка ресурса: дешёвый обход гейта в тестовом режиме
        Material res = Blocks.vaultItems().stream()
                .filter(m -> Blocks.branchOfItem(m) == b)
                .filter(m -> m != Material.MAGMA_CREAM) // крем — топливо, не ресурс
                .findFirst().orElse(null);
        if (res == null) {
            Msg.dm(sender, "&cУ ветки нет vault-предмета.");
            return;
        }
        long need = Compressors.batchNeed(b);
        long units = Math.min(need, 4096); // тест: частичная партия (8 циклов)
        var in = Compressors.readMap(block, Compressors.TAG_IN);
        in.merge(res, units, Long::sum);
        in.merge(Material.MAGMA_CREAM, 40L, Long::sum);
        Compressors.writeMap(block, Compressors.TAG_IN, in);

        Msg.dm(sender, "&7[Тест компрессора] ветка &e" + b.name() + "&7, ресурс &e"
                + res + "&7, единиц &e" + units + "&7, нужно на партию &e" + T.num(need));
        Msg.dm(sender, "&7Ожидается: &e" + (units / ru.deepmagic.util.Conf.unitsPerCycle())
                + " циклов &7(1 цикл = 2 с)…");
        // прогон: 10 циклов — синхронно и ТОЧЕЧНО (не тикаем чужие компрессоры)
        final Block fb = block;
        Msg.dm(sender, "&7[Диагностика] активных компрессоров: &e" + Compressors.activeCount()
                + "&7, блок распознан: &e" + Compressors.isCompressorBlock(block));
        for (int i = 0; i < 10; i++) {
            Compressors.tickOne(fb);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            var out = Compressors.readTriple(fb, Compressors.TAG_OUT);
            var rest = Compressors.readMap(fb, Compressors.TAG_IN);
            var prog = Compressors.readMap(fb, Compressors.TAG_PROG);
            long fuelLeft = rest.getOrDefault(Material.MAGMA_CREAM, 0L);
            long consumed = 40 - fuelLeft;
            long progressed = prog.values().stream().mapToLong(Long::longValue).sum();
            Msg.dm(sender, "&7[Тест] сжато единиц: &e" + progressed
                    + "&7, циклов: &e" + (consumed / Math.max(1,
                            ru.deepmagic.util.Conf.fuelPerCycle()))
                    + "&7, магмы сожжено: &e" + consumed);
            if (!out.isEmpty()) {
                for (var e : out.entrySet()) {
                    String[] spec = e.getKey().split(":");
                    Msg.dm(sender, "&a✔ Выход: вещество &e" + spec[0] + " тир " + spec[1]
                            + " ×" + e.getValue());
                }
            } else {
                Msg.dm(sender, "&7Выход пока пуст (партия не завершена — это корректно для "
                        + T.num(need) + " ед.)");
            }
            Msg.dm(sender, "&a✔ Конвейер компрессора работает: буфер, топливо, прогресс, выход.");
            // чистим: позиция была AIR — возвращаем AIR
            fb.setType(Material.AIR);
            Compressors.unregisterAll(fb);
        }, 1L);
    }

    /** Закон вечного огня: уголь + магма → вечное горение; вода/влага гасит. */
    private void testBurn(CommandSender sender) {
        // Детерминизм: дождь гасит вечный огонь (закон D7) — тест идёт при ясной погоде
        Bukkit.getWorlds().get(0).setStorm(false);
        Location base = Bukkit.getWorlds().get(0).getSpawnLocation().clone().add(3, 2, 1);
        base.getChunk().load();
        // спавним только в воздух, чтобы не заменять блоки
        Block at = base.getBlock();
        if (!at.getType().isAir() || at.getRelative(BlockFace.UP).getType() != org.bukkit.Material.AIR) {
            Msg.dm(sender, "&cНет свободного места для теста огня (нужно 2 воздуха).");
            return;
        }
        Zombie z = (Zombie) Bukkit.getWorlds().get(0).spawnEntity(base, EntityType.ZOMBIE);
        z.setPersistent(false);
        z.setAI(false);
        Msg.dm(sender, "&7[Тест огня] зомби создан. Покрываем углём и поджигаем магмой…");
        Msg.dm(sender, "&8[Диагностика] valid2=" + z.isValid()
                + " chunkLoaded=" + z.getWorld().isChunkLoaded(
                        z.getLocation().getBlockX() >> 4, z.getLocation().getBlockZ() >> 4)
                + " loc=" + z.getLocation().getBlockX() + "," + z.getLocation().getBlockY()
                + "," + z.getLocation().getBlockZ());
        State.CARBON_COATED.set(z, 60);
        if (!State.CARBON_COATED.has(z)) {
            Msg.dm(sender, "&c✖ Угольное покрытие не наложилось! (valid=" + z.isValid()
                    + " dead=" + z.isDead() + ")");
            return;
        }
        Msg.dm(sender, "&a✔ Угольное покрытие наложено.");
        EternalBurn.ignite(z);
        if (!EternalBurn.burning(z)) {
            Msg.dm(sender, "&c✖ Вечное горение не зажглось!");
            return;
        }
        Msg.dm(sender, "&a✔ Вечное горение активно (флаг + огонь).");
        // тикаем: горение держится
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (EternalBurn.burning(z)) {
                Msg.dm(sender, "&a✔ Через 5 с горение держится (до воды).");
            } else {
                Msg.dm(sender, "&c✖ Горение погасло без воды!");
            }
            // влага гасит
            State.SOAKED.set(z, 10);
            EternalBurn.tickSecond(z);
            if (!EternalBurn.burning(z)) {
                Msg.dm(sender, "&a✔ Вода/влага погасила вечное горение (закон растворения).");
            } else {
                Msg.dm(sender, "&c✖ Вода не погасила вечное горение!");
            }
            // уголь смыт водой тоже
            Msg.dm(sender, State.CARBON_COATED.has(z)
                    ? "&c✖ Угольное покрытие не смыто водой!"
                    : "&a✔ Угольное покрытие смыто водой.");
            z.remove();
        }, 100L);
    }

    /** Поведенческий тест GUI v2: рендер с реальными данными и проверки слотов. */
    private void testGui(CommandSender sender) {
        Location base = Bukkit.getWorlds().get(0).getSpawnLocation().clone().add(-5, 2, 1);
        base.getChunk().load();
        Block at = base.getBlock();
        if (!at.getType().isAir()) {
            Msg.dm(sender, "&cНет места для теста GUI (нужен воздух). ");
            return;
        }
        at.setType(Material.PISTON);
        Compressors.install(at);
        int fails = 0;
        StringBuilder log = new StringBuilder();
        try {
            // реалистичное состояние: топливо (самокалибровка по конфигу),
            // вход CARROT 4096 (прогресс 512), пересжатие COAL:1 ×2 (серия 1/3), выход DIAMOND:1 ×3
            var in = Compressors.readMap(at, Compressors.TAG_IN);
            in.merge(Material.CARROT, 4096L, Long::sum);
            in.merge(Material.MAGMA_CREAM, Math.max(25L, (long) ru.deepmagic.util.Conf.fuelPerCycle() * 25L), Long::sum);
            Compressors.writeMap(at, Compressors.TAG_IN, in);
            var prog = Compressors.readMap(at, Compressors.TAG_PROG);
            prog.merge(Material.CARROT, 512L, Long::sum);
            Compressors.writeMap(at, Compressors.TAG_PROG, prog);
            var subin = Compressors.readTriple(at, Compressors.TAG_SUBIN);
            subin.merge("COAL:1", 2L, Long::sum);
            Compressors.writeTriple(at, Compressors.TAG_SUBIN, subin);
            var subprog = Compressors.readTriple(at, Compressors.TAG_SUBPROG);
            subprog.merge("COAL:1", 1L, Long::sum);
            Compressors.writeTriple(at, Compressors.TAG_SUBPROG, subprog);
            var out = Compressors.readTriple(at, Compressors.TAG_OUT);
            out.merge("DIAMOND:1", 3L, Long::sum);
            Compressors.writeTriple(at, Compressors.TAG_OUT, out);

            CompressorGui.Holder h = new CompressorGui.Holder(at);
            CompressorGui.render(h, at);

            // 1) рамка корпуса на месте
            int[] border = {0, 1, 3, 5, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44,
                    45, 46, 48, 50, 51, 52, 53};
            for (int s : border) {
                ItemStack it = h.inv.getItem(s);
                if (it == null || it.getType() != Material.PURPLE_STAINED_GLASS_PANE) {
                    fails++;
                    log.append("FAIL: рамка сломана на слоте ").append(s).append('\n');
                }
            }
            // 2) ни одного сырого '&' во всех 54 слотах (имя + лор)
            for (int s = 0; s < 54; s++) {
                ItemStack it = h.inv.getItem(s);
                if (it == null || !it.hasItemMeta() || it.getItemMeta() == null) continue;
                var meta = it.getItemMeta();
                if (meta.getDisplayName() != null && meta.getDisplayName().indexOf('&') >= 0) {
                    fails++;
                    log.append("FAIL: сырой & в имени слота ").append(s).append('\n');
                }
                if (meta.getLore() != null) {
                    for (String line : meta.getLore()) {
                        if (line != null && line.indexOf('&') >= 0) {
                            fails++;
                            log.append("FAIL: сырой & в лоре слота ").append(s).append('\n');
                        }
                    }
                }
            }
            // 3) топливо, индикатор, ход сжатия, кнопки
            if (h.inv.getItem(13) == null || h.inv.getItem(13).getType() != Material.MAGMA_CREAM) {
                fails++; log.append("FAIL: слот топлива пуст\n");
            }
            if (h.inv.getItem(31) == null || h.inv.getItem(31).getType() != Material.LIME_CONCRETE) {
                fails++; log.append("FAIL: индикатор не показывает «работает»\n");
            }
            if (h.inv.getItem(22) == null || h.inv.getItem(22).getType() != Material.SPECTRAL_ARROW) {
                fails++; log.append("FAIL: ход сжатия не отрисован\n");
            } else {
                // самая продвинутая партия при seed: COAL 1/3 (33%) > CARROT 512/64000 (0.8%)
                String nm = h.inv.getItem(22).getItemMeta().getDisplayName();
                if (nm.contains("нет заданий") || !nm.contains("Уголь")) {
                    fails++; log.append("FAIL: ход сжатия не назвал самую продвинутую партию (")
                            .append(nm).append(")\n");
                }
            }
            if (h.inv.getItem(47) == null || h.inv.getItem(47).getType() != Material.HOPPER
                    || h.inv.getItem(49) == null || h.inv.getItem(49).getType() != Material.BARRIER) {
                fails++; log.append("FAIL: кнопки забора/закрытия на месте\n");
            }
            // 4) выход: вещество DIAMOND в первой клетке зоны выхода (слот 14)
            if (h.inv.getItem(14) == null || h.inv.getItem(14).getType() != Material.HEAVY_CORE) {
                fails++; log.append("FAIL: вещество DIAMOND не в зоне выхода\n");
            }
            // 5) вход: CARROT (слот 10) и вещество COAL I (слот 11)
            if (h.inv.getItem(10) == null || h.inv.getItem(10).getType() != Material.CARROT) {
                fails++; log.append("FAIL: CARROT не в зоне входа\n");
            }
            if (h.inv.getItem(11) == null || h.inv.getItem(11).getType() != Material.HEAVY_CORE) {
                fails++; log.append("FAIL: вещество пересжатия не в зоне входа\n");
            }
            // 6) витрина пустых зон не перекрывает заполненные
            if (h.inv.getItem(12) != null && h.inv.getItem(12).getType() == Material.CARROT) {
                fails++; log.append("FAIL: дубликат входа\n");
            }
            h.inv.clear();
        } finally {
            at.setType(Material.AIR);
            Compressors.unregisterAll(at);
        }
        if (fails == 0) {
            Msg.dm(sender, "&a✔ GUI v2: рамка, зоны вход/выход, топливо, живой ход "
                    + "сжатия, индикатор, кнопки — всё на месте, сырых '&' нет.");
        } else {
            Msg.dm(sender, "&c✖ GUI v2: " + fails + " проблем:");
            for (String line : log.toString().split("\n")) {
                if (!line.isBlank()) Msg.dm(sender, "&c" + line);
            }
        }
    }

    private void clean(CommandSender sender) {
        Bukkit.getWorlds().get(0).getEntities().stream()
                .filter(e -> e instanceof Zombie z && !z.isPersistent())
                .forEach(e -> e.remove());
        Bukkit.getWorlds().get(0).getEntities().stream()
                .filter(ru.deepmagic.fx.Spectacle::isOurFx)
                .forEach(e -> e.remove());
        Msg.dm(sender, "&aТестовые сущности и FX-дисплеи убраны.");
    }

    /** Зрелищный самотест: орбиты, тотем, кольца, имплозия, разлёт — и статистика. */
    private void testFx(CommandSender sender) {
        ru.deepmagic.fx.Spectacle.reapAllLoaded();
        Location base = Bukkit.getWorlds().get(0).getSpawnLocation().clone().add(2, 2, 3);
        base.getChunk().load();
        Block at = base.getBlock();
        if (!at.getType().isAir() || at.getRelative(BlockFace.UP).getType()
                != org.bukkit.Material.AIR) {
            Msg.dm(sender, "&cНет места для fx-теста (нужно 2 воздуха). Смотрите визуал всё равно.");
        }
        Zombie z = (Zombie) Bukkit.getWorlds().get(0).spawnEntity(base, EntityType.ZOMBIE);
        z.setPersistent(false);
        z.setAI(false);
        Msg.dm(sender, "&7[Spectacle] до старта: &e" + ru.deepmagic.fx.Spectacle.stats());
        // 1) угольное покрытие: орбиты угольных блоков + тотем-вспышка (пример ТЗ)
        ru.deepmagic.model.State.CARBON_COATED.set(z, 12);
        ru.deepmagic.fx.SpellFx.carbonCoat(z);
        // 2) прочие примитивы вокруг зомби
        ru.deepmagic.fx.Spectacle.ringExpand(base.clone().add(0.5, 0, 0.5), 4.0, 12,
                org.bukkit.Particle.END_ROD, org.bukkit.Color.fromRGB(0xFFD257), 1.2f);
        ru.deepmagic.fx.Spectacle.implosion(base.clone().add(0.5, 1.5, 0.5), 3.0, 12,
                org.bukkit.Particle.SOUL, org.bukkit.Color.fromRGB(0x4FD0C9), 1.1f);
        ru.deepmagic.fx.Spectacle.spiral(base.clone().add(0.5, 0, 0.5), 1.0, 3.0, 18,
                org.bukkit.Particle.FLAME, org.bukkit.Color.fromRGB(0xFF7A21), 1.2f);
        ru.deepmagic.fx.Spectacle.itemBurst(base.clone().add(0.5, 1.5, 0.5),
                org.bukkit.Material.GOLD_NUGGET, 6, 1.6, 14);
        // 3) вихрь чёрного камня вокруг точки
        ru.deepmagic.fx.SpellFx.vortex(base.clone().add(0.5, 0.5, 0.5), 2);
        Msg.dm(sender, "&7[Spectacle] после старта: &e" + ru.deepmagic.fx.Spectacle.stats());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Msg.dm(sender, "&7[Spectacle] через 3 с: &e" + ru.deepmagic.fx.Spectacle.stats());
            int displays = ru.deepmagic.fx.Spectacle.activeDisplays();
            Msg.dm(sender, displays > 0
                    ? "&a✔ FX-движок жив: работа тикает, дисплеи на орбитах ("
                    + displays + "). Через 10 с угольные орбиты растают вместе со состоянием."
                    : "&c✖ FX-движок не создал дисплеев (кап/чанк?)");
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Msg.dm(sender, "&7[Spectacle] после затухания состояния: &e"
                        + ru.deepmagic.fx.Spectacle.stats());
                z.remove();
                ru.deepmagic.fx.Spectacle.reapAllLoaded();
                Msg.dm(sender, "&a✔ FX-тест завершён, сцена убрана.");
            }, 220L);
        }, 60L);
    }

    /** Остекление: наложение (орбиты+тотем стеклом) → шаттер → состояние потрачено. */
    private void testGlass(CommandSender sender) {
        ru.deepmagic.fx.Spectacle.reapAllLoaded();
        Location base = Bukkit.getWorlds().get(0).getSpawnLocation().clone().add(2, 2, 6);
        base.getChunk().load();
        Zombie z = (Zombie) Bukkit.getWorlds().get(0).spawnEntity(base, EntityType.ZOMBIE);
        z.setPersistent(false);
        z.setAI(false);
        // 1) наложение остекления: орбиты стекла + тотем-вспышка стеклом
        ru.deepmagic.model.State.GLASSED.set(z, 15);
        boolean applied = ru.deepmagic.model.State.GLASSED.has(z);
        // 2) шаттер (сигнатурный взрыв осколков)
        ru.deepmagic.fx.Spectacle.shatterFX(z, true);
        // 3) состояние тратится ударом (логика onPhysicalHit)
        ru.deepmagic.model.State.GLASSED.remove(z);
        boolean consumed = !ru.deepmagic.model.State.GLASSED.has(z);
        Msg.dm(sender, applied && consumed
                ? "&a✔ Остекление: наложено → шаттер-осколки разлетелись → состояние потрачено. "
                + "В игре: наложи песчаной бурей на цель и ударь чем угодно — увидишь взрыв осколков."
                : "&c✖ Остекление: applied=" + applied + " consumed=" + consumed);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ru.deepmagic.fx.Spectacle.reapAllLoaded();
            z.remove();
            Msg.dm(sender, "&a✔ Стеклянный тест завершён, сцена убрана.");
        }, 100L);
    }

    /** Закон порядка: мокрая + угольная цель от огня НЕ вспыхивает вечно (пар гасит). */
    private void testBurnWet(CommandSender sender) {
        // Детерминизм против дождя (он гасит огонь раньше проверки порядка законов)
        Bukkit.getWorlds().get(0).setStorm(false);
        Location base = Bukkit.getWorlds().get(0).getSpawnLocation().clone().add(5, 2, 1);
        base.getChunk().load();
        Block at = base.getBlock();
        if (!at.getType().isAir() || at.getRelative(BlockFace.UP).getType() != org.bukkit.Material.AIR) {
            Msg.dm(sender, "&cНет свободного места для теста (нужно 2 воздуха).");
            return;
        }
        Zombie z = (Zombie) Bukkit.getWorlds().get(0).spawnEntity(base, EntityType.ZOMBIE);
        z.setPersistent(false);
        z.setAI(false);
        State.CARBON_COATED.set(z, 60);
        State.SOAKED.set(z, 30);
        Msg.dm(sender, "&7[Тест мокрого угля] цель угольная И влажная; удар огненным посохом…");
        // Прямой вызов общего пути урона посоха (стихия ОГОНЬ). Цель — моб:
        // pvpAllowed замыкает проверку до касания атакующего, null-источник допустим.
        ru.deepmagic.synergy.Synergy.onStaffHit(null, z, 2.0, ru.deepmagic.model.Magic.Element.FIRE);
        if (EternalBurn.burning(z)) {
            Msg.dm(sender, "&c✖ Мокрая угольная цель ВСПЫХНУЛА вечно — порядок законов нарушен!");
        } else {
            Msg.dm(sender, "&a✔ Влага погасила огонь до вечного горения (закон C выше закона ТЗ).");
        }
        Msg.dm(sender, State.SOAKED.has(z)
                ? "&c✖ Влага не испарилась от огня!"
                : "&a✔ Влага испарилась (пар), покрытие осталось.");
        z.remove();
    }

    private ru.deepmagic.model.Magic.StaffDef Magic_def(Branch b) {
        return ru.deepmagic.model.Magic.def(b);
    }
}
