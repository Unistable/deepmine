package ru.deepevents.events;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.deepevents.DeepEventsPlugin;
import ru.deepevents.core.AbstractEvent;
import ru.deepevents.core.ConfigManager;
import ru.deepevents.core.Fx;
import ru.deepevents.core.Msg;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * ЗВЕЗДОПАД — мирный кинематографичный ивент.
 * Ночь, в небе колышутся ленты авроры, падают звёзды и оставляют осколки.
 * 3 осколка + Shift+ПКМ = желание (случайное благословение).
 * ПКМ осколками по игроку = общее желание (оба получают удачу).
 * Финал: большая звезда и салют, удача всем онлайн.
 */
public final class StarfallEvent extends AbstractEvent {

    private final DeepEventsPlugin plugin;
    private final ConfigManager cfg;
    private final Random rnd = new Random();

    private NamespacedKey shardKey;
    private BukkitTask auroraTask;
    private BukkitTask mainTask;
    private BukkitTask meteorTask;
    private final List<Item> shards = new ArrayList<>();
    private final Map<Meteor, World> meteors = new HashMap<>();
    private final List<long[]> ribbons = new ArrayList<>(); // colorRGB, phaseSeed
    private final Map<UUID, double[]> auroraAnchors = new HashMap<>();

    private World world;
    private long oldTime;
    private boolean oldStorm;
    private boolean oldThunder;
    private int ticksLeft;
    private int nextMeteorIn;
    private int wishesGranted;
    private boolean finaleRunning;
    private BossBar wishBar;
    private static final int WISH_GOAL = 10;

    private static final class Meteor {
        Location start;
        Vector flight;
        int ticks;
        int totalTicks;
        Location target;
        int tier; // 0 обычная, 1 золотая, 2 великая
    }

    public StarfallEvent(DeepEventsPlugin plugin, ConfigManager cfg) {
        super(plugin);
        this.plugin = plugin;
        this.cfg = cfg;
    }

    @Override
    public String displayName() {
        return "Звездопад";
    }

    @Override
    public void launch() {
        shardKey = new NamespacedKey(plugin, "star_shard");
        List<Player> playerList = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (playerList.isEmpty()) {
            Msg.broadcast("<gray>Небо хотело зажечься звёздами… но никого не было на сервере.</gray>");
            plugin.events().clearActive(this);
            stop("нет игроков");
            return;
        }
        world = playerList.get(0).getWorld();
        if (!cfg.isEventWorld(world)) {
            world = Bukkit.getWorlds().stream().filter(w -> cfg.isEventWorld(w)).findFirst().orElse(world);
        }

        // окружение: ночь и ясно (восстанавливается в конце)
        oldTime = world.getFullTime();
        oldStorm = world.hasStorm();
        oldThunder = world.isThundering();
        if (cfg.starfallForceNight()) {
            long time = world.getTime();
            if (time < 13000 || time > 23000) world.setTime(18000);
        }
        world.setStorm(false);
        world.setThundering(false);
        world.setClearWeatherDuration(cfg.starfallDurationSeconds() * 20);

        // ленты авроры: цвет + фаза
        int[][] colors = {{0x5EEAD4, 0}, {0xA855F7, 1}, {0x34D399, 2}, {0xF472B6, 3}};
        for (int[] c : colors) ribbons.add(new long[]{c[0], c[1]});

        ticksLeft = cfg.starfallDurationSeconds() * 20;
        int[] mi = cfg.meteorInterval();
        nextMeteorIn = mi[0] * 20 + rnd.nextInt(Math.max(1, (mi[1] - mi[0]) * 20));

        Msg.titleAll("<gradient:#a5f3fc:#818cf8><bold>★ ЗВЕЗДОПАД ★</bold></gradient>",
                "<gray>Ночь желаний началась…</gray>", 300, 2200, 700);
        Msg.broadcastRaw("<gradient:#a5f3fc:#c4b5fd>Небо зажглось падающими звёздами. Собирайте осколки — "
                + "<b>" + cfg.wishCost() + "</b> осколков + Shift+ПКМ = <b>желание</b>. "
                + "ПКМ по игроку осколками = <b>общее желание</b>.</gradient>");
        Msg.soundAll("block.amethyst_block.chime", 1.2f, 0.8f);
        Msg.soundAll("entity.player.levelup", 0.8f, 1.4f);

        auroraTask = Bukkit.getScheduler().runTaskTimer(plugin, this::auroraTick, 10L, 8L);
        mainTask = Bukkit.getScheduler().runTaskTimer(plugin, this::mainTick, 20L, 20L);
        meteorTask = Bukkit.getScheduler().runTaskTimer(plugin, this::meteorTick, 40L, 1L);

        // общий счётчик желаний на боссбаре
        wishBar = BossBar.bossBar(Msg.mm("<gradient:#a5f3fc:#c4b5fd><bold>★ ЗВЕЗДОПАД</bold></gradient> — желаний: 0/" + WISH_GOAL),
                0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(wishBar);

        Msg.log("Звездопад начался, длительность " + cfg.starfallDurationSeconds() + "с");
    }

    // ---------- аврора ----------

    /**
     * Аврора v2 — ВИДИМЫЕ вертикальные занавеси света.
     * Было: dust size 1.6 по 1 частице на Y+48 — невидимые точки, дёргающиеся
     * за игроком. Стало:
     *  - якорь со СГЛАЖИВАНИЕМ (плывёт за игроком медленно, не дёргается);
     *  - 3 занавеси-арки, каждая — 21 колонна с вертикальными полосами дуста
     *    (size 3.8 — максимальная читаемость на расстоянии);
     *  - палитра переливается по времени (бирюза → фиолет → мята);
     *  - редкие GLOW-искры и приземное свечение.
     * Бюджет: ~380 дуста на игрока раз в 8 тиков (~48/тик) — дёшево.
     */
    private void auroraTick() {
        if (world == null) return;
        long t = world.getGameTime();
        for (Player p : world.getPlayers()) {
            double[] anchor = anchorFor(p);
            // палитра авроры (перелив)
            Color c1 = palette(t, 0);
            Color c2 = palette(t, 1);
            Color c3 = palette(t, 2);
            double baseY = p.getLocation().getY() + 46;
            for (int rb = 0; rb < RIBBONS.length; rb++) {
                double phase = RIBBONS[rb] * 1.7;
                Color color = switch (rb) { case 0 -> c1; case 1 -> c2; default -> c3; };
                for (int i = -10; i <= 10; i += 1) {
                    double dx = i * 3.4;
                    double dz = Math.sin(i * 0.22 + t * 0.015 + phase) * 11;
                    double dy = 5.5 * Math.sin(i * 0.13 + t * 0.022 + phase * 2);
                    double x = anchor[0] + dx + RIBBONS[rb] * 7.0;
                    double z = anchor[1] + dz + (RIBBONS[rb] - 1) * 5.0;
                    double yTop = baseY + dy + rb * 3.0;
                    // вертикальная полоса занавеса: 4-5 частиц вниз с затуханием
                    int drops = 4 + (i & 1);
                    for (int d = 0; d < drops; d++) {
                        double yy = yTop - d * 1.35;
                        // плотность гаснет к низу занавеса
                        if (d > 1 && ((t + i + d) & 1) == 0) continue;
                        Fx.dust(p, x + Math.sin(t * 0.02 + d) * 0.4, yy, z,
                                1, 0.1, 0.55, 0.1, 0, color, 3.8f);
                    }
                }
            }
            // искры-звёзды в небе и светлячки у земли
            if (t % 16 == 0) {
                p.spawnParticle(Particle.GLOW, anchor[0] + rnd.nextDouble() * 40 - 20,
                        baseY + rnd.nextDouble() * 14, anchor[1] + rnd.nextDouble() * 40 - 20,
                        2, 1.5, 1.0, 1.5, 0);
            }
            if (t % 40 == 0) {
                p.spawnParticle(Particle.GLOW, p.getLocation().add(
                        rnd.nextDouble() * 20 - 10, rnd.nextDouble() * 3, rnd.nextDouble() * 20 - 10),
                        6, 2, 1.2, 2, 0);
            }
        }
    }

    private static final int[] RIBBONS = {0, 1, 2};

    /** Переливающаяся палитра: бирюза → фиолет → мята → розовый. */
    private Color palette(long t, int idx) {
        int[][] colors = {
                {0x5EEAD4, 0xA855F7, 0x34D399, 0xF472B6},
                {0xA855F7, 0x34D399, 0x60A5FA, 0x5EEAD4},
                {0x34D399, 0x60A5FA, 0xF472B6, 0xA855F7}};
        int c = colors[idx][(int) ((t / 160) % 4)];
        return Color.fromRGB(c);
    }

    /** Сглаженный якорь неба: не прыгает за игроком, а медленно плывёт. */
    private double[] anchorFor(Player p) {
        double[] a = auroraAnchors.get(p.getUniqueId());
        Location loc = p.getLocation();
        if (a == null) {
            a = new double[]{loc.getX(), loc.getZ()};
            auroraAnchors.put(p.getUniqueId(), a);
        } else {
            a[0] += (loc.getX() - a[0]) * 0.06;
            a[1] += (loc.getZ() - a[1]) * 0.06;
        }
        return a;
    }

    // ---------- метеоры ----------

    private void meteorTick() {
        if (world == null || finaleRunning) return;
        if (--nextMeteorIn > 0) {
            // движение уже летящих
            moveMeteors();
            return;
        }
        int[] mi = cfg.meteorInterval();
        nextMeteorIn = mi[0] * 20 + rnd.nextInt(Math.max(1, (mi[1] - mi[0]) * 20));
        spawnMeteor();
        moveMeteors();
    }

    private void spawnMeteor() {
        List<Player> players = world.getPlayers();
        if (players.isEmpty()) return;
        Player anchor = players.get(rnd.nextInt(players.size()));
        double ang = rnd.nextDouble() * Math.PI * 2;
        double dist = 12 + rnd.nextDouble() * 30;
        Location target = anchor.getLocation().clone().add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist);
        target.setY(world.getHighestBlockYAt(target.getBlockX(), target.getBlockZ()) + 1);

        // тиры звёзд: обычная / золотая (12%) / великая (2%)
        int tier = 0;
        double roll = rnd.nextDouble();
        if (roll < 0.02) tier = 2;
        else if (roll < 0.14) tier = 1;

        Vector dir = new Vector(rnd.nextDouble() - 0.5, 0, rnd.nextDouble() - 0.5).normalize();
        Meteor m = new Meteor();
        m.tier = tier;
        m.target = target;
        m.start = target.clone().subtract(dir.clone().multiply(60)).add(0, 42, 0);
        m.flight = dir.multiply(60).add(new Vector(0, -42, 0));
        m.totalTicks = 45;
        m.ticks = 0;
        meteors.put(m, world);
        Msg.sound(target, "block.amethyst_block.hit", 1.2f, 0.6f);
    }

    private void moveMeteors() {
        List<Meteor> done = new ArrayList<>();
        for (Meteor m : meteors.keySet()) {
            m.ticks++;
            double k = (double) m.ticks / m.totalTicks;
            Location pos = m.start.clone().add(m.flight.clone().multiply(k));
            if (m.ticks >= m.totalTicks) {
                done.add(m);
                landStar(m.target, m.tier);
                continue;
            }
            for (Player p : world.getPlayers()) {
                if (p.getLocation().distance(pos) > 110) continue;
                p.spawnParticle(Particle.END_ROD, pos, 2, 0.1, 0.1, 0.1, 0.01);
                if (m.ticks % 4 == 0) {
                    p.spawnParticle(Particle.FIREWORK, pos, 2, 0.15, 0.15, 0.15, 0.02);
                }
            }
        }
        for (Meteor m : done) meteors.remove(m);
    }

    private void landStar(Location loc, int tier) {
        world.spawnParticle(Particle.FIREWORK, loc.clone().add(0, 0.6, 0), 18 + tier * 16, 0.5, 0.4, 0.5, 0.12);
        world.spawnParticle(Particle.GLOW, loc.clone().add(0, 0.8, 0), 20 + tier * 20, 0.6, 0.5, 0.6, 0.05);
        world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1.2, 0), 8 + tier * 6, 0.4, 0.8, 0.4, 0.06);
        Fx.dust(world, loc.clone().add(0, 0.6, 0), 12 + tier * 10, 0.4, 0.4, 0.4, 0,
                tier == 0 ? Color.fromRGB(0xFDE68A) : Color.fromRGB(0xFFD700), 2.2f + tier * 0.6f);
        // световое кольцо в месте падения — видно, куда смотреть
        Color ringColor = tier == 0 ? Color.fromRGB(0xFDE68A) : Color.fromRGB(0xFFD700);
        Fx.shockRing(world, loc, 2.5 + tier, 18 + tier * 6, ringColor, 3.0f, 2);
        if (tier >= 1) {
            world.spawnParticle(Particle.FLASH, loc.clone().add(0, 1.5, 0), 1, 0.5, 0.5, 0.5, 0, Color.WHITE);
        }
        Msg.sound(loc, "block.amethyst_block.chime", 1.0f, 1.3f - tier * 0.2f);
        Msg.sound(loc, "entity.player.levelup", 0.4f, 1.8f);

        int shardCount = 1 + tier; // обычная 1, золотая 2, великая 3
        for (int i = 0; i < shardCount; i++) {
            ItemStack shard = createShard();
            Item item = world.dropItem(loc.clone().add(0, 0.7, 0), shard);
            item.setGlowing(true);
            item.setPickupDelay(10);
            item.setPersistent(false);
            item.customName(Msg.mm(tier == 0 ? "<#fde68a><bold>Осколок звезды</bold></#fde68a>"
                    : (tier == 1 ? "<gold><bold>Золотой осколок</bold></gold>" : "<light_purple><bold>Осколок Великой звезды</bold></light_purple>")));
            track(item);
            shards.add(item);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (item.isValid()) item.remove();
                shards.remove(item);
            }, cfg.shardDespawnSeconds() * 20L);
        }
        if (tier == 2) {
            Msg.broadcastNear(loc, 128, "<light_purple><bold>Великая звезда упала рядом! Оставила 3 осколка!</bold></light_purple>");
            world.strikeLightningEffect(loc);
        }
    }

    public ItemStack createShard() {
        ItemStack shard = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = shard.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e✦ Осколок звезды");
            meta.setLore(List.of(
                    "§7Теплится светом далёкой звезды.",
                    "§e" + cfg.wishCost() + " осколка + Shift+ПКМ §7— желание",
                    "§dПКМ по игроку §7— общее желание",
                    "§8Исчезнет в конце Звездопада"));
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(shardKey, PersistentDataType.BYTE, (byte) 1);
            shard.setItemMeta(meta);
        }
        return shard;
    }

    private int countShards(Player p) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (isShard(it)) n += it.getAmount();
        }
        return n;
    }

    private boolean isShard(ItemStack it) {
        return it != null && it.getType() == Material.AMETHYST_SHARD && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(shardKey, PersistentDataType.BYTE);
    }

    private int consumeShards(Player p, int amount) {
        int left = amount;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (left <= 0) break;
            if (isShard(it)) {
                int take = Math.min(it.getAmount(), left);
                it.setAmount(it.getAmount() - take);
                left -= take;
                if (it.getAmount() <= 0) it.setAmount(0);
            }
        }
        return amount - left;
    }

    // ---------- желания ----------

    /** Личное желание: 3 осколка + Shift+ПКМ по воздуху/блоку. */
    public void tryPersonalWish(PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (!p.isSneaking() || !p.getInventory().contains(Material.AMETHYST_SHARD)) return;
        // только если в руке осколок
        ItemStack main = p.getInventory().getItemInMainHand();
        if (!isShard(main)) return;

        int have = countShards(p);
        int cost = cfg.wishCost();
        if (have < cost) {
            Msg.actionbar(p, "<#fde68a>Нужно " + cost + " осколка (есть " + have + "). Найдите падающую звезду!</#fde68a>");
            return;
        }
        if (consumeShards(p, cost) < cost) return;

        ConfigManager.Blessing b = pickBlessing();
        if (b != null) {
            p.addPotionEffect(new PotionEffect(b.effect(), b.seconds() * 20, b.amplifier(), true, true, true));
            Msg.title(p, "<#fde68a><bold>Желание услышано</bold></#fde68a>",
                    "<gradient:#a5f3fc:#c4b5fd>" + b.name() + "</gradient>", 100, 1500, 400);
            p.sendMessage(Msg.prefixed("<#fde68a>Звезда отвечает: <b>" + b.name() + "</b>!</#fde68a>"));
        }
        wishesGranted++;
        updateWishBar();
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.8, 1.2, 0.8, 0.3);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1.5, 0), 15, 0.6, 1, 0.6, 0.05);
        Msg.sound(p.getLocation(), "block.amethyst_block.chime", 1.2f, 1.6f);
        Msg.sound(p.getLocation(), "entity.player.levelup", 0.9f, 1.2f);
        e.setCancelled(true);
        Msg.log("Желание загадано: " + p.getName() + " -> " + (b != null ? b.name() : "нет"));
    }

    /** Общее желание: ПКМ по игроку с осколком в руке. */
    public void trySharedWish(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!(e.getRightClicked() instanceof Player other)) return;
        Player p = e.getPlayer();
        if (!isShard(p.getInventory().getItemInMainHand())) return;

        int cost = cfg.wishCost();
        int have = countShards(p);
        if (have < cost) {
            Msg.actionbar(p, "<#fde68a>Нужно " + cost + " осколка для общего желания (есть " + have + ").</#fde68a>");
            return;
        }
        if (consumeShards(p, cost) < cost) return;

        for (Player target : List.of(p, other)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 300 * 20, 0, true, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40 * 20, 1, true, true, true));
            target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add(0, 2, 0), 8, 0.5, 0.6, 0.5, 0);
            target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0, 1.5, 0), 12, 0.5, 0.8, 0.5, 0.03);
        }
        p.sendMessage(Msg.prefixed("<#c4b5fd>Вы поделились желанием с <b>" + other.getName() + "</b> — удача вам обоим!</#c4b5fd>"));
        other.sendMessage(Msg.prefixed("<#c4b5fd><b>" + p.getName() + "</b> поделился с вами звёздным желанием!</#c4b5fd>"));
        wishesGranted++;
        updateWishBar();
        Msg.sound(p.getLocation(), "block.amethyst_block.chime", 1.2f, 1.4f);
        Msg.sound(other.getLocation(), "block.amethyst_block.chime", 1.2f, 1.4f);
        e.setCancelled(true);
    }

    private ConfigManager.Blessing pickBlessing() {
        List<ConfigManager.Blessing> all = cfg.blessings();
        if (all.isEmpty()) return null;
        double total = 0;
        for (ConfigManager.Blessing b : all) total += b.weight();
        double roll = rnd.nextDouble() * total;
        for (ConfigManager.Blessing b : all) {
            if ((roll -= b.weight()) < 0) return b;
        }
        return all.get(all.size() - 1);
    }

    /** Боссбар-счётчик: при цели все получают общий бонус. */
    private void updateWishBar() {
        if (wishBar == null) return;
        wishBar.name(Msg.mm("<gradient:#a5f3fc:#c4b5fd><bold>★ ЗВЕЗДОПАД</bold></gradient> — желаний: "
                + wishesGranted + "/" + WISH_GOAL));
        wishBar.progress(Math.min(1f, (float) wishesGranted / WISH_GOAL));
        if (wishesGranted == WISH_GOAL) {
            wishBar.color(BossBar.Color.GREEN);
            wishBar.name(Msg.mm("<green><bold>★ НОЧЬ ИСПОЛНЕНА</bold></green> — благодарность звёзд всем онлайн!"));
            for (Player pl : Bukkit.getOnlinePlayers()) {
                pl.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 600 * 20, 1, true, true, true));
                pl.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 300 * 20, 1, true, true, true));
                pl.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, pl.getLocation().add(0, 2, 0), 30, 1, 1.5, 1, 0.2);
            }
            Msg.broadcast("<green><bold>Сервер загадал " + WISH_GOAL + " желаний!</bold> Звёзды благодарят всех: удача II и звёздный щит!</green>");
            Msg.soundAll("ui.toast.challenge_complete", 1.0f, 1.4f);
        }
    }

    // ---------- главный тик и финал ----------

    private void mainTick() {
        ticksLeft -= 20;
        if (ticksLeft == 600) {
            Msg.broadcast("<gray>Звездопад догорает… последняя звезда через 30 секунд.</gray>");
        }
        if (ticksLeft == 300) {
            Msg.broadcast("<#fde68a>Последняя звезда сейчас упадёт!</#fde68a>");
        }
        if (ticksLeft <= 0 && !finaleRunning) {
            finaleRunning = true;
            runFinale();
        }
    }

    private void runFinale() {
        Player anchor = world.getPlayers().isEmpty() ? null : world.getPlayers().get(0);
        Location spot = anchor != null
                ? anchor.getLocation().clone().add(rnd.nextDouble() * 24 - 12, 0, rnd.nextDouble() * 24 - 12)
                : world.getSpawnLocation();
        spot.setY(world.getHighestBlockYAt(spot.getBlockX(), spot.getBlockZ()) + 1);

        Msg.broadcast("<#fde68a><bold>Великая звезда опускается с небес…</bold></#fde68a>");
        Msg.soundAll("block.beacon.activate", 1.2f, 1.6f);

        // колонна света, опускающаяся 6 секунд
        final Location ground = spot.clone();
        new Object() {
            int k = 0;
            void step() {
                if (isStopped()) return;
                double height = 60 - k * 1.7;
                Location pos = ground.clone().add(0, Math.max(1, height), 0);
                for (Player p : world.getPlayers()) {
                    if (p.getLocation().distance(ground) > 120) continue;
                    p.spawnParticle(Particle.END_ROD, pos, 8, 0.3, 0.3, 0.3, 0.02);
                    p.spawnParticle(Particle.FIREWORK, pos, 3, 0.4, 0.4, 0.4, 0.02);
                }
                if (height > 1 && k < 40) {
                    k++;
                    Bukkit.getScheduler().runTaskLater(plugin, this::step, 1L);
                } else {
                    bigBurst(ground);
                }
            }
        }.step();
    }

    private void bigBurst(Location loc) {
        if (isStopped()) return;
        Firework fw = world.spawn(loc, Firework.class, f -> {
            FireworkMeta meta = f.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.fromRGB(0xFDE68A), Color.fromRGB(0xA5F3FC))
                    .withFade(Color.fromRGB(0xC4B5FD))
                    .withFlicker()
                    .withTrail()
                    .build());
            meta.addEffect(FireworkEffect.builder()
                    .with(FireworkEffect.Type.STAR)
                    .withColor(Color.WHITE)
                    .withFade(Color.fromRGB(0xFDE68A))
                    .build());
            meta.setPower(1);
            f.setFireworkMeta(meta);
        });
        track(fw);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 2, 0), 120, 2, 2.5, 2, 0.4);
        world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 2, 0), 60, 1.5, 2.5, 1.5, 0.15);
        Msg.soundAll("entity.firework_rocket.burst", 1.4f, 1.0f);
        Msg.soundAll("ui.toast.challenge_complete", 1.0f, 1.2f);

        // удача всем онлайн
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 300 * 20, 0, true, true, true));
            p.showTitle(Title.title(
                    Msg.mm("<#fde68a><bold>Ночь звёзд завершена</bold></#fde68a>"),
                    Msg.mm("<gray>Загадано желаний: <b>" + wishesGranted + "</b>. Удача со всеми вами!</gray>"),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2200), Duration.ofMillis(600))));
        }
        Msg.broadcast("<#fde68a>Ночь звёзд завершена. Загадано желаний: <b>" + wishesGranted + "</b>.</#fde68a>");

        // удаляем ВСЕ осколки из инвентарей, возвращая аметисты по количеству
        for (Player p : Bukkit.getOnlinePlayers()) {
            int returned = 0;
            for (ItemStack it : p.getInventory().getStorageContents()) {
                if (isShard(it) && it.getAmount() > 0) {
                    returned += it.getAmount();
                    it.setAmount(0);
                }
            }
            if (returned > 0) {
                int left = returned;
                while (left > 0) {
                    int stack = Math.min(Material.AMETHYST_SHARD.getMaxStackSize(), left);
                    var overflow = p.getInventory().addItem(new ItemStack(Material.AMETHYST_SHARD, stack));
                    for (var rest : overflow.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
                    left -= stack;
                }
                p.sendMessage(Msg.prefixed("<gray>Осколки звезды остыли и стали аметистами: ×" + returned + "</gray>"));
            }
        }
        for (Item item : shards) {
            if (item.isValid()) item.remove();
        }
        shards.clear();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.events().clearActive(this);
            stop("завершён");
        }, 60L);
    }

    @Override
    protected void cleanup() {
        if (auroraTask != null) auroraTask.cancel();
        if (mainTask != null) mainTask.cancel();
        if (meteorTask != null) meteorTask.cancel();
        auroraAnchors.clear();
        if (wishBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(wishBar);
        }
        for (Item item : shards) {
            if (item.isValid()) item.remove();
        }
        shards.clear();
        if (world != null) {
            world.setFullTime(oldTime);
            world.setStorm(oldStorm);
            world.setThundering(oldThunder);
        }
        Msg.log("Звездопад завершён. Желаний загадано: " + wishesGranted);
    }
}
