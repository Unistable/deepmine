package ru.deepevents;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.deepevents.core.ConfigManager;
import ru.deepevents.core.DeepCommand;
import ru.deepevents.core.EventManager;
import ru.deepevents.core.EventScheduler;
import ru.deepevents.core.GlobalListeners;
import ru.deepevents.core.Msg;
import ru.deepevents.core.ProtectionAdapter;
import ru.deepevents.core.RadiationZones;
import ru.deepevents.core.RestoreService;

/**
 * DeepEvents — кинематографичные случайные ивенты для Paper 1.21.x.
 * Кракен, Ядерный взрыв, Звездопад.
 */
public final class DeepEventsPlugin extends JavaPlugin {

    private ConfigManager config;
    private EventManager eventManager;
    private EventScheduler scheduler;
    private RadiationZones radiation;
    private RestoreService restoreService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = new ConfigManager(this);
        this.config.reload();

        ProtectionAdapter.init(this);
        this.restoreService = new RestoreService(this);
        this.radiation = new RadiationZones(this, config.radiationPeriodTicks());
        this.eventManager = new EventManager(this, config, radiation, restoreService);
        this.scheduler = new EventScheduler(this, config, eventManager);

        Bukkit.getPluginManager().registerEvents(new GlobalListeners(this, eventManager), this);

        PluginCommand cmd = getCommand("deepevents");
        if (cmd != null) {
            DeepCommand exec = new DeepCommand(this, config, eventManager, scheduler, restoreService, radiation);
            cmd.setExecutor(exec);
            cmd.setTabCompleter(exec);
        }

        scheduler.start();
        Msg.log("DeepEvents включён. Ивенты: Кракен, Ядерный взрыв, Звездопад.");
    }

    @Override
    public void onDisable() {
        if (scheduler != null) scheduler.stop();
        if (eventManager != null) eventManager.shutdownAll("Плагин выключается");
        if (radiation != null) radiation.clearTransient();
        Bukkit.getScheduler().cancelTasks(this);
        Msg.log("DeepEvents выключен корректно.");
    }

    public EventManager events() { return eventManager; }
    public RadiationZones radiation() { return radiation; }
    public RestoreService restores() { return restoreService; }
    public EventScheduler scheduler() { return scheduler; }
}
