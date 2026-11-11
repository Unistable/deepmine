package ru.deepevents.core;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/** База ивента: задачи, заспавненные сущности, защита от двойного стопа. */
public abstract class AbstractEvent {

    protected final JavaPlugin plugin;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final List<Entity> spawned = new ArrayList<>();
    private volatile boolean stopped = false;

    protected AbstractEvent(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Имя ивента для сообщений. */
    public abstract String displayName();

    /** Запуск (может использовать асинхронный поиск локаций). */
    public abstract void launch();

    /** Очистка ивента (блоки, сущности уже сняты базой). */
    protected abstract void cleanup();

    protected void every(long delayTicks, long periodTicks, Runnable r) {
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, guard(r), delayTicks, periodTicks));
    }

    protected void once(long delayTicks, Runnable r) {
        tasks.add(Bukkit.getScheduler().runTaskLater(plugin, guard(r), delayTicks));
    }

    protected void async(Runnable r) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, guard(r));
    }

    private Runnable guard(Runnable r) {
        return () -> {
            if (stopped) return;
            try {
                r.run();
            } catch (Throwable t) {
                Msg.error("Ошибка в ивенте '" + displayName() + "'", t);
                // анти-зомби: внутренний крэш не должен оставлять ивент активным
                stop("внутренняя ошибка: " + t.getClass().getSimpleName());
                if (plugin instanceof ru.deepevents.DeepEventsPlugin dep) {
                    dep.events().clearActive(this);
                }
            }
        };
    }

    protected void track(Entity e) {
        if (e != null && e.isValid()) spawned.add(e);
    }

    public boolean isStopped() { return stopped; }

    /** Полная остановка (идемпотентна). */
    public final void stop(String reason) {
        if (stopped) return;
        stopped = true;
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
        for (Entity e : spawned) {
            if (e.isValid()) e.remove();
        }
        spawned.clear();
        try {
            cleanup();
        } catch (Throwable t) {
            Msg.error("Ошибка очистки ивента '" + displayName() + "'", t);
        }
        Msg.log("Ивент '" + displayName() + "' остановлен: " + reason);
    }
}
