package ru.deepprogression.tools;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.deepprogression.DeepProgressionPlugin;
import ru.deepprogression.model.Blocks;
import ru.deepprogression.model.Branch;
import ru.deepprogression.progress.Medals;
import ru.deepprogression.progress.PlayerProfile;
import ru.deepprogression.progress.ProfileStore;
import ru.deepprogression.util.Cfg;
import ru.deepprogression.util.Keys;
import ru.deepprogression.util.Msg;
import ru.deepprogression.util.Text;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Чанковый бур — конечный автомат EMPTY → CHARGED → LOADED → EMPTY.
 * Выработка посекционно (1 секция 16³ за тик), руды → в хранилище PDC.
 */
public final class ChunkDrill {

    private ChunkDrill() {}

    /** Полный заряд без расхода магмы (через зарядную станцию). */
    public static boolean forceCharge(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        String state = meta.getPersistentDataContainer()
                .getOrDefault(Keys.CD_STATE, PersistentDataType.STRING, "EMPTY");
        if (!"EMPTY".equals(state)) return false;
        meta.getPersistentDataContainer().set(Keys.CD_STATE,
                PersistentDataType.STRING, "CHARGED");
        item.setItemMeta(meta);
        return true;
    }

    public static void interact(Player p) {
        ItemStack item = p.getInventory().getItemInMainHand();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        String state = meta.getPersistentDataContainer()
                .getOrDefault(Keys.CD_STATE, PersistentDataType.STRING, "EMPTY");

        switch (state) {
            case "EMPTY" -> charge(p, item, meta);
            case "CHARGED" -> startSweep(p, item, meta);
            case "RUNNING" -> Msg.send(p, "chunk.running-already");
            case "LOADED" -> {
                if (p.isSneaking()) collect(p, item, meta);
                else Msg.send(p, "chunk.loaded",
                        "units", Text.num(totalUnits(meta)));
            }
            default -> charge(p, item, meta);
        }
    }

    // --- EMPTY → CHARGED ---
    private static void charge(Player p, ItemStack item, ItemMeta meta) {
        int cost = Cfg.magmaCost();
        int have = countMaterial(p, Material.MAGMA_BLOCK);
        if (have < cost) {
            Msg.send(p, "chunk.need-magma",
                    "cost", Text.num(cost), "n", Text.num(have));
            return;
        }
        p.getInventory().removeItem(new ItemStack(Material.MAGMA_BLOCK, cost));
        meta.getPersistentDataContainer().set(Keys.CD_STATE,
                PersistentDataType.STRING, "CHARGED");
        item.setItemMeta(meta);
        Msg.send(p, "chunk.charged", "cost", Text.num(cost));
        p.playSound(p.getLocation(), "minecraft:block.lava.pop", 1.0f, 0.8f);
    }

    private static int countMaterial(Player p, Material m) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && it.getType() == m) n += it.getAmount();
        }
        return n;
    }

    // --- CHARGED → выработка → LOADED ---
    private static void startSweep(Player p, ItemStack item, ItemMeta meta) {
        long now = System.currentTimeMillis();
        long cd = Cfg.cooldownSeconds() * 1000L;
        long last = meta.getPersistentDataContainer()
                .getOrDefault(Keys.CD_COOLDOWN, PersistentDataType.LONG, 0L);
        if (now - last < cd) {
            long wait = (cd - (now - last) + 999) / 1000;
            Msg.send(p, "chunk.cooldown", "n", String.valueOf(wait));
            return;
        }

        World world = p.getWorld();
        var chunk = p.getLocation().getChunk();
        if (!chunk.isLoaded()) {
            Msg.send(p, "chunk.no-ores");
            return;
        }

        // U23: статус RUNNING — блокирует вторую параллельную выработку тем же буром
        meta.getPersistentDataContainer().set(Keys.CD_STATE,
                PersistentDataType.STRING, "RUNNING");
        item.setItemMeta(meta);

        Msg.send(p, "chunk.running");
        p.playSound(p.getLocation(), "minecraft:block.piston.extend", 0.8f, 0.7f);

        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        int sections = (maxY - minY) / 16;

        double eff = Cfg.efficiency();
        double progFactor = Cfg.progMultChunk();

        new org.bukkit.scheduler.BukkitRunnable() {
            int sec = 0;
            final Map<Material, Integer> loot = new LinkedHashMap<>();
            final Map<Branch, Double> prog = new EnumMap<>(Branch.class);

            @Override
            public void run() {
                if (!chunk.isLoaded()) { finish(p, item, loot, prog); cancel(); return; }
                int baseY = minY + sec * 16;
                for (int y = baseY; y < baseY + 16 && y < maxY; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            Block block = chunk.getBlock(x, y, z);
                            Material m = block.getType();
                            Blocks.DropSpec spec = Blocks.dropOf(m);
                            if (spec == null) continue;
                            // D4: не трогаем блочные сущности (сундуки, шалкеры,
                            // спавнеры, машины DeepProgression и т.п.) и их содержимое
                            if (block.getState(false) instanceof org.bukkit.block.TileState) continue;
                            if (ThreadLocalRandom.current().nextDouble() < eff) {
                                loot.merge(spec.item(), spec.units(), Integer::sum);
                            }
                            Branch b = Blocks.branchOfOre(m);
                            if (b != null) prog.merge(b, progFactor, Double::sum);
                            block.setType(Blocks.baseFor(m, y), false);
                        }
                    }
                }
                // Эффекты секции + прогресс на экшен-баре
                Location center = new Location(world,
                        chunk.getX() * 16 + 8, baseY + 8, chunk.getZ() * 16 + 8);
                world.spawnParticle(Particle.FLAME, center, 14, 6, 7, 6, 0.02);
                world.spawnParticle(Particle.LAVA, center, 6, 6, 7, 6, 0);
                for (Player q : world.getPlayers()) {
                    if (q.getLocation().distanceSquared(center) < 64 * 64) {
                        q.playSound(center, "minecraft:block.piston.extend", 0.5f, 0.9f);
                    }
                }
                if (p.isOnline()) {
                    Msg.action(p, "chunk.progress",
                            "sec", String.valueOf(sec + 1),
                            "all", String.valueOf(sections),
                            "bar", Text.bar(sec + 1, sections, 14));
                }
                sec++;
                if (sec >= sections) {
                    finish(p, item, loot, prog);
                    cancel();
                }
            }
        }.runTaskTimer(DeepProgressionPlugin.get(), 1L, 1L);
    }

    /** D6: обрезка добычи до лимита max_units. */
    private static void trimToCap(Map<Material, Integer> loot, int cap) {
        int total = loot.values().stream().mapToInt(Integer::intValue).sum();
        while (total > cap) {
            Material biggest = null;
            int best = 0;
            for (var e : loot.entrySet()) {
                if (e.getValue() > best) { best = e.getValue(); biggest = e.getKey(); }
            }
            if (biggest == null) break;
            int take = Math.min(best, total - cap);
            loot.put(biggest, best - take);
            total -= take;
        }
        loot.values().removeIf(v -> v <= 0);
    }

    private static void finish(Player p, ItemStack item,
                               Map<Material, Integer> loot, Map<Branch, Double> prog) {
        // Прогрессия по веткам
        for (var e : prog.entrySet()) {
            if (e.getKey() == null) continue;
            long amount = Math.max(0, Math.round(e.getValue()));
            if (p.isOnline() && amount > 0) {
                Medals.progress(p, e.getKey(), amount);
            }
        }
        int total = loot.values().stream().mapToInt(Integer::intValue).sum();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (total == 0) {
            // Пустая выработка: заряд сохраняется (возврат в CHARGED, как и было
            // в v2 — не наказываем за бедный чанк), лут-строка очищается.
            meta.getPersistentDataContainer().set(Keys.CD_STATE,
                    PersistentDataType.STRING, "CHARGED");
            meta.getPersistentDataContainer().set(Keys.CD_CONTENTS,
                    PersistentDataType.STRING, "");
            item.setItemMeta(meta);
            Msg.send(p, "chunk.no-ores");
            if (p.isOnline()) p.playSound(p.getLocation(), "minecraft:block.lava.pop", 0.8f, 0.6f);
            return;
        }
        // D6: лимит хранилища бура
        int cap = Math.max(1, Cfg.maxUnits());
        if (total > cap) {
            trimToCap(loot, cap);
            total = loot.values().stream().mapToInt(Integer::intValue).sum();
            if (p.isOnline()) Msg.send(p, "chunk.capped", "max", Text.num(cap));
        }
        // Экономика v3.1: учёт фактически извлечённого
        for (var le : loot.entrySet()) {
            ru.deepprogression.economy.Economy.mined(le.getKey(), le.getValue());
        }
        meta.getPersistentDataContainer().set(Keys.CD_CONTENTS,
                PersistentDataType.STRING, serialize(loot));
        meta.getPersistentDataContainer().set(Keys.CD_STATE,
                PersistentDataType.STRING, "LOADED");
        meta.getPersistentDataContainer().set(Keys.CD_COOLDOWN,
                PersistentDataType.LONG, System.currentTimeMillis());
        item.setItemMeta(meta);
        Msg.send(p, "chunk.done", "units", Text.num(total));
        if (p.isOnline()) {
            p.playSound(p.getLocation(), "minecraft:entity.player.levelup", 1.0f, 1.0f);
        }
    }

    public static String serialize(Map<Material, Integer> loot) {
        StringBuilder sb = new StringBuilder();
        for (var e : loot.entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey().name()).append(':').append(e.getValue());
        }
        return sb.toString();
    }

    public static Map<Material, Integer> deserialize(String s) {
        Map<Material, Integer> map = new LinkedHashMap<>();
        if (s == null || s.isEmpty()) return map;
        for (String part : s.split(",")) {
            int i = part.indexOf(':');
            if (i <= 0) continue;
            try {
                Material m = Material.valueOf(part.substring(0, i));
                int n = Integer.parseInt(part.substring(i + 1));
                map.merge(m, n, Integer::sum);
            } catch (Exception ignored) {
            }
        }
        return map;
    }

    private static int totalUnits(ItemMeta meta) {
        String s = meta.getPersistentDataContainer()
                .getOrDefault(Keys.CD_CONTENTS, PersistentDataType.STRING, "");
        int t = 0;
        for (var e : deserialize(s).values()) t += e;
        return t;
    }

    // --- LOADED → выгрузка → EMPTY ---
    private static void collect(Player p, ItemStack item, ItemMeta meta) {
        Map<Material, Integer> loot = deserialize(meta.getPersistentDataContainer()
                .getOrDefault(Keys.CD_CONTENTS, PersistentDataType.STRING, ""));
        if (loot.isEmpty()) {
            meta.getPersistentDataContainer().set(Keys.CD_STATE,
                    PersistentDataType.STRING, "EMPTY");
            item.setItemMeta(meta);
            Msg.send(p, "chunk.collected-all");
            return;
        }
        int taken = 0;
        int routedToPouch = 0;
        Map<Material, Integer> remain = new LinkedHashMap<>();
        for (var e : loot.entrySet()) {
            int left = e.getValue();
            if (left <= 0) continue;
            // сначала — маршрут в рудный тайник (v2.0)
            int routed = ru.deepprogression.storage.Vaults.route(p, e.getKey(), left);
            left -= routed;
            routedToPouch += routed;
            if (left <= 0) continue;
            // D3: одна попытка на материал — неAdded считается ровно один раз,
            // никаких фантомных единиц (дюп при полном инвентаре устранён)
            var notAdded = p.getInventory().addItem(new ItemStack(e.getKey(), left));
            int notAddedCount = notAdded.values().stream()
                    .mapToInt(ItemStack::getAmount).sum();
            taken += left - notAddedCount;
            if (notAddedCount > 0) {
                remain.merge(e.getKey(), notAddedCount, Integer::sum);
                break; // инвентарь полон
            }
        }
        if (routedToPouch > 0) {
            Msg.action(p, "chunk.pouch-routed", "n", Text.num(routedToPouch));
        }
        if (remain.isEmpty()) {
            meta.getPersistentDataContainer().set(Keys.CD_STATE,
                    PersistentDataType.STRING, "EMPTY");
            meta.getPersistentDataContainer().set(Keys.CD_CONTENTS,
                    PersistentDataType.STRING, "");
            item.setItemMeta(meta);
            Msg.send(p, "chunk.collected", "n", Text.num(taken),
                    "left", "0");
            Msg.send(p, "chunk.collected-all");
        } else {
            meta.getPersistentDataContainer().set(Keys.CD_CONTENTS,
                    PersistentDataType.STRING, serialize(remain));
            item.setItemMeta(meta);
            Msg.send(p, "chunk.collected",
                    "n", Text.num(taken),
                    "left", Text.num(remain.values().stream().mapToInt(Integer::intValue).sum()));
            Msg.send(p, "chunk.full-inv");
        }
        p.playSound(p.getLocation(), "minecraft:entity.experience_orb.pickup", 0.8f, 1.0f);
    }
}
