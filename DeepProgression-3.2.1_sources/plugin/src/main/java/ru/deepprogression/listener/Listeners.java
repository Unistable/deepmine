package ru.deepprogression.listener;

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
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import ru.deepprogression.gui.Gui;
import ru.deepprogression.model.Blocks;
import ru.deepprogression.model.Placed;
import ru.deepprogression.progress.Medals;
import ru.deepprogression.progress.PlayerProfile;
import ru.deepprogression.progress.ProfileStore;
import ru.deepprogression.recipe.Recipes;
import ru.deepprogression.storage.Vaults;
import ru.deepprogression.tools.ChunkDrill;
import ru.deepprogression.tools.Drills;
import ru.deepprogression.tools.Items;
import ru.deepprogression.tools.Machines;
import ru.deepprogression.tools.Probe;
import ru.deepprogression.tools.Tools;
import ru.deepprogression.util.Cfg;
import ru.deepprogression.util.Keys;
import ru.deepprogression.util.Msg;
import ru.deepprogression.util.Text;

/** Все слушатели событий. */
public final class Listeners implements Listener {

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        // Загрузка профиля с диска вне главного потока
        ProfileStore.load(e.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerProfile ref = ProfileStore.get(p.getUniqueId());
        ref.setLastName(p.getName());
        ref.trimActiveCooldowns(System.currentTimeMillis());
        ProfileStore.saveAsync(p.getUniqueId());
        Medals.checkAll(p);
        ru.deepprogression.progress.Quests.ensureToday(ref, p.getUniqueId());
        Msg.send(p, "join.welcome");
    }

    /** Очистка кэшей (U21 + троттлинг U4 + сессия тайника D2 + приспособления v3.2). */
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        ProfileStore.saveAsync(e.getPlayer().getUniqueId());
        ProfileStore.evict(e.getPlayer().getUniqueId());
        Probe.clear(e.getPlayer().getUniqueId());
        Drills.clearPlayer(e.getPlayer().getUniqueId());
        Vaults.clearSession(e.getPlayer().getUniqueId());
        ru.deepprogression.perks.Perks.clear(e.getPlayer().getUniqueId());
        ru.deepprogression.tools.Gadgets.clear(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        // v3.2: синтетические события сучкореза — не считаем их за реальную добычу
        if (ru.deepprogression.tools.Gadgets.isFiringInternal()) return;
        if (Vaults.machineId(e.getBlock()) != null) {
            Machines.handleBreak(e);
            return;
        }
        Drills.handle(e);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        // Машины разрешены (регистрируем TileState), прочие тегированные — нет
        if (Machines.isMachineItem(e.getItemInHand())) {
            Machines.place(e);
            return;
        }
        ItemStack hand = e.getItemInHand();
        // U68: медали и инструменты нельзя ставить как блоки
        if (Items.medalBranch(hand) != null || Items.toolId(hand) != null) {
            e.setCancelled(true);
            return;
        }
        // Анти-абуз «поставил-сломал»: помечаем блоки веток
        Placed.onPlayerPlace(e.getBlockPlaced(), Blocks.branchOf(e.getBlockPlaced().getType()));
    }

    @EventHandler
    public void onGrow(org.bukkit.event.block.BlockGrowEvent e) {
        Placed.onGrow(e.getNewState().getBlock());
    }

    @EventHandler
    public void onStructureGrow(org.bukkit.event.world.StructureGrowEvent e) {
        Placed.onStructureGrow(e.getBlocks());
    }

    @EventHandler
    public void onSpread(org.bukkit.event.block.BlockSpreadEvent e) {
        Placed.onSpread(e.getNewState().getBlock());
    }

    @EventHandler
    public void onEntityChangeBlock(org.bukkit.event.entity.EntityChangeBlockEvent e) {
        Placed.onEntityChange(e);
    }

    @EventHandler
    public void onChunkUnload(org.bukkit.event.world.ChunkUnloadEvent e) {
        Placed.onChunkUnload(e);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player p = e.getPlayer();

        // 1) Цель — машина (статус важнее предмета в руке)
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            String machine = Vaults.machineId(e.getClickedBlock());
            if (machine != null) {
                switch (machine) {
                    case "charge_station" -> {
                        e.setCancelled(true);
                        Machines.useStation(p, e.getClickedBlock(), e.getItem());
                        return;
                    }
                    case "geo_scanner" -> {
                        e.setCancelled(true);
                        Machines.useScanner(p, e.getClickedBlock());
                        return;
                    }
                    case "ore_vault" -> {
                        if (p.isSneaking() && e.getItem() != null
                                && Blocks.isVaultItem(e.getItem().getType())) {
                            e.setCancelled(true);
                            Vaults.sweepToVault(p, e.getClickedBlock());
                        }
                        return; // иначе — ванильный GUI склада
                    }
                    case "sawmill", "composter", "herbarium", "dryer", "glassworks",
                            "luminaforge", "assembler", "altar", "chorus_reaper",
                            "echo_chamber", "resinworks", "bakery", "nursery",
                            "coral_farm", "stone_works", "candleworks", "rail_works",
                            "foundry", "end_forge", "resonance_chamber" -> {
                        e.setCancelled(true);
                        Machines.useMachine(p, e.getClickedBlock(), machine, e.getItem(),
                                p.isSneaking());
                        return;
                    }
                    default -> { }
                }
            }
        }

        // 2) Предмет в руке — инструменты и хранилища
        ItemStack item = e.getItem();
        String id = Items.toolId(item);
        if (id == null) return;
        Tools.ToolSpec spec = Tools.get(id);
        if (spec == null) return;
        switch (spec.type) {
            case PROSPECTOR -> {
                e.setCancelled(true);
                Probe.use(p);
            }
            case SECTION_SCANNER -> {
                e.setCancelled(true);
                Probe.use(p, spec.section);
            }
            case CRYSTAL -> {
                e.setCancelled(true);
                if (p.isSneaking()) {
                    ru.deepprogression.tools.Actives.use(p);
                } else {
                    Msg.send(p, "active.hint");
                }
            }
            case CHUNK_DRILL -> {
                e.setCancelled(true);
                ChunkDrill.interact(p);
            }
            case BATTERY -> {
                e.setCancelled(true);
                useBattery(p);
            }
            case POUCH -> {
                e.setCancelled(true);
                if (p.isSneaking()) Vaults.sweepToPouch(p);
                else Vaults.openPouch(p, e.getItem());
            }
            case GADGET -> {
                e.setCancelled(true);
                ru.deepprogression.tools.Gadgets.use(p, id);
            }
            default -> { }
        }
    }

    private void useBattery(Player p) {
        ItemStack probe = p.getInventory().getItemInOffHand();
        String id = Items.toolId(probe);
        if (id == null || !("prospector".equals(id) || "air_tank".equals(id)
                || id.startsWith("scanner_"))) {
            Msg.send(p, "battery.probe-required");
            return;
        }
        var meta = probe.getItemMeta();
        if (meta == null) return;
        int cur = meta.getPersistentDataContainer()
                .getOrDefault(Keys.PROBE_CHARGES,
                        org.bukkit.persistence.PersistentDataType.INTEGER, 0);
        int max = Cfg.probeMaxCharges();
        if (cur >= max) {
            Msg.send(p, "battery.max-charges", "charges", String.valueOf(max));
            return;
        }
        int add = Cfg.batteryCharges();
        int next = Math.min(max, cur + add);
        meta.getPersistentDataContainer().set(Keys.PROBE_CHARGES,
                org.bukkit.persistence.PersistentDataType.INTEGER, next);
        probe.setItemMeta(meta);
        ItemStack battery = p.getInventory().getItemInMainHand();
        battery.setAmount(battery.getAmount() - 1);
        Msg.send(p, "battery.recharged",
                "n", String.valueOf(next - cur), "charges", String.valueOf(next));
        p.playSound(p.getLocation(), "minecraft:block.amethyst_block.chime", 0.9f, 1.4f);
    }

    /** Рудные предметы с земли — сразу в рудный тайник (v2.0). */
    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        ItemStack stack = e.getItem().getItemStack();
        if (!Blocks.isVaultItem(stack.getType())) return;
        int amount = stack.getAmount();
        int leftover = Vaults.route(p, stack.getType(), amount);
        if (leftover <= 0) {
            e.setCancelled(true);
            e.getItem().remove();
            return;
        }
        if (leftover < amount) {
            stack.setAmount(leftover);
            e.getItem().setItemStack(stack);
            e.setCancelled(true);
        }
    }

    /** Хопперы: в склад — только ресурсы; в прочие машины — ничего. */
    @EventHandler(ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent e) {
        var dest = e.getDestination().getHolder();
        if (dest instanceof org.bukkit.block.TileState ts) {
            String machine = ts.getPersistentDataContainer()
                    .get(Keys.MACHINE, org.bukkit.persistence.PersistentDataType.STRING);
            if (machine == null) return;
            if ("ore_vault".equals(machine)) {
                if (!Blocks.isVaultItem(e.getItem().getType())) e.setCancelled(true);
            } else {
                e.setCancelled(true);
            }
        }
    }

    /** Закрытие тайника — сохранить содержимое в предмет. */
    @EventHandler
    public void onInvClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (e.getInventory().getHolder() instanceof Gui.Holder h
                && h.type == Gui.MenuType.POUCH) {
            Vaults.savePouch(p, e.getInventory());
        }
    }

    /** Drag: в декоративные GUI ничего не перетаскиваем; в тайник — только руду. */
    @EventHandler
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Gui.Holder h)) return;
        ItemStack cursor = e.getOldCursor();
        boolean oreCursor = cursor != null && !cursor.getType().isAir()
                && Blocks.isVaultItem(cursor.getType());
        if (h.type == Gui.MenuType.POUCH) {
            int topSize = e.getView().getTopInventory().getSize();
            boolean touchesTop = e.getRawSlots().stream().anyMatch(s -> s < topSize);
            if (touchesTop && !oreCursor) e.setCancelled(true);
        } else {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        Machines.protectFromExplosion(e);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        Machines.protectFromExplosion(e);
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent e) {
        Machines.protectFromPiston(e);
        Placed.onPiston(e);
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent e) {
        Machines.protectFromPiston(e);
        Placed.onPiston(e);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (Recipes.handleCraftClick(e)) return;
        Gui.handleClick(e);
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        Recipes.handlePrepare(e);
    }

    // ==================== v3.1: перки ====================

    /** Модификация/отмена урона пассивными перками. */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL
                && ru.deepprogression.perks.Perks.pearlFree(p)) {
            e.setCancelled(true);
            return;
        }
        double mod = ru.deepprogression.perks.Perks.damageModifier(p, e);
        if (mod <= 0) e.setCancelled(true);
        else if (mod < 1.0) e.setDamage(e.getDamage() * mod);
    }

    /** Метка жемчуга (end_pearl). */
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent e) {
        if (e.getCause() == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            ru.deepprogression.perks.Perks.tagPearl(e.getPlayer());
        }
    }

    /** Сенсоры не слышат игрока с перком (sculk_quiet) или глушителем эха (v3.2). */
    @EventHandler(ignoreCancelled = true)
    public void onGameEvent(org.bukkit.event.block.BlockReceiveGameEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (ru.deepprogression.perks.Perks.sculkQuiet(p)) e.setCancelled(true);
        else if (ru.deepprogression.tools.Gadgets.jammed(p)) e.setCancelled(true);
    }

    /** Эндермены не агрятся (ender_calm). */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent e) {
        if (e.getTarget() instanceof Player p
                && e.getEntityType() == org.bukkit.entity.EntityType.ENDERMAN
                && ru.deepprogression.perks.Perks.enderCalm(p)) {
            e.setCancelled(true);
        }
    }
}
