package ru.deepmagic.compress;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import ru.deepmagic.DeepMagicPlugin;
import ru.deepmagic.model.ItemDefs;
import ru.deepmagic.util.Conf;
import ru.deepmagic.util.T;
import ru.deepprogression.model.Branch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Магический компрессор: блок PISTON, состояние — в PDC ЧАНКА
 * (поршень не имеет block-entity — паттерн машин DeepProgression v3.2).
 * Вход: ресурсы DeepProgression (реестр Blocks.branchOfItem — своего списка нет)
 * + вещества DeepMagic для пересжатия + топливо (магмовый крем).
 * Цикл: 512 ед. за 2 с, 1 крем за цикл. Вещество I = 512 ед. сжатия.
 * Партия завершена → вещество ветки в выход.
 */
public final class Compressors {

    public static final String TAG_ID = "dm_c";
    public static final String TAG_IN = "dm_c_in";
    public static final String TAG_SUBIN = "dm_c_subin";
    public static final String TAG_PROG = "dm_c_prog";
    public static final String TAG_SUBPROG = "dm_c_subprog";
    public static final String TAG_OUT = "dm_c_out";

    private static final Map<String, Block> ACTIVE = new LinkedHashMap<>();

    private Compressors() {}

    public static NamespacedKey tagKey(String tag) {
        return new NamespacedKey(DeepMagicPlugin.get(), tag);
    }

    private static String pos(Block b) {
        return b.getX() + "_" + b.getY() + "_" + b.getZ();
    }

    private static NamespacedKey key(Block b, String tag) {
        return new NamespacedKey(DeepMagicPlugin.get(), tag + "_" + pos(b));
    }

    // ---------- реестр активных ----------

    public static String keyOf(Block b) {
        return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    public static void register(Block b) {
        ACTIVE.put(keyOf(b), b);
    }

    /** Ре-регистрация всех компрессоров чанка (после рестарта/выгрузки). */
    public static void scanChunk(org.bukkit.Chunk c) {
        var pdc = c.getPersistentDataContainer();
        for (org.bukkit.NamespacedKey k : new java.util.ArrayList<>(pdc.getKeys())) {
            if (!k.getNamespace().equals(DeepMagicPlugin.get().getName().toLowerCase(java.util.Locale.ROOT)))
                continue;
            String key = k.getKey();
            if (!key.startsWith(TAG_ID + "_")) continue;
            String[] parts = key.substring(TAG_ID.length() + 1).split("_");
            if (parts.length != 3) continue;
            try {
                int x = Integer.parseInt(parts[0]), y = Integer.parseInt(parts[1]),
                        z = Integer.parseInt(parts[2]);
                Block b = c.getBlock(x & 15, y, z & 15);
                if (b.getType() == Material.PISTON) register(b);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public static void unregisterAll(Block b) {
        ACTIVE.remove(keyOf(b));
        var pdc = b.getChunk().getPersistentDataContainer();
        for (String tag : List.of(TAG_ID, TAG_IN, TAG_SUBIN, TAG_PROG, TAG_SUBPROG, TAG_OUT)) {
            pdc.remove(key(b, tag));
        }
    }

    public static boolean isCompressorBlock(Block b) {
        if (b == null || b.getType() != Material.PISTON) return false;
        return b.getChunk().getPersistentDataContainer()
                .has(key(b, TAG_ID), PersistentDataType.BYTE);
    }

    public static void install(Block b) {
        b.getChunk().getPersistentDataContainer()
                .set(key(b, TAG_ID), PersistentDataType.BYTE, (byte) 1);
        register(b);
    }

    // ---------- PDC-карты ----------

    public static Map<Material, Long> readMap(Block b, String tag) {
        Map<Material, Long> map = new LinkedHashMap<>();
        String raw = b.getChunk().getPersistentDataContainer()
                .get(key(b, tag), PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) return map;
        for (String part : raw.split(",")) {
            String[] kv = part.split(":");
            if (kv.length != 2) continue;
            Material m = Material.matchMaterial(kv[0]);
            long q;
            try { q = Long.parseLong(kv[1]); } catch (NumberFormatException e) { continue; }
            if (m != null && q > 0) map.merge(m, q, Long::sum);
        }
        return map;
    }

    public static void writeMap(Block b, String tag, Map<Material, Long> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Material, Long> e : map.entrySet()) {
            if (e.getValue() <= 0) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey().name()).append(':').append(e.getValue());
        }
        b.getChunk().getPersistentDataContainer()
                .set(key(b, tag), PersistentDataType.STRING, sb.toString());
    }

    /** Трёхчастные карты: "BRANCH:tier" → qty. */
    public static Map<String, Long> readTriple(Block b, String tag) {
        Map<String, Long> map = new LinkedHashMap<>();
        String raw = b.getChunk().getPersistentDataContainer()
                .get(key(b, tag), PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) return map;
        for (String part : raw.split(",")) {
            String[] kv = part.split(":");
            if (kv.length != 3) continue;
            long q;
            try { q = Long.parseLong(kv[2]); } catch (NumberFormatException e) { continue; }
            if (q > 0 && Branch.byId(kv[0]) != null) {
                map.merge(kv[0] + ":" + kv[1], q, Long::sum);
            }
        }
        return map;
    }

    public static void writeTriple(Block b, String tag, Map<String, Long> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Long> e : map.entrySet()) {
            if (e.getValue() <= 0) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey()).append(':').append(e.getValue());
        }
        b.getChunk().getPersistentDataContainer()
                .set(key(b, tag), PersistentDataType.STRING, sb.toString());
    }

    // ---------- правила сжатия ----------

    public static long batchNeed(Branch br) {
        return Conf.unitsFor(br.rarity);
    }

    // ---------- главный тик ----------

    public static void tick() {
        if (ACTIVE.isEmpty()) return;
        List<String> dead = new ArrayList<>();
        for (Map.Entry<String, Block> e : ACTIVE.entrySet()) {
            Block b = e.getValue();
            if (!b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4)
                    || b.getType() != Material.PISTON
                    || !isCompressorBlock(b)) {
                dead.add(e.getKey());
                continue;
            }
            process(b);
            CompressorGui.refresh(b); // живая перерисовка открытых GUI этой машины
        }
        for (String k : dead) ACTIVE.remove(k);
    }

    /** Точечный прогон ОДНОГО компрессора (для /dmtest — не трогает чужие машины). */
    public static void tickOne(Block b) {
        if (b.getType() == Material.PISTON && isCompressorBlock(b)
                && b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4)) {
            process(b);
        }
    }

    private static void process(Block b) {
        Map<Material, Long> in = readMap(b, TAG_IN);
        Map<Material, Long> prog = readMap(b, TAG_PROG);
        Map<String, Long> subin = readTriple(b, TAG_SUBIN);
        Map<String, Long> subprog = readTriple(b, TAG_SUBPROG);

        long fuel = in.getOrDefault(Material.MAGMA_CREAM, 0L);
        if (fuel >= Conf.fuelPerCycle()) {
            fuel -= Conf.fuelPerCycle();
            if (fuel > 0) in.put(Material.MAGMA_CREAM, fuel);
            else in.remove(Material.MAGMA_CREAM);
        } else {
            save(b, in, prog, subin, subprog);
            return;
        }

        // Пересжатие: 3 вещества текущего тира → 1 вещество следующего (3 цикла)
        String subTarget = null;
        for (Map.Entry<String, Long> e : subin.entrySet()) {
            if (e.getValue() > 0) { subTarget = e.getKey(); break; }
        }
        if (subTarget != null) {
            long done = subprog.getOrDefault(subTarget, 0L) + 1;
            subin.put(subTarget, subin.get(subTarget) - 1);
            if (subin.get(subTarget) <= 0) subin.remove(subTarget);
            if (done >= 3) {
                subprog.remove(subTarget);
                String[] spec = subTarget.split(":");
                int nextTier = Math.min(3, Integer.parseInt(spec[1]) + 1);
                Map<String, Long> out = readTriple(b, TAG_OUT);
                out.merge(spec[0] + ":" + nextTier, 1L, Long::sum);
                writeTriple(b, TAG_OUT, out);
                celebrate(b, nextTier);
            } else {
                subprog.put(subTarget, done);
                press(b);
            }
        } else {
            Material target = null;
            for (Map.Entry<Material, Long> e : in.entrySet()) {
                if (e.getKey() == Material.MAGMA_CREAM) continue;
                Branch br = ru.deepprogression.model.Blocks.branchOfItem(e.getKey());
                if (br == null) continue;
                if (target == null || e.getValue() > in.get(target)) target = e.getKey();
            }
            if (target == null) {
                in.merge(Material.MAGMA_CREAM, (long) Conf.fuelPerCycle(), Long::sum);
                save(b, in, prog, subin, subprog);
                return;
            }
            long batch = Math.min(Conf.unitsPerCycle(), in.get(target));
            in.put(target, in.get(target) - batch);
            if (in.get(target) <= 0) in.remove(target);
            Branch br = ru.deepprogression.model.Blocks.branchOfItem(target);
            long need = batchNeed(br);
            long done = prog.getOrDefault(target, 0L) + batch;
            if (done >= need) {
                prog.put(target, done - need); // остаток не теряется
                Map<String, Long> out = readTriple(b, TAG_OUT);
                out.merge(br.name() + ":1", 1L, Long::sum);
                writeTriple(b, TAG_OUT, out);
                celebrate(b, 1);
            } else {
                prog.put(target, done);
                press(b);
            }
        }
        save(b, in, prog, subin, subprog);
    }

    private static void press(Block b) {
        b.getWorld().playSound(b.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 0.4f, 0.8f);
        if (Math.random() < 0.4) {
            b.getWorld().spawnParticle(Particle.SMOKE,
                    b.getLocation().add(0.5, 1.2, 0.5), 6, 0.2, 0.1, 0.2, 0.01);
        }
    }

    private static void celebrate(Block b, int tier) {
        Location loc = b.getLocation().add(0.5, 1.1, 0.5);
        b.getWorld().spawnParticle(Particle.WAX_ON, loc, 24, 0.3, 0.3, 0.3, 0.02);
        if (tier >= 3) b.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 20, 0.4, 0.4, 0.4, 0.1);
        b.getWorld().playSound(loc, Sound.BLOCK_ANVIL_USE, 0.5f, 1.8f);
    }

    private static void save(Block b, Map<Material, Long> in, Map<Material, Long> prog,
                             Map<String, Long> subin, Map<String, Long> subprog) {
        writeMap(b, TAG_IN, in);
        writeMap(b, TAG_PROG, prog);
        writeTriple(b, TAG_SUBIN, subin);
        writeTriple(b, TAG_SUBPROG, subprog);
    }

    // ---------- закладка ----------

    public static long deposit(Block b, ItemStack hand, long maxQty) {
        Material m = hand.getType();
        Map<Material, Long> in = readMap(b, TAG_IN);
        long qty = Math.min(maxQty, hand.getAmount());
        in.merge(m, qty, Long::sum);
        writeMap(b, TAG_IN, in);
        return qty;
    }

    // ---------- забор и сохранение при поломке ----------

    public static void dropAll(Block b) {
        Location loc = b.getLocation().add(0.5, 0.5, 0.5);
        for (Map.Entry<Material, Long> e : readMap(b, TAG_IN).entrySet()) {
            long left = e.getValue();
            while (left > 0) {
                int amt = (int) Math.min(64, left);
                b.getWorld().dropItemNaturally(loc, new ItemStack(e.getKey(), amt));
                left -= amt;
            }
        }
        // недожатый прогресс партий возвращаем ресурсами (лор: «содержимое не теряется»)
        for (Map.Entry<Material, Long> e : readMap(b, TAG_PROG).entrySet()) {
            long left = e.getValue();
            while (left > 0) {
                int amt = (int) Math.min(64, left);
                b.getWorld().dropItemNaturally(loc, new ItemStack(e.getKey(), amt));
                left -= amt;
            }
        }
        // незавершённое пересжатие: уже потраченные вещества возвращаются
        for (Map.Entry<String, Long> e : readTriple(b, TAG_SUBPROG).entrySet()) {
            String[] spec = e.getKey().split(":");
            dropSubstances(b, loc, Branch.byId(spec[0]), Integer.parseInt(spec[1]), e.getValue());
        }
        for (Map.Entry<String, Long> e : readTriple(b, TAG_SUBIN).entrySet()) {
            String[] spec = e.getKey().split(":");
            dropSubstances(b, loc, Branch.byId(spec[0]), Integer.parseInt(spec[1]), e.getValue());
        }
        for (Map.Entry<String, Long> e : readTriple(b, TAG_OUT).entrySet()) {
            String[] spec = e.getKey().split(":");
            dropSubstances(b, loc, Branch.byId(spec[0]), Integer.parseInt(spec[1]), e.getValue());
        }
    }

    private static void dropSubstances(Block b, Location loc, Branch br, int tier, long count) {
        for (long i = 0; i < count; i++) {
            ItemStack st = ItemDefs.substance(br, tier);
            if (st != null) b.getWorld().dropItemNaturally(loc, st);
        }
    }

    public static int activeCount() {
        return ACTIVE.size();
    }
}
