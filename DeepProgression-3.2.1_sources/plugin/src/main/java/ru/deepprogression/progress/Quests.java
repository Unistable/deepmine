package ru.deepprogression.progress;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.deepprogression.DeepProgressionPlugin;
import ru.deepprogression.model.Branch;
import ru.deepprogression.tools.Items;
import ru.deepprogression.tools.Tools;
import ru.deepprogression.util.Cfg;
import ru.deepprogression.util.Msg;
import ru.deepprogression.util.Text;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Ежедневные контракты шахтёра: 3 задания из пула на игрока в день.
 * Прогресс хранится в профиле; награда — инженерные компоненты.
 */
public final class Quests {

    /** type: MINE (target=ветка) | CRAFT (target=id предмета). */
    public record Quest(String id, String type, String target, int amount,
                        String rewardId, int rewardCount) {

        public String name() {
            if ("MINE".equals(type)) {
                return "Добыть " + Text.num(amount) + " " + genitive(target);
            }
            Tools.ToolSpec s = Tools.get(target);
            String what = s != null ? s.name : target;
            return "Скрафтить " + amount + " × «" + what + "»";
        }

        public String rewardName() {
            Tools.ToolSpec s = Tools.get(rewardId);
            return s != null ? s.name : rewardId;
        }
    }

    private static final Map<String, String> GEN = Map.ofEntries(
            // шахтёрская секция
            Map.entry("STONE", "камня"), Map.entry("COAL", "угля"),
            Map.entry("COPPER", "меди"), Map.entry("IRON", "железа"),
            Map.entry("GOLD", "золота"), Map.entry("REDSTONE", "редстоуна"),
            Map.entry("LAPIS", "лазурита"), Map.entry("QUARTZ", "кварца"),
            Map.entry("DIAMOND", "алмазов"), Map.entry("EMERALD", "изумрудов"),
            Map.entry("DEBRIS", "древних обломков"), Map.entry("AMETHYST", "аметиста"),
            // лесоруб
            Map.entry("OAK", "дубовых брёвен"), Map.entry("SPRUCE", "еловых брёвен"),
            Map.entry("BIRCH", "берёзовых брёвен"), Map.entry("JUNGLE", "брёвен джунглей"),
            Map.entry("ACACIA", "акациевых брёвен"), Map.entry("DARK_OAK", "брёвен тёмного дуба"),
            Map.entry("MANGROVE", "мангровых брёвен"), Map.entry("CHERRY", "вишнёвых брёвен"),
            Map.entry("PALE_OAK", "брёвен белого дуба"), Map.entry("RESIN", "смолы"),
            // земледелец
            Map.entry("WHEAT", "пшеницы"), Map.entry("CARROT", "моркови"),
            Map.entry("POTATO", "картофеля"), Map.entry("BEETROOT", "свёклы"),
            Map.entry("MELON", "арбузов"), Map.entry("PUMPKIN", "тыкв"),
            Map.entry("SUGAR_CANE", "тростника"), Map.entry("COCOA", "какао-бобов"),
            Map.entry("NETHER_WART", "адского нароста"), Map.entry("WOOL", "шерсти"),
            Map.entry("HONEY", "мёда"),
            // флорист
            Map.entry("CACTUS", "кактусов"), Map.entry("BAMBOO", "бамбука"),
            Map.entry("FLOWERS", "цветов"), Map.entry("MOSS", "мха"),
            Map.entry("AZALEA", "азалий"), Map.entry("MUSHROOMS", "грибов"),
            // рыболов
            Map.entry("KELP", "водорослей"), Map.entry("CORAL", "кораллов"),
            Map.entry("SPONGE", "губок"), Map.entry("PRISMARINE", "призмарина"),
            // каменщик
            Map.entry("SAND", "песка"), Map.entry("GRAVEL", "гравия"),
            Map.entry("SANDSTONE", "песчаника"), Map.entry("SNOW_ICE", "льда"),
            Map.entry("GLASS", "стекла"), Map.entry("BRICKS", "кладки"),
            Map.entry("TERRACOTTA", "терракоты"), Map.entry("CONCRETE", "бетона"),
            // световод
            Map.entry("GLOWSTONE", "светокамня"), Map.entry("SEA_LANTERN", "морских фонарей"),
            Map.entry("SHROOMLIGHT", "светогриба"), Map.entry("FROG_LIGHT", "жабьего света"),
            Map.entry("CANDLE", "свечей"), Map.entry("GLOW_FLORA", "светящихся растений"),
            // инженер
            Map.entry("MECHANISMS", "механизмов"), Map.entry("COPPER_TECH", "меднотеха"),
            Map.entry("RAILS", "рельсов"),
            // незер
            Map.entry("NETHERRACK", "незеррака"), Map.entry("SOUL", "песка душ"),
            Map.entry("MAGMA", "магмы"), Map.entry("BLACKSTONE", "чёрного камня"),
            Map.entry("BASALT", "базальта"), Map.entry("CRIMSON", "багрового дерева"),
            Map.entry("WARPED", "искривлённого дерева"), Map.entry("WART", "бородавочных блоков"),
            // край
            Map.entry("END_STONE", "энд-камня"), Map.entry("PURPUR", "пурпура"),
            Map.entry("CHORUS", "хоруса"), Map.entry("OBSIDIAN", "обсидиана"),
            // аномалии
            Map.entry("SCULK", "скулка"));

    private static final List<Quest> POOL = List.of(
            // шахтёрская секция
            new Quest("q_mine_coal", "MINE", "COAL", 256, "board_basic", 2),
            new Quest("q_mine_iron", "MINE", "IRON", 128, "steel_plate", 4),
            new Quest("q_mine_copper", "MINE", "COPPER", 128, "copper_coil", 2),
            new Quest("q_mine_diamond", "MINE", "DIAMOND", 24, "board_advanced", 1),
            new Quest("q_mine_stone", "MINE", "STONE", 1024, "steel_ingot", 4),
            new Quest("q_mine_redstone", "MINE", "REDSTONE", 256, "redstone_coil", 2),
            new Quest("q_mine_gold", "MINE", "GOLD", 64, "quartz_chip", 2),
            new Quest("q_mine_lapis", "MINE", "LAPIS", 128, "battery", 1),
            new Quest("q_mine_emerald", "MINE", "EMERALD", 12, "scan_module", 1),
            new Quest("q_mine_quartz", "MINE", "QUARTZ", 128, "copper_wire", 8),
            new Quest("q_mine_amethyst", "MINE", "AMETHYST", 64, "scan_module", 1),
            new Quest("q_mine_debris", "MINE", "DEBRIS", 8, "netherite_core", 1),
            // лесоруб
            new Quest("q_mine_oak", "MINE", "OAK", 256, "saw_blade", 1),
            new Quest("q_mine_spruce", "MINE", "SPRUCE", 256, "steel_plate", 4),
            new Quest("q_mine_cherry", "MINE", "CHERRY", 128, "board_basic", 3),
            // земледелец
            new Quest("q_mine_wheat", "MINE", "WHEAT", 256, "bio_module", 1),
            new Quest("q_mine_carrot", "MINE", "CARROT", 128, "copper_wire", 8),
            new Quest("q_mine_wool", "MINE", "WOOL", 64, "copper_coil", 3),
            // рыболов и флорист
            new Quest("q_mine_kelp", "MINE", "KELP", 512, "salt_filter", 1),
            new Quest("q_mine_cactus", "MINE", "CACTUS", 128, "steel_ingot", 4),
            // каменщик и световод
            new Quest("q_mine_sand", "MINE", "SAND", 512, "steel_plate", 4),
            new Quest("q_mine_glowstone", "MINE", "GLOWSTONE", 64, "lumin_core", 1),
            // инженер, незер, край, аномалии
            new Quest("q_mine_mechanisms", "MINE", "MECHANISMS", 32, "servo_drive", 1),
            new Quest("q_mine_magma", "MINE", "MAGMA", 128, "flame_relic", 1),
            new Quest("q_mine_chorus", "MINE", "CHORUS", 128, "chorus_lens", 1),
            new Quest("q_mine_sculk", "MINE", "SCULK", 32, "board_elite", 1),
            // крафты
            new Quest("q_craft_board", "CRAFT", "board_basic", 4, "thermal_core", 1),
            new Quest("q_craft_coil", "CRAFT", "copper_coil", 6, "steel_plate", 4),
            new Quest("q_craft_tool_oak", "CRAFT", "tool_oak_1", 1, "steel_plate", 4),
            new Quest("q_craft_tool_wheat", "CRAFT", "tool_wheat_1", 1, "copper_coil", 4),
            // v3.2: приспособления секций
            new Quest("q_craft_pruner", "CRAFT", "pruner", 1, "steel_plate", 4),
            new Quest("q_craft_air_tank", "CRAFT", "air_tank", 1, "scan_module", 1));

    private static DeepProgressionPlugin plugin;

    private Quests() {}

    public static void init(DeepProgressionPlugin pl) {
        plugin = pl;
    }

    public static String genitive(String branch) {
        return GEN.getOrDefault(branch, branch.toLowerCase(java.util.Locale.ROOT));
    }

    public static List<Quest> dailyFor(UUID uuid) {
        long seed = LocalDate.now().toEpochDay() * 1_000_003L + uuid.hashCode();
        Random r = new Random(seed);
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < POOL.size(); i++) idx.add(i);
        java.util.Collections.shuffle(idx, r);
        List<Quest> out = new ArrayList<>();
        int n = Math.max(1, Cfg.questsPerDay());
        for (int i = 0; i < n && i < idx.size(); i++) out.add(POOL.get(idx.get(i)));
        return out;
    }

    /** Гарантировать, что в профиле есть задания на сегодня. */
    public static void ensureToday(PlayerProfile prof, UUID uuid) {
        String today = LocalDate.now().toString();
        if (today.equals(prof.getQuestDay())) return;
        prof.setQuestDay(today);
        prof.clearQuests();
        for (Quest q : dailyFor(uuid)) {
            prof.setQuest(q.id(), 0, 0);
        }
    }

    /** Начисление добычи (вызывается из Medals.progress). Главный поток. */
    public static void progressMine(Player p, Branch b, long amount) {
        if (p == null || amount <= 0) return;
        PlayerProfile prof = ProfileStore.get(p.getUniqueId());
        ensureToday(prof, p.getUniqueId());
        String today = prof.getQuestDay();
        if (!LocalDate.now().toString().equals(today)) return;
        for (Quest q : POOL) {
            if (!"MINE".equals(q.type()) || !q.target().equals(b.name())) continue;
            int[] st = prof.questState(q.id());
            if (st == null || st[1] == 1) continue;
            st[0] = (int) Math.min(q.amount(), st[0] + amount);
            prof.setQuest(q.id(), st[0], st[1]);
            if (st[0] >= q.amount()) complete(p, prof, q);
        }
    }

    /** Крафт наших предметов (вызывается из Recipes.handleCraftClick). */
    public static void onCraft(Player p, String itemId) {
        if (p == null || itemId == null) return;
        PlayerProfile prof = ProfileStore.get(p.getUniqueId());
        ensureToday(prof, p.getUniqueId());
        for (Quest q : POOL) {
            if (!"CRAFT".equals(q.type()) || !q.target().equals(itemId)) continue;
            int[] st = prof.questState(q.id());
            if (st == null || st[1] == 1) continue;
            st[0] = Math.min(q.amount(), st[0] + 1);
            prof.setQuest(q.id(), st[0], st[1]);
            if (st[0] >= q.amount()) complete(p, prof, q);
        }
    }

    private static void complete(Player p, PlayerProfile prof, Quest q) {
        int[] st = prof.questState(q.id());
        if (st != null) prof.setQuest(q.id(), st[0], 1);
        ProfileStore.saveAsync(p.getUniqueId());

        org.bukkit.inventory.ItemStack reward = Items.build(q.rewardId());
        if (reward == null) return;
        // Экономика v3.1: дефицитный материал → щедрее, перепроизводство → скромнее
        int qty = (int) Math.max(1, Math.round(q.rewardCount() * rewardFactorOf(q)));
        reward.setAmount(qty);
        var leftover = p.getInventory().addItem(reward);
        if (!leftover.isEmpty()) {
            p.getWorld().dropItemNaturally(p.getLocation(), leftover.values().iterator().next());
        }
        Msg.send(p, "quest.done",
                "quest", q.name(), "reward", q.rewardName() + " ×" + qty);
        p.playSound(p.getLocation(), "minecraft:ui.toast.challenge_complete", 1.0f, 1.2f);
    }

    /** Множитель награды по индексу экономики связанной ветки. */
    private static double rewardFactorOf(Quest q) {
        if ("MINE".equals(q.type())) {
            Branch b = Branch.byId(q.target());
            return ru.deepprogression.economy.Economy.rewardFactor(b);
        }
        Tools.ToolSpec s = Tools.get(q.target());
        if (s != null) {
            if (s.branch != null) return ru.deepprogression.economy.Economy.rewardFactor(s.branch);
            if (s.section != null) {
                return ru.deepprogression.economy.Economy.rewardFactor(
                        ru.deepprogression.recipe.Recipes.flagshipOf(s.section));
            }
        }
        return 1.0;
    }

    /** /dm quests — отчёт в чат. */
    public static void sendList(Player p) {
        PlayerProfile prof = ProfileStore.get(p.getUniqueId());
        ensureToday(prof, p.getUniqueId());
        Msg.send(p, "quest.header");
        for (Quest q : dailyFor(p.getUniqueId())) {
            int[] st = prof.questState(q.id());
            int prog = st == null ? 0 : st[0];
            boolean done = st != null && st[1] == 1;
            String line = Msg.plain(done ? "quest.line-done" : "quest.line",
                    "quest", q.name(),
                    "count", Text.num(prog) + "/" + Text.num(q.amount()),
                    "bar", Text.bar(prog, q.amount(), 10),
                    "reward", q.rewardName() + " ×" + q.rewardCount());
            p.sendMessage(line);
        }
    }
}
