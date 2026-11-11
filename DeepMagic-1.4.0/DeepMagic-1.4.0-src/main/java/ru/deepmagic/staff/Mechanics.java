package ru.deepmagic.staff;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Switch;
import org.bukkit.block.data.Openable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import ru.deepmagic.model.Magic;
import ru.deepmagic.model.State;
import ru.deepmagic.model.EternalBurn;
import ru.deepmagic.model.ItemDefs;
import ru.deepmagic.fx.SpellFx;
import ru.deepmagic.fx.Spectacle;
import ru.deepmagic.synergy.Synergy;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import ru.deepmagic.util.Conf;
import ru.deepmagic.util.Msg;
import ru.deepmagic.util.T;
import ru.deepprogression.model.Branch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Реализация всех 73 механик посохов. Каждая уникальна (mechId-реестр),
 * копипаста запрещена selftest'ом. Сила = тир вещества (I/II/III → ×1/×1.5/×2).
 */
public final class Mechanics {

    private Mechanics() {}

    public static double tierMult(int tier) {
        return switch (tier) {
            case 1 -> 1.0;
            case 2 -> 1.5;
            default -> 2.0;
        };
    }

    // ==================== общие хелперы ====================

    /** Сущность в прицеле. */
    public static LivingEntity lookEntity(Player p, double range) {
        RayTraceResult r = p.getWorld().rayTraceEntities(
                p.getEyeLocation(), p.getEyeLocation().getDirection(), range,
                e -> e instanceof LivingEntity && e != p);
        return r == null ? null : (LivingEntity) r.getHitEntity();
    }

    /** Блок в прицеле. */
    public static Block lookBlock(Player p, double range) {
        RayTraceResult r = p.getWorld().rayTraceBlocks(
                p.getEyeLocation(), p.getEyeLocation().getDirection(), range,
                FluidCollisionMode.SOURCE_ONLY, true);
        return r == null ? null : r.getHitBlock();
    }

    /** Живые цели в конусе взгляда (dot ≥ 0.75). Сортировка по прицелу: get(0) = та, в которую целишься. */
    public static List<LivingEntity> cone(Player p, double range) {
        List<LivingEntity> out = new ArrayList<>();
        Vector dir = p.getEyeLocation().getDirection().normalize();
        for (Entity e : p.getNearbyEntities(range, range, range)) {
            if (!(e instanceof LivingEntity le) || le.isDead()) continue;
            Vector to = le.getLocation().toVector()
                    .subtract(p.getEyeLocation().toVector());
            double dist = to.length();
            if (dist < 0.5 || dist > range) continue;
            if (to.normalize().dot(dir) < 0.75) continue;
            out.add(le);
        }
        // порядок getNearbyEntities не определён — «прямое попадание» обязано быть ПРИЦЕЛЬНЫМ
        out.sort((a, b) -> Double.compare(
                b.getLocation().toVector().subtract(p.getEyeLocation().toVector())
                        .normalize().dot(dir),
                a.getLocation().toVector().subtract(p.getEyeLocation().toVector())
                        .normalize().dot(dir)));
        return out;
    }

    /** Живые цели в радиусе точки. */
    public static List<LivingEntity> circle(Location c, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : c.getWorld().getNearbyEntities(c, radius, radius, radius)) {
            if (e instanceof LivingEntity le && !le.isDead()
                    && e.getLocation().distanceSquared(c) <= radius * radius) {
                out.add(le);
            }
        }
        return out;
    }

    /** Урон от посоха: проходит через матрицу реакций Synergy. */
    public static void staffHit(Player attacker, LivingEntity victim, double dmg,
                                Magic.Element element) {
        Synergy.onStaffHit(attacker, victim, dmg, element);
    }

    private static void fx(Location loc, Particle particle, int count) {
        loc.getWorld().spawnParticle(particle, loc, count, 0.3, 0.3, 0.3, 0.02);
    }

    private static boolean pvpOk(Player p, LivingEntity target) {
        return Synergy.pvpAllowed(p, target);
    }

    /** Горизонтальное направление взгляда — устойчиво при взгляде строго вниз/вверх. */
    private static Vector flatDir(Player p) {
        double yaw = Math.toRadians(p.getLocation().getYaw());
        Vector v = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        return v.lengthSquared() < 1e-6 ? new Vector(0, 0, 1) : v;
    }

    /** Нормализация с защитой от нулевого вектора (цель в той же точке). */
    private static Vector safeNorm(Vector v, Vector fallback) {
        return v.lengthSquared() < 1e-6 ? fallback : v.normalize();
    }

    /** Контроль на игроках — не дольше 10 с (PvP-протокол); тир усиливает мобов. */
    private static int ctrl(LivingEntity t, int base, double m) {
        return t instanceof Player ? Math.min(base, 10) : (int) Math.round(base * m);
    }

    // ==================== временные блоки (терраформ с таймером) ====================

    private static final Map<String, Long> TEMP_BLOCKS = new java.util.HashMap<>();

    private static void tempBlock(Block b, int seconds) {
        TEMP_BLOCKS.put(b.getWorld().getName() + ":" + b.getX() + ":" + b.getY()
                + ":" + b.getZ(), System.currentTimeMillis() + seconds * 1000L);
    }

    /** Вызывается раз в секунду из главного таска. */
    public static void tickTempBlocks() {
        if (TEMP_BLOCKS.isEmpty()) return;
        long now = System.currentTimeMillis();
        TEMP_BLOCKS.entrySet().removeIf(en -> {
            if (en.getValue() >= now) return false;
            String[] p = en.getKey().split(":");
            org.bukkit.World w = org.bukkit.Bukkit.getWorld(p[0]);
            if (w != null && w.isChunkLoaded(Integer.parseInt(p[1]) >> 4,
                    Integer.parseInt(p[3]) >> 4)) {
                Block b = w.getBlockAt(Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                        Integer.parseInt(p[3]));
                if (b.getType() == Material.MANGROVE_ROOTS) {
                    b.setType(Material.AIR);
                }
            }
            return true;
        });
    }

    // ==================== диспетчер ====================

    /** Выполнить механику посоха. Возвращает true — успех (кулау-д можно ставить). */
    public static boolean execute(Player p, Magic.StaffDef d, int tier, boolean shift) {
        double m = tierMult(tier);
        if (Synergy.inRitual(p.getLocation())) m *= 1.25; // свечной ритуал
        Magic.Mech mech = d.mech();
        Branch b = d.branch();

        switch (mech) {
            // ==================== ШАХТЁР ====================
            case STONE_SHELL -> {
                int pts = (int) Math.round(6 * m);
                Synergy.addShell(p, pts, (int) (10 * m));
                Msg.actionBar(p, "&7Каменный панцирь: &e" + pts + " поглощения");
                p.playSound(p.getLocation(), Sound.BLOCK_STONE_PLACE, 1f, 0.7f);
                SpellFx.stoneShell(p, (int) (10 * m));
                return true;
            }
            case CARBON_COAT -> {
                LivingEntity t = lookEntity(p, 12);
                if (t == null) { Msg.dm(p, "&cНет цели в прицеле."); return false; }
                if (!pvpOk(p, t)) return false;
                if (t.getEquipment() != null
                        && ItemDefs.isStaff(t.getEquipment().getItemInMainHand())
                        && ItemDefs.staffBranch(t.getEquipment().getItemInMainHand()) == Branch.DEBRIS) {
                    Msg.dm(p, "&7Незеритовый жар цели выжигает угольную пыль!");
                    return false;
                }
                State.CARBON_COATED.apply(t, 60); // apply даёт орбиты угля + тотем-вспышку
                SpellFx.carbonCoat(t);
                fx(t.getLocation().add(0, 1, 0), Particle.SMOKE, 30);
                t.getWorld().playSound(t.getLocation(), Sound.BLOCK_GRAVEL_PLACE, 1f, 0.6f);
                nameMsg(p, t, " покрыт углём! Огонь теперь опаснее вдвое.");
                return true;
            }
            case COPPER_ANCHOR -> {
                Block target = lookBlock(p, 16);
                if (target == null) { Msg.dm(p, "&cНет точки в прицеле."); return false; }
                Block top = target.getRelative(BlockFace.UP);
                if (!top.getType().isAir()) { Msg.dm(p, "&cНет места для якоря."); return false; }
                top.setType(Material.LIGHTNING_ROD);
                Synergy.registerAnchor(top);
                SpellFx.copperAnchor(top.getLocation());
                Msg.dm(p, "&eМедный якорь установлен: молнии в радиусе 24 притянутся к нему.");
                return true;
            }
            case IRON_YOKE -> {
                LivingEntity t = lookEntity(p, 12);
                if (t == null) { Msg.dm(p, "&cНет цели в прицеле."); return false; }
                if (!pvpOk(p, t)) return false;
                int dur = ctrl(t, 8, m);
                t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, dur * 20, 1));
                t.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, dur * 20, 249));
                State.ROOTED.set(t, dur);
                SpellFx.ironYoke(t);
                fx(t.getLocation(), Particle.CRIT, 20);
                nameMsg(p, t, " прижато железным ярмом!");
                return true;
            }
            case GOLD_BRIBE -> {
                LivingEntity t = lookEntity(p, 12);
                if (t == null) { Msg.dm(p, "&cНет цели в прицеле."); return false; }
                if (!pvpOk(p, t)) return false;
                State.GILDED.set(t, (int) Math.round(30 * m));
                SpellFx.goldBribe(t);
                fx(t.getLocation().add(0, 1, 0), Particle.GLOW, 25);
                nameMsg(p, t, " позолочен: незер-орда отвернётся, но станет мишенью!");
                return true;
            }
            case REDSTONE_PULSE -> {
                LivingEntity t = lookEntity(p, 12);
                if (t != null && pvpOk(p, t)) {
                    staffHit(p, t, 4 * m, Magic.Element.LIGHTNING);
                    State.CHARGED.apply(t, 15);
                    SpellFx.redstonePulse(p, t, null);
                    fx(t.getLocation(), Particle.ELECTRIC_SPARK, 30);
                    return true;
                }
                Block tb = lookBlock(p, 8);
                if (tb != null) {
                    BlockData bd = tb.getBlockData();
                    if (bd instanceof Switch lever) {
                        lever.setPowered(!lever.isPowered());
                        tb.setBlockData(lever);
                        SpellFx.redstonePulse(p, null, tb.getLocation());
                        p.playSound(tb.getLocation(), Sound.BLOCK_LEVER_CLICK, 1f, 1f);
                        return true;
                    }
                    if (bd instanceof Openable open) {
                        open.setOpen(!open.isOpen());
                        tb.setBlockData(open);
                        p.playSound(tb.getLocation(), Sound.BLOCK_LEVER_CLICK, 1f, 1.3f);
                        return true;
                    }
                    Msg.dm(p, "&cРазряд не нашёл ни цели, ни механизма (рычаг/дверь).");
                    return false;
                }
                Msg.dm(p, "&cРазряд не нашёл ни цели, ни механизма (рычаг/дверь).");
                return false;
            }
            case LAPIS_MARK -> {
                LivingEntity t = lookEntity(p, 16);
                if (t == null) { Msg.dm(p, "&cНет цели в прицеле."); return false; }
                if (!pvpOk(p, t)) return false;
                State.ARCANE_MARK.set(t, (int) Math.round(30 * m));
                t.setGlowing(true);
                Synergy.tempGlow(t, (int) Math.round(30 * m)); // гаснет по таймеру, не навсегда
                SpellFx.lapisMark(p, t);
                t.getWorld().playSound(t.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 0.8f);
                nameMsg(p, t, " несёт лазурную печать: магия бьёт больнее!");
                return true;
            }
            case QUARTZ_PING -> {
                int count = 0;
                for (LivingEntity le : circle(p.getLocation(), 12)) {
                    if (le == p) continue;
                    le.setGlowing(true);
                    // Подсветка гасится таймером Synergy
                    Synergy.tempGlow(le, 4);
                    count++;
                }
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.6f);
                SpellFx.quartzPing(p, count);
                Msg.dm(p, "&bКварцевый пинг: подсвечено &e" + count + "&b сущностей сквозь стены.");
                return true;
            }
            case DIAMOND_EDGE -> {
                List<LivingEntity> targets = cone(p, 5);
                if (targets.isEmpty()) { Msg.dm(p, "&cВ конусе нет целей."); return false; }
                LivingEntity main = targets.get(0); // прицельная (кон отсортирован)
                for (LivingEntity le : targets) {
                    if (!pvpOk(p, le)) continue;
                    staffHit(p, le, 6 * m, Magic.Element.ICE);
                }
                if (pvpOk(p, main)) {
                    State.BRITTLE.set(main, 8);
                    if (main instanceof Player mp) {
                        mp.sendActionBar(T.color("&fВы хрупки: следующий удар — ×2!"));
                    }
                    Msg.dm(p, "&fГлавная цель стала &fхрупкой&f: следующий удар по ней — ×2!");
                }
                SpellFx.diamondEdge(p, targets);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.4f);
                return true;
            }
            case EMERALD_DEAL -> {
                LivingEntity t = lookEntity(p, 12);
                if (t == null) t = p;
                double heal = 12.0 * m;
                t.setHealth(Math.min(t.getMaxHealth(), t.getHealth() + heal));
                State.FROSTED.remove(t);
                State.NECROSIS.remove(t);
                if (!EternalBurn.burning(t)) t.setFireTicks(0);
                fx(t.getLocation().add(0, 1, 0), Particle.HAPPY_VILLAGER, 20);
                SpellFx.emeraldDeal(t);
                t.getWorld().playSound(t.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
                return true;
            }
            case DEBRIS_QUAKE -> {
                Block tb = lookBlock(p, 10);
                Location c = tb != null ? tb.getLocation().add(0.5, 1, 0.5)
                        : p.getLocation().clone().add(flatDir(p).multiply(3));
                for (LivingEntity le : circle(c, 2.5)) {
                    if (le == p) continue;
                    if (!pvpOk(p, le)) continue;
                    staffHit(p, le, 8 * m, Magic.Element.FIRE);
                    Vector away = safeNorm(le.getLocation().toVector().subtract(c.toVector()),
                            new Vector(0, 1, 0)).multiply(1.2).setY(0.5);
                    le.setVelocity(away);
                    le.setFireTicks(Math.max(le.getFireTicks(), (int) (5 * 20)));
                }
                c.getWorld().spawnParticle(Particle.EXPLOSION, c, 3);
                SpellFx.debrisQuake(c);
                c.getWorld().playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.9f);
                return true;
            }
            case AMETHYST_RESONATE -> {
                int extended = 0;
                for (LivingEntity le : circle(p.getLocation(), 8)) {
                    if (le == p) continue;
                    if (!pvpOk(p, le)) continue;
                    staffHit(p, le, 2 * m, Magic.Element.ECHO);
                    for (State s : State.values()) {
                        int tl = s.ticksLeft(le);
                        if (tl > 0) {
                            s.set(le, Math.min(s.defaultSeconds * 2, (int) Math.round(tl * 2 * m / 20.0)));
                            extended++;
                        }
                    }
                    le.getWorld().spawnParticle(Particle.NOTE,
                            le.getLocation().add(0, 1.5, 0), 6, 0.3, 0.3, 0.3, 1);
                }
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.2f);
                SpellFx.amethystResonate(p);
                Msg.dm(p, "&9Резонанс: продлено состояний — &e" + extended);
                return true;
            }

            // ==================== ЛЕСОРУБ ====================
            case OAK_BARK -> {
                int pts = (int) Math.round(6 * m);
                int dur = (int) Math.round(30 * m);
                Synergy.addBark(p, pts, dur);
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, dur * 20, 0));
                Msg.actionBar(p, "&2Кора дуба активна: &e" + pts + " поглощения (самовосстановление)");
                p.playSound(p.getLocation(), Sound.BLOCK_WOOD_PLACE, 1f, 0.8f);
                SpellFx.oakBark(p, dur);
                return true;
            }
            case SPRUCE_FROST_CONE -> {
                List<LivingEntity> targets = cone(p, 6);
                if (targets.isEmpty()) { Msg.dm(p, "&cВ конусе нет целей."); return false; }
                for (LivingEntity le : targets) {
                    if (!pvpOk(p, le)) continue;
                    State.FROSTED.apply(le, (int) Math.round(15 * m));
                    staffHit(p, le, 1 * m, Magic.Element.ICE);
                    fx(le.getLocation(), Particle.SNOWFLAKE, 20);
                }
                SpellFx.spruceCone(p, targets);
                p.playSound(p.getLocation(), Sound.BLOCK_GLASS_PLACE, 1f, 0.6f);
                return true;
            }
            case BIRCH_CLEANSE -> {
                LivingEntity t = shift ? lookEntity(p, 8) : p;
                if (shift && t == null) t = p;
                State.wipeNegative(t); // негативные состояния — полезные (обаяние) остаются
                t.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                        (int) (30 * m) * 20, 0));
                fx(t.getLocation().add(0, 1, 0), Particle.COMPOSTER, 25);
                SpellFx.birchCleanse(t);
                if (t == p) Msg.dm(p, "&aБерёзовый сок очистил вас и лечит 30 с.");
                else Msg.dm(p, "&aЦель очищена и лечится.");
                return true;
            }
            case JUNGLE_HOOK -> {
                LivingEntity t = lookEntity(p, 10);
                if (t != null && pvpOk(p, t)) {
                    Vector pull = safeNorm(p.getLocation().toVector()
                                    .subtract(t.getLocation().toVector()), new Vector(0, 0.4, 0))
                            .multiply(1.4).setY(0.4);
                    t.setVelocity(pull);
                    SpellFx.jungleHook(p, t, null);
                    fx(t.getLocation(), Particle.EFFECT, 15);
                    nameMsg(p, t, " притянут лианой!");
                    return true;
                }
                Block tb = lookBlock(p, 10);
                if (tb == null) { Msg.dm(p, "&cНет ни цели, ни блока для крюка."); return false; }
                Vector dir = tb.getLocation().toVector()
                        .add(new Vector(0.5, 1, 0.5)).subtract(p.getLocation().toVector())
                        .normalize().multiply(1.6);
                p.setVelocity(dir);
                SpellFx.jungleHook(p, null, tb.getLocation().add(0.5, 1, 0.5));
                fx(p.getLocation(), Particle.EFFECT, 15);
                return true;
            }
            case ACACIA_THORNS -> {
                Synergy.addThorns(p, (int) Math.round(20 * m), 3.0 * m);
                Msg.actionBar(p, "&6Колючий ореол активен 20 с");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 1f, 0.8f);
                SpellFx.acaciaThorns(p, (int) Math.round(20 * m));
                return true;
            }
            case DARK_OAK_VEIL -> {
                Block tb = lookBlock(p, 10);
                Location c = tb != null ? tb.getLocation() : p.getLocation();
                int dur = (int) Math.round(15 * m);
                for (LivingEntity le : circle(c.clone().add(0.5, 1, 0.5), 3)) {
                    if (le == p) continue;
                    if (!pvpOk(p, le)) continue;
                    le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, dur * 20, 0));
                    State.FEAR.apply(le, 8);
                }
                p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, dur * 20, 0));
                c.getWorld().spawnParticle(Particle.SQUID_INK,
                        c.clone().add(0.5, 1.5, 0.5), 60, 2.5, 1, 2.5, 0.01);
                SpellFx.darkVeil(c);
                c.getWorld().playSound(c, Sound.ENTITY_WITHER_AMBIENT, 0.6f, 0.5f);
                Msg.dm(p, "&8Тёмный покров опустился на зону.");
                return true;
            }
            case MANGROVE_ROOTS -> {
                Block tb = lookBlock(p, 8);
                Location c = tb != null ? tb.getLocation() : p.getLocation().subtract(1, 1, 1);
                int placed = 0;
                List<Location> rootSpots = new ArrayList<>();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block f = c.getWorld().getBlockAt(c.getBlockX() + dx, c.getBlockY(), c.getBlockZ() + dz);
                        if ((f.getType().isAir() || f.isLiquid())
                                && f.getRelative(BlockFace.DOWN).getType().isSolid()) {
                            f.setType(Material.MANGROVE_ROOTS);
                            tempBlock(f, 30); // корневая сеть рассасывается через 30 с
                            rootSpots.add(f.getLocation());
                            placed++;
                        }
                    }
                }
                for (LivingEntity le : circle(c.clone().add(0.5, 1, 0.5), 2.5)) {
                    if (le == p) continue;
                    if (!pvpOk(p, le)) continue;
                    int dur = ctrl(le, 10, m);
                    State.ROOTED.set(le, dur);
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, dur * 20, 2));
                }
                c.getWorld().playSound(c, Sound.BLOCK_MANGROVE_ROOTS_PLACE, 1f, 0.9f);
                SpellFx.rootsRise(rootSpots, c);
                Msg.dm(p, "&2Корневая сеть: &e" + placed + "&2 блоков корней.");
                return true;
            }
            case CHERRY_STORM -> {
                int count = 0;
                for (LivingEntity le : circle(p.getLocation(), 5)) {
                    if (le instanceof Mob) {
                        if (!pvpOk(p, le)) continue;
                        State.BLOOMED.set(le, (int) Math.round(10 * m));
                        ((Mob) le).setTarget(null);
                        count++;
                        le.getWorld().spawnParticle(Particle.CHERRY_LEAVES,
                                le.getLocation().add(0, 1.5, 0), 15, 0.4, 0.4, 0.4, 0.02);
                    } else if (le instanceof Player ally && le != p
                            && !State.BLOOMED.has(ally)) {
                        // лепестковый вихрь ещё и лечит союзников — отличие от одиночного обаяния
                        ally.setHealth(Math.min(ally.getMaxHealth(), ally.getHealth() + 4 * m));
                        ally.getWorld().spawnParticle(Particle.CHERRY_LEAVES,
                                ally.getLocation().add(0, 1.5, 0), 10, 0.3, 0.4, 0.3, 0.02);
                    }
                }
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AZALEA_LEAVES_PLACE, 1f, 1.3f);
                SpellFx.cherryStorm(p);
                Msg.dm(p, "&dЛепестковый вихрь: очаровано &e" + count);
                return true;
            }
            case PALE_OAK_PETRIFY -> {
                LivingEntity t = lookEntity(p, 12);
                if (t == null) { Msg.dm(p, "&cНет цели в прицеле."); return false; }
                if (!pvpOk(p, t)) return false;
                int dur = (int) Math.max(3, Math.round(5 * m));
                t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, dur * 20, 6));
                t.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, dur * 20, 249));
                t.setGlowing(true);
                Synergy.tempGlow(t, dur);
                t.getWorld().spawnParticle(Particle.ASH,
                        t.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.01);
                SpellFx.petrify(t);
                t.getWorld().playSound(t.getLocation(), Sound.BLOCK_ROOTED_DIRT_BREAK, 1f, 0.4f);
                nameMsg(p, t, " цепенеет в бледной окаменелости!");
                return true;
            }
            case RESIN_TRAP -> {
                LivingEntity t = lookEntity(p, 12);
                if (t == null) { Msg.dm(p, "&cНет цели в прицеле."); return false; }
                if (!pvpOk(p, t)) return false;
                // Смоляной КАПКАН: одиночная цель в коконе (липкая + прикована)
                int dur = ctrl(t, 8, m);
                State.STICKY.set(t, (int) Math.round(20 * m));
                t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, dur * 20, 3));
                t.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, dur * 20, 249));
                State.ROOTED.set(t, dur);
                fx(t.getLocation().add(0, 0.5, 0), Particle.FALLING_HONEY, 30);
                SpellFx.resinTrap(t);
                nameMsg(p, t, " пойман в смоляной капкан и прикован!");
                return true;
            }

            // ==================== ЗЕМЛЕДЕЛЕЦ ====================
            case WHEAT_HARVEST -> {
                int pulled = 0;
                for (Entity e : p.getNearbyEntities(8, 8, 8)) {
                    if (e instanceof org.bukkit.entity.Item item) {
                        Vector v = safeNorm(p.getLocation().toVector()
                                        .subtract(e.getLocation().toVector()), new Vector(0, 0.3, 0))
                                .multiply(0.8);
                        e.setVelocity(v.setY(0.3));
                        pulled++;
                    }
                }
                // Досев 3×3 вокруг блока прицела
                Block tb = lookBlock(p, 6);
                int seeded = 0;
                if (tb != null) {
                    Material seed = null;
                    Material soil = tb.getType();
                    if (soil == Material.FARMLAND) seed = Material.WHEAT_SEEDS;
                    for (int dx = -1; dx <= 1 && seed != null; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            Block f = tb.getRelative(dx, 0, dz);
                            Block above = f.getRelative(BlockFace.UP);
                            if (f.getType() == Material.FARMLAND && above.getType().isAir()
                                    && p.getInventory().containsAtLeast(new ItemStack(seed), 1)) {
                                p.getInventory().removeItem(new ItemStack(seed, 1));
                                above.setType(Material.WHEAT);
                                seeded++;
                            }
                        }
                    }
                }
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                SpellFx.wheatHarvest(p, pulled);
                Msg.dm(p, "&eУрожайный призыв: предметов &7" + pulled + "&e, досеяно &7" + seeded);
                return true;
            }
            case CARROT_VITAMIN -> {
                LivingEntity t = shift ? lookEntity(p, 8) : p;
                if (shift && t == null) t = p;
                t.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                        (int) (120 * m) * 20, 0));
                fx(t.getLocation().add(0, 1, 0), Particle.HAPPY_VILLAGER, 15);
                SpellFx.carrotVision(t, 6);
                if (t == p) {
                    Synergy.addVitaminShield(p);
                    Msg.dm(p, "&6Морковное зрение на 120 с. &7Витаминный щит готов.");
                } else Msg.dm(p, "&6Цель видит в темноте 120 с.");
                return true;
            }
            case POTATO_KITCHEN -> {
                int baked = 0;
                for (int i = 0; i < p.getInventory().getSize() && baked < (int) (16 * m); i++) {
                    ItemStack it = p.getInventory().getItem(i);
                    if (it == null) continue;
                    if (it.getType() == Material.POTATO) {
                        int n = Math.min(it.getAmount(), (int) (16 * m) - baked);
                        it.setAmount(it.getAmount() - n);
                        if (it.getAmount() <= 0) p.getInventory().setItem(i, null);
                        var left = p.getInventory().addItem(new ItemStack(Material.BAKED_POTATO, n));
                        for (ItemStack rest : left.values())
                            p.getWorld().dropItemNaturally(p.getLocation(), rest);
                        baked += n;
                    }
                }
                for (LivingEntity le : circle(p.getLocation(), 5)) {
                    if (le instanceof Player pl)
                        pl.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION,
                                (int) (60 * m) * 20, 2));
                }
                p.playSound(p.getLocation(), Sound.BLOCK_CAMPFIRE_CRACKLE, 1f, 1f);
                SpellFx.potatoKitchen(p);
                Msg.dm(p, "&6Полевая кухня: испечено &e" + baked + "&6, группа сыта.");
                return true;
            }
            case BEETROOT_FEAST -> {
                int healed = 0;
                double heal = 8.0 * m;
                List<LivingEntity> healedList = new ArrayList<>();
                for (LivingEntity le : circle(p.getLocation(), 5)) {
                    if (!(le instanceof Player)) continue;
                    le.setHealth(Math.min(le.getMaxHealth(), le.getHealth() + heal));
                    fx(le.getLocation().add(0, 1, 0), Particle.HEART, 6);
                    healed++;
                    healedList.add(le);
                }
                if (healed == 0) { // нет группы — лечим себя
                    p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + heal));
                    fx(p.getLocation().add(0, 1, 0), Particle.HEART, 6);
                    healed = 1;
                    healedList.add(p);
                }
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.9f, 1.1f);
                SpellFx.beetFeast(p, healedList);
                Msg.dm(p, "&cСвекольный обед: накормлено &e" + healed);
                return true;
            }
            case MELON_QUENCH -> {
                int extinguished = 0;
                for (int dx = -6; dx <= 6; dx++) {
                    for (int dy = -3; dy <= 3; dy++) {
                        for (int dz = -6; dz <= 6; dz++) {
                            Block f = p.getLocation().getBlock().getRelative(dx, dy, dz);
                            if (f.getType() == Material.FIRE) {
                                f.setType(Material.AIR);
                                extinguished++;
                            }
                        }
                    }
                }
                for (LivingEntity le : circle(p.getLocation(), 6)) {
                    EternalBurn.quench(le);
                    State.CARBON_COATED.remove(le);
                    State.SOAKED.apply(le, 30);
                }
                EternalBurn.quench(p);
                State.SOAKED.apply(p, 30);
                p.getWorld().spawnParticle(Particle.SPLASH,
                        p.getLocation().add(0, 1, 0), 80, 4, 2, 4, 0.2);
                SpellFx.melonQuench(p, extinguished);
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_SPLASH, 1f, 1f);
                Msg.dm(p, "&bАрбузная влага: потушено очагов &e" + extinguished
                        + "&b, вечное горение снято.");
                return true;
            }
            case PUMPKIN_SCARE -> {
                List<LivingEntity> targets = cone(p, 8);
                int scared = 0;
                for (LivingEntity le : targets) {
                    if (!pvpOk(p, le)) continue;
                    State.FEAR.set(le, 8);
                    Vector away = safeNorm(le.getLocation().toVector()
                                    .subtract(p.getLocation().toVector()), new Vector(0, 0.2, 0))
                            .multiply(1.1).setY(0.2);
                    le.setVelocity(away);
                    scared++;
                }
                SpellFx.pumpkinScare(p, targets);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 0.7f, 0.5f);
                Msg.dm(p, "&6Лик урожая: испугано &e" + scared);
                return true;
            }
            case CANE_RUSH -> {
                int dur = (int) (20 * m);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, dur * 20, 2));
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, dur * 20, 0));
                p.playSound(p.getLocation(), Sound.ENTITY_RABBIT_JUMP, 1f, 1.3f);
                SpellFx.caneRush(p, dur);
                Msg.dm(p, "&aСахарный рывок: &e" + dur + " с скорости.");
                return true;
            }
            case COCOA_FORTIFY -> {
                // Какао — ЩИТ (поглощение), без лечения: отличие от свекольного обеда
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,
                        (int) (30 * m) * 20, (int) Math.min(3, Math.round(m))));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 40, 1));
                fx(p.getLocation().add(0, 1, 0), Particle.NOTE, 10);
                SpellFx.cocoaFortify(p);
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.9f, 1.2f);
                return true;
            }
            case WART_BOMB -> {
                Block tb = lookBlock(p, 8);
                Location c = tb != null ? tb.getLocation().add(0.5, 1, 0.5)
                        : p.getLocation().clone().add(p.getEyeLocation().getDirection().normalize().multiply(4));
                int hit = 0;
                for (LivingEntity le : circle(c, 2)) {
                    if (le == p || !pvpOk(p, le)) continue;
                    le.addPotionEffect(new PotionEffect(PotionEffectType.POISON,
                            (int) (6 * m) * 20, 1));
                    Synergy.markContagious(le, (int) (6 * m));
                    hit++;
                }
                c.getWorld().spawnParticle(Particle.DRAGON_BREATH, c, 40, 1.2, 0.6, 1.2, 0.01);
                SpellFx.wartBomb(p, c);
                c.getWorld().playSound(c, Sound.ENTITY_WITCH_THROW, 1f, 0.8f);
                Msg.dm(p, "&5Наростовая зараза: отравлено &e" + hit);
                return true;
            }
            case WOOL_SILENCE -> {
                LivingEntity t = lookEntity(p, 10);
                if (t == null) { Msg.dm(p, "&cНет цели в прицеле."); return false; }
                if (!pvpOk(p, t)) return false;
                int dur = t instanceof Player ? 8 : (int) (20 * m);
                Synergy.silence(t, dur);
                t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, dur * 20, 1));
                fx(t.getLocation().add(0, 1, 0), Particle.WHITE_SMOKE, 25);
                SpellFx.woolSilence(t, dur);
                nameMsg(p, t, " закутан в шерстяную тишину!");
                return true;
            }
            case HONEY_GLAZE -> {
                // Медовая ЛУЖА: зона 3×3 на 20 с — враги вязнут, пока стоят в ней
                Block tb = lookBlock(p, 8);
                Location c = tb != null ? tb.getLocation().add(0.5, 1, 0.5) : p.getLocation();
                Synergy.addHoneyZone(c, (int) (20 * m), 2, p.getUniqueId());
                c.getWorld().spawnParticle(Particle.FALLING_HONEY,
                        c, 60, 2.2, 0.6, 2.2, 0.01);
                SpellFx.honeyGlaze(c, 2);
                p.playSound(c, Sound.BLOCK_HONEY_BLOCK_PLACE, 1f, 0.9f);
                Msg.dm(p, "&6Медовая лужа накрыта: вязнут в зоне &e"
                        + (int) (20 * m) + " с.");
                return true;
            }

            // ==================== ФЛОРИСТ ====================
            case CACTUS_RING -> {
                Block tb = lookBlock(p, 10);
                if (tb == null) { Msg.dm(p, "&cНет точки в прицеле."); return false; }
                int placed = 0;
                int pedestals = 0;
                List<Location> cactusSpots = new ArrayList<>();
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                        if (!ring) continue;
                        Block f = tb.getRelative(dx, 1, dz);
                        Block below = tb.getRelative(dx, 0, dz);
                        if (!f.getType().isAir()) continue;
                        // Песчаная подушка: кактус приживётся не только в пустыне —
                        // любой твёрдый грунт под кольцом конвертируется в песок
                        if (below.getType() != Material.SAND
                                && below.getType() != Material.RED_SAND
                                && below.getType() != Material.CACTUS
                                && below.getType().isSolid()) {
                            below.setType(Material.SAND);
                            pedestals++;
                        }
                        if (below.getType() == Material.SAND
                                || below.getType() == Material.RED_SAND
                                || below.getType() == Material.CACTUS) {
                            f.setType(Material.CACTUS);
                            cactusSpots.add(f.getLocation());
                            placed++;
                        }
                    }
                }
                if (placed == 0) {
                    Msg.dm(p, "&cКактусовому кольцу нужен грунт — нет ни одной точки с опорой.");
                    return false;
                }
                p.playSound(tb.getLocation(), Sound.BLOCK_GRASS_PLACE, 1f, 0.9f);
                SpellFx.cactusRing(cactusSpots, tb.getLocation());
                Msg.dm(p, "&aКактусовое кольцо: &e" + placed + "&a кактусов"
                        + (pedestals > 0 ? " &7(песчаных подушек: " + pedestals + ")" : ""));
                return true;
            }
            case BAMBOO_JUMP -> {
                int dur = (int) (15 * m);
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, dur * 20, 2));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, dur * 20, 0));
                p.playSound(p.getLocation(), Sound.BLOCK_BAMBOO_BREAK, 1f, 1.4f);
                SpellFx.bambooJump(p, dur);
                Msg.dm(p, "&aБамбуковый батут готов.");
                return true;
            }
            case FLOWER_CHARM -> {
                LivingEntity t = lookEntity(p, 10);
                if (t == null) { Msg.dm(p, "&cНет цели в прицеле."); return false; }
                if (!pvpOk(p, t)) return false;
                // Одиночное ДЛИТЕЛЬНОЕ обаяние + умиротворение (снимает страх и гниль)
                State.BLOOMED.set(t, (int) (20 * m));
                State.FEAR.remove(t);
                State.NECROSIS.remove(t);
                if (t instanceof Mob mob) mob.setTarget(null);
                t.getWorld().spawnParticle(Particle.CHERRY_LEAVES,
                        t.getLocation().add(0, 1.5, 0), 20, 0.4, 0.4, 0.4, 0.02);
                SpellFx.flowerCharm(t, (int) (20 * m));
                nameMsg(p, t, " очарован цветочной пыльцой!");
                return true;
            }
            case MOSS_CUSHION -> {
                Block tb = lookBlock(p, 8);
                Location c = tb != null ? tb.getLocation() : p.getLocation();
                Synergy.addMossZone(c, (int) (60 * m), 4);
                c.getWorld().spawnParticle(Particle.COMPOSTER,
                        c.clone().add(0.5, 1, 0.5), 40, 2.5, 0.5, 2.5, 0.02);
                SpellFx.mossCushion(c, 4);
                p.playSound(c, Sound.BLOCK_MOSS_PLACE, 1f, 1f);
                Msg.dm(p, "&2Моховая подушка накрыта на &e" + (int) (60 * m) + " с.");
                return true;
            }
            case AZALEA_TOXIN -> {
                LivingEntity t = lookEntity(p, 12);
                if (t == null) { Msg.dm(p, "&cНет цели в прицеле."); return false; }
                if (!pvpOk(p, t)) return false;
                t.addPotionEffect(new PotionEffect(PotionEffectType.POISON, (int) (8 * m) * 20, 1));
                t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) (8 * m) * 20, 0));
                State.SPORED.apply(t, 12);
                SpellFx.azaleaToxin(t);
                nameMsg(p, t, " отравлен азалиевым дурманом!");
                return true;
            }
            case MUSHROOM_CLOUD -> {
                Block tb = lookBlock(p, 8);
                Location c = tb != null ? tb.getLocation().add(0.5, 1, 0.5)
                        : p.getLocation().clone().add(p.getEyeLocation().getDirection().normalize().multiply(3));
                int hit = 0;
                for (LivingEntity le : circle(c, 3)) {
                    if (le == p || !pvpOk(p, le)) continue;
                    le.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, (int) (10 * m) * 20, 0));
                    State.SPORED.apply(le, 12);
                    hit++;
                }
                c.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, c, 60, 2, 1, 2, 0.01);
                SpellFx.mushroomCloud(c, 3);
                c.getWorld().playSound(c, Sound.BLOCK_SCULK_SPREAD, 0.8f, 1.2f);
                Msg.dm(p, "&cОблако спор: захвачено &e" + hit);
                return true;
            }

            // ==================== РЫБОЛОВ ====================
            case KELP_WATERS -> {
                if (shift) {
                    // Водоворот: стягивает цель к кастеру и ПРОПИТЫВАЕТ влагой (матрица:
                    // мокрый + молния = цепной шок, мокрый + лёд = корка) — отличие от ярма
                    LivingEntity t = lookEntity(p, 8);
                    if (t == null) { Msg.dm(p, "&cНет цели в прицеле."); return false; }
                    if (!pvpOk(p, t)) return false;
                    Vector swirl = safeNorm(p.getLocation().toVector()
                                    .subtract(t.getLocation().toVector()), new Vector(0, 0.4, 0))
                            .multiply(1.3).setY(0.35);
                    t.setVelocity(swirl);
                    State.SOAKED.apply(t, 30);
                    State.ROOTED.set(t, ctrl(t, 4, m));
                    t.getWorld().spawnParticle(Particle.UNDERWATER,
                            t.getLocation(), 30, 0.4, 0.6, 0.4, 0.01);
                    SpellFx.kelpVortex(t);
                    nameMsg(p, t, " затянут водоворотом и пропитан влагой!");
                    return true;
                }
                int dur = (int) (60 * m);
                p.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, dur * 20, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, dur * 20, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, dur * 20, 0));
                p.playSound(p.getLocation(), Sound.ENTITY_DOLPHIN_PLAY, 1f, 1f);
                Msg.dm(p, "&bВодорослевые объятия: полный водный режим &e" + dur + " с.");
                return true;
            }
            case CORAL_REEF -> {
                int placed = 0;
                Vector dir = flatDir(p);
                Vector perp = new Vector(-dir.getZ(), 0, dir.getX()).normalize();
                // Стенка ПЕРЕД игроком (2 блока вперёд) — укрытие между вами и врагом,
                // а не вокруг игрока (старое поведение было неинтуитивным)
                Block center = p.getLocation().clone().add(dir.clone().multiply(2)).getBlock();
                Material[] corals = {Material.TUBE_CORAL_BLOCK, Material.BRAIN_CORAL_BLOCK,
                        Material.BUBBLE_CORAL_BLOCK, Material.FIRE_CORAL_BLOCK, Material.HORN_CORAL_BLOCK};
                List<Location> coralSpots = new ArrayList<>();
                for (int i = -2; i <= 2; i++) {
                    Block base = center.getRelative(
                            (int) Math.round(perp.getX() * i), 0,
                            (int) Math.round(perp.getZ() * i));
                    Block above = base.getRelative(BlockFace.UP);
                    if ((above.getType().isAir() || above.isLiquid())
                            && base.getType().isSolid()) {
                        above.setType(corals[(i + 2) % corals.length]);
                        coralSpots.add(above.getLocation());
                        placed++;
                    }
                }
                if (placed == 0) {
                    Msg.dm(p, "&cКоралловому рифу нужна твёрдая опора перед вами.");
                    return false;
                }
                p.playSound(p.getLocation(), Sound.BLOCK_CORAL_BLOCK_PLACE, 1f, 1f);
                SpellFx.coralWall(coralSpots, corals);
                Msg.dm(p, "&bКоралловый риф: стена впереди, поставлено &e"
                        + placed + "&b блоков.");
                return true;
            }
            case SPONGE_DRAIN -> {
                int drained = 0;
                for (int dx = -5; dx <= 5 && drained < 200; dx++) {
                    for (int dy = -3; dy <= 3 && drained < 200; dy++) {
                        for (int dz = -5; dz <= 5 && drained < 200; dz++) {
                            Block f = p.getLocation().getBlock().getRelative(dx, dy, dz);
                            if (f.getType() == Material.WATER
                                    || f.getType() == Material.BUBBLE_COLUMN
                                    || f.getType() == Material.SEAGRASS
                                    || f.getType() == Material.TALL_SEAGRASS
                                    || f.getType() == Material.KELP
                                    || f.getType() == Material.KELP_PLANT) {
                                f.setType(Material.AIR);
                                drained++;
                            }
                        }
                    }
                }
                for (LivingEntity le : circle(p.getLocation(), 5)) {
                    State.SOAKED.remove(le);
                }
                State.SOAKED.remove(p);
                p.playSound(p.getLocation(), Sound.BLOCK_SPONGE_ABSORB, 1f, 1f);
                SpellFx.spongeDrain(p, drained);
                Msg.dm(p, "&eГубка осушила &e" + drained + "&e блоков. Влажность снята — угольной цели не спрятаться!");
                return true;
            }
            case PRISMARINE_BEAM -> {
                LivingEntity t = lookEntity(p, 12);
                if (t != null && pvpOk(p, t)) {
                    staffHit(p, t, 5 * m, Magic.Element.WATER);
                    Vector push = t.getLocation().toVector()
                            .subtract(p.getLocation().toVector()).normalize().multiply(1.3).setY(0.3);
                    t.setVelocity(push);
                    State.SOAKED.apply(t, 30);
                    SpellFx.prismarineBeam(p, null, t);
                    fx(t.getLocation(), Particle.DRIPPING_DRIPSTONE_WATER, 20);
                    return true;
                }
                Block tb = lookBlock(p, 12);
                if (tb == null) { Msg.dm(p, "&cЛуч никуда не попал."); return false; }
                Location from = p.getEyeLocation();
                Location to = tb.getLocation().add(0.5, 0.5, 0.5);
                drawBeam(from, to, Particle.DOLPHIN);
                SpellFx.prismarineBeam(p, to, null);
                Msg.dm(p, "&bЛуч пронзил пространство.");
                return true;
            }

            // ==================== КАМЕНЩИК ====================
            case SAND_STORM -> {
                List<LivingEntity> targets = cone(p, 8);
                int hit = 0;
                LivingEntity glazed = null;
                for (LivingEntity le : targets) {
                    if (!pvpOk(p, le)) continue;
                    le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, (int) (8 * m) * 20, 0));
                    staffHit(p, le, 1 * m, Magic.Element.EARTH);
                    hit++;
                }
                // ПРЯМОЕ ПОПАДАНИЕ: остекляется та, в которую целишься (кон отсортирован)
                if (!targets.isEmpty() && pvpOk(p, targets.get(0))) {
                    glazed = targets.get(0);
                    State.GLASSED.apply(glazed, (int) (20 * m));
                    if (glazed instanceof Player gp) {
                        gp.sendActionBar(T.color("&fВас остеклили! Любой удар — взрыв осколков."));
                    }
                }
                p.getWorld().spawnParticle(Particle.CLOUD,
                        p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(3)),
                        60, 1.5, 0.8, 1.5, 0.05);
                SpellFx.sandStorm(p, targets);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 1f, 0.7f);
                Msg.dm(p, "&eПесчаная буря: засыпано &e" + hit
                        + (glazed != null
                        ? "&f. Прямое попадание остеклило цель — бей, взорвутся осколки!"
                        : ""));
                return true;
            }
            case GRAVEL_HAMMER -> {
                LivingEntity t = lookEntity(p, 12);
                if (t == null) { Msg.dm(p, "&cНет цели в прицеле."); return false; }
                if (!pvpOk(p, t)) return false;
                Location c = t.getLocation();
                int placed = 0;
                List<Location> gravelSpots = new ArrayList<>();
                for (int dy = 3; dy <= 5; dy++) {
                    Block f = c.getWorld().getBlockAt(c.getBlockX(), c.getBlockY() + dy, c.getBlockZ());
                    if (f.getType().isAir()) {
                        f.setType(Material.GRAVEL);
                        gravelSpots.add(f.getLocation());
                        placed++;
                    }
                }
                if (placed == 0) { Msg.dm(p, "&cНад целью нет места."); return false; }
                State.ROOTED.apply(t, 6);
                p.playSound(c, Sound.BLOCK_GRAVEL_PLACE, 1f, 0.7f);
                SpellFx.gravelHammer(t, gravelSpots);
                nameMsg(p, t, " под гравитационным молотом!");
                return true;
            }
            case SANDSTONE_BRIDGE -> {
                int placed = 0;
                Vector dir = flatDir(p);
                Location base = p.getLocation();
                List<Location> bridgeSpots = new ArrayList<>();
                for (int i = 1; i <= 5; i++) {
                    Block f = base.clone().add(dir.clone().multiply(i)).getBlock();
                    if (f.getType().isAir()) {
                        f.setType(Material.SANDSTONE);
                        bridgeSpots.add(f.getLocation());
                        placed++;
                    }
                }
                Vector dash = dir.clone().multiply(1.1).setY(0.15);
                p.setVelocity(dash);
                p.playSound(p.getLocation(), Sound.BLOCK_STONE_PLACE, 1f, 1.2f);
                SpellFx.bridgeBuild(bridgeSpots);
                Msg.dm(p, "&eКаменный мост: &e" + placed + "&e блоков и рывок вперёд.");
                return placed > 0;
            }
            case ICE_BIND -> {
                LivingEntity t = lookEntity(p, 12);
                if (t == null) { Msg.dm(p, "&cНет цели в прицеле."); return false; }
                if (!pvpOk(p, t)) return false;
                State.FROSTED.set(t, (int) (20 * m));
                t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                        ctrl(t, 10, m) * 20, 3));
                if (t.isInWaterOrRain()) {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 6 * 20, 250));
                    t.getWorld().spawnParticle(Particle.SNOWFLAKE,
                            t.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.02);
                    SpellFx.iceBind(t, true);
                    nameMsg(p, t, " вмерз в лёд!");
                } else {
                    SpellFx.iceBind(t, false);
                    nameMsg(p, t, " сковывает льдом!");
                }
                t.getWorld().playSound(t.getLocation(), Sound.BLOCK_GLASS_PLACE, 1f, 0.5f);
                return true;
            }
            case GLASS_FADE -> {
                int dur = (int) (15 * m);
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, dur * 20, 0));
                Synergy.glassInvis(p, dur);
                SpellFx.glassFade(p, dur);
                p.playSound(p.getLocation(), Sound.BLOCK_GLASS_PLACE, 1f, 1.6f);
                Msg.dm(p, "&fСтеклянный камуфляж: &e" + dur
                        + "&f с. Вода и дождь смоют его. Атака — НЕТ.");
                return true;
            }
            case BRICKS_BASTION -> {
                int placed = 0;
                Location c = p.getLocation();
                List<Location> shell = new ArrayList<>();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            boolean shellBlock = Math.abs(dx) == 1 || Math.abs(dz) == 1 || dy == 2;
                            if (!shellBlock) continue;
                            Block f = c.getBlock().getRelative(dx, dy, dz);
                            if (f.getType().isAir() || f.isLiquid() || f.getType() == Material.FIRE) {
                                f.setType(Material.BRICKS);
                                shell.add(f.getLocation());
                                placed++;
                            }
                        }
                    }
                }
                p.playSound(p.getLocation(), Sound.BLOCK_NETHER_BRICKS_PLACE, 1f, 0.8f);
                SpellFx.bastion(shell, c);
                Msg.dm(p, "&6Кирпичный бастион: &e" + placed + "&6 блоков кладки.");
                return placed > 0;
            }
            case TERRACOTTA_MASK -> {
                int dur = (int) (20 * m);
                Synergy.terracottaMask(p, dur);
                p.getWorld().spawnParticle(Particle.DUST_PLUME,
                        p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.02);
                SpellFx.terracottaMask(p, dur);
                p.playSound(p.getLocation(), Sound.BLOCK_DECORATED_POT_INSERT, 1f, 0.7f);
                Msg.dm(p, "&6Терракотовая маска: мобы вас не видят &e" + dur + " с.");
                return true;
            }
            case CONCRETE_TOMB -> {
                LivingEntity t = lookEntity(p, 12);
                if (t == null) { Msg.dm(p, "&cНет цели в прицеле."); return false; }
                if (!pvpOk(p, t)) return false;
                int dur = (int) Math.round(5 * m);
                t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, dur * 20, 250));
                t.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, dur * 20, 249));
                t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, dur * 20, 0));
                boolean brokeCam = Synergy.dispelGlassInvis(t, "бетонный саркофаг");
                State.wipeAll(t); // стирание всех состояний — антисинергия-сброс
                t.getWorld().spawnParticle(Particle.BLOCK,
                        t.getLocation().add(0, 1, 0), 50, 0.4, 0.8, 0.4,
                        Material.WHITE_CONCRETE.createBlockData());
                SpellFx.concreteTomb(t, dur);
                nameMsg(p, t, " схвачен бетонным саркофагом! Все состояния стёрты"
                        + (brokeCam ? ", стеклянный камуфляж разбит." : "."));
                return true;
            }

            // ==================== СВЕТОВОД ====================
            case GLOWSTONE_FLASH -> {
                int hit = 0;
                int brokeGlass = 0;
                for (LivingEntity le : circle(p.getLocation(), 6)) {
                    if (le == p) continue;
                    // Свет разбивает стеклянный камуфляж (лор: «смоет свет»)
                    if (Synergy.dispelGlassInvis(le, "свет")) brokeGlass++;
                    if (le instanceof Player && !pvpOk(p, le)) continue;
                    le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, (int) (5 * m) * 20, 0));
                    hit++;
                }
                for (LivingEntity le : circle(p.getLocation(), 6)) {
                    if (le != p && State.BLOOMED.has(le)) {
                        le.setHealth(Math.min(le.getMaxHealth(), le.getHealth() + 4 * m));
                    }
                }
                p.getWorld().spawnParticle(Particle.FLASH, p.getLocation(), 1);
                SpellFx.glowFlash(p, hit);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.8f);
                Msg.dm(p, "&eСветовая вспышка: ослеплено &e" + hit
                        + (brokeGlass > 0 ? "&f, стеклянный камуфляж разбит светом: &e" + brokeGlass : ""));
                return true;
            }
            case SEA_LANTERN_BEACON -> {
                Block tb = lookBlock(p, 12);
                if (tb == null) { Msg.dm(p, "&cНет точки для маяка."); return false; }
                Block top = tb.getRelative(BlockFace.UP);
                if (!top.getType().isAir()) { Msg.dm(p, "&cНет места для маяка."); return false; }
                top.setType(Material.SEA_LANTERN);
                for (LivingEntity le : circle(p.getLocation(), 8)) {
                    if (le instanceof Player pl)
                        pl.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER,
                                (int) (30 * m) * 20, 0));
                }
                p.playSound(top.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);
                SpellFx.seaBeacon(top.getLocation(), (int) (30 * m));
                Msg.dm(p, "&bМорской маяк установлен, группа под силой источника.");
                return true;
            }
            case SHROOMLIGHT_AURA -> {
                Synergy.addAura(p, (int) (60 * m), 5);
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.1f);
                SpellFx.shroomAura(p, (int) (60 * m));
                Msg.dm(p, "&6Тёплая аура: союзники в радиусе 5 лечатся &e" + (int) (60 * m) + " с.");
                return true;
            }
            case FROG_RAIN -> {
                SpellFx.frogRain(p, (int) (60 * m));
                Synergy.summonRain(p, (int) (60 * m));
                Msg.dm(p, "&eЖабий дождь вызван на &e" + (int) (60 * m)
                        + "&e с. ВЕЧНОЕ горение гаснет по всему миру; молнии ищут позолоченных!");
                return true;
            }
            case CANDLE_RITUAL -> {
                Block tb = lookBlock(p, 8);
                if (tb == null) { Msg.dm(p, "&cНет точки для ритуала."); return false; }
                Block center = tb.getRelative(BlockFace.UP);
                int placed = 0;
                List<Location> candleSpots = new ArrayList<>();
                int[][] ring = {{0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}, {-1, -1}};
                for (int[] off : ring) {
                    Block f = tb.getRelative(off[0], 1, off[1]);
                    if (f.getType().isAir() && f.getRelative(BlockFace.DOWN).getType().isSolid()) {
                        f.setType(Material.CANDLE);
                        candleSpots.add(f.getLocation());
                        placed++;
                    }
                }
                Synergy.addRitual(center.getLocation(), (int) (20 * m), 3);
                p.playSound(center.getLocation(), Sound.BLOCK_CANDLE_EXTINGUISH, 1f, 0.6f);
                SpellFx.candleRitual(center.getLocation(), candleSpots);
                Msg.dm(p, "&eСвечной ритуал: &e" + placed + "&e свечей, сила посохов в зоне +25%.");
                return placed > 0;
            }
            case GLOWFLORA_INNERLIGHT -> {
                LivingEntity t = shift ? lookEntity(p, 8) : p;
                if (shift && t == null) t = p;
                int dur = (int) (60 * m);
                t.setGlowing(true);
                Synergy.tempGlow(t, dur);
                t.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, dur * 20, 0));
                fx(t.getLocation().add(0, 1, 0), Particle.GLOW, 25);
                SpellFx.innerLight(t, dur);
                return true;
            }

            // ==================== ИНЖЕНЕР ====================
            case MECH_PUSH -> {
                LivingEntity t = lookEntity(p, 10);
                if (t == null) { Msg.dm(p, "&cНет цели в прицеле."); return false; }
                if (!pvpOk(p, t)) return false;
                Vector push = t.getLocation().toVector()
                        .subtract(p.getLocation().toVector()).normalize().multiply(1.5 * m).setY(0.45);
                t.setVelocity(push);
                t.getWorld().spawnParticle(Particle.CRIT,
                        t.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.1);
                SpellFx.mechPush(p, t);
                t.getWorld().playSound(t.getLocation(), Sound.BLOCK_PISTON_EXTEND, 1f, 0.7f);
                nameMsg(p, t, " отброшен поршневым толчком!");
                return true;
            }
            case GALVANIC_ARC -> {
                LivingEntity first = lookEntity(p, 10);
                if (first == null) { Msg.dm(p, "&cНет цели в прицеле."); return false; }
                if (!pvpOk(p, first)) return false;
                LivingEntity cur = first;
                Location last = p.getLocation();
                List<Location> chain = new ArrayList<>();
                chain.add(last.clone());
                int chains = Math.min(3, 1 + (tier >= 3 ? 2 : tier == 2 ? 1 : 0));
                double dmg = 3 * m;
                LivingEntity skip = null;
                for (int i = 0; i < chains && cur != null; i++) {
                    drawBeam(last, cur.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK);
                    chain.add(cur.getLocation().add(0, 1, 0));
                    double dd = (State.CHARGED.has(cur) || State.SOAKED.has(cur)) ? dmg * 2 : dmg;
                    staffHit(p, cur, dd, Magic.Element.LIGHTNING);
                    State.CHARGED.apply(cur, 10);
                    last = cur.getLocation().add(0, 1, 0);
                    skip = cur;
                    cur = nextChainTarget(last, p, skip);
                }
                SpellFx.galvanicArc(chain);
                p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.4f, 1.6f);
                return true;
            }
            case RAIL_DASH -> {
                Vector dir = flatDir(p);
                int placed = 0;
                List<Location> railSpots = new ArrayList<>();
                for (int i = 1; i <= 8; i++) {
                    Block base = p.getLocation().clone().add(dir.clone().multiply(i)).getBlock();
                    if (base.getType().isSolid() && base.getRelative(BlockFace.UP).getType().isAir()) {
                        base.getRelative(BlockFace.UP).setType(Material.RAIL);
                        railSpots.add(base.getRelative(BlockFace.UP).getLocation());
                        placed++;
                    }
                }
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, (int) (10 * m) * 20, 1));
                p.playSound(p.getLocation(), Sound.ENTITY_MINECART_RIDING, 0.8f, 1.4f);
                SpellFx.railDash(railSpots, p);
                Msg.dm(p, "&7Рельсовый разгон: &e" + placed + "&7 рельсов.");
                return placed > 0;
            }

            // ==================== НЕЗЕР ====================
            case NETHERRACK_PYRE -> {
                LivingEntity t = lookEntity(p, 10);
                Block tb = t != null ? t.getLocation().getBlock() : lookBlock(p, 12);
                if (tb == null) { Msg.dm(p, "&cНет цели."); return false; }
                Block top = tb.getRelative(BlockFace.UP);
                if (t != null && t.isOnGround()) top = t.getLocation().getBlock();
                if (!top.getType().isAir() && top.getType() != Material.FIRE) {
                    Msg.dm(p, "&cНет места для огня.");
                    return false;
                }
                top.setType(Material.FIRE);
                boolean eternal = top.getRelative(BlockFace.DOWN).getType() == Material.NETHERRACK;
                // Связка ТЗ: угольная цель над вечным огнём вспыхивает НАВСЕГДА
                if (eternal && t != null && State.CARBON_COATED.has(t)
                        && !EternalBurn.burning(t) && pvpOk(p, t)) {
                    EternalBurn.ignite(t);
                    nameMsg(p, t, " вспыхнул ВЕЧНЫМ огнём на адской подложке!");
                }
                top.getWorld().spawnParticle(Particle.FLAME, top.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0.01);
                SpellFx.pyre(top.getLocation(), eternal, t);
                p.playSound(top.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.8f, 1f);
                Msg.dm(p, eternal ? "&cОгонь на незерраке — ВЕЧНЫЙ."
                        : "&cОгонь подожжён (обычный).");
                return true;
            }
            case SOUL_FLAME -> {
                LivingEntity t = lookEntity(p, 12);
                if (t == null) { Msg.dm(p, "&cНет цели в прицеле."); return false; }
                if (!pvpOk(p, t)) return false;
                staffHit(p, t, 4 * m, Magic.Element.DARK);
                State.SOUL_MARKED.set(t, (int) (20 * m));
                t.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                        t.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.02);
                SpellFx.soulFlame(t);
                t.getWorld().playSound(t.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.6f);
                nameMsg(p, t, " отмечен пламенем душ: телепорты закрыты!");
                return true;
            }
            case MAGMA_IGNITE -> {
                LivingEntity t = lookEntity(p, 12);
                if (t == null) { Msg.dm(p, "&cНет цели в прицеле."); return false; }
                if (!pvpOk(p, t)) return false;
                if (State.SOAKED.has(t)) {
                    t.getWorld().spawnParticle(Particle.SMOKE,
                            t.getLocation().add(0, 1, 0), 30, 0.4, 0.4, 0.4, 0.02);
                    t.getWorld().playSound(t.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1f);
                    Msg.dm(p, "&7Магма шипит и не берёт влажную цель.");
                    return false;
                }
                if (State.CARBON_COATED.has(t)) {
                    EternalBurn.ignite(t);
                    t.getWorld().spawnParticle(Particle.FLAME,
                            t.getLocation().add(0, 1, 0), 60, 0.5, 0.9, 0.5, 0.05);
                    SpellFx.magmaIgnite(t, true);
                    t.getWorld().playSound(t.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.6f);
                    Msg.dm(p, "&4&lВЕЧНОЕ ГОРЕНИЕ! &cУголь вспыхнул навсегда — спасёт только вода.");
                    if (t instanceof Player pt) pt.sendActionBar(T.color("&4&lТЫ ГОРИШЬ ВЕЧНО! Вода!"));
                    return true;
                }
                t.setFireTicks((int) (8 * m) * 20);
                t.getWorld().spawnParticle(Particle.FLAME,
                        t.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.02);
                SpellFx.magmaIgnite(t, false);
                nameMsg(p, t, " подожжён магмовым жаром!");
                return true;
            }
            case BLACKSTONE_VORTEX -> {
                Block tb = lookBlock(p, 12);
                Location c = tb != null ? tb.getLocation().add(0.5, 1, 0.5)
                        : p.getLocation().clone().add(p.getEyeLocation().getDirection().normalize().multiply(5));
                int hit = 0;
                for (LivingEntity le : circle(c, 6)) {
                    if (le == p || !pvpOk(p, le)) continue;
                    Vector pull = c.toVector().subtract(le.getLocation().toVector())
                            .normalize().multiply(0.9).setY(0.25);
                    le.setVelocity(pull);
                    hit++;
                }
                c.getWorld().spawnParticle(Particle.PORTAL, c, 80, 3, 1.5, 3, 0.3);
                SpellFx.vortex(c, 3);
                c.getWorld().playSound(c, Sound.ENTITY_WITHER_SHOOT, 0.6f, 1.4f);
                Msg.dm(p, "&8Чёрное притяжение: стянуто &e" + hit);
                return true;
            }
            case BASALT_PILLAR -> {
                LivingEntity t = lookEntity(p, 12);
                if (t == null) { Msg.dm(p, "&cНет цели в прицеле."); return false; }
                if (!pvpOk(p, t)) return false;
                Location c = t.getLocation();
                int placed = 0;
                List<Location> basaltSpots = new ArrayList<>();
                for (int dy = 0; dy < 4; dy++) {
                    Block f = c.getWorld().getBlockAt(c.getBlockX(), c.getBlockY() - 1 + dy, c.getBlockZ());
                    if (f.getType().isAir() || f.isLiquid() || f.getType() == Material.GRAVEL
                            || f.getType() == Material.SAND) {
                        f.setType(Material.BASALT);
                        basaltSpots.add(f.getLocation());
                        placed++;
                    }
                }
                t.setVelocity(new Vector(0, 0.55, 0));
                c.getWorld().playSound(c, Sound.BLOCK_BASALT_PLACE, 1f, 0.8f);
                SpellFx.pillarRise(t, basaltSpots);
                nameMsg(p, t, " взнесён базальтовым шпилем!");
                return true;
            }
            case CRIMSON_RAGE -> {
                LivingEntity t = lookEntity(p, 12);
                if (t == null) { Msg.dm(p, "&cНет цели в прицеле."); return false; }
                if (!pvpOk(p, t)) return false;
                if (t instanceof Mob mob) {
                    LivingEntity nearest = null;
                    double best = Double.MAX_VALUE;
                    for (LivingEntity le : circle(t.getLocation(), 8)) {
                        if (le == t || le == p) continue;
                        double dist = le.getLocation().distanceSquared(t.getLocation());
                        if (dist < best) { best = dist; nearest = le; }
                    }
                    mob.setTarget(nearest);
                    mob.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 10 * 20, 0));
                } else if (t instanceof Player pt) {
                    Synergy.rage(pt, 10);
                }
                t.getWorld().spawnParticle(Particle.ANGRY_VILLAGER,
                        t.getLocation().add(0, 2, 0), 8, 0.4, 0.4, 0.4, 0.05);
                SpellFx.crimsonRage(t);
                t.getWorld().playSound(t.getLocation(), Sound.ENTITY_HOGLIN_ANGRY, 1f, 1f);
                nameMsg(p, t, " впадает в багровое бешенство!");
                return true;
            }
            case WARPED_SHIFT -> {
                LivingEntity t = lookEntity(p, 12);
                if (t == null) { Msg.dm(p, "&cНет цели в прицеле."); return false; }
                if (!pvpOk(p, t)) return false;
                if (State.SOUL_MARKED.has(t)) {
                    Msg.dm(p, "&7Душевная печать не пускает искривление!");
                    return false;
                }
                Location from = t.getLocation();
                double ang = Math.random() * Math.PI * 2;
                double dist = 3 + Math.random() * 5;
                Location to = from.clone().add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist);
                Block ground = to.getWorld().getHighestBlockAt(to);
                to = ground.getLocation().add(0.5, 1, 0.5);
                t.getWorld().spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.3);
                t.teleport(to);
                t.getWorld().spawnParticle(Particle.PORTAL, to.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.3);
                SpellFx.warpedShift(from, to);
                t.getWorld().playSound(to, Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.7f);
                nameMsg(p, t, " искривлён в случайную точку!");
                return true;
            }
            case WART_ROT -> {
                LivingEntity t = lookEntity(p, 12);
                if (t == null) { Msg.dm(p, "&cНет цели в прицеле."); return false; }
                if (!pvpOk(p, t)) return false;
                t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, (int) (8 * m) * 20, 0));
                State.NECROSIS.set(t, 8);
                t.getWorld().spawnParticle(Particle.SCULK_SOUL,
                        t.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.02);
                SpellFx.wartRot(t);
                nameMsg(p, t, " поражён гнилью!");
                return true;
            }

            // ==================== КРАЙ ====================
            case ENDSTONE_OBELISK -> {
                Block tb = lookBlock(p, 12);
                if (tb == null) { Msg.dm(p, "&cНет точки для обелиска."); return false; }
                Block top = tb.getRelative(BlockFace.UP);
                if (!top.getType().isAir()) { Msg.dm(p, "&cНет места для обелиска."); return false; }
                top.setType(Material.END_STONE);
                Block top2 = top.getRelative(BlockFace.UP);
                if (top2.getType().isAir()) top2.setType(Material.END_STONE_BRICKS);
                Synergy.addObeliskZone(top.getLocation(), (int) (30 * m), 4);
                top.getWorld().playSound(top.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.4f, 1.8f);
                SpellFx.obelisk(top.getLocation());
                Msg.dm(p, "&5Пустотный обелиск: телепорты в зоне запрещены &e" + (int) (30 * m) + " с.");
                return true;
            }
            case PURPUR_LEVITATE -> {
                LivingEntity t = shift ? lookEntity(p, 10) : p;
                if (shift && t == null) t = p;
                if (t != p && !pvpOk(p, t)) return false;
                int dur = ctrl(t, 10, m);
                t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, dur * 20, 0));
                fx(t.getLocation(), Particle.REVERSE_PORTAL, 30);
                SpellFx.levitate(t, dur);
                t.getWorld().playSound(t.getLocation(), Sound.ENTITY_SHULKER_SHOOT, 1f, 1.6f);
                return true;
            }
            case CHORUS_PHASE -> {
                Block tb = lookBlock(p, 12);
                if (tb == null) { Msg.dm(p, "&cНет точки перелёта."); return false; }
                Location from = p.getLocation();
                Location to = tb.getLocation().add(0.5, 1.2, 0.5);
                p.getWorld().spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.3);
                p.teleport(to);
                State.wipeAll(p); // фазовый сдвиг смывает ваши состояния
                p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, to, 30, 0.3, 0.6, 0.3, 0.05);
                SpellFx.chorusPhase(from, to);
                p.playSound(to, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1f, 1.2f);
                Msg.dm(p, "&dХорусовый перелёт: состояния смыты фазовым сдвигом.");
                return true;
            }
            case OBSIDIAN_SKIN -> {
                int dur = (int) (10 * m);
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, dur * 20, 2));
                Synergy.obsidianSkin(p, dur);
                p.getWorld().spawnParticle(Particle.DUST_PILLAR,
                        p.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4,
                        new Particle.DustOptions(org.bukkit.Color.BLACK, 1.4f));
                SpellFx.obsidianSkin(p, dur);
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.8f, 0.6f);
                Msg.dm(p, "&5Обсидиановая кожа: сопротивление III, отбрасывание бессильно.");
                return true;
            }

            // ==================== АНОМАЛИИ ====================
            case SCULK_PULSE -> {
                int lit = 0, burst = 0;
                for (LivingEntity le : circle(p.getLocation(), 16)) {
                    if (le == p) continue;
                    le.setGlowing(true);
                    Synergy.tempGlow(le, 5);
                    if (State.ECHO_TAGGED.has(le)) {
                        if (pvpOk(p, le)) {
                            staffHit(p, le, 6 * m, Magic.Element.ECHO);
                            State.NECROSIS.set(le, 4); // эхо выедает изнутри
                            burst++;
                        }
                    } else {
                        State.ECHO_TAGGED.apply(le, 5);
                    }
                    lit++;
                }
                p.playSound(p.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 1f, 0.8f);
                SpellFx.sculkPulse(p, lit);
                Msg.dm(p, "&9Эхо-импульс: подсвечено &e" + lit + "&9, резонансных разрядов &e" + burst);
                return true;
            }
            default -> {
                Msg.dm(p, "&cМеханика " + mech + " не реализована.");
                return false;
            }
        }
    }

    /** Ближайшая цель для цепи гальванической дуги. */
    private static LivingEntity nextChainTarget(Location from, Player p, LivingEntity skip) {
        LivingEntity best = null;
        double bestDist = 5 * 5;
        for (Entity e : from.getWorld().getNearbyEntities(from, 5, 5, 5)) {
            if (!(e instanceof LivingEntity le) || le.isDead() || le == skip || le == p) continue;
            if (le instanceof Player && !pvpAllowed(p, le)) continue;
            double d = le.getLocation().distanceSquared(from);
            if (d < bestDist) { bestDist = d; best = le; }
        }
        return best;
    }

    private static boolean pvpAllowed(Player p, LivingEntity target) {
        return Synergy.pvpAllowed(p, target);
    }

    private static void drawBeam(Location from, Location to, Particle particle) {
        Vector v = to.toVector().subtract(from.toVector());
        double len = v.length();
        if (len < 0.1) return;
        v.normalize();
        for (double d = 0; d < len; d += 0.5) {
            Location point = from.toVector().add(v.clone().multiply(d)).toLocation(from.getWorld());
            from.getWorld().spawnParticle(particle, point, 1, 0, 0, 0, 0);
        }
    }

    /** Угольная пыль: эмиттер удушения. Топливо списано в StaffUse; здесь кулдаун. */
    public static boolean dustEmitter(Player p) {
        long cd = Cooldowns.left(p.getUniqueId(), "dust");
        if (cd > 0) {
            Msg.dm(p, "&7Угольная пыль будет готова через &e" + cd + " с.");
            return false;
        }
        Synergy.startDust(p, Conf.dustDuration());
        Cooldowns.set(p.getUniqueId(), "dust", Conf.dustCooldown());
        Msg.dm(p, "&8Угольная пыль идёт &e" + Conf.dustDuration()
                + "&8 с: &7удушение в радиусе " + Conf.dustRadius() + ".");
        return true;
    }

    private static void nameMsg(Player p, LivingEntity t, String what) {
        String name = t.customName() != null
                ? PlainTextComponentSerializer.plainText().serialize(t.customName())
                : t.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        Msg.dm(p, "&e" + name + "&f" + what);
    }
}
