package ru.deepprogression;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.deepprogression.command.Cmd;
import ru.deepprogression.gui.Gui;
import ru.deepprogression.listener.Listeners;
import ru.deepprogression.model.Blocks;
import ru.deepprogression.model.Branch;
import ru.deepprogression.model.Section;
import ru.deepprogression.progress.ProfileStore;
import ru.deepprogression.recipe.Recipes;
import ru.deepprogression.tools.Items;
import ru.deepprogression.tools.Probe;
import ru.deepprogression.tools.Tools;
import ru.deepprogression.util.Cfg;
import ru.deepprogression.util.Keys;
import ru.deepprogression.util.Msg;

/** DeepProgression — глубокая прогрессия по всем блокам игры. */
public final class DeepProgressionPlugin extends JavaPlugin {

    private static DeepProgressionPlugin instance;

    public static DeepProgressionPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        Keys.init(this);
        Msg.init(this);
        Cfg.init(this);
        Blocks.init();
        if (!Blocks.missingMaterials().isEmpty()) {
            getLogger().warning("Материалы, отсутствующие в API ("
                    + Blocks.missingMaterials().size() + "): "
                    + String.join(", ", Blocks.missingMaterials()));
        }
        Tools.init();
        Items.init(this);
        Probe.init(this);
        ru.deepprogression.progress.Quests.init(this);
        ru.deepprogression.perks.Perks.init(this);
        ru.deepprogression.economy.Economy.init(this);
        Gui.init();
        ProfileStore.init(this);
        Recipes.registerAll(this);

        Bukkit.getPluginManager().registerEvents(new Listeners(), this);
        PluginCommand cmd = getCommand("deepprogression");
        if (cmd != null) {
            Cmd executor = new Cmd(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        // Периодические эффекты пассивных перков (каждые 2с)
        ru.deepprogression.perks.Perks.startTask(this);

        // Периодическое автосохранение профилей (каждые 5 минут, вне главного потока)
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, ProfileStore::periodicSave,
                6000L, 6000L);

        // Автосохранение экономики + смена недели (каждые 5 минут, вне главного потока)
        Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                () -> {
                    ru.deepprogression.economy.Economy.tickWeek();
                    ru.deepprogression.economy.Economy.saveAsync();
                },
                6000L, 6000L);

        getLogger().info("Включён: секций " + Section.values().length
                + ", веток " + Branch.values().length
                + ", инструментов " + Tools.all().size()
                + ", крафтов " + Recipes.count()
                + ", перков " + ru.deepprogression.perks.Perks.count()
                + ", материалов в экономике " + ru.deepprogression.economy.Economy.trackedCount()
                + ", профилей в кэше " + ProfileStore.cachedCount() + ".");
    }

    @Override
    public void onDisable() {
        ProfileStore.saveAllSync();
        ru.deepprogression.economy.Economy.saveSync();
        ru.deepprogression.storage.Vaults.clearAll();
        ru.deepprogression.model.Placed.clearAll();
        getLogger().info("Выключен: профили и экономика сохранены.");
    }
}
