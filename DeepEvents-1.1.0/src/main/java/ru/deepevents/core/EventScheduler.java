package ru.deepevents.core;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import ru.deepevents.DeepEventsPlugin;

import java.util.Random;

/** Случайный планировщик ивентов: интервал 45-90 мин, предупреждение за 60 сек. */
public final class EventScheduler {

    private final DeepEventsPlugin plugin;
    private final ConfigManager config;
    private final EventManager manager;
    private final Random rnd = new Random();

    private BukkitTask warnTask;
    private BukkitTask startTask;
    private long nextAtMillis = 0;

    public EventScheduler(DeepEventsPlugin plugin, ConfigManager config, EventManager manager) {
        this.plugin = plugin;
        this.config = config;
        this.manager = manager;
    }

    public void start() {
        stop();
        if (!config.schedulerEnabled()) {
            Msg.log("Автопланировщик отключён в конфиге.");
            return;
        }
        scheduleNext();
    }

    private void scheduleNext() {
        int min = config.schedulerMinMinutes();
        int max = config.schedulerMaxMinutes();
        int minutes = min + rnd.nextInt(Math.max(1, max - min + 1));
        long delayTicks = minutes * 60L * 20L;
        long warnTicks = config.schedulerWarnSeconds() * 20L;
        nextAtMillis = System.currentTimeMillis() + minutes * 60_000L;

        if (warnTicks > 0 && warnTicks < delayTicks) {
            warnTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (manager.isActive()) return;
                EventManager.EventType next = manager.pickWeighted();
                Msg.broadcast("<dark_aqua>Атмосфера гудит… через <b>" + config.schedulerWarnSeconds()
                        + " сек.</b> произойдёт нечто" + hint(next) + "</dark_aqua>");
                Msg.soundAll("block.note_block.bell", 0.7f, 0.6f);
            }, delayTicks - warnTicks);
        }

        startTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (manager.isActive()) return;
                if (Bukkit.getOnlinePlayers().size() < config.requireOnlinePlayers()) {
                    Msg.log("Ивент отложен: мало игроков онлайн (" + Bukkit.getOnlinePlayers().size()
                            + " < " + config.requireOnlinePlayers() + ").");
                    return;
                }
                if (!Bukkit.getOnlinePlayers().isEmpty()) {
                    manager.startRandom(null);
                    EventScheduler.this.stop();
                }
            }
        }, delayTicks, 200L);

        Msg.log("Следующий ивент через " + minutes + " мин.");
    }

    private String hint(EventManager.EventType t) {
        return switch (t) {
            case KRAKEN -> "…вода начнёт бурлить";
            case NUKE -> "…воздух задрожит";
            case STARFALL -> "…небо зажгётся звёздами";
        };
    }

    public void stop() {
        if (warnTask != null) warnTask.cancel();
        if (startTask != null) startTask.cancel();
        warnTask = null;
        startTask = null;
    }

    /** Минут до следующего ивента (-1 = не запланирован). */
    public long minutesToNext() {
        if (nextAtMillis == 0) return -1;
        return Math.max(0, (nextAtMillis - System.currentTimeMillis()) / 60_000L);
    }
}
