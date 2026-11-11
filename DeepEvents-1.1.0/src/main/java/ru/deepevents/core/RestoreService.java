package ru.deepevents.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Сервис отката мира: снапшот изменённых блоков (память + диск)
 * и восстановление порциями по тикам.
 */
public final class RestoreService {

    /** Один изменённый блок: координата + строковое представление старого BlockData. */
    public record Change(long packed, String data) {}

    /** Парсинг упакованной координаты (vanilla 26/12/26 бит). */
    public record BlockPos(int x, int y, int z) {}

    private final JavaPlugin plugin;
    private RestoreSession lastSession;

    public RestoreService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public RestoreSession beginSession(World world, String tag) {
        RestoreSession s = new RestoreSession(world, tag);
        lastSession = s;
        return s;
    }

    public RestoreSession lastSession() { return lastSession; }

    public boolean undoLast() {
        if (lastSession == null) return false;
        return lastSession.restore();
    }

    public final class RestoreSession {

        private final World world;
        private final String tag;
        private final List<Change> changes = new ArrayList<>();
        private boolean restored = false;
        private org.bukkit.Location origin;

        private RestoreSession(World world, String tag) {
            this.world = world;
            this.tag = tag;
        }

        public World world() { return world; }
        public String tag() { return tag; }
        public int size() { return changes.size(); }
        public void setOrigin(org.bukkit.Location l) { this.origin = l == null ? null : l.clone(); }
        public org.bukkit.Location origin() { return origin; }

        public void capture(Block b) {
            if (restored) return;
            changes.add(new Change(pack(b.getX(), b.getY(), b.getZ()), b.getBlockData().getAsString()));
        }

        /** Восстановление порциями по 4000 блоков за тик (синхронно, если плагин выключается). */
        public boolean restore() {
            if (restored || changes.isEmpty()) return false;
            restored = true;
            List<Change> snapshot = new ArrayList<>(changes);
            Msg.log("Откат '" + tag + "': восстановление " + snapshot.size() + " блоков...");
            if (!plugin.isEnabled()) {
                // при выключении плагина задачи не планируются — откат синхронно
                for (Change c : snapshot) {
                    BlockPos p = unpack(c.packed());
                    try {
                        world.getBlockAt(p.x(), p.y(), p.z()).setBlockData(Bukkit.createBlockData(c.data()), false);
                    } catch (IllegalArgumentException ignored) {}
                }
                Msg.log("Откат '" + tag + "' завершён (синхронно).");
                deleteDisk();
                return true;
            }
            new Object() {
                int index = 0;
                void step() {
                    long start = System.currentTimeMillis();
                    // бюджет 12мс/тик (было 40 — гарантированные просадки TPS)
                    while (index < snapshot.size() && System.currentTimeMillis() - start < 12) {
                        Change c = snapshot.get(index++);
                        BlockPos p = unpack(c.packed());
                        Block block = world.getBlockAt(p.x(), p.y(), p.z());
                        try {
                            block.setBlockData(Bukkit.createBlockData(c.data()), false);
                        } catch (IllegalArgumentException ex) {
                            // данные устарели — пропускаем блок
                        }
                    }
                    if (index < snapshot.size()) {
                        Bukkit.getScheduler().runTask(plugin, this::step);
                    } else {
                        Msg.log("Откат '" + tag + "' завершён.");
                        deleteDisk();
                    }
                }
            }.step();
            return true;
        }

        /** Сохранение сессии на диск (переживает рестарт сервера). */
        public void saveToDisk() {
            try {
                File dir = new File(plugin.getDataFolder(), "undo");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, tag + ".gz");
                try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                        new GZIPOutputStream(new FileOutputStream(f)), StandardCharsets.UTF_8))) {
                    String originStr = origin == null ? "-" : (origin.getWorld().getName() + ","
                            + origin.getBlockX() + "," + origin.getBlockY() + "," + origin.getBlockZ());
                    w.write("# world=" + world.getName() + " blocks=" + changes.size() + " origin=" + originStr);
                    w.newLine();
                    for (Change c : changes) {
                        w.write(c.packed() + "\t" + c.data());
                        w.newLine();
                    }
                }
                Msg.log("Снапшот '" + tag + "' сохранён: " + f.getPath());
            } catch (Exception e) {
                Msg.error("Не удалось сохранить снапшот '" + tag + "'", e);
            }
        }

        private void deleteDisk() {
            File f = new File(plugin.getDataFolder(), "undo/" + tag + ".gz");
            if (f.exists()) f.delete();
        }
    }

    /** Загрузка последнего снапшота с диска (после рестарта) и восстановление. */
    public boolean restoreFromDisk(String tag) {
        // защита от двойного восстановления одной и той же сессии
        if (lastSession != null && lastSession.tag().equals(tag)) {
            return lastSession.restore();
        }
        File f = new File(plugin.getDataFolder(), "undo/" + tag + ".gz");
        if (!f.exists()) return false;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(new FileInputStream(f)), StandardCharsets.UTF_8))) {
            String header = r.readLine();
            String worldName = header.contains("world=") ? header.split("world=")[1].split(" ")[0] : "world";
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                Msg.warn("Откат '" + tag + "': мир '" + worldName + "' не загружен.");
                return false;
            }
            RestoreSession s = beginSession(world, tag);
            if (header.contains("origin=") && !header.endsWith("origin=-")) {
                try {
                    String[] o = header.split("origin=")[1].trim().split(",");
                    s.setOrigin(new Location(world, Integer.parseInt(o[1]) + 0.5,
                            Integer.parseInt(o[2]) + 0.5, Integer.parseInt(o[3]) + 0.5));
                } catch (Exception ignored) {}
            }
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#")) continue;
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                try {
                    s.changes.add(new Change(Long.parseLong(line.substring(0, tab)), line.substring(tab + 1)));
                } catch (NumberFormatException ignored) {}
            }
            return s.restore();
        } catch (Exception e) {
            Msg.error("Не удалось загрузить снапшот '" + tag + "'", e);
            return false;
        }
    }

    // vanilla-схема упаковки координат: x:26 | z:26 | y:12
    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) (y & 0xFFF);
    }

    public static BlockPos unpack(long v) {
        int x = (int) (v >> 38);
        int z = (int) (v << 26 >> 38);
        int y = (int) (v << 52 >> 52);
        // знак для x/z через дополнение до 26 бит
        if ((x & 0x2000000) != 0) x |= 0xFC000000;
        if ((z & 0x2000000) != 0) z |= 0xFC000000;
        if ((y & 0x800) != 0) y |= 0xFFFFF000;
        return new BlockPos(x, y, z);
    }
}
