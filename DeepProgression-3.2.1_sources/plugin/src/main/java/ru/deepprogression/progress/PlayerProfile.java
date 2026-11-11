package ru.deepprogression.progress;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.deepprogression.model.Branch;
import ru.deepprogression.util.Cfg;

import java.io.File;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Профиль игрока: добытые блоки по веткам, звания, даты. Потокобезопасность: синхронизация на объекте. */
public final class PlayerProfile {

    private final Map<Branch, Long> blocks = new EnumMap<>(Branch.class);
    private final Map<Branch, Integer> tier = new EnumMap<>(Branch.class);
    private final Map<String, String> dates = new HashMap<>();
    private final Map<String, String> quests = new LinkedHashMap<>(); // id -> "prog;claimed"
    private String questDay = "";
    private String lastName = "";
    private final java.util.Set<String> perkOff = new java.util.HashSet<>(); // выключенные пассивные перки
    private final Map<String, Long> activeCd = new HashMap<>(); // кристалл -> epoch мс кулдауна

    public synchronized long getBlocks(Branch b) {
        return blocks.getOrDefault(b, 0L);
    }

    public synchronized void addBlocks(Branch b, long amount) {
        if (amount <= 0) return;
        blocks.merge(b, amount, Long::sum);
    }

    public synchronized int getTier(Branch b) {
        return tier.getOrDefault(b, 0);
    }

    public synchronized void setTier(Branch b, int t) {
        tier.put(b, t);
    }

    public synchronized void putDate(Branch b, int t, String date) {
        dates.put(b.name() + ":" + t, date);
    }

    public synchronized String getDate(Branch b, int t) {
        return dates.get(b.name() + ":" + t);
    }

    public synchronized double score() {
        double s = 0;
        for (Map.Entry<Branch, Long> e : blocks.entrySet()) {
            s += e.getValue() * Cfg.weight(e.getKey());
        }
        return s;
    }

    // ---------- Контракты v2.0 ----------

    public synchronized String getQuestDay() { return questDay; }

    public synchronized void setQuestDay(String day) { questDay = day == null ? "" : day; }

    /** [прогресс, выдана]; null — задания нет. */
    public synchronized int[] questState(String id) {
        String v = quests.get(id);
        if (v == null || !v.contains(";")) return null;
        try {
            int i = v.indexOf(';');
            return new int[]{Integer.parseInt(v.substring(0, i)),
                    Integer.parseInt(v.substring(i + 1))};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public synchronized void setQuest(String id, int progress, int claimed) {
        quests.put(id, progress + ";" + claimed);
    }

    public synchronized void clearQuests() { quests.clear(); }

    public synchronized void setQuestsFrom(PlayerProfile src) {
        quests.clear();
        quests.putAll(src.quests);
        questDay = src.questDay;
    }

    public synchronized long totalBlocks() {
        long t = 0;
        for (long v : blocks.values()) t += v;
        return t;
    }

    // ---------- Перки v3.1 ----------

    public synchronized boolean isPerkOff(String perkId) {
        return perkOff.contains(perkId);
    }

    public synchronized void togglePerk(String perkId) {
        if (!perkOff.remove(perkId)) perkOff.add(perkId);
    }

    public synchronized int perksOffCount() {
        return perkOff.size();
    }

    public synchronized long activeCooldown(String crystalId) {
        return activeCd.getOrDefault(crystalId, 0L);
    }

    public synchronized void setActiveCooldown(String crystalId, long epochMs) {
        activeCd.put(crystalId, epochMs);
    }

    /** Чистка устаревших кулдаунов (старше 24ч). */
    public synchronized void trimActiveCooldowns(long now) {
        activeCd.values().removeIf(t -> now - t > 86_400_000L);
    }

    public void setLastName(String name) {
        if (name != null) this.lastName = name;
    }

    public String getLastName() {
        return lastName;
    }

    public synchronized void serialize(YamlConfiguration y) {
        for (Map.Entry<Branch, Long> e : blocks.entrySet()) {
            y.set("blocks." + e.getKey().name(), e.getValue());
        }
        for (Map.Entry<Branch, Integer> e : tier.entrySet()) {
            y.set("tier." + e.getKey().name(), e.getValue());
        }
        for (Map.Entry<String, String> e : dates.entrySet()) {
            y.set("medals." + e.getKey(), e.getValue());
        }
        y.set("name", lastName);
        y.set("quests.day", questDay);
        for (Map.Entry<String, String> e : quests.entrySet()) {
            y.set("quests.items." + e.getKey(), e.getValue());
        }
        for (String id : perkOff) y.set("perks.off." + id, true);
        for (Map.Entry<String, Long> e : activeCd.entrySet()) {
            y.set("actives." + e.getKey(), e.getValue());
        }
    }

    public static PlayerProfile deserialize(YamlConfiguration y) {
        PlayerProfile p = new PlayerProfile();
        p.lastName = y.getString("name", "");
        ConfigurationSection b = y.getConfigurationSection("blocks");
        if (b != null) {
            for (String k : b.getKeys(false)) {
                Branch br = Branch.byId(k);
                if (br != null) p.blocks.put(br, b.getLong(k));
            }
        }
        ConfigurationSection t = y.getConfigurationSection("tier");
        if (t != null) {
            for (String k : t.getKeys(false)) {
                Branch br = Branch.byId(k);
                if (br != null) p.tier.put(br, (int) Math.min(10, Math.max(0, t.getInt(k))));
            }
        }
        ConfigurationSection d = y.getConfigurationSection("medals");
        if (d != null) {
            for (String k : d.getKeys(false)) p.dates.put(k, d.getString(k, ""));
        }
        p.questDay = y.getString("quests.day", "");
        ConfigurationSection q = y.getConfigurationSection("quests.items");
        if (q != null) {
            for (String k : q.getKeys(false)) p.quests.put(k, q.getString(k, "0;0"));
        }
        ConfigurationSection po = y.getConfigurationSection("perks.off");
        if (po != null) {
            for (String k : po.getKeys(false)) p.perkOff.add(k);
        }
        ConfigurationSection ac = y.getConfigurationSection("actives");
        if (ac != null) {
            for (String k : ac.getKeys(false)) p.activeCd.put(k, ac.getLong(k, 0L));
        }
        return p;
    }

    public synchronized PlayerProfile snapshot() {
        PlayerProfile c = new PlayerProfile();
        c.blocks.putAll(this.blocks);
        c.tier.putAll(this.tier);
        c.dates.putAll(this.dates);
        c.quests.putAll(this.quests);
        c.questDay = this.questDay;
        c.lastName = this.lastName;
        c.perkOff.addAll(this.perkOff);
        c.activeCd.putAll(this.activeCd);
        return c;
    }
}
