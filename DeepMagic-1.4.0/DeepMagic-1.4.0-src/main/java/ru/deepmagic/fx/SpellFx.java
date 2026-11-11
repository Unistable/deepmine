package ru.deepmagic.fx;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import ru.deepmagic.model.Magic;

import java.util.ArrayList;
import java.util.List;

/**
 * SpellFx — персональные зрелищные сцены каждого посоха DeepMagic 1.2.0.
 * Правило ТЗ: «не просто частицы, а что-то особенное у каждого» —
 * физичные блоки, эффект тотема, взаимодействие с окружением, анимации.
 * Каждый метод — один посох; gameplay-логика остаётся в Mechanics.
 */
public final class SpellFx {

    private SpellFx() {}

    // палитра стихий
    private static final Color BLACK = Color.fromRGB(0x2B2B2B);
    private static final Color GOLD = Color.fromRGB(0xFFD257);
    private static final Color LAPIS = Color.fromRGB(0x2A5FDE);
    private static final Color ICE = Color.fromRGB(0xA9E9FF);
    private static final Color EMERALD = Color.fromRGB(0x41C05A);
    private static final Color FIRE = Color.fromRGB(0xFF7A21);
    private static final Color SOUL = Color.fromRGB(0x4FD0C9);
    private static final Color VOID = Color.fromRGB(0x9B59D0);
    private static final Color SAND = Color.fromRGB(0xE7D9A8);
    private static final Color ECHO = Color.fromRGB(0x18B5C2);
    private static final Color CRIMSON = Color.fromRGB(0xB02E26);
    private static final Color HONEY = Color.fromRGB(0xE8A33D);
    private static final Color WHITE = Color.fromRGB(0xF5F5F5);
    private static final Color GREEN = Color.fromRGB(0x587B3A);
    private static final Color REDSTONE = Color.fromRGB(0xD8382A);
    private static final Color WARM = Color.fromRGB(0xFFA53C);

    // ==================== ШАХТЁР ====================

    /** Каменный панцирь: каменные глыбы выходят на орбиту вокруг мага. */
    public static void stoneShell(Player p, int seconds) {
        Spectacle.orbitBlocks(p, Material.COBBLESTONE.createBlockData(),
                6, 1.05, 1.0, 0.3f, 4.5, Math.min(seconds, 20));
        Spectacle.implosion(p.getLocation().add(0, 1, 0), 2.2, 8, Particle.CRIT, null, 0);
        Spectacle.spiral(p.getLocation(), 0.8, 2.4, 14, null, Color.fromRGB(0x8A8A8A), 1.1f);
    }

    /** Угольное покрытие: пылевой вихрь стягивается в цель (орбиты и тотем даёт State). */
    public static void carbonCoat(LivingEntity t) {
        Spectacle.implosion(t.getLocation().add(0, 1, 0), 2.6, 10, Particle.SMOKE, BLACK, 1.2f);
        Spectacle.spiral(t.getLocation(), 0.7, 2.6, 16, Particle.ASH, BLACK, 1.0f);
    }

    /** Медный якорь: стержень «гудит», искры стягиваются с неба. */
    public static void copperAnchor(Location anchor) {
        Spectacle.spiral(anchor.clone().add(0, 0.5, 0), 0.9, 3.0, 20,
                Particle.END_ROD, Color.fromRGB(0xC97C4A), 1.0f);
        Spectacle.ringExpand(anchor, 3.5, 10, Particle.ELECTRIC_SPARK,
                Color.fromRGB(0xE8955A), 1.1f);
        Spectacle.sound(anchor, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 0.6f);
    }

    /** Железное ярмо: клетка из железа захлопывается. */
    public static void ironYoke(LivingEntity t) {
        Spectacle.cage(t, Material.IRON_BARS.createBlockData(), 0.5f, 16);
        Spectacle.implosion(t.getLocation().add(0, 1, 0), 2.4, 8, Particle.CRIT,
                Color.fromRGB(0xC8C8C8), 1.2f);
        Spectacle.sound(t.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.7f, 1.6f);
    }

    /** Золотой подкуп: расходящиеся золотые волны. */
    public static void goldBribe(LivingEntity t) {
        Spectacle.ringExpand(t.getLocation(), 3.0, 10, Particle.GLOW, GOLD, 1.2f);
    }

    /** Импульсный разряд: красный луч и шоквейв (или вспышка на рычаге). */
    public static void redstonePulse(Player p, LivingEntity t, Location lever) {
        if (t != null) {
            Spectacle.beam(p.getEyeLocation(), t.getLocation().add(0, 1, 0),
                    Particle.ELECTRIC_SPARK, REDSTONE, 1.3f, 8, 0.45);
            Spectacle.ringExpand(t.getLocation(), 2.4, 8, Particle.ELECTRIC_SPARK,
                    REDSTONE, 1.2f);
        } else if (lever != null) {
            Spectacle.burst(lever.clone().add(0.5, 0.5, 0.5), Particle.ELECTRIC_SPARK, 26, 0.4);
            Spectacle.ringDust(lever, 1.4, REDSTONE, 1.2f);
        }
    }

    /** Лазурная печать: синий луч-проводка к цели. */
    public static void lapisMark(Player p, LivingEntity t) {
        Spectacle.beam(p.getEyeLocation(), t.getLocation().add(0, 1, 0),
                Particle.ENCHANT, LAPIS, 1.2f, 10, 0.4);
    }

    /** Кварцевый пинг: сонарные кольца + вспышка на каждой находке. */
    public static void quartzPing(Player p, int found) {
        Spectacle.ringExpand(p.getLocation(), 12, 24, Particle.END_ROD, WHITE, 1.0f);
        if (found > 0) {
            Spectacle.sound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.8f);
        }
    }

    /** Алмазная грань: веер ледяных клинков в конусе. */
    public static void diamondEdge(Player p, List<LivingEntity> targets) {
        org.bukkit.util.Vector dir = p.getEyeLocation().getDirection().normalize();
        for (int i = -1; i <= 1; i++) {
            org.bukkit.util.Vector v = dir.clone();
            org.bukkit.util.Vector side = new org.bukkit.util.Vector(-v.getZ(), 0, v.getX())
                    .normalize().multiply(i * 0.9);
            Location to = p.getEyeLocation().clone().add(v.multiply(6)).add(side);
            Spectacle.beam(p.getEyeLocation(), to, Particle.SNOWFLAKE, ICE, 1.0f, 8, 0.35);
        }
        for (LivingEntity t : targets) {
            Spectacle.burst(t.getLocation().add(0, 1, 0), Particle.ITEM_SNOWBALL, 14, 0.4);
        }
    }

    /** Изумрудный договор: живородящая спираль и парящие изумруды. */
    public static void emeraldDeal(LivingEntity t) {
        Spectacle.spiral(t.getLocation(), 0.8, 2.6, 18, Particle.HAPPY_VILLAGER,
                EMERALD, 1.1f);
        Spectacle.itemBurst(t.getLocation().add(0, 1.2, 0), Material.EMERALD, 5, 1.4, 14);
        Spectacle.ringExpand(t.getLocation(), 2.0, 8, Particle.HEART, null, 0);
    }

    /** Незеритовая волна: двойной шоквейв и разлёт древних осколков. */
    public static void debrisQuake(Location c) {
        Spectacle.ringExpand(c, 3.5, 10, Particle.FLAME, FIRE, 1.4f);
        Spectacle.ringExpand(c, 2.5, 8, Particle.LAVA, null, 0);
        Spectacle.itemBurst(c.clone().add(0, 0.8, 0), Material.NETHERITE_SCRAP, 6, 1.8, 16);
        Spectacle.burst(c, Particle.LAVA, 8, 0.6);
        Spectacle.sound(c, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 0.7f);
    }

    /** Аметистовый резонанс: двойные эхо-волны по площади. */
    public static void amethystResonate(Player p) {
        Spectacle.ringExpand(p.getLocation(), 8, 20, Particle.NOTE,
                Color.fromRGB(0xB07FE0), 1.1f);
        Spectacle.ringExpand(p.getLocation(), 5, 14, null,
                Color.fromRGB(0x8A5FD0), 0.9f);
    }

    // ==================== ЛЕСОРУБ ====================

    /** Кора дуба: живая кора собирается кольцом. */
    public static void oakBark(Player p, int seconds) {
        Spectacle.orbitBlocks(p, Material.OAK_LOG.createBlockData(),
                5, 1.0, 1.0, 0.28f, 4.0, Math.min(seconds, 12));
        Spectacle.ringExpand(p.getLocation(), 2.2, 8, Particle.COMPOSTER, GREEN, 1.0f);
    }

    /** Хвойный конус: ледяная завеса в конусе взгляда. */
    public static void spruceCone(Player p, List<LivingEntity> targets) {
        org.bukkit.util.Vector dir = p.getEyeLocation().getDirection().normalize();
        for (int i = -1; i <= 1; i += 2) {
            org.bukkit.util.Vector side = new org.bukkit.util.Vector(-dir.getZ(), 0, dir.getX())
                    .normalize().multiply(i * 1.4);
            Location to = p.getEyeLocation().clone().add(dir.clone().multiply(6)).add(side);
            Spectacle.beam(p.getEyeLocation(), to, Particle.SNOWFLAKE, ICE, 1.0f, 8, 0.4);
        }
        Spectacle.beam(p.getEyeLocation(),
                p.getEyeLocation().clone().add(dir.clone().multiply(6)),
                Particle.SNOWFLAKE, ICE, 1.0f, 8, 0.4);
        for (LivingEntity t : targets) {
            Spectacle.ringDust(t.getLocation(), 1.0, ICE, 1.0f);
        }
    }

    /** Берёзовый сок: очистительный столб света. */
    public static void birchCleanse(LivingEntity t) {
        Spectacle.spiral(t.getLocation(), 0.7, 2.8, 18, Particle.END_ROD, WHITE, 1.0f);
        Spectacle.ringExpand(t.getLocation(), 2.4, 10, Particle.COMPOSTER,
                Color.fromRGB(0xDFF3E0), 1.0f);
    }

    /** Лиана-крюк: анимированная лиана к цели/блоку. */
    public static void jungleHook(Player p, LivingEntity target, Location block) {
        Location to = target != null ? target.getLocation().add(0, 1, 0) : block;
        if (to == null) return;
        Spectacle.beam(p.getEyeLocation(), to, Particle.WAX_ON,
                Color.fromRGB(0x3E7A2E), 1.1f, 10, 0.35);
        Spectacle.burst(to, Particle.CHERRY_LEAVES, 10, 0.4);
    }

    /** Колючий ореол: шипы выходят на орбиту. */
    public static void acaciaThorns(Player p, int seconds) {
        Spectacle.orbitBlocks(p, Material.POINTED_DRIPSTONE.createBlockData(),
                5, 1.0, 1.1, 0.25f, 5.5, Math.min(seconds, 15));
        Spectacle.ringExpand(p.getLocation(), 2.0, 8, Particle.CRIT, GOLD, 1.0f);
    }

    /** Тёмный покров: ночь опускается куполом. */
    public static void darkVeil(Location c) {
        Spectacle.implosion(c.clone().add(0, 1.5, 0), 4.0, 14, Particle.SQUID_INK, BLACK, 1.5f);
        Spectacle.ringExpand(c, 4.5, 12, Particle.WHITE_SMOKE, BLACK, 1.4f);
        Spectacle.sound(c, Sound.ENTITY_WITHER_SHOOT, 0.5f, 0.6f);
    }

    /** Мангровые корни: корневая сеть прорастает снизу. */
    public static void rootsRise(List<Location> placed, Location c) {
        if (!placed.isEmpty()) {
            Spectacle.blocksDescend(placed, Material.MANGROVE_ROOTS.createBlockData(),
                    0.9f, 2.0, 18);
        }
        Spectacle.ringExpand(c, 3.0, 10, Particle.COMPOSTER, GREEN, 1.2f);
    }

    /** Лепестковый вихрь: розовый шторм вокруг мага. */
    public static void cherryStorm(Player p) {
        Spectacle.spiral(p.getLocation(), 1.4, 2.6, 20, Particle.CHERRY_LEAVES,
                Color.fromRGB(0xF5B8D9), 1.0f);
        Spectacle.spiral(p.getLocation(), 1.0, 2.2, 20, Particle.HEART, null, 0);
    }

    /** Бледное окаменение: серая пыль стягивается в цель. */
    public static void petrify(LivingEntity t) {
        Spectacle.implosion(t.getLocation().add(0, 1, 0), 2.6, 12, Particle.ASH,
                Color.fromRGB(0xB8B8B0), 1.2f);
        Spectacle.cage(t, Material.PALE_MOSS_BLOCK.createBlockData(), 0.22f, 14);
    }

    /** Смоляной капкан: янтарный кокон. */
    public static void resinTrap(LivingEntity t) {
        Spectacle.cage(t, Material.HONEY_BLOCK.createBlockData(), 0.3f, 22);
        Spectacle.ringDust(t.getLocation(), 1.4, HONEY, 1.3f);
        Spectacle.burst(t.getLocation().add(0, 1.5, 0), Particle.FALLING_HONEY, 20, 0.4);
    }

    // ==================== ЗЕМЛЕДЕЛЕЦ ====================

    /** Урожайный призыв: золотой вихрь стягивает дроп. */
    public static void wheatHarvest(Player p, int pulled) {
        Spectacle.spiral(p.getLocation(), 2.2, 2.8, 18, Particle.HAPPY_VILLAGER, GOLD, 1.0f);
        if (pulled > 0) Spectacle.itemBurst(p.getLocation().add(0, 1, 0),
                Material.WHEAT, Math.min(6, 2 + pulled / 3), 1.2, 12);
    }

    /** Морковное зрение: витаминная вспышка. */
    public static void carrotVision(LivingEntity t, int seconds) {
        Spectacle.orbitItems(t, Material.CARROT, 4, 0.9, 1.1, 0.35f, 5.0,
                Math.min(seconds, 8));
        Spectacle.ringExpand(t.getLocation(), 2.2, 8, Particle.GLOW,
                Color.fromRGB(0xE8952A), 1.1f);
    }

    /** Полевая кухня: пар, дым и румяная выпечка. */
    public static void potatoKitchen(Player p) {
        Location c = p.getLocation().add(0, 1, 0);
        c.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, c, 12, 0.6, 0.8, 0.6, 0.01);
        Spectacle.itemBurst(c, Material.BAKED_POTATO, 5, 1.3, 14);
        Spectacle.ringExpand(p.getLocation(), 2.5, 10, Particle.WHITE_SMOKE, WARM, 1.0f);
    }

    /** Свекольный обед: трапеза разлетается к группе. */
    public static void beetFeast(Player p, List<LivingEntity> healed) {
        for (LivingEntity t : healed) {
            Spectacle.beam(p.getEyeLocation(), t.getLocation().add(0, 1, 0),
                    Particle.HEART, Color.fromRGB(0xC23B57), 1.1f, 8, 0.45);
        }
        Spectacle.itemBurst(p.getLocation().add(0, 1.2, 0), Material.BEETROOT, 5, 1.3, 14);
    }

    /** Арбузная влага: водяной купол гасит всё. */
    public static void melonQuench(Player p, int extinguished) {
        Spectacle.ringExpand(p.getLocation(), 6, 16, Particle.SPLASH,
                Color.fromRGB(0x64C8FF), 1.2f);
        Spectacle.ringExpand(p.getLocation(), 4, 12, Particle.FALLING_WATER, null, 0);
        Spectacle.itemBurst(p.getLocation().add(0, 1.4, 0), Material.MELON_SLICE, 7, 1.7, 16);
        if (extinguished > 0) {
            Spectacle.sound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1f);
        }
    }

    /** Лик урожая: тыквы-страшилища летят в толпу. */
    public static void pumpkinScare(Player p, List<LivingEntity> targets) {
        int n = Math.min(targets.size(), 3);
        for (int i = 0; i < n; i++) {
            LivingEntity t = targets.get(i);
            Spectacle.itemProjectile(p.getEyeLocation(), t.getLocation().add(0, 1, 0),
                    Material.CARVED_PUMPKIN, Particle.SQUID_INK, Particle.SOUL_FIRE_FLAME, 10);
        }
        Spectacle.ringExpand(p.getLocation(), 4.0, 12, Particle.SQUID_INK, BLACK, 1.3f);
    }

    /** Сахарный рывок: линии скорости и взрыв сахара. */
    public static void caneRush(Player p, int seconds) {
        Location from = p.getLocation().add(0, 1, 0);
        Location to = from.clone().add(p.getLocation().getDirection().setY(0).normalize().multiply(-4));
        Spectacle.beam(from, to, Particle.CLOUD, WHITE, 0.9f, 10, 0.4);
        Spectacle.itemBurst(p.getLocation().add(0, 1, 0), Material.SUGAR, 6, 1.5, 12);
    }

    /** Какао-плоть: шоколадные волны защиты. */
    public static void cocoaFortify(Player p) {
        Spectacle.ringExpand(p.getLocation(), 2.4, 10, Particle.NOTE,
                Color.fromRGB(0x7A4A2A), 1.2f);
        Spectacle.spiral(p.getLocation(), 0.8, 2.2, 14, null,
                Color.fromRGB(0x8A5A3A), 1.2f);
    }

    /** Наростовая зараза: снаряд-бомба по дуге + облако. */
    public static void wartBomb(Player p, Location c) {
        Spectacle.itemProjectile(p.getEyeLocation(), c.clone().add(0, 0.5, 0),
                Material.NETHER_WART, Particle.DRAGON_BREATH, Particle.DRAGON_BREATH, 12);
        Spectacle.implosion(c.clone().add(0, 0.6, 0), 2.2, 10, Particle.DRAGON_BREATH,
                Color.fromRGB(0x8A2A8A), 1.2f);
        Spectacle.ringExpand(c, 2.6, 10, Particle.SPORE_BLOSSOM_AIR,
                Color.fromRGB(0x9A3A9A), 1.0f);
    }

    /** Шерстяная тишина: кокон оборачивает цель. */
    public static void woolSilence(LivingEntity t, int seconds) {
        Spectacle.cage(t, Material.WHITE_WOOL.createBlockData(), 0.3f,
                Math.min(seconds * 10, 60));
        Spectacle.implosion(t.getLocation().add(0, 1, 0), 2.0, 10, Particle.WHITE_SMOKE,
                WHITE, 1.2f);
    }

    /** Медовый залив: янтарный купол над лужей. */
    public static void honeyGlaze(Location c, int radius) {
        Spectacle.spiral(c, radius * 0.8, 2.2, 18, Particle.FALLING_HONEY, HONEY, 1.2f);
        Spectacle.ringExpand(c, radius + 1.5, 12, Particle.FALLING_HONEY, HONEY, 1.3f);
    }

    // ==================== ФЛОРИСТ ====================

    /** Кактусовое кольцо: шипастая стена прорастает. */
    public static void cactusRing(List<Location> placed, Location c) {
        if (!placed.isEmpty()) {
            Spectacle.blocksDescend(placed, Material.CACTUS.createBlockData(), 0.95f, 2.2, 18);
        }
        Spectacle.ringExpand(c, 3.5, 12, Particle.CRIT, GREEN, 1.1f);
    }

    /** Бамбуковый батут: пружина бамбука под ногами. */
    public static void bambooJump(Player p, int seconds) {
        Spectacle.itemBurst(p.getLocation().add(0, 0.4, 0), Material.BAMBOO, 7, 1.6, 14);
        Spectacle.spiral(p.getLocation(), 0.9, 2.8, 16, Particle.COMPOSTER, EMERALD, 1.0f);
    }

    /** Цветочное обаяние: букет окружает цель. */
    public static void flowerCharm(LivingEntity t, int seconds) {
        Spectacle.itemBurst(t.getLocation().add(0, 1.3, 0), Material.POPPY, 6, 1.2, 14);
        Spectacle.spiral(t.getLocation(), 0.8, 2.4, 16, Particle.CHERRY_LEAVES,
                Color.fromRGB(0xF5B8D9), 1.0f);
    }

    /** Мшистая мягкость: зелёный купол зоны. */
    public static void mossCushion(Location c, int radius) {
        Spectacle.ringExpand(c, radius + 1.5, 14, Particle.COMPOSTER, GREEN, 1.2f);
        Spectacle.spiral(c, radius * 0.7, 2.0, 16, Particle.SPORE_BLOSSOM_AIR, null, 0);
    }

    /** Азалиевый дурман: токсичный вихрь. */
    public static void azaleaToxin(LivingEntity t) {
        Spectacle.implosion(t.getLocation().add(0, 1, 0), 2.4, 10,
                Particle.SPORE_BLOSSOM_AIR, Color.fromRGB(0x9A5AB8), 1.1f);
        Spectacle.spiral(t.getLocation(), 0.7, 2.4, 14, Particle.WAX_OFF,
                Color.fromRGB(0xB87AC8), 1.0f);
    }

    /** Грибные споры: споровый купол площади. */
    public static void mushroomCloud(Location c, int radius) {
        Spectacle.ringExpand(c, radius + 1.5, 14, Particle.SPORE_BLOSSOM_AIR,
                Color.fromRGB(0xC85A5A), 1.1f);
        Spectacle.spiral(c, radius * 0.8, 2.6, 18, Particle.MYCELIUM, null, 0);
    }

    // ==================== РЫБОЛОВ ====================

    /** Водорослевый водоворот: стягивающая спираль. */
    public static void kelpVortex(LivingEntity t) {
        Spectacle.implosion(t.getLocation().add(0, 1, 0), 2.6, 12, Particle.UNDERWATER,
                Color.fromRGB(0x2A8A5A), 1.1f);
        Spectacle.spiral(t.getLocation(), 1.0, 2.6, 16, Particle.SPLASH,
                Color.fromRGB(0x2AA86A), 1.0f);
    }

    /** Коралловый риф: живая стена собирается. */
    public static void coralWall(List<Location> placed, Material[] mats) {
        for (int i = 0; i < placed.size(); i++) {
            List<Location> one = List.of(placed.get(i));
            Spectacle.blocksDescend(one, mats[i % mats.length].createBlockData(), 0.95f, 2.0, 16);
        }
        if (!placed.isEmpty()) {
            Spectacle.ringExpand(placed.get(0), 2.5, 10, Particle.BUBBLE,
                    Color.fromRGB(0x3ABAC8), 1.0f);
        }
    }

    /** Губка-осушитель: вода втягивается в мага. */
    public static void spongeDrain(Player p, int drained) {
        Spectacle.implosion(p.getLocation().add(0, 1, 0), 5.0, 16, Particle.FALLING_WATER,
                Color.fromRGB(0x64C8FF), 1.1f);
        Spectacle.burst(p.getLocation().add(0, 1.5, 0), Particle.SPLASH, 30, 0.6);
    }

    /** Призмариновый луч: пробивной столб воды. */
    public static void prismarineBeam(Player p, Location to, LivingEntity t) {
        Location target = t != null ? t.getLocation().add(0, 1, 0) : to;
        if (target == null) return;
        Spectacle.beam(p.getEyeLocation(), target, Particle.DOLPHIN,
                Color.fromRGB(0x5AD0D8), 1.2f, 10, 0.35);
        Spectacle.ringExpand(target, 2.6, 10, Particle.SPLASH,
                Color.fromRGB(0x3ABAC8), 1.2f);
    }

    // ==================== КАМЕНЩИК ====================

    /** Песчаная буря: жёлтая мгла несётся конусом. */
    public static void sandStorm(Player p, List<LivingEntity> targets) {
        org.bukkit.util.Vector dir = p.getEyeLocation().getDirection().normalize();
        for (int i = -1; i <= 1; i++) {
            org.bukkit.util.Vector side = new org.bukkit.util.Vector(-dir.getZ(), 0, dir.getX())
                    .normalize().multiply(i * 1.2);
            Location to = p.getEyeLocation().clone().add(dir.clone().multiply(7)).add(side);
            Spectacle.beam(p.getEyeLocation(), to, Particle.CLOUD, SAND, 1.3f, 10, 0.4);
        }
        for (LivingEntity t : targets) {
            Spectacle.itemFx(t.getLocation().add(0, 1, 0), Material.SAND, 6, 0.5);
        }
    }

    /** Гравитационный молот: глыбы падают на цель. */
    public static void gravelHammer(LivingEntity t, List<Location> spots) {
        if (!spots.isEmpty()) {
            Spectacle.blocksDescend(spots, Material.GRAVEL.createBlockData(), 0.95f, 3.0, 16);
        }
        Spectacle.implosion(t.getLocation().add(0, 1.5, 0), 2.0, 10, Particle.CRIT,
                Color.fromRGB(0x8A7A6A), 1.2f);
        Spectacle.ringDust(t.getLocation(), 1.6, Color.fromRGB(0x7A6A5A), 1.2f);
    }

    /** Каменный мост: путь материализуется под шагами. */
    public static void bridgeBuild(List<Location> placed) {
        if (!placed.isEmpty()) {
            Spectacle.blocksDescend(placed, Material.SANDSTONE.createBlockData(), 0.95f, 1.6, 16);
        }
        Location last = placed.isEmpty() ? null : placed.get(placed.size() - 1);
        if (last != null) {
            Spectacle.ringExpand(last, 2.0, 8, Particle.CLOUD, SAND, 1.0f);
        }
    }

    /** Ледяные оковы: морозная клетка. */
    public static void iceBind(LivingEntity t, boolean inWater) {
        Spectacle.cage(t, Material.PACKED_ICE.createBlockData(),
                inWater ? 0.34f : 0.26f, inWater ? 30 : 18);
        Spectacle.implosion(t.getLocation().add(0, 1, 0), 2.4, 12, Particle.SNOWFLAKE,
                ICE, 1.1f);
    }

    /** Стеклянный камуфляж: стёкла собираются и растворяют мага. */
    public static void glassFade(Player p, int seconds) {
        Spectacle.cage(p, Material.GLASS.createBlockData(), 0.2f, 12);
        Spectacle.implosion(p.getLocation().add(0, 1, 0), 2.0, 10, Particle.WAX_OFF, WHITE, 0.9f);
        Spectacle.ringExpand(p.getLocation(), 2.2, 8, Particle.END_ROD, null, 0);
    }

    /** Кирпичный бастион: кладка собирается кольцом. */
    public static void bastion(List<Location> shell, Location c) {
        if (!shell.isEmpty()) {
            Spectacle.blocksDescend(shell, Material.BRICKS.createBlockData(), 0.95f, 2.4, 20);
        }
        Spectacle.implosion(c.clone().add(0, 1, 0), 3.0, 12, Particle.CRIT,
                Color.fromRGB(0xB07050), 1.2f);
    }

    /** Терракотовая маска: глиняные черепки кружат вокруг. */
    public static void terracottaMask(Player p, int seconds) {
        Spectacle.orbitBlocks(p, Material.ORANGE_TERRACOTTA.createBlockData(),
                5, 1.0, 1.0, 0.2f, 5.0, Math.min(seconds, 15));
        Spectacle.spiral(p.getLocation(), 0.8, 2.2, 14, Particle.DUST_PLUME,
                Color.fromRGB(0xC87A4A), 1.1f);
    }

    /** Бетонный саркофаг: схватывающий купол стирает всё. */
    public static void concreteTomb(LivingEntity t, int seconds) {
        Spectacle.cage(t, Material.WHITE_CONCRETE.createBlockData(), 0.3f,
                Math.min(seconds * 10, 40));
        Spectacle.implosion(t.getLocation().add(0, 1, 0), 2.8, 12, Particle.WHITE_SMOKE,
                WHITE, 1.3f);
        Spectacle.ringExpand(t.getLocation(), 2.6, 10, Particle.CRIT, WHITE, 1.0f);
    }

    // ==================== СВЕТОВОД ====================

    /** Световая вспышка: фото-шоквейв. */
    public static void glowFlash(Player p, int hit) {
        Spectacle.ringExpand(p.getLocation(), 6, 14, Particle.END_ROD, WHITE, 1.3f);
        Spectacle.ringExpand(p.getLocation(), 4, 10, Particle.GLOW, GOLD, 1.2f);
        Spectacle.spiral(p.getLocation(), 1.4, 3.0, 16, Particle.GLOW, null, 0);
    }

    /** Морской маяк: столб света до неба. */
    public static void seaBeacon(Location top, int seconds) {
        Spectacle.spiral(top.clone().add(0.5, 0.5, 0.5), 1.2, 6.0, 24, Particle.END_ROD,
                Color.fromRGB(0x5AD0D8), 1.2f);
        Spectacle.ringExpand(top.clone().add(0.5, 1, 0.5), 4.0, 14, Particle.DOLPHIN,
                Color.fromRGB(0x3ABAC8), 1.1f);
    }

    /** Тёплая аура: светогрибные фонари на орбите. */
    public static void shroomAura(Player p, int seconds) {
        Spectacle.orbitBlocks(p, Material.SHROOMLIGHT.createBlockData(),
                4, 1.2, 1.6, 0.22f, 3.5, Math.min(seconds, 20));
        Spectacle.ringExpand(p.getLocation(), 2.4, 10, Particle.GLOW, WARM, 1.2f);
    }

    /** Жабий дождь: призыв грозового купола. */
    public static void frogRain(Player p, int seconds) {
        Spectacle.ringExpand(p.getLocation(), 8, 20, Particle.FALLING_WATER,
                Color.fromRGB(0x64C8FF), 1.2f);
        Spectacle.spiral(p.getLocation(), 1.6, 4.0, 20, Particle.BUBBLE, null, 0);
        Spectacle.sound(p.getLocation(), Sound.ENTITY_FROG_AMBIENT, 1f, 0.8f);
    }

    /** Свечной ритуал: 7 огней вспыхивают по кругу. */
    public static void candleRitual(Location center, List<Location> candles) {
        for (Location cl : candles) {
            Spectacle.implosion(cl.clone().add(0.5, 0.6, 0.5), 0.9, 6, Particle.FLAME,
                    WARM, 1.0f);
        }
        Spectacle.ringExpand(center, 3.2, 14, Particle.END_ROD, GOLD, 1.1f);
    }

    /** Внутренний свет: светящиеся ягоды кружат внутри ауры. */
    public static void innerLight(LivingEntity t, int seconds) {
        Spectacle.orbitItems(t, Material.GLOW_BERRIES, 4, 0.9, 1.2, 0.3f, 4.5,
                Math.min(seconds, 16));
        Spectacle.spiral(t.getLocation(), 0.7, 2.4, 16, Particle.GLOW, WARM, 1.0f);
    }

    // ==================== ИНЖЕНЕР ====================

    /** Поршневой толчок: призрачный поршень выстреливает. */
    public static void mechPush(Player p, LivingEntity t) {
        Spectacle.beam(p.getEyeLocation(), t.getLocation().add(0, 1, 0),
                Particle.CRIT, Color.fromRGB(0xC8C8C8), 1.2f, 8, 0.4);
        Spectacle.ringExpand(t.getLocation(), 2.4, 10, Particle.CRIT, null, 0);
        Spectacle.itemBurst(t.getLocation().add(0, 1, 0), Material.PISTON, 3, 1.5, 12);
    }

    /** Гальваническая дуга: живая цепь молний. */
    public static void galvanicArc(List<Location> chain) {
        for (int i = 0; i + 1 < chain.size(); i++) {
            Spectacle.beam(chain.get(i), chain.get(i + 1), Particle.ELECTRIC_SPARK,
                    WHITE, 1.0f, 12, 0.3);
        }
        for (Location l : chain) {
            Spectacle.burst(l, Particle.ELECTRIC_SPARK, 14, 0.4);
        }
    }

    /** Рельсовый разгон: стальная дорога и искры. */
    public static void railDash(List<Location> rails, Player p) {
        if (!rails.isEmpty()) {
            Spectacle.blocksDescend(rails, Material.RAIL.createBlockData(), 0.95f, 1.2, 14);
            Spectacle.beam(rails.get(0), rails.get(rails.size() - 1).clone().add(0, 1, 0),
                    Particle.ELECTRIC_SPARK, null, 0, 10, 0.5);
        }
        Spectacle.ringExpand(p.getLocation(), 2.0, 8, Particle.ELECTRIC_SPARK, null, 0);
    }

    // ==================== НЕЗЕР ====================

    /** Адская подложка: столб пламени из земли. */
    public static void pyre(Location fire, boolean eternal, LivingEntity t) {
        Spectacle.spiral(fire.clone().add(0.5, 0.2, 0.5), 0.8, 2.8, 16, Particle.FLAME,
                FIRE, 1.2f);
        Spectacle.ringExpand(fire.clone().add(0.5, 0.2, 0.5), 2.4, 10, Particle.LAVA, null, 0);
        if (eternal && t != null) {
            Spectacle.ringExpand(t.getLocation(), 3.4, 14, Particle.FLAME, FIRE, 1.4f);
            Spectacle.itemBurst(t.getLocation().add(0, 1, 0), Material.MAGMA_CREAM, 5, 1.5, 14);
        }
    }

    /** Пламя душ: синий вихрь отмечает душу. */
    public static void soulFlame(LivingEntity t) {
        Spectacle.spiral(t.getLocation(), 0.8, 2.8, 18, Particle.SOUL_FIRE_FLAME, SOUL, 1.1f);
        Spectacle.implosion(t.getLocation().add(0, 1.2, 0), 2.2, 10, Particle.SOUL, null, 0);
    }

    /** Магмовый жар: раскалённый взрыв. */
    public static void magmaIgnite(LivingEntity t, boolean eternal) {
        Spectacle.burst(t.getLocation().add(0, 1, 0), Particle.LAVA, 6, 0.5);
        Spectacle.ringExpand(t.getLocation(), eternal ? 3.6 : 2.2, 12, Particle.FLAME,
                FIRE, 1.3f);
        if (eternal) {
            Spectacle.spiral(t.getLocation(), 0.9, 3.0, 20, Particle.FLAME, FIRE, 1.3f);
            Spectacle.itemBurst(t.getLocation().add(0, 1.2, 0), Material.MAGMA_BLOCK, 5, 1.6, 14);
            Spectacle.sound(t.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.4f, 0.7f);
        }
    }

    /** Чёрное притяжение: каменный вихрь стягивает. */
    public static void vortex(Location c, int seconds) {
        Spectacle.orbitPoint(c, Material.BLACKSTONE.createBlockData(),
                8, 2.2, 0.8, 0.3f, 14, Math.min(seconds, 3));
        Spectacle.implosion(c.clone().add(0, 0.5, 0), 4.5, 16, Particle.PORTAL, BLACK, 1.3f);
        Spectacle.ringExpand(c, 4.0, 12, Particle.SMOKE, BLACK, 1.2f);
    }

    /** Базальтовый шпиль: колонна вырастает из-под земли. */
    public static void pillarRise(LivingEntity t, List<Location> blocks) {
        if (!blocks.isEmpty()) {
            Spectacle.blocksDescend(blocks, Material.BASALT.createBlockData(), 0.95f, 3.0, 16);
        }
        Spectacle.implosion(t.getLocation().add(0, 0.5, 0), 1.8, 10, Particle.CRIT,
                Color.fromRGB(0x5A5A5A), 1.2f);
        Spectacle.ringDust(t.getLocation(), 1.8, Color.fromRGB(0x6A6A6A), 1.2f);
    }

    /** Багровое бешенство: кровавая волна ярости. */
    public static void crimsonRage(LivingEntity t) {
        Spectacle.ringExpand(t.getLocation(), 3.0, 12, Particle.ANGRY_VILLAGER,
                CRIMSON, 1.3f);
        Spectacle.implosion(t.getLocation().add(0, 1, 0), 2.4, 10, Particle.CRIMSON_SPORE,
                null, 0);
    }

    /** Искривлённый шаг: фазовый разрыв между точками. */
    public static void warpedShift(Location from, Location to) {
        Spectacle.implosion(from.clone().add(0, 1, 0), 2.4, 12, Particle.PORTAL, VOID, 1.2f);
        Spectacle.beam(from.clone().add(0, 1, 0), to.clone().add(0, 1, 0),
                Particle.WARPED_SPORE, VOID, 1.1f, 12, 0.4);
        Spectacle.ringExpand(to, 2.4, 10, Particle.REVERSE_PORTAL, null, 0);
    }

    /** Гниль: некротический вихрь выедает жизнь. */
    public static void wartRot(LivingEntity t) {
        Spectacle.spiral(t.getLocation(), 0.7, 2.6, 16, Particle.SCULK_SOUL,
                Color.fromRGB(0x3A5A2A), 1.1f);
        Spectacle.ringExpand(t.getLocation(), 2.0, 10, Particle.ASH,
                Color.fromRGB(0x4A6A3A), 1.1f);
    }

    // ==================== КРАЙ ====================

    /** Пустотный обелиск: монolith прорывается из пустоты. */
    public static void obelisk(Location top) {
        Spectacle.implosion(top.clone().add(0.5, 1, 0.5), 3.2, 14, Particle.PORTAL, VOID, 1.2f);
        Spectacle.spiral(top.clone().add(0.5, 0.5, 0.5), 1.0, 3.4, 20, Particle.PORTAL, null, 0);
        Spectacle.ringExpand(top.clone().add(0.5, 0.2, 0.5), 3.6, 12, Particle.REVERSE_PORTAL,
                VOID, 1.1f);
    }

    /** Пурпурная левитация: антигравитационный вихрь поднимает. */
    public static void levitate(LivingEntity t, int seconds) {
        Spectacle.orbitBlocks(t, Material.PURPUR_BLOCK.createBlockData(),
                5, 1.0, 0.8, 0.2f, 6.0, Math.min(seconds, 10));
        Spectacle.spiral(t.getLocation(), 0.9, 3.0, 20, Particle.REVERSE_PORTAL, VOID, 1.1f);
        Spectacle.ringExpand(t.getLocation(), 2.2, 10, Particle.END_ROD, null, 0);
    }

    /** Хорусовый перелёт: фазовый порыв. */
    public static void chorusPhase(Location from, Location to) {
        Spectacle.implosion(from.clone().add(0, 1, 0), 2.4, 10, Particle.PORTAL, VOID, 1.2f);
        Spectacle.beam(from.clone().add(0, 1, 0), to.clone().add(0, 1, 0),
                Particle.CHERRY_LEAVES, VOID, 1.0f, 10, 0.5);
        Spectacle.ringExpand(to, 2.6, 10, Particle.REVERSE_PORTAL, null, 0);
        Spectacle.itemBurst(to.clone().add(0, 1, 0), Material.CHORUS_FRUIT, 5, 1.4, 14);
    }

    /** Обсидиановая кожа: чёрные глыбы кружат в защите. */
    public static void obsidianSkin(Player p, int seconds) {
        Spectacle.orbitBlocks(p, Material.OBSIDIAN.createBlockData(),
                5, 1.1, 1.0, 0.26f, 4.5, Math.min(seconds, 10));
        Spectacle.ringExpand(p.getLocation(), 2.4, 10, Particle.LAVA, BLACK, 1.3f);
    }

    // ==================== АНОМАЛИИ ====================

    /** Эхо-импульс: тройная сонарная волна скулка. */
    public static void sculkPulse(Player p, int found) {
        Spectacle.ringExpand(p.getLocation(), 16, 28, Particle.SCULK_SOUL, ECHO, 1.2f);
        Spectacle.ringExpand(p.getLocation(), 10, 22, Particle.SONIC_BOOM, null, 0);
        Spectacle.ringExpand(p.getLocation(), 6, 16, Particle.GLOW, ECHO, 1.0f);
    }

    /** Диспетчер эффектов удара посоха (общие усилители матрицы). */
    public static void matrixBoost(LivingEntity victim, Magic.Element el) {
        if (victim == null || victim.isDead()) return;
        Location c = victim.getLocation().add(0, 1, 0);
        switch (el) {
            case LIGHTNING -> Spectacle.ringExpand(c, 2.2, 8, Particle.ELECTRIC_SPARK,
                    Color.fromRGB(0xFFF0A0), 1.2f);
            case ICE -> Spectacle.implosion(c, 1.8, 8, Particle.SNOWFLAKE, ICE, 1.1f);
            case FIRE -> Spectacle.ringExpand(c, 2.0, 8, Particle.FLAME, FIRE, 1.2f);
            case DARK -> Spectacle.implosion(c, 2.0, 10, Particle.SQUID_INK, BLACK, 1.2f);
            case ECHO -> Spectacle.ringExpand(c, 2.4, 10, Particle.NOTE, ECHO, 1.1f);
            case ARCANE -> Spectacle.spiral(c, 0.7, 2.2, 12, Particle.ENCHANT, LAPIS, 1.0f);
            default -> { }
        }
    }
}
