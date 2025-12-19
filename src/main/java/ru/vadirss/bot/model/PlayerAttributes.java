package ru.vadirss.bot.model;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PlayerAttributes {

    // Technical
    public double shortPass;
    public double firstTouch;
    public double longPass;
    public double positioning;
    public double heading;
    public double ballBattle;

    // Physical
    public double strength;
    public double flexibility;
    public double speed;
    public double endurance;
    public double agility;

    // Mental
    public double analysis;
    public double communication;
    public double teamwork;
    public double concentration;
    public double nervousness;
    public double leadership;

    public String updatedAt;

    public Map<String, Double> asOrderedMap() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("Короткий пас", shortPass);
        m.put("Первое касание", firstTouch);
        m.put("Дальний пас", longPass);
        m.put("Выбор позиции", positioning);
        m.put("Удар головой", heading);
        m.put("Навыки борьбы за мяч", ballBattle);

        m.put("Сила", strength);
        m.put("Гибкость", flexibility);
        m.put("Скорость", speed);
        m.put("Выносливость", endurance);
        m.put("Ловкость", agility);

        m.put("Аналитическое мышление", analysis);
        m.put("Общение", communication);
        m.put("Работа в команде", teamwork);
        m.put("Концентрация", concentration);
        m.put("Волнение в игре", nervousness);
        m.put("Лидерство", leadership);
        return m;
    }

    public static PlayerAttributes emptyNow(String now) {
        PlayerAttributes a = new PlayerAttributes();
        a.updatedAt = now;
        return a;
    }
}
