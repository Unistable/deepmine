package ru.deepprogression.util;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import ru.deepprogression.DeepProgressionPlugin;

/** Ключи PDC для тегирования предметов и данных. */
public final class Keys {
    public static NamespacedKey TOOL_ID;      // STRING — id инструмента
    public static NamespacedKey MEDAL;        // STRING — "ВЕТКА:тир"
    public static NamespacedKey PROBE_CHARGES; // INTEGER — заряды зонда
    public static NamespacedKey CD_STATE;     // STRING — EMPTY/CHARGED/LOADED
    public static NamespacedKey CD_CONTENTS;  // STRING — "MATERIAL:кол-во,..."
    public static NamespacedKey CD_COOLDOWN;  // LONG — время последней выработки
    public static NamespacedKey POUCH_CONTENTS; // STRING — содержимое рудного тайника
    public static NamespacedKey POUCH_SESSION; // STRING — маркер открытой сессии тайника
    public static NamespacedKey MACHINE;      // STRING (TileState) — id машины блока

    private Keys() {}

    public static void init(DeepProgressionPlugin plugin) {
        TOOL_ID = new NamespacedKey(plugin, "tool_id");
        MEDAL = new NamespacedKey(plugin, "medal");
        PROBE_CHARGES = new NamespacedKey(plugin, "probe_charges");
        CD_STATE = new NamespacedKey(plugin, "cd_state");
        CD_CONTENTS = new NamespacedKey(plugin, "cd_contents");
        CD_COOLDOWN = new NamespacedKey(plugin, "cd_cooldown");
        POUCH_CONTENTS = new NamespacedKey(plugin, "pouch_contents");
        POUCH_SESSION = new NamespacedKey(plugin, "pouch_session");
        MACHINE = new NamespacedKey(plugin, "machine");
    }
}
