package ru.deepevents.core;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.deepevents.DeepEventsPlugin;
import ru.deepevents.events.KrakenEvent;
import ru.deepevents.events.NukeEvent;
import ru.deepevents.events.StarfallEvent;

import java.util.Map;
import java.util.Random;

/** Управление активным ивентом и выбор случайного по весам. */
public final class EventManager {

    public enum EventType { KRAKEN, NUKE, STARFALL }

    private final DeepEventsPlugin plugin;
    private final ConfigManager config;
    private final RadiationZones radiation;
    private final RestoreService restores;
    private final Random rnd = new Random();
    private AbstractEvent active;
    private EventType lastType;

    public EventManager(DeepEventsPlugin plugin, ConfigManager config,
                        RadiationZones radiation, RestoreService restores) {
        this.plugin = plugin;
        this.config = config;
        this.radiation = radiation;
        this.restores = restores;
    }

    public AbstractEvent active() { return active; }

    public boolean isActive() { return active != null; }

    public EventType pickWeighted() {
        Map<String, Double> weights = config.schedulerWeights();
        double total = 0;
        double wk = weights.getOrDefault("KRAKEN", 1.0);
        double wn = weights.getOrDefault("NUKE", 1.0);
        double ws = weights.getOrDefault("STARFALL", 1.0);
        if (lastType == EventType.KRAKEN) wk = 0;
        if (lastType == EventType.NUKE) wn = 0;
        if (lastType == EventType.STARFALL) ws = 0;
        total = wk + wn + ws;
        if (total <= 0) total = 1;
        double roll = rnd.nextDouble() * total;
        if ((roll -= wk) < 0) return EventType.KRAKEN;
        if ((roll -= wn) < 0) return EventType.NUKE;
        return EventType.STARFALL;
    }

    /** Запуск случайного ивента (не совпадающего с предыдущим). */
    public void startRandom(CommandSender feedback) {
        start(pickWeighted(), feedback);
    }

    public void start(EventType type, CommandSender feedback) {
        if (active != null) {
            if (feedback != null) feedback.sendMessage(Msg.mm("<red>Ивент уже идёт: " + active.displayName() + ". Остановите его: /deepevents stop</red>"));
            return;
        }
        AbstractEvent event = switch (type) {
            case KRAKEN -> new KrakenEvent(plugin, config);
            case NUKE -> new NukeEvent(plugin, config, restores, radiation);
            case STARFALL -> new StarfallEvent(plugin, config);
        };
        if (!enabled(type)) {
            if (feedback != null) feedback.sendMessage(Msg.mm("<red>Ивент отключён в конфиге.</red>"));
            return;
        }
        lastType = type;
        active = event;
        Msg.log("Запуск ивента: " + event.displayName());
        event.launch();
    }

    private boolean enabled(EventType t) {
        return switch (t) {
            case KRAKEN -> config.krakenEnabled();
            case NUKE -> config.nukeEnabled();
            case STARFALL -> config.starfallEnabled();
        };
    }

    public void stopActive(CommandSender feedback) {
        if (active == null) {
            if (feedback != null) feedback.sendMessage(Msg.mm("<yellow>Сейчас ивентов нет.</yellow>"));
            return;
        }
        AbstractEvent e = active;
        active = null;
        e.stop("остановлен вручную");
        Msg.broadcast("Ивент <b>" + e.displayName() + "</b> остановлен администратором.");
    }

    /** Остановка при выключении плагина. */
    public void shutdownAll(String reason) {
        if (active != null) {
            AbstractEvent e = active;
            active = null;
            e.stop(reason);
        }
    }

    /** Снятие активного ивента изнутри (по завершению). */
    public void clearActive(AbstractEvent event) {
        if (active == event) active = null;
    }

    /** Активный Кракен (для слушателей). */
    public KrakenEvent activeKraken() {
        return active instanceof KrakenEvent k ? k : null;
    }

    public StarfallEvent activeStarfall() {
        return active instanceof StarfallEvent s ? s : null;
    }

    public RadiationZones radiation() { return radiation; }

    public RestoreService restores() { return restores; }

    /** Строка статуса для команды. */
    public String statusLine() {
        return active == null ? "<gray>сейчас тихо…</gray>" : "<green>" + active.displayName() + "</green>";
    }
}
