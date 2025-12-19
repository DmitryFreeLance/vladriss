package ru.vadirss.bot.util;

public final class Html {
    private Html() {}

    public static String esc(String s) {
        if (s == null) return "";
        return s
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}