package ru.deepevents.core;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import ru.deepevents.DeepEventsPlugin;

/** Маршрутизация событий боя/взаимодействия в активные ивенты. */
public final class GlobalListeners implements Listener {

    private final DeepEventsPlugin plugin;
    private final EventManager manager;

    public GlobalListeners(DeepEventsPlugin plugin, EventManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        var kraken = manager.activeKraken();
        if (kraken != null) kraken.handleDamage(e);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        var kraken = manager.activeKraken();
        if (kraken != null) kraken.handleDeath(e);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        var starfall = manager.activeStarfall();
        if (starfall != null) starfall.tryPersonalWish(e);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        var starfall = manager.activeStarfall();
        if (starfall != null) starfall.trySharedWish(e);
    }

    /** Атакующий-игрок (учёт урона через снаряды). */
    public static Player shooter(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }
}
