package ru.deepprogression.util;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.deepprogression.DeepProgressionPlugin;

import java.io.File;
import java.util.List;

/** Сообщения messages.yml (русский). */
public final class Msg {

    private static FileConfiguration m;
    private static DeepProgressionPlugin plugin;

    private Msg() {}

    public static void init(DeepProgressionPlugin pl) {
        plugin = pl;
        File f = new File(pl.getDataFolder(), "messages.yml");
        if (!f.exists()) pl.saveResource("messages.yml", false);
        m = YamlConfiguration.loadConfiguration(f);
        applyDefaults(m);
    }

    public static void reload(DeepProgressionPlugin pl) {
        File f = new File(pl.getDataFolder(), "messages.yml");
        if (!f.exists()) pl.saveResource("messages.yml", false);
        m = YamlConfiguration.loadConfiguration(f);
        applyDefaults(m);
    }

    /** Фолбэк: отсутствующие в старом файле ключи берутся из jar-ресурса. */
    private static void applyDefaults(FileConfiguration cfg) {
        try (java.io.InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                YamlConfiguration def = YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                cfg.setDefaults(def);
            }
        } catch (Exception ignored) {
        }
    }

    public static String raw(String key) {
        String s = m.getString(key);
        return s == null ? "&c[нет сообщения: " + key + "]" : s;
    }

    public static String prefix() {
        return Text.color(raw("prefix"));
    }

    /** Полное сообщение с префиксом и подстановками (пары key,value...). */
    public static String fmt(String key, String... pairs) {
        String s = raw(key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            s = s.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return prefix() + Text.color(s);
    }

    public static void send(CommandSender to, String key, String... pairs) {
        Text.send(to, fmt(key, pairs));
    }

    /** Список строк (для help и отчётов) с префиксом на первой строке. */
    public static void sendList(CommandSender to, String key, String... pairs) {
        List<String> list = m.getStringList(key);
        if (list.isEmpty()) {
            Text.send(to, fmt(key, pairs));
            return;
        }
        boolean first = true;
        for (String line : list) {
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                line = line.replace("{" + pairs[i] + "}", pairs[i + 1]);
            }
            String colored = Text.color(line);
            to.sendMessage(first ? prefix() + colored : colored);
            first = false;
        }
    }

    /** Сообщение без префикса (для GUI/lore). */
    public static String plain(String key, String... pairs) {
        String s = raw(key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            s = s.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return Text.color(s);
    }

    public static void action(Player p, String key, String... pairs) {
        p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                new net.md_5.bungee.api.chat.TextComponent(plain(key, pairs)));
    }
}
