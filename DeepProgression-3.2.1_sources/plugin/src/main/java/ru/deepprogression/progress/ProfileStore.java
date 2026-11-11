package ru.deepprogression.progress;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.deepprogression.DeepProgressionPlugin;
import ru.deepprogression.model.Branch;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Хранилище профилей: кэш в памяти + YAML-файлы players/<uuid>.yml. */
public final class ProfileStore {

    private static final Map<UUID, PlayerProfile> CACHE = new ConcurrentHashMap<>();
    private static File dir;
    private static DeepProgressionPlugin plugin;

    private ProfileStore() {}

    public static void init(DeepProgressionPlugin pl) {
        plugin = pl;
        dir = new File(pl.getDataFolder(), "players");
        if (!dir.exists()) dir.mkdirs();
        migrateFromDeepMining(pl);
    }

    /** Одноразовая миграция профилей из плагина DeepMining (v2.x). */
    private static void migrateFromDeepMining(DeepProgressionPlugin pl) {
        File[] existing = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (existing != null && existing.length > 0) return; // уже есть данные
        File oldDir = new File(pl.getDataFolder().getParentFile(), "DeepMining/players");
        File[] old = oldDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (old == null || old.length == 0) return;
        int copied = 0;
        for (File f : old) {
            try {
                java.nio.file.Files.copy(f.toPath(),
                        new File(dir, f.getName()).toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                copied++;
            } catch (Exception ex) {
                pl.getLogger().warning("Миграция " + f.getName() + ": " + ex.getMessage());
            }
        }
        pl.getLogger().info("Миграция из DeepMining: перенесено профилей — " + copied + ".");
    }

    public static File dir() {
        return dir;
    }

    /** Синхронная загрузка (вызывать из async или при отсутствии в кэше). */
    public static PlayerProfile load(UUID uuid) {
        PlayerProfile inCache = CACHE.get(uuid);
        if (inCache != null) return inCache;
        File f = new File(dir, uuid + ".yml");
        PlayerProfile p;
        if (f.exists()) {
            try {
                p = PlayerProfile.deserialize(YamlConfiguration.loadConfiguration(f));
            } catch (Exception ex) {
                plugin.getLogger().warning("Повреждённый профиль " + uuid + ": " + ex.getMessage());
                p = new PlayerProfile();
            }
        } else {
            p = new PlayerProfile();
        }
        CACHE.put(uuid, p);
        return p;
    }

    /** Основной поток: получить из кэша (или загрузить). */
    public static PlayerProfile get(UUID uuid) {
        return load(uuid);
    }

    public static void saveAsync(UUID uuid) {
        PlayerProfile snapshot = load(uuid).snapshot();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(uuid, snapshot));
    }

    public static void saveSync(UUID uuid) {
        write(uuid, load(uuid).snapshot());
    }

    private static void write(UUID uuid, PlayerProfile p) {
        try {
            File f = new File(dir, uuid + ".yml");
            YamlConfiguration y = new YamlConfiguration();
            p.serialize(y);
            y.save(f);
        } catch (Exception ex) {
            plugin.getLogger().warning("Не удалось сохранить профиль " + uuid + ": " + ex.getMessage());
        }
    }

    public static void evict(UUID uuid) {
        CACHE.remove(uuid);
    }

    public static void saveAllSync() {
        for (UUID u : List.copyOf(CACHE.keySet())) write(u, load(u).snapshot());
    }

    /** Периодическое сохранение всех профилей (вызов — уже вне главного потока). */
    public static void periodicSave() {
        for (UUID u : List.copyOf(CACHE.keySet())) {
            PlayerProfile p = CACHE.get(u);
            if (p != null) write(u, p.snapshot());
        }
    }

    public static int cachedCount() {
        return CACHE.size();
    }

    /** Топ по очкам славы (асинхронно; callback — на главном потоке). */
    public static void topAsync(int limit, java.util.function.BiConsumer<List<String>, Boolean> cb) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Object[]> rows = new ArrayList<>(); // [name, score, online]
            File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
            if (files != null) {
                for (File f : files) {
                    try {
                        PlayerProfile p = PlayerProfile.deserialize(
                                YamlConfiguration.loadConfiguration(f));
                        String name = p.getLastName();
                        if (name.isEmpty()) name = f.getName().replace(".yml", "");
                        rows.add(new Object[]{name, p.score(),
                                onlineName(name)});
                    } catch (Exception ignored) {
                    }
                }
            }
            rows.sort((a, b) -> Double.compare((Double) b[1], (Double) a[1]));
            List<String> lines = new ArrayList<>();
            int i = 1;
            for (Object[] r : rows) {
                if (i > limit) break;
                lines.add(((String) r[0]) + ";" + ((Double) r[1]).longValue());
                i++;
            }
            boolean any = !rows.isEmpty();
            Bukkit.getScheduler().runTask(plugin, () -> cb.accept(lines, any));
        });
    }

    private static boolean onlineName(String n) {
        return Bukkit.getPlayerExact(n) != null;
    }

    /** Топ по конкретной ветке (асинхронно; callback — на главном потоке). */
    public static void topBranchAsync(Branch branch, int limit,
                                      java.util.function.BiConsumer<List<String>, Boolean> cb) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Object[]> rows = new ArrayList<>(); // [name, count]
            File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
            if (files != null) {
                for (File f : files) {
                    try {
                        PlayerProfile p = PlayerProfile.deserialize(
                                YamlConfiguration.loadConfiguration(f));
                        String name = p.getLastName();
                        if (name.isEmpty()) name = f.getName().replace(".yml", "");
                        long v = p.getBlocks(branch);
                        if (v > 0) rows.add(new Object[]{name, v});
                    } catch (Exception ignored) {
                    }
                }
            }
            rows.sort((a, b) -> Long.compare((Long) b[1], (Long) a[1]));
            List<String> lines = new ArrayList<>();
            int i = 1;
            for (Object[] r : rows) {
                if (i > limit) break;
                lines.add(((String) r[0]) + ";" + ((Long) r[1]).longValue());
                i++;
            }
            boolean any = !rows.isEmpty();
            Bukkit.getScheduler().runTask(plugin, () -> cb.accept(lines, any));
        });
    }
}
