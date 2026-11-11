package ru.deepmagic.util;

import org.bukkit.command.CommandSender;
import ru.deepmagic.DeepMagicPlugin;

/** Сообщения DeepMagic (префикс из конфига). */
public final class Msg {
    private Msg() {}

    public static void dm(CommandSender to, String msg) {
        T.send(to, Conf.prefix() + msg);
    }

    public static void actionBar(org.bukkit.entity.Player p, String msg) {
        p.sendActionBar(T.color(msg));
    }
}
