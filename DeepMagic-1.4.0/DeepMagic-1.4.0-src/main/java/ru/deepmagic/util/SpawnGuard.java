package ru.deepmagic.util;

/**
 * Мост к DeepSpawn (softdepend): глушение посохов в святилище спавна.
 * Проверка каждый раз (дешёвый lookup): DeepSpawn может включиться позже.
 */
public final class SpawnGuard {
    private SpawnGuard() {}

    /** true — посохи в этой точке глушатся спавн-зоной DeepSpawn. */
    public static boolean staffBlocked(org.bukkit.Location loc) {
        try {
            if (!org.bukkit.Bukkit.getPluginManager().isPluginEnabled("DeepSpawn")) return false;
        } catch (Throwable t) {
            return false;
        }
        return ru.deepspawn.api.SpawnApi.isProtected(loc);
    }
}
