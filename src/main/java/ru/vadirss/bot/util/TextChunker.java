package ru.vadirss.bot.util;

import java.util.ArrayList;
import java.util.List;

public final class TextChunker {

    private TextChunker() {}

    public static List<String> splitByLines(String text, int maxLen) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        if (text.length() <= maxLen) {
            out.add(text);
            return out;
        }
        String[] lines = text.split("\n");
        StringBuilder cur = new StringBuilder();
        for (String line : lines) {
            if (cur.length() + line.length() + 1 > maxLen) {
                if (cur.length() > 0) out.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append("\n");
            cur.append(line);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }
}