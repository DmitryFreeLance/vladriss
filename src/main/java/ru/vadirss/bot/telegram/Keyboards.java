package ru.vadirss.bot.telegram;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Keyboards {

    private Keyboards() {}

    public static InlineKeyboardButton btn(String text, String data) {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText(text);
        b.setCallbackData(data);
        return b;
    }

    public static InlineKeyboardMarkup rows(List<List<InlineKeyboardButton>> rows) {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(rows);
        return m;
    }

    public static InlineKeyboardMarkup ofRows(List<InlineKeyboardButton>... rows) {
        List<List<InlineKeyboardButton>> list = new ArrayList<>();
        list.addAll(Arrays.asList(rows));
        return rows(list);
    }

    public static InlineKeyboardMarkup menuBack(String callback) {
        return ofRows(List.of(btn("⬅️ Вернуться в меню", callback)));
    }

    public static InlineKeyboardMarkup numbers1to10(String prefix) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 1; i <= 10; i += 5) {
            List<InlineKeyboardButton> r = new ArrayList<>();
            for (int j = i; j < i + 5 && j <= 10; j++) {
                r.add(btn(String.valueOf(j), prefix + j));
            }
            rows.add(r);
        }
        return rows(rows);
    }

    public static InlineKeyboardMarkup numbers0to4(String prefix) {
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (int i = 0; i <= 4; i++) row.add(btn(String.valueOf(i), prefix + i));
        return ofRows(row);
    }

    public static InlineKeyboardMarkup numbers0to3(String prefix) {
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (int i = 0; i <= 3; i++) row.add(btn(String.valueOf(i), prefix + i));
        return ofRows(row);
    }

    public static InlineKeyboardMarkup numbers0to2(String prefix) {
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (int i = 0; i <= 2; i++) row.add(btn(String.valueOf(i), prefix + i));
        return ofRows(row);
    }


    public static InlineKeyboardMarkup numbers0to10(String prefix) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i <= 10; i += 5) {
            List<InlineKeyboardButton> r = new ArrayList<>();
            for (int j = i; j < i + 5 && j <= 10; j++) {
                r.add(btn(String.valueOf(j), prefix + j));
            }
            rows.add(r);
        }
        return rows(rows);
    }

    public static InlineKeyboardMarkup moodButtons(String prefix) {
        return ofRows(
                List.of(
                        btn("😁", prefix + "HAPPY"),
                        btn("🙂", prefix + "GOOD"),
                        btn("😐", prefix + "OK"),
                        btn("😕", prefix + "BAD"),
                        btn("😞", prefix + "SAD")
                )
        );
    }

    public static InlineKeyboardMarkup yesNo(String yesData, String noData) {
        return ofRows(
                List.of(btn("✅ Выполнено", yesData), btn("❌ Не выполнено", noData))
        );
    }

    public static InlineKeyboardMarkup backOnly() {
        return menuBack(CallbackData.BACK_TO_MENU);
    }
}
