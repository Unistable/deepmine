package ru.deepmagic.synergy;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import ru.deepmagic.DeepMagicPlugin;
import ru.deepmagic.model.EternalBurn;
import ru.deepmagic.model.Magic;
import ru.deepmagic.model.State;
import ru.deepmagic.util.Conf;
import ru.deepmagic.util.T;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Движок синергий: матрица «состояние × стихия», флаги игроков (панцири, ореолы),
 * зоны (мох, обелиск, ритуал), дождь, медные якоря, угольная пыль.
 * Реализует законы A–E дизайн-документа. Все PDC-флаги — ASCII.
 */
public final class Synergy {

    private Synergy() {}

    private static final Map<String, org.bukkit.NamespacedKey> KEY_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static org.bukkit.NamespacedKey key(String k) {
        return KEY_CACHE.computeIfAbsent(k,
                x -> new org.bukkit.NamespacedKey(DeepMagicPlugin.get(), "dm_" + x));
    }

    private static long getLong(LivingEntity e, String k, long def) {
        Long v = e.getPersistentDataContainer().get(key(k), PersistentDataType.LONG);
        return v == null ? def : v;
    }

    private static void setLong(LivingEntity e, String k, long v) {
        e.getPersistentDataContainer().set(key(k), PersistentDataType.LONG, v);
    }

    private static int getInt(LivingEntity e, String k, int def) {
        Integer v = e.getPersistentDataContainer().get(key(k), PersistentDataType.INTEGER);
        return v == null ? def : v;
    }

    private static void setInt(LivingEntity e, String k, int v) {
        e.getPersistentDataContainer().set(key(k), PersistentDataType.INTEGER, v);
    }

    private static boolean flagActive(LivingEntity e, String k) {
        return getLong(e, k, 0L) > System.currentTimeMillis();
    }

    // ---------- флаги, ставимые механиками ----------

    public static void addShell(Player p, int pts, int seconds) {
        setInt(p, "shell_pts", pts);
        setLong(p, "shell_until", System.currentTimeMillis() + seconds * 1000L);
    }

    public static void addBark(Player p, int pts, int seconds) {
        setInt(p, "bark_pts", pts);
        setLong(p, "bark_until", System.currentTimeMillis() + seconds * 1000L);
    }

    public static void addThorns(Player p, int seconds, double dmg) {
        setLong(p, "thorns_until", System.currentTimeMillis() + seconds * 1000L);
        setInt(p, "thorns_dmg", (int) dmg);
    }

    public static void addAura(Player p, int seconds, int radius) {
        setLong(p, "aura_until", System.currentTimeMillis() + seconds * 1000L);
    }

    public static boolean auraActive(Player p) {
        return flagActive(p, "aura_until");
    }

    public static void addVitaminShield(Player p) {
        if (getLong(p, "vitamin_cd", 0L) == 0L) setLong(p, "vitamin_cd", 1L);
    }

    public static void glassInvis(Player p, int seconds) {
        setLong(p, "glassinvis_until", System.currentTimeMillis() + seconds * 1000L);
    }

    /**
     * Единый смыв стеклянного камуфляжа (лор посоха: «только водой, бетоном или светом»).
     * reason — человеческая причина: «влага», «бетонный саркофаг», «свет».
     * Возвращает true, если камуфляж был активен и разбит.
     */
    public static boolean dispelGlassInvis(LivingEntity e, String reason) {
        if (e == null || e.isDead()) return false;
        if (!flagActive(e, "glassinvis_until")) return false;
        setLong(e, "glassinvis_until", 0L);
        e.removePotionEffect(PotionEffectType.INVISIBILITY);
        ru.deepmagic.fx.Spectacle.shatterFX(e, false);
        if (e instanceof Player gp) {
            gp.sendActionBar(T.color("&fСтеклянный камуфляж разбит &7(" + reason + ")&f!"));
        }
        return true;
    }

    public static void terracottaMask(Player p, int seconds) {
        setLong(p, "mask_until", System.currentTimeMillis() + seconds * 1000L);
    }

    public static void rage(Player p, int seconds) {
        setLong(p, "rage_until", System.currentTimeMillis() + seconds * 1000L);
        p.sendActionBar(T.color("&cБАГРОВОЕ БЕШЕНСТВО: посохи и жемчуг недоступны!"));
    }

    public static void obsidianSkin(Player p, int seconds) {
        setLong(p, "obsidian_until", System.currentTimeMillis() + seconds * 1000L);
    }

    public static void silence(LivingEntity e, int seconds) {
        setLong(e, "silence_until", System.currentTimeMillis() + seconds * 1000L);
        if (e instanceof Mob mob) mob.setTarget(null);
    }

    public static boolean silenced(LivingEntity e) {
        return flagActive(e, "silence_until");
    }

    public static boolean masked(Player p) {
        return flagActive(p, "mask_until");
    }

    public static boolean raging(Player p) {
        return flagActive(p, "rage_until");
    }

    public static void markContagious(LivingEntity e, int seconds) {
        setLong(e, "contagion_until", System.currentTimeMillis() + seconds * 1000L);
    }

    public static boolean contagious(LivingEntity e) {
        return flagActive(e, "contagion_until");
    }

    public static void tempGlow(LivingEntity e, int seconds) {
        TEMP_GLOW.put(e.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    // ==================== зоны (память + файл-персистент) ====================

    public record Zone(String world, int x, int y, int z, int radius, long until,
                       java.util.UUID caster) {}

    private static final List<Zone> MOSS_ZONES = new ArrayList<>();
    private static final List<Zone> OBELISK_ZONES = new ArrayList<>();
    private static final List<Zone> RITUAL_ZONES = new ArrayList<>();
    private static final List<Zone> HONEY_ZONES = new ArrayList<>();
    private static final Map<String, Long> RAIN = new HashMap<>();
    private static final Map<UUID, Long> RAIN_CASTERS = new HashMap<>();
    private static final Set<String> ANCHORS = new HashSet<>();
    private static final Map<UUID, Long> TEMP_GLOW = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> DUST_EMITTERS = new ConcurrentHashMap<>();

    public static void addMossZone(Location c, int seconds, int radius) {
        MOSS_ZONES.add(new Zone(c.getWorld().getName(), c.getBlockX(), c.getBlockY(),
                c.getBlockZ(), radius, System.currentTimeMillis() + seconds * 1000L, null));
    }

    public static void addObeliskZone(Location c, int seconds, int radius) {
        OBELISK_ZONES.add(new Zone(c.getWorld().getName(), c.getBlockX(), c.getBlockY(),
                c.getBlockZ(), radius, System.currentTimeMillis() + seconds * 1000L, null));
    }

    public static void addRitual(Location c, int seconds, int radius) {
        RITUAL_ZONES.add(new Zone(c.getWorld().getName(), c.getBlockX(), c.getBlockY(),
                c.getBlockZ(), radius, System.currentTimeMillis() + seconds * 1000L, null));
    }

    /** Медовая лужа: зона-ловушка с кастером (PvP-проверка по кастеру каждый тик). */
    public static void addHoneyZone(Location c, int seconds, int radius, java.util.UUID caster) {
        HONEY_ZONES.add(new Zone(c.getWorld().getName(), c.getBlockX(), c.getBlockY(),
                c.getBlockZ(), radius, System.currentTimeMillis() + seconds * 1000L, caster));
    }

    public static boolean inMossZone(Location l) {
        return inZone(MOSS_ZONES, l);
    }

    public static boolean inHoneyZone(Location l) {
        return inZone(HONEY_ZONES, l);
    }

    public static boolean inObeliskZone(Location l) {
        return inZone(OBELISK_ZONES, l);
    }

    public static boolean inRitual(Location l) {
        return inZone(RITUAL_ZONES, l);
    }

    private static boolean inZone(List<Zone> zones, Location l) {
        for (Zone z : zones) {
            if (!z.world().equals(l.getWorld().getName())) continue;
            int dx = z.x() - l.getBlockX(), dy = z.y() - l.getBlockY(), dz = z.z() - l.getBlockZ();
            if (dx * dx + dy * dy + dz * dz <= (double) z.radius() * z.radius()) return true;
        }
        return false;
    }

    public static void summonRain(Player p, int seconds) {
        p.getWorld().setStorm(true);
        RAIN.put(p.getWorld().getName(), System.currentTimeMillis() + seconds * 1000L);
        RAIN_CASTERS.put(p.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    public static void registerAnchor(Block b) {
        ANCHORS.add(b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ());
    }

    public static void removeAnchor(Block b) {
        ANCHORS.remove(b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ());
    }

    public static boolean nearAnchor(Location l, int radius) {
        for (String a : ANCHORS) {
            String[] parts = a.split(":");
            if (!parts[0].equals(l.getWorld().getName())) continue;
            try {
                int ax = Integer.parseInt(parts[1]), ay = Integer.parseInt(parts[2]),
                        az = Integer.parseInt(parts[3]);
                int dx = ax - l.getBlockX(), dy = ay - l.getBlockY(), dz = az - l.getBlockZ();
                if (dx * dx + dy * dy + dz * dz <= (double) radius * radius) return true;
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    // ==================== угольная пыль ====================

    public static void startDust(Player p, int seconds) {
        DUST_EMITTERS.put(p.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    // ==================== PvP-доступ ====================

    public static boolean pvpAllowed(Player attacker, LivingEntity target) {
        if (!(target instanceof Player victim)) return true;
        if (victim == attacker) return false;
        if (victim.getGameMode() == GameMode.CREATIVE
                || victim.getGameMode() == GameMode.SPECTATOR) return false;
        if (!Conf.pvpEnabled()) return false;
        return !spawnSafe(attacker.getLocation()) && !spawnSafe(victim.getLocation());
    }

    public static boolean spawnSafe(Location l) {
        int r = Conf.spawnSafeRadius();
        if (r <= 0) return false;
        Location spawn = l.getWorld().getSpawnLocation();
        return spawn.distanceSquared(l) <= (double) r * r;
    }

    // ==================== матрица: урон посоха ====================

    public static void onStaffHit(Player attacker, LivingEntity victim, double dmg,
                                  Magic.Element element) {
        if (victim == null || victim.isDead()) return;
        if (!pvpAllowed(attacker, victim)) return;
        boolean physical = element == Magic.Element.EARTH || element == Magic.Element.TECH;

        // --- Закон A: реагент / Закон B: усиление ---
        if (State.BRITTLE.has(victim)) {
            dmg *= 2;
            State.BRITTLE.remove(victim);
            // Хрупкость разбивается: осколки-визуал + ясный фидбек (интуитивность)
            ru.deepmagic.fx.Spectacle.shatterFX(victim, false);
            if (attacker != null) {
                attacker.sendActionBar(T.color("&fХРУПКОСТЬ: удар ×2!"));
            }
        }
        if (State.ARCANE_MARK.has(victim)) {
            dmg += 3;
        }
        if (State.GILDED.has(victim)) {
            dmg *= 1.5; // жадность делает мишенью
        }
        if (element == Magic.Element.LIGHTNING
                && (State.CHARGED.has(victim) || State.GILDED.has(victim))) {
            dmg *= 2;
            victim.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                    victim.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.1);
        }
        if (element == Magic.Element.LIGHTNING && State.SOAKED.has(victim)) {
            chainShock(victim, attacker);
        }
        if (element == Magic.Element.ICE && State.SOAKED.has(victim)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 6 * 20, 4));
            State.FROSTED.set(victim, 8);
            victim.getWorld().spawnParticle(Particle.SNOWFLAKE,
                    victim.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.02);
        }
        if (physical && State.FROSTED.has(victim)) {
            dmg *= 1.5;
            victim.getWorld().spawnParticle(Particle.ITEM_SNOWBALL,
                    victim.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.1);
            victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 1.4f);
        }
        if (element == Magic.Element.FIRE && State.SOAKED.has(victim)) {
            // Закон C: влага гасит магический огонь (пар вместо пламени).
            // Ветка ДО угольной — мокрая угольная цель НЕ вспыхивает вечно.
            State.SOAKED.remove(victim);
            victim.getWorld().spawnParticle(Particle.SMOKE,
                    victim.getLocation().add(0, 1, 0), 25, 0.3, 0.4, 0.3, 0.02);
            victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.7f, 1f);
            victim.damage(dmg * 0.5, attacker); // половинный урон паром, без поджога
            return;
        }
        if (element == Magic.Element.FIRE && State.CARBON_COATED.has(victim)
                && !State.SOAKED.has(victim) && !EternalBurn.burning(victim)) {
            // ЗАКОН ТЗ: любой магический огонь по угольному покрытию = ВЕЧНОЕ горение до воды
            EternalBurn.ignite(victim);
            victim.getWorld().spawnParticle(Particle.FLAME,
                    victim.getLocation().add(0, 1, 0), 50, 0.4, 0.8, 0.4, 0.05);
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.6f);
        }
        if (element == Magic.Element.FIRE && State.FEAR.has(victim)) {
            // Паника: страх + огонь = паническое бегство
            State.FEAR.set(victim, 4);
            if (victim instanceof Mob mob) mob.setTarget(null);
            dmg += 2;
        }
        if (element == Magic.Element.LIGHTNING && State.FROSTED.has(victim)) {
            // Шок-заморозка: разряд промораживает насквозь → хрупкость
            State.FROSTED.remove(victim);
            State.BRITTLE.set(victim, 6);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 5 * 20, 3));
            victim.getWorld().spawnParticle(Particle.SNOWFLAKE,
                    victim.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.02);
            victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_GLASS_PLACE, 1f, 0.5f);
        }
        if (element == Magic.Element.WATER && State.CHARGED.has(victim)) {
            // Вода проводит: заряд уходит цепным разрядом
            State.CHARGED.remove(victim);
            dmg *= 1.5;
            chainShock(victim, attacker);
        }
        if (element == Magic.Element.ICE && State.STICKY.has(victim)) {
            // Смола замерзает и треснет (лор смоляного капкана)
            State.STICKY.remove(victim);
            State.BRITTLE.set(victim, 6);
            victim.getWorld().spawnParticle(Particle.SNOWFLAKE,
                    victim.getLocation().add(0, 0.5, 0), 20, 0.3, 0.3, 0.3, 0.02);
        }
        if (element == Magic.Element.FIRE && State.SPORED.has(victim)) {
            dmg += 8;
            State.SPORED.remove(victim);
            victim.getWorld().spawnParticle(Particle.FLASH, victim.getLocation(), 1);
        }
        if (element == Magic.Element.FIRE && State.STICKY.has(victim)) {
            victim.setFireTicks(Math.max(victim.getFireTicks(), 15 * 20));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10 * 20, 0));
        }
        if (element == Magic.Element.FIRE && State.ROOTED.has(victim)) {
            State.ROOTED.remove(victim);
            dmg += 4;
        }
        if (element == Magic.Element.WATER) {
            // Закон C: вода гасит (в т.ч. вечное горение)
            EternalBurn.quench(victim);
            State.CARBON_COATED.remove(victim);
            victim.setFireTicks(0);
        }
        if (element == Magic.Element.LIGHT) {
            State.SOUL_MARKED.remove(victim);
            State.NECROSIS.remove(victim);
        }
        if (element == Magic.Element.DARK && State.SOUL_MARKED.has(victim)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10 * 20, 1));
        }
        if (element == Magic.Element.ECHO && State.ECHO_TAGGED.has(victim)) {
            dmg += 6;
        }
        // GLASSED намеренно НЕ обрабатывается здесь: шаттер работает через
        // onPhysicalHit (ниже) на ЛЮБОЙ физический удар — мил, посох, снаряд.

        victim.damage(dmg, attacker);
        // Стихийный акцент удара посоха (зрелищность матрицы)
        ru.deepmagic.fx.SpellFx.matrixBoost(victim, element);
    }

    // ==================== физический удар по цели (GLASSED/BRITTLE) ====================

    /**
     * Единый обработчик ЛЮБОГО удара сущностью (мил, посох, снаряд).
     * Вызывается из MagicListener на EntityDamageByEntityEvent (HIGH).
     *
     * ОСТЕКЛЁН (GLASSED): цель разбивается — осколки разлетаются (Spectacle.shatterFX),
     * удару +3 урона, осколки бьют всех рядом на 3. Состояние ТРАТИТСЯ — интуитивно:
     * наложил песок → бей → видишь взрыв осколков.
     * ХРУПКИЙ (BRITTLE): мили/снаряд ×2 (посохи ×2 уже в onStaffHit — их пропускаем).
     */
    public static void onPhysicalHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim) || victim.isDead()) return;
        if (!(e.getDamageSource().getCausingEntity() instanceof LivingEntity damager)) return;

        boolean staffStrike = damager instanceof Player dp
                && dp.getEquipment() != null
                && ru.deepmagic.model.ItemDefs.isStaff(
                        dp.getEquipment().getItemInMainHand());

        // ХРУПКИЙ: мили и снаряды ломают ×2 (у посохов ×2 уже учтён в onStaffHit)
        if (State.BRITTLE.has(victim) && !staffStrike) {
            e.setDamage(e.getDamage() * 2);
            State.BRITTLE.remove(victim);
            ru.deepmagic.fx.Spectacle.shatterFX(victim, false);
            if (damager instanceof Player dp2) {
                dp2.sendActionBar(T.color("&fХРУПКОСТЬ: удар ×2!"));
            }
        }

        // ОСТЕКЛЁН: ЛЮБОЙ физический удар разбивает корку стекла
        if (State.GLASSED.has(victim)) {
            State.GLASSED.remove(victim); // тратится — понятно и честно
            e.setDamage(e.getDamage() + 3.0);
            ru.deepmagic.fx.Spectacle.shatterFX(victim, true);
            // осколки бьют всех рядом (цепное разбитие: второй остеклённый тоже лопнет)
            for (LivingEntity other : nearby(victim.getLocation(), 2.5)) {
                if (other == victim || other == damager) continue;
                if (damager instanceof Player pa && !pvpAllowed(pa, other)) continue;
                other.damage(3.0, damager);
            }
            if (damager instanceof Player pa) {
                pa.sendActionBar(T.color("&f&lОСКОЛКИ!&f Остеклённая цель разбита: +3 урона, осколки бьют соседей"));
            }
            if (victim instanceof Player vp) {
                vp.sendActionBar(T.color("&fВЫ РАЗБИЛИСЬ НА ОСКОЛКИ!"));
            }
        }
    }

    private static void chainShock(LivingEntity source, Player attacker) {
        int shocked = 0;
        for (LivingEntity le : nearby(source.getLocation(), 6)) {
            if (le == source || le == attacker) continue;
            if (le instanceof Player && !pvpAllowed(attacker, le)) continue;
            if (!State.SOAKED.has(le) && !State.CHARGED.has(le)) continue;
            le.damage(6, attacker);
            le.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                    le.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.1);
            shocked++;
        }
        if (shocked > 0) {
            source.getWorld().playSound(source.getLocation(),
                    Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.4f, 1.8f);
        }
    }

    private static List<LivingEntity> nearby(Location c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : c.getWorld().getNearbyEntities(c, r, r, r)) {
            if (e instanceof LivingEntity le && !le.isDead()) out.add(le);
        }
        return out;
    }

    // ==================== секундный тик ====================

    /** Разметка зон мягкими кольцами частиц (вызывается раз в секунду). */
    private static void fxZones(List<Zone> zones, org.bukkit.Color color) {
        for (Zone z : zones) {
            org.bukkit.World w = Bukkit.getWorld(z.world());
            if (w == null) continue;
            ru.deepmagic.fx.Spectacle.zoneRing(
                    new Location(w, z.x() + 0.5, z.y(), z.z() + 0.5),
                    Math.max(2.5, z.radius() + 0.5), color);
        }
    }

    public static void tickSecondAll() {
        long now = System.currentTimeMillis();

        // чистка истёкших зон
        MOSS_ZONES.removeIf(z -> z.until() < now);
        OBELISK_ZONES.removeIf(z -> z.until() < now);
        RITUAL_ZONES.removeIf(z -> z.until() < now);
        HONEY_ZONES.removeIf(z -> z.until() < now);
        // зрелищная разметка активных зон (мягкие кольца)
        fxZones(MOSS_ZONES, org.bukkit.Color.fromRGB(0x587B3A));
        fxZones(HONEY_ZONES, org.bukkit.Color.fromRGB(0xE8A33D));
        fxZones(RITUAL_ZONES, org.bukkit.Color.fromRGB(0xFFA53C));
        fxZones(OBELISK_ZONES, org.bukkit.Color.fromRGB(0x9B59D0));
        // дождь: снимаем истёкший и выключаем шторм (он был вызван магией)
        Iterator<Map.Entry<String, Long>> rainIt = RAIN.entrySet().iterator();
        while (rainIt.hasNext()) {
            Map.Entry<String, Long> rainEntry = rainIt.next();
            if (rainEntry.getValue() < now) {
                rainIt.remove();
                org.bukkit.World rw = Bukkit.getWorld(rainEntry.getKey());
                if (rw != null && rw.hasStorm()) rw.setStorm(false);
            }
        }
        RAIN_CASTERS.entrySet().removeIf(e2 -> e2.getValue() < now);

        for (org.bukkit.World w : Bukkit.getWorlds()) {
            List<LivingEntity> marked = w.hasStorm() && RAIN.containsKey(w.getName())
                    && Math.random() < 0.08 ? new ArrayList<>() : null;
            for (Entity e : w.getEntities()) {
                if (!(e instanceof LivingEntity le) || le.isDead()) continue;
                Long glow = TEMP_GLOW.get(le.getUniqueId());
                if (glow != null) {
                    if (glow < now) {
                        TEMP_GLOW.remove(le.getUniqueId());
                        if (!le.hasPotionEffect(PotionEffectType.GLOWING)) le.setGlowing(false);
                    } else {
                        le.setGlowing(true);
                    }
                }
                if (marked != null && (State.GILDED.has(le) || State.CHARGED.has(le))) {
                    marked.add(le);
                }
                EternalBurn.tickSecond(le);
                State.tickSecond(le);
                // страх: цель убегает от ближайшего игрока
                if (State.FEAR.has(le)) {
                    Player feared = null;
                    double bestD = 10 * 10;
                    for (Entity near : le.getNearbyEntities(10, 5, 10)) {
                        if (near instanceof Player pl && !pl.isDead()) {
                            double d2 = pl.getLocation().distanceSquared(le.getLocation());
                            if (d2 < bestD) { bestD = d2; feared = pl; }
                        }
                    }
                    if (feared != null) {
                        Vector fleeDir = le.getLocation().toVector()
                                .subtract(feared.getLocation().toVector());
                        Vector flee = (fleeDir.lengthSquared() < 1e-6
                                ? new Vector(0, 0, 1)
                                : fleeDir.normalize()).multiply(0.9).setY(0.15);
                        le.setVelocity(flee);
                    }
                }
                // моховая подушка: мягкое падение внутри зоны
                if (le.getLocation() != null && !MOSS_ZONES.isEmpty()
                        && inMossZone(le.getLocation()) && le.getFallDistance() > 2) {
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0));
                }
                // медовая лужа: мобы вяжутся всегда; игроки — только если PvP разрешён
                // кастером (оффлайн/безвестный кастёр = эффект на игроков не действует)
                if (!HONEY_ZONES.isEmpty() && inHoneyZone(le.getLocation())) {
                    if (le instanceof Player vp) {
                        Player caster = null;
                        for (Zone hz : HONEY_ZONES) {
                            if (hz.caster() == null) continue;
                            // кастер только из зоны, в которой стоит игрок
                            if (!hz.world().equals(vp.getWorld().getName())) continue;
                            int dx = hz.x() - vp.getLocation().getBlockX();
                            int dy = hz.y() - vp.getLocation().getBlockY();
                            int dz = hz.z() - vp.getLocation().getBlockZ();
                            if (dx * dx + dy * dy + dz * dz
                                    > (double) hz.radius() * hz.radius()) continue;
                            Player c2 = Bukkit.getPlayer(hz.caster());
                            if (c2 != null && c2.isOnline()) { caster = c2; break; }
                        }
                        if (caster != null && pvpAllowed(caster, vp)) {
                            State.STICKY.apply(vp, 3);
                            vp.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2));
                            vp.getWorld().spawnParticle(Particle.FALLING_HONEY,
                                    vp.getLocation().add(0, 1, 0), 8, 0.3, 0.4, 0.3, 0.01);
                        }
                    } else {
                        State.STICKY.apply(le, 3);
                        le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2));
                        le.getWorld().spawnParticle(Particle.FALLING_HONEY,
                                le.getLocation().add(0, 1, 0), 8, 0.3, 0.4, 0.3, 0.01);
                    }
                }
                // стеклянный камуфляж: вода и дождь смывают
                if (le instanceof Player gp
                        && flagActive(gp, "glassinvis_until") && gp.isInWaterOrRain()) {
                    dispelGlassInvis(gp, "влага");
                }
                // фотосинтез: очарованные на ярком свету лечатся 1❤/2 с
                if (State.BLOOMED.has(le) && le.getEyeLocation().getBlock().getLightLevel() >= 10
                        && (now / 1000) % 2 == 0) {
                    le.setHealth(Math.min(le.getMaxHealth(), le.getHealth() + 1.0));
                }
                // тёплая аура светогриба: владелец лечит союзников в радиусе 5
                if (le instanceof Player ap && auraActive(ap) && (now / 1000) % 2 == 0) {
                    for (Entity other : ap.getNearbyEntities(5, 5, 5)) {
                        if (other instanceof Player ally && other != ap) {
                            ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0));
                        }
                    }
                }
            }
            // вызванный дождь: молния бьёт по меченым этого мира
            if (marked != null && !marked.isEmpty()) {
                LivingEntity target = marked.get((int) (Math.random() * marked.size()));
                target.getWorld().strikeLightning(target.getLocation());
            }
        }

        // угольная пыль
        Iterator<Map.Entry<UUID, Long>> it = DUST_EMITTERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (entry.getValue() < now) {
                it.remove();
                Player ep = Bukkit.getPlayer(entry.getKey());
                if (ep != null && ep.isOnline()) {
                    for (Entity near : ep.getNearbyEntities(4, 4, 4)) {
                        if (near instanceof LivingEntity le) {
                            le.getPersistentDataContainer().remove(key("dust_exp"));
                        }
                    }
                }
                continue;
            }
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline()) {
                it.remove();
                continue;
            }
            dustPulse(p);
        }
    }

    private static void dustPulse(Player p) {
        Location c = p.getLocation().add(0, 1, 0);
        double r = Conf.dustRadius();
        c.getWorld().spawnParticle(Particle.SMOKE, c, 30, r * 0.6, 1, r * 0.6, 0.01);
        ru.deepmagic.fx.Spectacle.dustAccent(p, r);
        long now = System.currentTimeMillis();
        for (Entity e : c.getWorld().getNearbyEntities(c, r, r, r)) {
            if (!(e instanceof LivingEntity le) || le.isDead() || le == p) continue;
            if (le instanceof Player vp && !pvpAllowed(p, vp)) continue;
            long first = getLong(le, "dust_exp", 0L);
            if (first == 0 || now - first > 30_000) {
                first = now;
                setLong(le, "dust_exp", first);
            }
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0));
            if (now - first > Conf.dustGrace() * 1000L) {
                le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 30, 1));
                le.damage(Conf.dustDps());
                if (le instanceof Player vp) {
                    vp.sendActionBar(T.color("&8Угольная пыль душит! Выйди из облака!"));
                }
            } else if (le instanceof Player vp) {
                vp.sendActionBar(T.color("&8Угольная пыль в лёгких..."));
            }
        }
    }

    // ==================== урон-хуки ====================

    /** Панцирь/кора/витаминный щит. */
    public static void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.isCancelled()) return;
        double amount = e.getFinalDamage();
        long now = System.currentTimeMillis();

        // Каменный панцирь: поглощение + блок поджога
        if (flagActive(p, "shell_until")) {
            int pts = getInt(p, "shell_pts", 0);
            if (pts > 0 && amount > 0) {
                if (e.getCause() == EntityDamageEvent.DamageCause.FIRE
                        || e.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK) {
                    p.setFireTicks(0);
                }
                int need = (int) Math.ceil(amount);
                int use = Math.min(pts, need);
                if (use >= need) {
                    e.setCancelled(true);
                } else {
                    e.setDamage(amount - use);
                }
                setInt(p, "shell_pts", pts - use);
                if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
                    p.getInventory().removeItem(new ItemStack(Material.COBBLESTONE, 1));
                }
                p.sendActionBar(T.color("&7Панцирь: &e" + Math.max(0, pts - use) + " поглощения"));
                return;
            }
        }
        // Кора дуба: поглощение
        if (flagActive(p, "bark_until")) {
            int pts = getInt(p, "bark_pts", 0);
            if (pts > 0 && amount > 0) {
                int need = (int) Math.ceil(amount);
                int use = Math.min(pts, need);
                if (use >= need) {
                    e.setCancelled(true);
                } else {
                    e.setDamage(amount - use);
                }
                setInt(p, "bark_pts", pts - use);
                p.sendActionBar(T.color("&2Кора: &e" + Math.max(0, pts - use) + " поглощения"));
                return;
            }
        }
        // Витаминный щит: раз в 3 мин спасает от смертельного урона за 5 морковок
        long vitamin = getLong(p, "vitamin_cd", 0L);
        if (vitamin > 0L && vitamin <= now
                && amount >= p.getHealth()
                && e.getCause() != EntityDamageEvent.DamageCause.VOID
                && e.getCause() != EntityDamageEvent.DamageCause.KILL) {
            ItemStack carrots = new ItemStack(Material.CARROT, 5);
            if (p.getInventory().containsAtLeast(carrots, 5)) {
                p.getInventory().removeItem(carrots);
                e.setCancelled(true);
                setLong(p, "vitamin_cd", now + 180_000L);
                p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + 6.0));
                p.sendTitle("", T.color("&6Витаминный щит!"), 5, 30, 10);
                p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.2f);
            }
        }
    }

    /** Колючки (контратака) и ярость (+25% урона). */
    public static void afterDamage(EntityDamageEvent e) {
        if (e.isCancelled()) return;
        if (e.getEntity() instanceof Player victim && flagActive(victim, "thorns_until")) {
            Entity causing = e.getDamageSource().getCausingEntity();
            long nowT = System.currentTimeMillis();
            if (causing instanceof LivingEntity damager
                    && damager.getLocation().distance(victim.getLocation()) <= 3.5
                    && getLong(victim, "thorns_last", 0L) + 2000L <= nowT) {
                setLong(victim, "thorns_last", nowT); // анти-пинг-понг
                damager.damage(getInt(victim, "thorns_dmg", 3), victim);
                damager.getWorld().spawnParticle(Particle.CRIT,
                        damager.getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.1);
            }
        }
        Entity attackerEntity = e.getDamageSource().getCausingEntity();
        if (attackerEntity instanceof Player attacker && flagActive(attacker, "rage_until")) {
            e.setDamage(e.getFinalDamage() * 1.25);
        }
    }

    /** Обсидиановая кожа: гасим отбрасывание тиком позже. */
    public static void noKnockback(Player p) {
        if (!flagActive(p, "obsidian_until")) return;
        Bukkit.getScheduler().runTaskLater(DeepMagicPlugin.get(), () -> {
            Vector v = p.getVelocity();
            p.setVelocity(new Vector(v.getX() * 0.1, Math.min(v.getY(), 0.05), v.getZ() * 0.1));
        }, 1L);
    }

    public static Set<String> anchors() {
        return ANCHORS;
    }

    public static void clearTempGlow(UUID u) {
        TEMP_GLOW.remove(u);
    }

    public static void clearPlayer(UUID u) {
        DUST_EMITTERS.remove(u);
        RAIN_CASTERS.remove(u);
        TEMP_GLOW.remove(u);
    }

    public static void clearAll() {
        MOSS_ZONES.clear();
        OBELISK_ZONES.clear();
        RITUAL_ZONES.clear();
        HONEY_ZONES.clear();
        RAIN.clear();
        RAIN_CASTERS.clear();
        ANCHORS.clear();
        TEMP_GLOW.clear();
        DUST_EMITTERS.clear();
    }

    public static int zoneCount() {
        return MOSS_ZONES.size() + OBELISK_ZONES.size() + RITUAL_ZONES.size()
                + HONEY_ZONES.size();
    }

    // ==================== персистентность зон/якорей (файл) ====================

    private static final String DATA_FILE = "zones.yml";

    /** Сохранение активных зон и якорей (вызов onDisable). */
    public static void save(DeepMagicPlugin pl) {
        var cfg = new org.bukkit.configuration.file.YamlConfiguration();
        saveZones(cfg, "moss", MOSS_ZONES);
        saveZones(cfg, "obelisk", OBELISK_ZONES);
        saveZones(cfg, "ritual", RITUAL_ZONES);
        saveZones(cfg, "honey", HONEY_ZONES);
        cfg.set("anchors", new ArrayList<>(ANCHORS));
        try {
            cfg.save(new java.io.File(pl.getDataFolder(), DATA_FILE));
        } catch (java.io.IOException ex) {
            pl.getLogger().warning("Не сохранить зоны: " + ex.getMessage());
        }
    }

    private static void saveZones(org.bukkit.configuration.file.YamlConfiguration cfg,
                                  String path, List<Zone> zones) {
        List<String> raw = new ArrayList<>();
        for (Zone z : zones) {
            raw.add(z.world() + ";" + z.x() + ";" + z.y() + ";" + z.z() + ";"
                    + z.radius() + ";" + z.until() + ";"
                    + (z.caster() == null ? "-" : z.caster()));
        }
        cfg.set(path, raw);
    }

    /** Восстановление зон и якорей после рестарта (вызов onEnable). */
    public static void load(DeepMagicPlugin pl) {
        java.io.File f = new java.io.File(pl.getDataFolder(), DATA_FILE);
        if (!f.isFile()) return;
        var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        loadZones(cfg, "moss", MOSS_ZONES);
        loadZones(cfg, "obelisk", OBELISK_ZONES);
        loadZones(cfg, "ritual", RITUAL_ZONES);
        loadZones(cfg, "honey", HONEY_ZONES);
        for (String a : cfg.getStringList("anchors")) ANCHORS.add(a);
        if (zoneCount() + ANCHORS.size() > 0) {
            pl.getLogger().info("Восстановлено зон: " + zoneCount()
                    + ", якорей: " + ANCHORS.size());
        }
    }

    private static void loadZones(org.bukkit.configuration.file.YamlConfiguration cfg,
                                  String path, List<Zone> into) {
        for (String raw : cfg.getStringList(path)) {
            String[] p = raw.split(";");
            if (p.length != 7) continue;
            try {
                into.add(new Zone(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                        Integer.parseInt(p[3]), Integer.parseInt(p[4]), Long.parseLong(p[5]),
                        "-".equals(p[6]) ? null : java.util.UUID.fromString(p[6])));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
