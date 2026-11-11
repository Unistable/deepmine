package ru.deepprogression.progress;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import ru.deepprogression.model.Branch;
import ru.deepprogression.tools.Items;
import ru.deepprogression.util.Cfg;
import ru.deepprogression.util.Msg;
import ru.deepprogression.util.Text;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Начисление прогрессии, выдача званий-медалей. */
public final class Medals {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Medals() {}

    /** Начислить блоки ветки и проверить звания. Главный поток. */
    public static void progress(org.bukkit.entity.Player p, Branch b, long amount) {
        PlayerProfile prof = ProfileStore.get(p.getUniqueId());
        prof.addBlocks(b, amount);
        Quests.progressMine(p, b, amount);
        long cur = prof.getBlocks(b);
        long[] th = Cfg.thresholds(b);
        int t = prof.getTier(b);
        boolean changed = false;
        while (t < 10 && cur >= th[t]) {
            t++;
            prof.setTier(b, t);
            prof.putDate(b, t, LocalDateTime.now().format(FMT));
            grant(p, b, t, cur);
            changed = true;
        }
        if (changed) ProfileStore.saveAsync(p.getUniqueId());
    }

    private static void grant(org.bukkit.entity.Player p, Branch b, int tier, long count) {
        // Титул
        String subtitle = Text.color(b.color + b.display + " &8· " + b.tierColored(tier));
        p.sendTitle(Msg.plain("medal.title"), subtitle, 10, 70, 20);
        // Звук
        p.playSound(p.getLocation(), "minecraft:ui.toast.challenge_complete", 1.0f, 1.0f);
        // Медаль предметом
        if (Cfg.medalGiveItem()) {
            var leftover = p.getInventory().addItem(Items.medalItem(b, tier));
            if (!leftover.isEmpty()) {
                p.getWorld().dropItemNaturally(p.getLocation(),
                        leftover.values().iterator().next());
            }
        }
        // Сообщение игроку
        Msg.send(p, "medal.chat",
                "branch", Text.color(b.color + b.display),
                "tier", b.tierColored(tier),
                "count", Text.num(count));
        // Общий чат для высоких званий
        int from = Cfg.broadcastFromTier();
        if (from > 0 && tier >= from) {
            Bukkit.broadcastMessage(Msg.fmt("medal.broadcast",
                    "player", p.getName(),
                    "branch", Text.color(b.color + b.display),
                    "tier", b.tierColored(tier)));
        }
    }

    /** Проверка всех веток (на входе в игру) — догоняет пропущенные звания. */
    public static void checkAll(org.bukkit.entity.Player p) {
        for (Branch b : Branch.values()) {
            PlayerProfile prof = ProfileStore.get(p.getUniqueId());
            long cur = prof.getBlocks(b);
            long[] th = Cfg.thresholds(b);
            int t = prof.getTier(b);
            boolean changed = false;
            while (t < 10 && cur >= th[t]) {
                t++;
                prof.setTier(b, t);
                prof.putDate(b, t, LocalDateTime.now().format(FMT));
                grant(p, b, t, cur);
                changed = true;
            }
            if (changed) ProfileStore.saveAsync(p.getUniqueId());
        }
    }

    /** Пере-выдача заработанных медалей ветки. Возвращает количество. */
    public static int reissue(org.bukkit.entity.Player p, Branch b) {
        PlayerProfile prof = ProfileStore.get(p.getUniqueId());
        int earned = prof.getTier(b);
        for (int t = 1; t <= earned; t++) {
            var leftover = p.getInventory().addItem(Items.medalItem(b, t));
            if (!leftover.isEmpty()) {
                p.getWorld().dropItemNaturally(p.getLocation(),
                        leftover.values().iterator().next());
            }
        }
        return earned;
    }
}
