package ru.deepprogression.model;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Реестр размещённых игроком блоков веток (анти-абуз "поставил-сломал").
 *
 * Любой блок ветки, поставленный игроком (или упавший/двиганный поршнем),
 * помечается: при ломании помеченного блока кастомные множители НЕ применяются
 * — только ванильный дроп и прогресс. Блоки, выросшие естественно
 * (BlockGrow/StructureGrow/Spread), снимаются с учёта.
 *
 * Память: Map<мир, Map<ключ чанка, Set<упакованная локальная позиция>>>.
 * Чистка: при выгрузке чанка и при ломании блока.
 */
public final class Placed {

    private static final Map<UUID, Map<Long, Set<Integer>>> MARKS = new HashMap<>();

    private Placed() {}

    /** Упаковка локальной позиции чанка: y+512 << 8 | (x&15) << 4 | (z&15). */
    private static int pack(int x, int y, int z) {
        return ((y + 512) << 8) | ((x & 15) << 4) | (z & 15);
    }

    private static long chunkKey(Chunk c) {
        return ((long) c.getX() << 32) ^ (c.getZ() & 0xFFFFFFFFL);
    }

    public static void mark(Block b) {
        if (b == null) return;
        Chunk c = b.getChunk();
        MARKS.computeIfAbsent(c.getWorld().getUID(), k -> new HashMap<>())
                .computeIfAbsent(chunkKey(c), k -> new HashSet<>())
                .add(pack(b.getX(), b.getY(), b.getZ()));
    }

    public static void unmark(Block b) {
        if (b == null) return;
        Chunk c = b.getChunk();
        Map<Long, Set<Integer>> world = MARKS.get(c.getWorld().getUID());
        if (world == null) return;
        Set<Integer> set = world.get(chunkKey(c));
        if (set != null) set.remove(pack(b.getX(), b.getY(), b.getZ()));
    }

    public static boolean isMarked(Block b) {
        if (b == null) return false;
        Chunk c = b.getChunk();
        Map<Long, Set<Integer>> world = MARKS.get(c.getWorld().getUID());
        if (world == null) return false;
        Set<Integer> set = world.get(chunkKey(c));
        return set != null && set.contains(pack(b.getX(), b.getY(), b.getZ()));
    }

    // ---------- хуки событий (вызывает Listeners) ----------

    /** Установка блока игроком. */
    public static void onPlayerPlace(Block b, Branch branchCheck) {
        if (branchCheck != null) mark(b);
    }

    /** Естественный рост (урожай, тростник, бамбук, кактус, кораллы?). */
    public static void onGrow(Block b) {
        unmark(b);
    }

    /** Рост структуры (сапляк -> дерево, гриб -> огромный гриб). */
    public static void onStructureGrow(java.util.List<org.bukkit.block.BlockState> blocks) {
        if (blocks == null) return;
        for (org.bukkit.block.BlockState bs : blocks) {
            if (bs != null) unmark(bs.getBlock());
        }
    }

    /** Распространение (мох, трава, лианы, скульк). */
    public static void onSpread(Block b) {
        unmark(b);
    }

    /** Сущность изменила блок: падающий песок/гравий, эндермены. */
    public static void onEntityChange(EntityChangeBlockEvent e) {
        org.bukkit.Material to = e.getTo(); // getTo() возвращает Material
        if (to.isAir()) {
            unmark(e.getBlock()); // подняли (эндермен)
        } else if (Blocks.branchOf(to) != null) {
            mark(e.getBlock()); // положили/приземлился падающий блок
        }
    }

    /** Поршни двигают блоки веток: новые позиции помечаются. */
    public static void onPiston(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) {
            if (Blocks.branchOf(b.getType()) != null) mark(b.getRelative(e.getDirection()));
        }
    }

    public static void onPiston(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) {
            if (Blocks.branchOf(b.getType()) != null) mark(b.getRelative(e.getDirection()));
        }
    }

    /** Выгрузка чанка — чистим память. */
    public static void onChunkUnload(ChunkUnloadEvent e) {
        Map<Long, Set<Integer>> world = MARKS.get(e.getWorld().getUID());
        if (world != null) world.remove(chunkKey(e.getChunk()));
    }

    /** Полная очистка (выключение сервера). */
    public static void clearAll() {
        MARKS.clear();
    }
}
