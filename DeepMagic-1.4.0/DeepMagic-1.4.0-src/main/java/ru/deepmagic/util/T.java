package ru.deepmagic.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/** Утилиты текста DeepMagic. */
public final class T {
    private T() {}

    public static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    public static void send(CommandSender to, String msg) {
        if (msg == null || msg.isEmpty()) return;
        to.sendMessage(color(msg));
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

    public static String lower(String s) {
        return s == null ? "" : s.toLowerCase(java.util.Locale.ROOT);
    }

    public static String roman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            default -> "III";
        };
    }

    /** Тики в секунды для лора/сообщений: 40 → «2 с», 10 → «0.5 с». */
    public static String sec(int ticks) {
        if (ticks % 20 == 0) return (ticks / 20) + " с";
        return String.format(java.util.Locale.ROOT, "%.1f с", ticks / 20.0);
    }
}
