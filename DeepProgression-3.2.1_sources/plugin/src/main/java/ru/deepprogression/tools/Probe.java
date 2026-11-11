package ru.deepprogression.tools;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.deepprogression.DeepProgressionPlugin;
import ru.deepprogression.model.Blocks;
import ru.deepprogression.model.Branch;
import ru.deepprogression.model.Section;
import ru.deepprogression.util.Cfg;
import ru.deepprogression.util.Keys;
import ru.deepprogression.util.Msg;
import ru.deepprogression.util.Text;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Зонд георазведки: скан чанка на руды (off-main), отчёт в чат. */
public final class Probe {

    private static final Map<UUID, Long> COOLDOWN = new java.util.HashMap<>();
    private static DeepProgressionPlugin plugin;

    private Probe() {}

    public static void init(DeepProgressionPlugin pl) {
        plugin = pl;
    }

    /** Очистка кулдаунов игрока (выход с сервера, U21). */
    public static void clear(UUID uuid) {
        COOLDOWN.remove(uuid);
    }

    /** Использование зонда (с расходом заряда). */
    public static void use(Player p) {
        use(p, null);
    }

    /** Использование зонда или секционного сканера (с расходом заряда). */
    public static void use(Player p, Section section) {
        long now = System.currentTimeMillis();
        long cd = Cfg.probeCooldownSeconds() * 1000L;
        Long last = COOLDOWN.get(p.getUniqueId());
        if (last != null && now - last < cd) {
            Msg.send(p, "probe.cooldown");
            return;
        }

        ItemStack probe = p.getInventory().getItemInMainHand();
        ItemMeta meta = probe.getItemMeta();
        if (meta == null) return;
        int charges = meta.getPersistentDataContainer()
                .getOrDefault(Keys.PROBE_CHARGES, PersistentDataType.INTEGER, 0);
        if (charges <= 0) {
            Msg.send(p, "probe.no-charges");
            return;
        }
        charges--;
        meta.getPersistentDataContainer().set(Keys.PROBE_CHARGES,
                PersistentDataType.INTEGER, charges);
        probe.setItemMeta(meta);
        COOLDOWN.put(p.getUniqueId(), now);
        Msg.send(p, "probe.charged", "charges", String.valueOf(charges));

        scan(p, section);
    }

    /** Скан чанка (без расхода зарядов) — для зонда и геосканера. */
    public static void scan(Player p) {
        scan(p, null);
    }

    /**
     * Скан чанка: section == null — только руды (шахтёрский режим),
     * иначе — все зарегистрированные блоки указанной секции.
     */
    public static void scan(Player p, Section section) {
        Location loc = p.getLocation();
        World world = loc.getWorld();
        ChunkSnapshot snap = loc.getChunk().getChunkSnapshot(false, false, false);
        int cx = loc.getChunk().getX();
        int cz = loc.getChunk().getZ();
        String worldName = world.getName();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        // Тяжёлый скан — вне главного потока (снимок чанка неизменяем).
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<Branch, Integer> counts = new EnumMap<>(Branch.class);
            for (int y = minY; y < maxY; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        Material m = snap.getBlockType(x, y, z);
                        Branch b = section == null
                                ? Blocks.branchOfOre(m)
                                : Blocks.branchOf(m);
                        if (b == null) continue;
                        if (section != null && b.section != section) continue;
                        counts.merge(b, 1, Integer::sum);
                    }
                }
            }
            double richness = 0;
            for (var e : counts.entrySet()) richness += e.getValue() * Cfg.weight(e.getKey());
            final double richnessFinal = richness;

            Bukkit.getScheduler().runTask(plugin, () -> report(p, cx, cz, worldName,
                    counts, richnessFinal, section));
        });
    }

    private static void report(Player p, int cx, int cz, String world,
                               Map<Branch, Integer> counts, double richness,
                               Section section) {
        if (!p.isOnline()) return;
        if (section != null) {
            Msg.send(p, "scan.header",
                    "section", Text.color(section.color + section.display),
                    "cx", String.valueOf(cx), "cz", String.valueOf(cz), "world", world);
        } else {
            Msg.send(p, "probe.header",
                    "cx", String.valueOf(cx), "cz", String.valueOf(cz), "world", world);
        }
        if (counts.isEmpty()) {
            Msg.send(p, section == null ? "probe.empty" : "scan.empty");
        } else {
            List<Map.Entry<Branch, Integer>> sorted = new ArrayList<>(counts.entrySet());
            sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            int maxCount = sorted.get(0).getValue();
            int shown = 0;
            for (var e : sorted) {
                if (section != null && shown++ >= 10) {
                    Msg.send(p, "scan.more", "n", String.valueOf(sorted.size() - 10));
                    break;
                }
                String bar = Text.bar(e.getValue(), Math.max(1, maxCount), 12);
                Msg.send(p, "probe.line",
                        "branch", Text.color(e.getKey().color + e.getKey().display),
                        "count", Text.num(e.getValue()),
                        "bar", bar);
            }
        }
        Msg.send(p, "probe.richness", "count", Text.num(richness));
        if (section == null) {
            if (richness >= 500) Msg.send(p, "probe.verdict-high");
            else if (richness >= 150) Msg.send(p, "probe.verdict-mid");
            else Msg.send(p, "probe.verdict-low");
        }
        if (counts.containsKey(Branch.DIAMOND)) Msg.send(p, "probe.hint-diamond");
        if (counts.containsKey(Branch.DEBRIS)) Msg.send(p, "probe.hint-debris");
        if (counts.containsKey(Branch.EMERALD)) Msg.send(p, "probe.hint-emerald");

        p.playSound(p.getLocation(), "minecraft:block.amethyst_block.chime", 1.0f, 1.2f);
        p.getWorld().spawnParticle(Particle.ENCHANT,
                p.getLocation().clone().add(0, 1.5, 0), 12, 0.4, 0.4, 0.4, 0.5);
    }
}
