package ru.deepmagic.model;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.deepmagic.DeepMagicPlugin;
import ru.deepmagic.util.Conf;
import ru.deepmagic.util.T;
import ru.deepprogression.model.Branch;

import java.util.ArrayList;
import java.util.List;

/**
 * Построение всех предметов DeepMagic: вещества (3 тира), посохи (3 силы),
 * магический компрессор. PDC-ключи — источник истины идентификации.
 * Основа веществ: HEAVY_CORE (не vault-материал DP — не всасывается тайниками),
 * посохов: BREEZE_ROD. Все наши предметы блокируются от установки как блоков.
 */
public final class ItemDefs {

    public static final String SUBSTANCE_KEY = "dm_substance";   // STRING "BRANCH:tier"
    public static final String STAFF_KEY = "dm_staff";           // STRING "BRANCH"
    public static final String COMPRESSOR_KEY = "dm_compressor"; // BYTE 1

    private ItemDefs() {}

    public static NamespacedKey key(String name) {
        return new NamespacedKey(DeepMagicPlugin.get(), name);
    }

    /**
     * Стереть PDC-метки (для ВИТРИННЫХ копий в меню): копия не считается
     * настоящим посохом/веществом и не может участвовать в механиках.
     */
    public static void clearIds(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return;
        ItemMeta meta = it.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        pdc.remove(key(SUBSTANCE_KEY));
        pdc.remove(key(STAFF_KEY));
        pdc.remove(key(STAFF_KEY + "_tier"));
        pdc.remove(key(COMPRESSOR_KEY));
        it.setItemMeta(meta);
    }

    // ---------- идентификация ----------

    public static boolean isSubstance(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta() != null
                && it.getItemMeta().getPersistentDataContainer()
                        .has(key(SUBSTANCE_KEY), PersistentDataType.STRING);
    }

    public static boolean isStaff(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta() != null
                && it.getItemMeta().getPersistentDataContainer()
                        .has(key(STAFF_KEY), PersistentDataType.STRING);
    }

    public static boolean isCompressor(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta() != null
                && it.getItemMeta().getPersistentDataContainer()
                        .has(key(COMPRESSOR_KEY), PersistentDataType.BYTE);
    }

    public static boolean isOurs(ItemStack it) {
        return isSubstance(it) || isStaff(it) || isCompressor(it);
    }

    /** Ветка и тир вещества: "BRANCH:tier" или null. */
    public static Branch substanceBranch(ItemStack it) {
        if (!isSubstance(it)) return null;
        String v = it.getItemMeta().getPersistentDataContainer()
                .get(key(SUBSTANCE_KEY), PersistentDataType.STRING);
        if (v == null || !v.contains(":")) return null;
        return Branch.byId(v.substring(0, v.indexOf(':')));
    }

    public static int substanceTier(ItemStack it) {
        if (!isSubstance(it)) return 0;
        String v = it.getItemMeta().getPersistentDataContainer()
                .get(key(SUBSTANCE_KEY), PersistentDataType.STRING);
        if (v == null || !v.contains(":")) return 0;
        try {
            return Math.max(1, Math.min(3, Integer.parseInt(v.substring(v.indexOf(':') + 1))));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static Branch staffBranch(ItemStack it) {
        if (!isStaff(it)) return null;
        String v = it.getItemMeta().getPersistentDataContainer()
                .get(key(STAFF_KEY), PersistentDataType.STRING);
        return v == null ? null : Branch.byId(v);
    }

    // ---------- строительство ----------

    /** Вещество ветки тира 1..3. */
    public static ItemStack substance(Branch b, int tier) {
        Magic.StaffDef d = Magic.def(b);
        if (d == null) return null;
        tier = Math.max(1, Math.min(3, tier));
        ItemStack it = new ItemStack(Material.HEAVY_CORE);
        ItemMeta meta = it.getItemMeta();
        String color = b.color;
        meta.setDisplayName(T.color(color + d.substance()
                + (tier > 1 ? " &8· " + Conf.tierName(tier) : "")));
        List<String> lore = new ArrayList<>();
        lore.add("&7" + d.substanceLore());
        lore.add("");
        lore.add("&8Ветка: " + b.color + b.display + " &8(" + b.section.color
                + b.section.display + "&8)");
        lore.add("&8Ступень сжатия: &e" + ru.deepmagic.util.T.roman(tier)
                + (tier == 1 ? "" : tier == 2 ? " &8(×1.5 сила посоха)" : " &8(×2 сила посоха)"));
        lore.add("&8Ценность: " + rarityColor(b.rarity) + rarityName(b.rarity));
        lore.add("");
        lore.add("&5Компонент посоха «" + d.staff() + "»");
        lore.add("&8Магический артефакт DeepMagic");
        meta.setLore(lore.stream().map(ru.deepmagic.util.T::color).toList());
        meta.setEnchantmentGlintOverride(tier >= 2);
        meta.getPersistentDataContainer().set(key(SUBSTANCE_KEY),
                PersistentDataType.STRING, b.name() + ":" + tier);
        it.setItemMeta(meta);
        return it;
    }

    /** Посох ветки (сила = тир вещества 1..3). */
    public static ItemStack staff(Branch b, int tier) {
        Magic.StaffDef d = Magic.def(b);
        if (d == null) return null;
        tier = Math.max(1, Math.min(3, tier));
        ItemStack it = new ItemStack(Material.BREEZE_ROD);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(T.color(b.color + d.staff()
                + " &8[" + T.roman(tier) + "]"));
        List<String> lore = new ArrayList<>();
        lore.add("&7" + d.staffLore());
        lore.add("");
        lore.add("&8Стихия: " + d.element().color + d.element().display
                + " &8· Роль: &e" + d.role().display);
        lore.add("&7Топливо: &e" + d.fuelQty() + "× " + ruName(d.fuel())
                + " &7за применение");
        lore.add("&7Кулдаун: &e" + Conf.cooldown(d) + " с");
        lore.add("&7ПКМ — применить · Shift+ПКМ (уголь) — пыль");
        lore.add("");
        lore.add("&8Мана берётся из инвентаря или тайника ресурсов.");
        lore.add("&8Сила: &e" + ru.deepmagic.util.T.roman(tier)
                + (tier == 1 ? "" : tier == 2 ? " (×1.5)" : " (×2)"));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(key(STAFF_KEY),
                PersistentDataType.STRING, b.name());
        meta.getPersistentDataContainer().set(key(STAFF_KEY + "_tier"),
                PersistentDataType.INTEGER, tier);
        it.setItemMeta(meta);
        return it;
    }

    public static int staffTier(ItemStack it) {
        if (!isStaff(it)) return 0;
        Integer v = it.getItemMeta().getPersistentDataContainer()
                .get(key(STAFF_KEY + "_tier"), PersistentDataType.INTEGER);
        return v == null ? 1 : Math.max(1, Math.min(3, v));
    }

    /** Предмет магического компрессора. */
    public static ItemStack compressorItem() {
        ItemStack it = new ItemStack(Material.PISTON);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(T.color("&5&lМагический Компрессор"));
        List<String> lore = new ArrayList<>();
        lore.add("&7Прессует массовые ресурсы в сверхспрессованное");
        lore.add("&7вещество для изготовления посохов.");
        lore.add("");
        lore.add("&7ПКМ по блоку: &fоткрыть компрессор");
        lore.add("&7Топливо: &eмагмовый крем &7(1 на 512 ед. сжатия)");
        lore.add("&7Гейт: &eмедаль ветки тир 3+ &7для закладки ресурсов");
        lore.add("&7Пересжатие: &eвещество I→II (медаль 6), II→III (медаль 9)");
        lore.add("&8Ставится как поршень · содержимое не теряется");
        meta.setLore(lore.stream().map(ru.deepmagic.util.T::color).toList());
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(key(COMPRESSOR_KEY),
                PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    // ---------- вспомогательное ----------

    public static String rarityName(Branch.Rarity r) {
        return switch (r) {
            case COMMON -> "обычная";
            case MEDIUM -> "средняя";
            case RARE -> "редкая";
        };
    }

    public static String rarityColor(Branch.Rarity r) {
        return switch (r) {
            case COMMON -> "&f";
            case MEDIUM -> "&e";
            case RARE -> "&b";
        };
    }

    /** Русское имя материала (только для топлива/статусов, без DP-зависимости). */
    public static String ruName(Material m) {
        return ru.deepprogression.tools.Items.ruName(m);
    }
}
