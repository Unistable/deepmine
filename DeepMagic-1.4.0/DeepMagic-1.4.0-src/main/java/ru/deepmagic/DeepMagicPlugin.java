package ru.deepmagic;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.deepmagic.cmd.MageCmd;
import ru.deepmagic.compress.Compressors;
import ru.deepmagic.model.Magic;
import ru.deepmagic.staff.Mechanics;
import ru.deepmagic.synergy.Synergy;
import ru.deepmagic.util.Conf;

/**
 * DeepMagic — магический арсенал DeepProgression.
 * Требует DeepProgression 3.2.0 (depend). Все данные — из его реестров.
 */
public final class DeepMagicPlugin extends JavaPlugin {

    private static DeepMagicPlugin instance;

    public static DeepMagicPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        var dp = Bukkit.getPluginManager().getPlugin("DeepProgression");
        if (dp == null || !dp.isEnabled()) {
            getLogger().severe("DeepProgression не найден или выключен — DeepMagic отключается.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Conf.init(this);
        Synergy.load(this); // восстанавливаем зоны/якоря после рестарта
        ru.deepmagic.fx.Spectacle.init(this); // двигатель зрелищности
        if (!Magic.ready()) {
            getLogger().severe("Реестр DeepMagic не совпадает с реестром веток DeepProgression ("
                    + Magic.all().size() + " ≠ " + ru.deepprogression.model.Branch.values().length
                    + ") — отключение.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Bukkit.getPluginManager().registerEvents(new MagicListener(this), this);
        var cmd = getCommand("deepmagic");
        if (cmd != null) {
            MageCmd executor = new MageCmd(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }
        var test = getCommand("dmtest");
        if (test != null) {
            test.setExecutor(new ru.deepmagic.cmd.DmTestCmd(this));
        }

        // Тик компрессора
        Bukkit.getScheduler().runTaskTimer(this, Compressors::tick,
                Conf.cycleTicks(), Conf.cycleTicks());

        // Секундный тик синергий (состояния, вечный огонь, пыль, дождь)
        Bukkit.getScheduler().runTaskTimer(this, Synergy::tickSecondAll, 20L, 20L);

        // Секундный тик временных блоков (корневая сеть и т.п.)
        Bukkit.getScheduler().runTaskTimer(this, Mechanics::tickTempBlocks, 20L, 20L);

        // Очистка кулдаунов раз в минуту
        Bukkit.getScheduler().runTaskTimer(this, ru.deepmagic.staff.Cooldowns::trim, 1200L, 1200L);

        getLogger().info("Включён: веществ " + Magic.all().size()
                + " (×3 тира), посохов " + Magic.all().size()
                + ", механик уникальных " + Magic.all().size()
                + ", компрессор: цикл " + ru.deepmagic.util.T.sec(Conf.cycleTicks())
                + " × " + Conf.unitsPerCycle() + " ед.");
    }

    @Override
    public void onDisable() {
        Synergy.save(this); // зоны/якоря переживают рестарт
        Synergy.clearAll();
        ru.deepmagic.fx.Spectacle.shutdown(); // убрать все FX-дисплеи
        getLogger().info("Выключен. Состояния и зоны сохранены и очищены.");
    }
}
