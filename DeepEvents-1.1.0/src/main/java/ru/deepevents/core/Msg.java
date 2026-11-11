package ru.deepevents.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;

/** Утилиты сообщений: MiniMessage, титулы, экшнбары, звуки. */
public final class Msg {

    public static final String PREFIX =
            "<gradient:#5eead4:#0ea5e9><bold>DEEP</bold></gradient><gray> »</gray> ";

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Msg() {}

    public static Component mm(String s) {
        return MM.deserialize(s);
    }

    public static Component prefixed(String s) {
        return MM.deserialize(PREFIX + s);
    }

    public static void broadcast(String miniMessage) {
        Bukkit.broadcast(prefixed(miniMessage));
    }

    public static void broadcastRaw(String miniMessage) {
        Bukkit.broadcast(mm(miniMessage));
    }

    public static void titleAll(String title, String sub, int inMs, int stayMs, int outMs) {
        for (Player p : Bukkit.getOnlinePlayers()) title(p, title, sub, inMs, stayMs, outMs);
    }

    public static void title(Player p, String title, String sub, int inMs, int stayMs, int outMs) {
        p.showTitle(Title.title(
                mm(title),
                mm(sub == null ? "" : sub),
                Title.Times.times(Duration.ofMillis(inMs), Duration.ofMillis(stayMs), Duration.ofMillis(outMs))));
    }

    public static void actionbarAll(String miniMessage) {
        Component c = mm(miniMessage);
        for (Player p : Bukkit.getOnlinePlayers()) p.sendActionBar(c);
    }

    public static void actionbar(Player p, String miniMessage) {
        p.sendActionBar(mm(miniMessage));
    }

    public static void sound(Location loc, String key, float volume, float pitch) {
        loc.getWorld().playSound(loc, key, SoundCategory.MASTER, volume, pitch);
    }

    public static void soundAll(String key, float volume, float pitch) {
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), key, SoundCategory.MASTER, volume, pitch);
    }

    /** Звук только игрокам в радиусе (антиспам для дальних). */
    public static void soundNear(Location loc, double radius, String key, float volume, float pitch) {
        if (loc.getWorld() == null) return;
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= radius * radius) {
                p.playSound(p.getLocation(), key, SoundCategory.MASTER, volume, pitch);
            }
        }
    }

    /** Сообщение только игрокам в радиусе (антиспам чата). */
    public static void broadcastNear(Location loc, double radius, String miniMessage) {
        if (loc.getWorld() == null) return;
        Component c = prefixed(miniMessage);
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= radius * radius) {
                p.sendMessage(c);
            }
        }
    }

    public static void log(String s) {
        Bukkit.getLogger().info("[DeepEvents] " + s);
    }

    public static void warn(String s) {
        Bukkit.getLogger().warning("[DeepEvents] " + s);
    }

    public static void error(String s, Throwable t) {
        Bukkit.getLogger().severe("[DeepEvents] " + s);
        if (t != null) t.printStackTrace();
    }
}
