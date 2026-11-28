package com.ruoyi.web.controller.weather;

import com.ruoyi.system.domain.WeatherRecord;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** M08 天气记录 Excel 导入导出（POI）。 */
public final class WeatherWorkbook {

    public static final String[] HEADERS = {"城市编码", "预报时间", "天气现象", "最低温", "最高温", "湿度", "风力", "数据来源"};

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private WeatherWorkbook() {
    }

    public static void write(List<WeatherRecord> rows, OutputStream out) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("气象记录");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
                sheet.setColumnWidth(i, 4200);
            }
            int index = 1;
            for (WeatherRecord record : rows) {
                Row row = sheet.createRow(index++);
                row.createCell(0).setCellValue(value(record.getCityCode()));
                row.createCell(1).setCellValue(record.getForecastTime() == null ? "" : record.getForecastTime().format(FMT));
                row.createCell(2).setCellValue(value(record.getWeatherText()));
                row.createCell(3).setCellValue(record.getTempMin() == null ? "" : record.getTempMin().toPlainString());
                row.createCell(4).setCellValue(record.getTempMax() == null ? "" : record.getTempMax().toPlainString());
                row.createCell(5).setCellValue(record.getHumidity() == null ? "" : record.getHumidity().toPlainString());
                row.createCell(6).setCellValue(value(record.getWindLevel()));
                row.createCell(7).setCellValue(value(record.getDataSource()));
            }
            workbook.write(out);
        }
    }

    public static void writeTemplate(OutputStream out) throws IOException {
        write(new ArrayList<>(), out);
    }

    public static List<List<String>> read(InputStream in) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                List<String> values = new ArrayList<>();
                boolean blank = true;
                for (int c = 0; c < HEADERS.length; c++) {
                    String text = cell(row.getCell(c));
                    values.add(text);
                    if (!text.isEmpty()) blank = false;
                }
                if (!blank) rows.add(values);
            }
        }
        return rows;
    }

    private static String cell(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC) {
            return org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().format(FMT)
                    : new java.math.BigDecimal(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        return cell.toString().trim();
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
