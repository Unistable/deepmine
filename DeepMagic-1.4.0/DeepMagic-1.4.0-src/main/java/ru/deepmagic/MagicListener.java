package ru.deepmagic;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.inventory.ItemStack;
import ru.deepmagic.compress.CompressorGui;
import ru.deepmagic.compress.Compressors;
import ru.deepmagic.craft.Crafts;
import ru.deepmagic.model.EternalBurn;
import ru.deepmagic.model.ItemDefs;
import ru.deepmagic.model.State;
import ru.deepmagic.staff.Cooldowns;
import ru.deepmagic.staff.StaffUse;
import ru.deepmagic.synergy.Synergy;
import ru.deepmagic.util.Conf;
import ru.deepmagic.util.Msg;
import ru.deepmagic.util.T;
import ru.deepprogression.model.Branch;
import ru.deepprogression.progress.ProfileStore;
import ru.deepprogression.progress.PlayerProfile;

import java.util.Map;

/** Все слушатели DeepMagic. */
public final class MagicListener implements Listener {

    private final DeepMagicPlugin plugin;

    public MagicListener(DeepMagicPlugin plugin) {
        this.plugin = plugin;
    }

    // ==================== посохи ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack hand = e.getItem();
        if (ItemDefs.isStaff(hand)) {
            e.setCancelled(true);
            StaffUse.use(e.getPlayer(), e.getPlayer().isSneaking());
            return;
        }
        if (a == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && Compressors.isCompressorBlock(e.getClickedBlock())) {
            // DP-инструмент в руке — приоритет у DeepProgression
            if (hand != null && ru.deepprogression.tools.Items.toolId(hand) != null) return;
            e.setCancelled(true);
            if (e.getPlayer().isSneaking() && hand != null && !hand.getType().isAir()) {
                depositToCompressor(e.getPlayer(), e.getClickedBlock(), hand);
            } else {
                CompressorGui.open(e.getPlayer(), e.getClickedBlock());
            }
        }
    }

    /** Закладка ресурсов/веществ в компрессор (Shift+ПКМ с предметом). */
    private void depositToCompressor(Player p, Block b, ItemStack hand) {
        Material m = hand.getType();

        // 0) анти-потеря: наши предметы и медали DP — НЕ ресурс
        if (ItemDefs.isCompressor(hand) || ItemDefs.isStaff(hand)
                || ru.deepprogression.tools.Items.medalBranch(hand) != null) {
            Msg.dm(p, "&cЭто не ресурс — закладывать в компрессор нельзя.");
            return;
        }

        // 1) вещество → пересжатие (гейт по медали в профиле)
        if (ItemDefs.isSubstance(hand)) {
            Branch br = ItemDefs.substanceBranch(hand);
            int tier = ItemDefs.substanceTier(hand);
            if (br == null || tier >= 3) {
                Msg.dm(p, "&7Вещество III тира — вершина сжатия, дальше некуда.");
                return;
            }
            int need = tier == 1 ? Conf.recompressGate2() : Conf.recompressGate3();
            PlayerProfile prof = ProfileStore.get(p.getUniqueId());
            if (prof.getTier(br) < need) {
                Msg.dm(p, "&cПересжатие " + T.roman(tier) + "→" + T.roman(tier + 1)
                        + " требует медаль &e" + br.display + " тир " + need + "&c (у вас: "
                        + prof.getTier(br) + ").");
                return;
            }
            Map<String, Long> subin = Compressors.readTriple(b, Compressors.TAG_SUBIN);
            subin.merge(br.name() + ":" + tier, 1L, Long::sum);
            Compressors.writeTriple(b, Compressors.TAG_SUBIN, subin);
            hand.setAmount(hand.getAmount() - 1);
            p.playSound(b.getLocation(), org.bukkit.Sound.BLOCK_PISTON_CONTRACT, 0.6f, 1.2f);
            Msg.dm(p, "&7Вещество «" + br.display + "» загружено на пересжатие ("
                    + T.roman(tier) + "→" + T.roman(tier + 1) + ").");
            return;
        }

        // 2) ресурс из реестра DeepProgression
        Branch br = ru.deepprogression.model.Blocks.branchOfItem(m);
        if (br == null) {
            Msg.dm(p, "&7" + ItemDefs.ruName(m) + " &c— не ресурс реестра DeepProgression.");
            return;
        }
        // Магмовый крем — ТОПЛИВО, а не ресурс: не сжимается, честное сообщение
        if (m == Material.MAGMA_CREAM) {
            long qty2 = hand.getAmount();
            long deposited2 = Compressors.deposit(b, hand, qty2);
            hand.setAmount((int) (qty2 - deposited2));
            p.playSound(b.getLocation(), org.bukkit.Sound.BLOCK_PISTON_CONTRACT, 0.6f, 1.1f);
            Msg.dm(p, "&6Топливо: &e" + T.num(deposited2) + "× магмовый крем&7 (1 крем = "
                    + T.num(ru.deepmagic.util.Conf.unitsPerCycle())
                    + " ед. сжатия). Сжимать крем нельзя — магму ветки закладывайте "
                    + "&eмагмовым блоком&7.");
            return;
        }
        // Гейт: медаль ветки тир ≥ 3 в профиле
        PlayerProfile prof = ProfileStore.get(p.getUniqueId());
        if (prof.getTier(br) < Conf.craftGateTier()) {
            Msg.dm(p, "&cКомпрессор принимает &e" + br.display
                    + "&c только со званием тир &e" + Conf.craftGateTier()
                    + "+&c (у вас: &e" + prof.getTier(br) + "&c).");
            return;
        }
        long qty = hand.getAmount();
        long deposited = Compressors.deposit(b, hand, qty);
        hand.setAmount((int) (qty - deposited));
        p.playSound(b.getLocation(), org.bukkit.Sound.BLOCK_PISTON_CONTRACT, 0.6f, 1f);
        Msg.dm(p, "&7Заложено: &e" + T.num(deposited) + "× " + ItemDefs.ruName(m)
                + "&7 (" + br.color + br.display + "&7). Для партии: &e"
                + T.num(Compressors.batchNeed(br)) + " ед.");
    }

    // ==================== компрессор: жизненный цикл ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        ItemStack hand = e.getItemInHand();
        // Компрессор: ставим блок и тегируем (MONITOR — после всех проверок других плагинов)
        if (ItemDefs.isCompressor(hand)) {
            Block b = e.getBlockPlaced();
            if (b.getType() != Material.PISTON) return;
            Compressors.install(b);
            Msg.dm(e.getPlayer(), "&5Магический Компрессор установлен.");
            return;
        }
        // Вещества и посохи ставить нельзя
        if (ItemDefs.isSubstance(hand) || ItemDefs.isStaff(hand)) {
            e.setCancelled(true);
            Msg.dm(e.getPlayer(), "&7Артефакт магии нельзя поставить как блок.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        // Медный якорь сломан — снимаем с притяжения
        if (b.getType() == Material.LIGHTNING_ROD) {
            Synergy.removeAnchor(b);
        }
        if (Compressors.isCompressorBlock(b)) {
            e.setCancelled(true);
            // Окна открытых GUI этой машины закрываем — иначе «призрачный» GUI
            // останется смотреть в пустоту и писать PDC по старому ключу
            CompressorGui.closeAll(b);
            // Содержимое возвращается в ЛЮБОМ режиме (анти-потеря)
            Compressors.dropAll(b);
            if (e.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE) {
                var item = ItemDefs.compressorItem();
                if (item != null) {
                    b.getWorld().dropItemNaturally(
                            b.getLocation().add(0.5, 0.5, 0.5), item);
                }
            }
            b.setType(Material.AIR);
            Compressors.unregisterAll(b);
            e.getPlayer().playSound(b.getLocation(),
                    org.bukkit.Sound.BLOCK_STONE_BREAK, 0.8f, 0.9f);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(Compressors::isCompressorBlock);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(Compressors::isCompressorBlock);
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) {
            if (Compressors.isCompressorBlock(b)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) {
            if (Compressors.isCompressorBlock(b)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent e) {
        Compressors.scanChunk(e.getChunk());
    }

    // ==================== крафты ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        Crafts.handlePrepare(e);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onCraftClick(InventoryClickEvent e) {
        Crafts.handleClick(e);
    }

    // ==================== GUI меню (Магический Арсенал) ====================

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof ru.deepmagic.menu.MenuGui.Holder mh)) return;
        // Меню — витрина: любые клики (в т.ч. по своему инвентарю) гасим,
        // как в DeepProgression; обработка — только по верхнему окну.
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (e.getWhoClicked() instanceof Player p) {
            ru.deepmagic.menu.MenuGui.handleClick(p, mh, e.getRawSlot());
        }
    }

    @EventHandler
    public void onMenuDrag(org.bukkit.event.inventory.InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof ru.deepmagic.menu.MenuGui.Holder) {
            e.setCancelled(true);
        }
    }

    // ==================== GUI компрессора ====================

    @EventHandler
    public void onGuiClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof CompressorGui.Holder h)) return;
        boolean top = e.getClickedInventory() == e.getView().getTopInventory();
        if (top) {
            // Верхний инвентарь — витрина и забор (закладка снаружи:
            // Shift+ПКМ по блоку). Все клики отменяются, забор — по логике GUI.
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player p) {
                CompressorGui.handleClick(p, h, e.getRawSlot(), e.getClick().isShiftClick());
            }
            return;
        }
        // Личный инвентарь: обычные клики разрешены (не «колхоз»). Опасные
        // пути (shift-перенос в витрину, цифровые слоты, двойной сбор) — гасим.
        org.bukkit.event.inventory.ClickType click = e.getClick();
        if (click.isShiftClick() || click.isKeyboardClick()
                || click == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onGuiDrag(org.bukkit.event.inventory.InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof CompressorGui.Holder)) return;
        // Гасим только перетаскивания, задевающие витрину; по своему инвентарю — можно.
        int topSize = e.getView().getTopInventory().getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < topSize) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onGuiClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof CompressorGui.Holder h
                && e.getPlayer() instanceof Player p) {
            CompressorGui.closed(h);
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_PISTON_CONTRACT, 0.5f, 1f);
        }
    }

    // ==================== анти-потеря: авто-подбор наших предметов ====================

    /** DP маршрутизирует vault-материалы в тайник и теряет PDC. Мы перехватываем раньше. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        ItemStack stack = e.getItem().getItemStack();
        if (!ItemDefs.isOurs(stack)) return;
        e.setCancelled(true);
        var leftover = p.getInventory().addItem(stack.clone());
        if (leftover.isEmpty()) {
            e.getItem().remove();
        } else {
            // остатки возвращаем на землю честно: первый — в предмет, остальные — рядом
            boolean first = true;
            for (ItemStack rest : leftover.values()) {
                if (first) {
                    e.getItem().setItemStack(rest);
                    first = false;
                } else {
                    var drop = e.getItem().getWorld()
                            .dropItem(e.getItem().getLocation(), rest);
                    drop.setPickupDelay(100); // не циклим подбор
                }
            }
        }
    }

    // ==================== синергии: урон ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) {
            Synergy.onDamage(e);
            if (!e.isCancelled()) Synergy.noKnockback(p);
        }
    }

    /** Моховая подушка: урон от падения в зоне отменён (мобам и игрокам). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallMoss(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        if (Synergy.inMossZone(le.getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageMonitor(EntityDamageEvent e) {
        Synergy.afterDamage(e);
    }

    /** Остеклён/хрупкий: ЛЮБОЙ физический удар сущностью — шаттер (мил/посох/снаряд). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHitByEntity(EntityDamageByEntityEvent e) {
        Synergy.onPhysicalHit(e);
    }

    /** Огонь по угольному покрытию от обычных источников — вдвое дольше. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent e) {
        if (!(e.getEntity() instanceof org.bukkit.entity.LivingEntity le)) return;
        if (!State.CARBON_COATED.has(le)) return;
        if (EternalBurn.burning(le)) return;
        e.setDuration(e.getDuration() * 2);
    }

    /** Гнили и зараза: смерть от яда заражает соседей. Смерть — сброс всех состояний. */
    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        var le = e.getEntity();
        if (Synergy.contagious(le)) {
            for (var other : le.getLocation().getWorld()
                    .getNearbyEntities(le.getLocation(), 3, 3, 3)) {
                if (other instanceof org.bukkit.entity.LivingEntity oe && !oe.isDead()) {
                    oe.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.POISON, 6 * 20, 0));
                    Synergy.markContagious(oe, 6);
                }
            }
        }
        // Смерть — универсальный сброс (в т.ч. вечное горение): игрок не горит после респавна
        State.wipeAll(le);
        // очистка временной подсветки
        ru.deepmagic.synergy.Synergy.clearTempGlow(le.getUniqueId());
    }

    /** Агро-состояния: позолота, обаяние, маска, тишина. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getEntity() instanceof org.bukkit.entity.Mob mob)) return;
        LivingEntity target = e.getTarget();
        if (target == null) return;
        // Позолота: незер-орда и зомби-семья не трогают
        var gildedIgnore = java.util.Set.of(
                org.bukkit.entity.EntityType.PIGLIN,
                org.bukkit.entity.EntityType.PIGLIN_BRUTE,
                org.bukkit.entity.EntityType.ZOMBIFIED_PIGLIN,
                org.bukkit.entity.EntityType.ZOMBIE,
                org.bukkit.entity.EntityType.HUSK,
                org.bukkit.entity.EntityType.DROWNED,
                org.bukkit.entity.EntityType.ZOMBIE_VILLAGER);
        if (State.GILDED.has(target) && gildedIgnore.contains(mob.getType())) {
            e.setCancelled(true);
            return;
        }
        // Обаяние: меченый не таргетится вовсе
        if (State.BLOOMED.has(target)) {
            e.setCancelled(true);
            return;
        }
        // Шерстяная тишина: замолчавшего не слышат и не находят
        if (Synergy.silenced(target)) {
            e.setCancelled(true);
            return;
        }
        // Терракотовая маска: игрок-пейзаж
        if (target instanceof Player pt && Synergy.masked(pt)) {
            e.setCancelled(true);
        }
    }

    /** Сенсоры не слышат замолчавших. */
    @EventHandler(ignoreCancelled = true)
    public void onGameEvent(org.bukkit.event.block.BlockReceiveGameEvent e) {
        if (e.getEntity() instanceof org.bukkit.entity.LivingEntity le
                && Synergy.silenced(le)) {
            e.setCancelled(true);
        }
    }

    /** Телепорты: душевная печать и пустотный обелиск (в т.ч. плагиновые —
     *  жезлы искривления, хорус-перелёты). Админы — исключение. */
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        boolean soulBlock = State.SOUL_MARKED.has(p);
        boolean obelisk = Synergy.inObeliskZone(e.getTo())
                || Synergy.inObeliskZone(e.getFrom());
        boolean seal = soulBlock || obelisk;
        PlayerTeleportEvent.TeleportCause cause = e.getCause();
        boolean blocked = seal && (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT
                || cause == PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT
                || cause == PlayerTeleportEvent.TeleportCause.PLUGIN);
        if (blocked && !p.hasPermission("deepmagic.admin")) {
            e.setCancelled(true);
            if (soulBlock) Msg.dm(p, "&7Душевная печать держит вас на месте.");
            else Msg.dm(p, "&7Пустотный обелиск запечатал пространство.");
        }
        // Багровое бешенство: жемчуг заблокирован
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                && Synergy.raging(p)) {
            e.setCancelled(true);
            Msg.dm(p, "&cЯрость не отпускает жемчуг!");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTeleport(org.bukkit.event.entity.EntityTeleportEvent e) {
        if (e.getEntity() instanceof org.bukkit.entity.LivingEntity le
                && State.SOUL_MARKED.has(le)) {
            e.setCancelled(true);
        }
    }

    /** Молнии: медный якорь притягивает удары. */
    @EventHandler(ignoreCancelled = true)
    public void onLightning(LightningStrikeEvent e) {
        // Анти-цикл: удар уже в точке якоря — не редиректим повторно
        if (Synergy.nearAnchor(e.getLightning().getLocation(), 2)) return;
        if (Synergy.nearAnchor(e.getLightning().getLocation(), 24)) {
            // редирект: отменяем случайный удар и бьём в якорь (самый ближний)
            e.setCancelled(true);
            Block anchor = nearestAnchor(e.getLightning().getLocation(), 24);
            if (anchor != null) {
                e.getWorld().strikeLightning(anchor.getLocation().add(0.5, 0, 0.5));
                // цепной разряд по меченым возле якоря
                for (var other : anchor.getWorld()
                        .getNearbyEntities(anchor.getLocation().add(0.5, 0, 0.5), 4, 4, 4)) {
                    if (other instanceof org.bukkit.entity.LivingEntity le && !le.isDead()
                            && (State.CARBON_COATED.has(le) || State.CHARGED.has(le)
                            || State.GILDED.has(le))) {
                        le.damage(6.0);
                        le.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK,
                                le.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.1);
                    }
                }
            }
        }
    }

    private Block nearestAnchor(Location l, int radius) {
        Block best = null;
        double bestDist = radius * (double) radius;
        for (String a : Synergy.anchors()) {
            String[] parts = a.split(":");
            if (!parts[0].equals(l.getWorld().getName())) continue;
            try {
                int ax = Integer.parseInt(parts[1]), ay = Integer.parseInt(parts[2]),
                        az = Integer.parseInt(parts[3]);
                double d = Math.pow(ax - l.getBlockX(), 2)
                        + Math.pow(ay - l.getBlockY(), 2) + Math.pow(az - l.getBlockZ(), 2);
                if (d < bestDist) {
                    bestDist = d;
                    best = l.getWorld().getBlockAt(ax, ay, az);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return best;
    }

    // ==================== прочее ====================

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Cooldowns.trim();
        Synergy.clearPlayer(e.getPlayer().getUniqueId());
    }
}
