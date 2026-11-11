package ru.deepprogression.tools;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import ru.deepprogression.DeepProgressionPlugin;
import ru.deepprogression.economy.Economy;
import ru.deepprogression.model.Blocks;
import ru.deepprogression.model.Branch;
import ru.deepprogression.model.Placed;
import ru.deepprogression.model.Section;
import ru.deepprogression.perks.Perks;
import ru.deepprogression.progress.Medals;
import ru.deepprogression.progress.PlayerProfile;
import ru.deepprogression.progress.ProfileStore;
import ru.deepprogression.storage.Vaults;
import ru.deepprogression.util.Cfg;
import ru.deepprogression.util.Msg;
import ru.deepprogression.util.Text;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Логика добычи v3.0: обобщена на ВСЕ ветки всех секций.
 *  - Своя ветка + инструмент ветки: дроп × множитель (руда — по DropSpec,
 *    остальные — по ванильным getDrops) + перк звания.
 *  - Универсальные инструменты: своя секция (legacy — шахтёрская).
 *  - Анти-абуз Placed: размещённые игроком блоки добываются ванильно.
 *  - Перк звания, жилы удачи, рудный тайник — без изменений.
 */
public final class Drills {

    private Drills() {}

    public static void handle(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block block = e.getBlock();
        org.bukkit.Material m = block.getType();
        Branch b = Blocks.branchOf(m);
        if (b == null) return;
        if (!Cfg.worldAllowed(block.getWorld())) return;
        if (p.getGameMode() == GameMode.CREATIVE && !Cfg.creativeCounts()) return;

        ItemStack tool = p.getInventory().getItemInMainHand();
        String toolId = Items.toolId(tool);
        Tools.ToolSpec spec = Tools.get(toolId);
        Blocks.DropSpec ore = Blocks.dropOf(m);

        boolean placed = Placed.isMarked(block);
        Placed.unmark(block);

        double progMult = 1.0;

        if (spec != null && !placed) {
            switch (spec.type) {
                case DRILL -> progMult = drill(p, e, block, tool, b, spec, ore);
                case UNIVERSAL -> progMult = universal(p, e, block, tool, b, spec, ore);
                case GEOLOGIST -> progMult = geologist(p, e, block, tool, b, spec, ore);
                default -> { }
            }
        } else if (!placed) {
            // Ванильная добыча ветки: перки секции + жилы удачи
            progMult = vanilla(p, e, block, tool, b, ore);
        }

        // Пересадка (wood_replant / farm_replant) — только естественные блоки
        if (!placed && b != null) {
            Perks.scheduleReplant(DeepProgressionPlugin.get(), p, block, b);
        }

        Medals.progress(p, b, Math.max(1, Math.round(progMult)));
    }

    /**
     * Ванильный путь v3.1: перки секций (двойной дроп, бонусы +1, опыт),
     * перк звания и жилы удачи — для руд. @return множитель прогрессии.
     */
    private static double vanilla(Player p, BlockBreakEvent e, Block block,
                                  ItemStack tool, Branch b, Blocks.DropSpec ore) {
        // Опытный мастер (инженер): +25% опыта за ветки секции
        int xpMod = Perks.xpModifier(p, b);
        if (xpMod != 100) e.setExpToDrop((int) Math.round(e.getExpToDrop() * xpMod / 100.0));

        if (ore != null) {
            if (block.getDrops(tool, p).isEmpty()) return 1.0;
            // Перк звания (legacy) и перк секции (mine_double) — независимые шансы
            if (perkRoll(p, Blocks.branchOfOre(block.getType()))) {
                e.setDropItems(false);
                give(p, block.getLocation(), ore.item(), ore.units() * 2);
            }
            luckyVein(p, block.getLocation(), ore);
            Economy.mined(ore.item(), ore.units());
            return 1.0;
        }

        if (b == null) return 1.0;
        double dc = Perks.sectionDoubleChance(p, b);
        Perks.Bonus bonus = Perks.bonusDrop(p, block, b);
        if (dc <= 0 && bonus == null) {
            if (Economy.tracked(block.getType())) Economy.mined(block.getType(), 1);
            return 1.0;
        }
        if (block.getDrops(tool, p).isEmpty()) return 1.0;
        e.setDropItems(false);
        for (ItemStack d : block.getDrops(tool, p)) {
            if (d == null || d.getType().isAir()) continue;
            int amt = d.getAmount();
            if (dc > 0 && ThreadLocalRandom.current().nextDouble() < dc) amt *= 2;
            give(p, block.getLocation(), d.getType(), amt);
            if (Economy.tracked(d.getType())) Economy.mined(d.getType(), amt);
        }
        if (bonus != null && bonus.item() != null
                && ThreadLocalRandom.current().nextDouble() < bonus.chance()) {
            give(p, block.getLocation(), bonus.item(), bonus.qty());
            Msg.action(p, "perk.bonus", "item", Items.ruName(bonus.item()));
        }
        return 1.0;
    }

    private static double drill(Player p, BlockBreakEvent e, Block block,
                                ItemStack tool, Branch b, Tools.ToolSpec spec,
                                Blocks.DropSpec ore) {
        if (spec.branch != b) {
            // Чужая ветка: на камне/природном массиве — только бонусный шанс
            if (Blocks.isBonusBase(block.getType())) rollBonus(p, block.getLocation(), spec.id, spec.branch);
            return 1.0;
        }
        // D5: если этим инструментом блок в ваниле не добывается — без кастома
        if (block.getDrops(tool, p).isEmpty()) return 1.0;
        e.setDropItems(false);
        int mult = Math.max(1, Cfg.mult(spec.id));

        if (ore != null) {
            int units = ore.units() * mult;
            units = applyPerk(p, b, units);
            give(p, block.getLocation(), ore.item(), units);
            maybeEffect(p, block.getLocation());
            luckyVein(p, block.getLocation(), ore);
            Economy.mined(ore.item(), units);
        } else {
            java.util.Collection<ItemStack> drops = block.getDrops(tool, p);
            for (ItemStack d : drops) {
                if (d == null || d.getType().isAir()) continue;
                int amt = d.getAmount() * mult;
                amt = applyPerk(p, b, amt);
                give(p, block.getLocation(), d.getType(), amt);
                if (Economy.tracked(d.getType())) Economy.mined(d.getType(), amt);
            }
            maybeEffect(p, block.getLocation());
        }
        // Бонус "из камня" для шахтёрских инструментов
        if (Blocks.isBonusBase(block.getType())) rollBonus(p, block.getLocation(), spec.id, spec.branch);
        return 2.0;
    }

    /** Универсальный инструмент секции (null — шахтёрский legacy). */
    private static double universal(Player p, BlockBreakEvent e, Block block,
                                    ItemStack tool, Branch b, Tools.ToolSpec spec,
                                    Blocks.DropSpec ore) {
        boolean applies = spec.section == null
                ? b.section == Section.MINING
                : spec.section == b.section;
        if (!applies) return 1.0;
        if (block.getDrops(tool, p).isEmpty()) return 1.0;
        e.setDropItems(false);
        int mult = Math.max(1, Cfg.mult(spec.id));

        if (ore != null) {
            int units = ore.units() * mult;
            give(p, block.getLocation(), ore.item(), units);
            maybeEffect(p, block.getLocation());
            luckyVein(p, block.getLocation(), ore);
            Economy.mined(ore.item(), units);
        } else {
            java.util.Collection<ItemStack> drops = block.getDrops(tool, p);
            for (ItemStack d : drops) {
                if (d == null || d.getType().isAir()) continue;
                give(p, block.getLocation(), d.getType(), d.getAmount() * mult);
                if (Economy.tracked(d.getType())) Economy.mined(d.getType(), d.getAmount() * mult);
            }
            maybeEffect(p, block.getLocation());
        }
        if (spec.section == null && Blocks.isBonusBase(block.getType())) {
            rollBonus(p, block.getLocation(), spec.id, Branch.COAL);
        }
        return 1.5;
    }

    /** Кирка геолога (legacy: руды шахтёрской секции). */
    private static double geologist(Player p, BlockBreakEvent e, Block block,
                                    ItemStack tool, Branch b, Tools.ToolSpec spec,
                                    Blocks.DropSpec ore) {
        if (ore == null || b.section != Section.MINING) return 1.0;
        if (block.getDrops(tool, p).isEmpty()) return 1.0;
        e.setDropItems(false);
        int units = ore.units() * Math.max(1, Cfg.mult(spec.id));
        units = applyPerk(p, b, units);
        give(p, block.getLocation(), ore.item(), units);
        maybeEffect(p, block.getLocation());
        luckyVein(p, block.getLocation(), ore);
        Economy.mined(ore.item(), units);
        if (Blocks.isBonusBase(block.getType())) rollBonus(p, block.getLocation(), spec.id, Branch.COAL);
        return 1.2;
    }

    /** Перк звания: шанс двойного дропа своей ветки (тир ≥ 4). */
    private static boolean perkRoll(Player p, Branch b) {
        if (b == null) return false;
        PlayerProfile prof = ProfileStore.get(p.getUniqueId());
        int tier = prof.getTier(b);
        int from = Cfg.perkFromTier();
        if (tier < from) return false;
        double chance = Math.min(Cfg.perkChanceCap(),
                (tier - from + 1) * Cfg.perkChancePerTier());
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    private static int applyPerk(Player p, Branch b, int units) {
        if (b == null || units <= 0) return units;
        if (perkRoll(p, b)) {
            Msg.action(p, "perk.double", "branch", b.color + b.display);
            return units * 2;
        }
        return units;
    }

    /** Жила удачи: редкий джекпот при добыче руды. */
    private static void luckyVein(Player p, Location loc, Blocks.DropSpec ore) {
        double chance = Cfg.luckyChance();
        if (chance <= 0) return;
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;
        int bonus = ore.units() * Cfg.luckyMult();
        give(p, loc, ore.item(), bonus);
        Msg.action(p, "lucky.hit", "count", Text.num(bonus),
                "item", Items.ruName(ore.item()));
        p.playSound(loc, "minecraft:ui.toast.challenge_complete", 1.0f, 1.4f);
        p.getWorld().spawnParticle(Particle.FLAME,
                loc.clone().add(0.5, 1.0, 0.5), 25, 0.5, 0.5, 0.5, 0.05);
        p.getWorld().spawnParticle(Particle.CRIT,
                loc.clone().add(0.5, 1.0, 0.5), 30, 0.8, 0.8, 0.8, 0.3);
        Branch branch = branchByDrop(ore.item());
        if (branch != null && Cfg.luckyBroadcast()
                && (branch.rarity == Branch.Rarity.RARE || branch == Branch.GOLD)) {
            Bukkit.broadcastMessage(Msg.fmt("lucky.broadcast",
                    "player", p.getName(),
                    "branch", Text.color(branch.color + branch.display),
                    "count", Text.num(bonus)));
        }
    }

    private static Branch branchByDrop(org.bukkit.Material drop) {
        for (Branch b : Branch.values()) {
            Blocks.DropSpec d = Blocks.bonusOf(b);
            if (d == null) continue;
            if (d.item() == drop) return b;
        }
        return null;
    }

    private static void rollBonus(Player p, Location loc, String toolId, Branch branch) {
        double chance = Cfg.fromStoneChance(toolId, branch);
        if (chance <= 0) return;
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;
        Blocks.DropSpec bonus = Blocks.bonusOf(branch);
        if (bonus == null) return;
        give(p, loc, bonus.item(), bonus.units());
        if (ThreadLocalRandom.current().nextDouble() < 0.3) {
            Msg.action(p, "drill.bonus-stone",
                    "count", String.valueOf(bonus.units()),
                    "item", Items.ruName(bonus.item()));
        }
    }

    private static void maybeEffect(Player p, Location loc) {
        if (ThreadLocalRandom.current().nextDouble() < Cfg.effectChance()) {
            p.getWorld().spawnParticle(
                    Particle.CRIT, loc.clone().add(0.5, 0.5, 0.5),
                    6, 0.2, 0.2, 0.2, 0.05);
            p.playSound(loc, "minecraft:block.note_block.pling", 0.3f, 1.6f);
        }
    }

    /** Авто-подбор: сначала тайник ресурсов, потом инвентарь, остаток на землю. */
    public static void give(Player p, Location loc, org.bukkit.Material mat, int count) {
        if (count <= 0) return;
        int routed = Vaults.route(p, mat, count);
        if (routed > 0) {
            notifyRouted(p, routed, mat);
        }
        count = routed;
        if (count <= 0) return;
        if (!Cfg.autoPickup()) {
            dropStacks(p.getWorld(), loc, mat, count);
            return;
        }
        int max = Math.max(1, mat.getMaxStackSize());
        int left = count;
        while (left > 0) {
            int stack = Math.min(max, left);
            left -= stack;
            var notAdded = p.getInventory().addItem(new ItemStack(mat, stack));
            if (!notAdded.isEmpty()) {
                int notAddedCount = notAdded.values().stream()
                        .mapToInt(ItemStack::getAmount).sum();
                dropStacks(p.getWorld(), loc, mat, notAddedCount);
                break;
            }
        }
    }

    // ---------- Троттлинг экшен-бара маршрута в тайник ----------
    private static final Map<java.util.UUID, long[]> ROUTE_THROTTLE = new java.util.HashMap<>();

    private static void notifyRouted(Player p, int amount, org.bukkit.Material mat) {
        long[] st = ROUTE_THROTTLE.computeIfAbsent(p.getUniqueId(),
                k -> new long[]{0L, 0L});
        long now = System.currentTimeMillis();
        st[1] += amount;
        if (now - st[0] >= 800) {
            Msg.action(p, "pouch.routed", "n", Text.num(st[1]),
                    "item", Items.ruName(mat));
            st[0] = now;
            st[1] = 0;
        }
    }

    /** Очистка кэшей игрока (выход с сервера). */
    public static void clearPlayer(java.util.UUID uuid) {
        ROUTE_THROTTLE.remove(uuid);
    }

    private static void dropStacks(org.bukkit.World w, Location loc,
                                   org.bukkit.Material mat, int count) {
        int max = Math.max(1, mat.getMaxStackSize());
        while (count > 0) {
            int stack = Math.min(max, count);
            count -= stack;
            w.dropItemNaturally(loc, new ItemStack(mat, stack));
        }
    }
}
