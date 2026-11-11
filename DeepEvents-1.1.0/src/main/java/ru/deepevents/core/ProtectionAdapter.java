package ru.deepevents.core;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/**
 * Адаптер защиты: если стоит WorldGuard — ивенты не трогают регионы.
 * Полностью рефлексивный (нет compile-зависимости), поведение то же:
 * Union-регионы учитываются, ошибки API молча деградируют в "не защищено".
 * Towny не хукается (нет зависимости): защита достигается дистанцией
 * до игроков/спавна и командой /deepevents undo.
 */
public final class ProtectionAdapter {

    private static boolean wgEnabled = false;
    private static Object regionQuery; // com.sk89q.worldguard.protection.regions.RegionQuery
    private static Method adaptMethod; // BukkitAdapter.adapt(Location)
    private static Method queryMethod; // RegionQuery.getApplicableRegions(weLoc)

    private ProtectionAdapter() {}

    public static void init(JavaPlugin plugin) {
        Plugin wg = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        if (wg == null || !wg.isEnabled()) {
            Msg.log("WorldGuard не найден — проверка регионов отключена.");
            return;
        }
        try {
            Class<?> guardCls = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object guard = guardCls.getMethod("getInstance").invoke(null);
            Object platform = guard.getClass().getMethod("getPlatform").invoke(guard);
            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            regionQuery = container.getClass().getMethod("createQuery").invoke(container);
            Class<?> bukkitAdapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            adaptMethod = bukkitAdapter.getMethod("adapt", Location.class);
            queryMethod = regionQuery.getClass().getMethod("getApplicableRegions",
                    Class.forName("com.sk89q.worldedit.util.Location"));
            wgEnabled = regionQuery != null && adaptMethod != null && queryMethod != null;
            Msg.log("WorldGuard обнаружен — ивенты уважают регионы.");
        } catch (Throwable t) {
            Msg.warn("WorldGuard есть, но API недоступно: " + t.getMessage());
            wgEnabled = false;
            regionQuery = null;
        }
    }

    /** true, если локация внутри защищённого региона WG. */
    public static boolean isProtected(Location loc) {
        if (!wgEnabled || regionQuery == null || loc == null) return false;
        try {
            Object weLoc = adaptMethod.invoke(null, loc);
            Object set = queryMethod.invoke(regionQuery, weLoc);
            return ((Integer) set.getClass().getMethod("size").invoke(set)) > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isWorldGuardHooked() { return wgEnabled; }
}
