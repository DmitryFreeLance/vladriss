package ru.vadirss.bot.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import ru.vadirss.bot.config.Config;
import ru.vadirss.bot.db.Database;

import java.io.File;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ExcelService {

    private final Database db;
    private final Config cfg;

    public ExcelService(Database db, Config cfg) {
        this.db = db;
        this.cfg = cfg;
    }

    public File buildTeamStatsExcel(long teamId) {
        String fileName = "team_" + teamId + "_stats.xlsx";
        try {
            File tmp = File.createTempFile("vadirss_", "_" + fileName);
            try (Workbook wb = new XSSFWorkbook()) {
                Sheet sheet = wb.createSheet("Stats");

                int r = 0;
                Row header = sheet.createRow(r++);
                int c = 0;
                header.createCell(c++).setCellValue("ФИО");
                header.createCell(c++).setCellValue("Позиция");
                header.createCell(c++).setCellValue("LIM (avg)");
                header.createCell(c++).setCellValue("T2 (avg)");
                header.createCell(c++).setCellValue("EIQ (avg)");

                try (Connection conn = db.getConnection()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT u.full_name, u.position, " +
                                    "ROUND(AVG(cr.lim), 2) AS lim_avg, " +
                                    "ROUND(AVG(cr.t2), 2) AS t2_avg, " +
                                    "ROUND(AVG(cr.eiq), 2) AS eiq_avg " +
                                    "FROM users u " +
                                    "LEFT JOIN coach_ratings cr ON cr.player_id = u.tg_id " +
                                    "WHERE u.role='PLAYER' AND u.team_id=? " +
                                    "GROUP BY u.tg_id " +
                                    "ORDER BY u.full_name"
                    )) {
                        ps.setLong(1, teamId);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                Row row = sheet.createRow(r++);
                                int cc = 0;
                                row.createCell(cc++).setCellValue(nvl(rs.getString("full_name")));
                                row.createCell(cc++).setCellValue(nvl(rs.getString("position")));
                                setNumOrEmpty(row.createCell(cc++), rs, "lim_avg");
                                setNumOrEmpty(row.createCell(cc++), rs, "t2_avg");
                                setNumOrEmpty(row.createCell(cc++), rs, "eiq_avg");
                            }
                        }
                    }
                }

                for (int i = 0; i < 5; i++) sheet.autoSizeColumn(i);

                try (FileOutputStream fos = new FileOutputStream(tmp)) {
                    wb.write(fos);
                }
            }
            return tmp;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setNumOrEmpty(Cell cell, ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        if (rs.wasNull()) {
            cell.setCellValue("");
        } else {
            cell.setCellValue(v);
        }
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
