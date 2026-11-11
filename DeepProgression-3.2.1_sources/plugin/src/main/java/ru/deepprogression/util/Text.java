package ru.deepprogression.util;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Утилиты текста: цвета, бары, числа, экшен-бар. */
public final class Text {

    private Text() {}

    public static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    public static String colorList(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) sb.append(color(s)).append('\n');
        return sb.toString().trim();
    }

    public static void send(CommandSender to, String msg) {
        if (msg == null || msg.isEmpty()) return;
        to.sendMessage(msg);
    }

    public static void action(Player p, String legacyColored) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(legacyColored));
    }

    /** Строка-прогресс: ████░░░░ */
    public static String bar(long cur, long max, int len) {
        if (len <= 0) len = 10;
        if (max < 1) max = 1;
        if (cur < 0) cur = 0;
        if (cur > max) cur = max;
        int filled = (int) Math.round((double) cur / max * len);
        if (filled > len) filled = len;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(i < filled ? '█' : '░');
        return sb.toString();
    }

    /** Числа с пробелами: 1234567 -> 1 234 567 */
    public static String num(long n) {
        String s = Long.toString(n);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            sb.append(s.charAt(i));
            int rest = s.length() - 1 - i;
            if (rest > 0 && rest % 3 == 0) sb.append(' ');
        }
        return sb.toString();
    }

    public static String num(double n) {
        return num((long) Math.round(n));
    }

    public static String pct(long cur, long max) {
        if (max < 1) max = 1;
        long p = Math.min(100, Math.max(0, cur * 100 / max));
        return Long.toString(p);
    }

    public static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    public static List<String> lines(String... s) {
        List<String> l = new ArrayList<>();
        for (String x : s) if (x != null) l.add(x);
        return l;
    }
}
