package com.ruoyi.web.controller.borrow;

import com.ruoyi.system.domain.BorrowRecord;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** 借阅记录的 Excel 导出与历史记录导入模板。 */
final class BorrowWorkbook {
    static final List<String> IMPORT_HEADERS = List.of("档号", "借阅人登录名", "借阅事由", "借期开始", "应归还日", "状态");
    private static final List<String> EXPORT_HEADERS = List.of("借阅单号", "档号", "档案题名", "申请人", "借期开始", "应归还日", "状态", "逾期", "借阅事由");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Map<String, String> LABELS = Map.of(
            "PENDING", "待审批", "APPROVED", "已批准", "REJECTED", "已驳回",
            "WITHDRAWN", "已撤回", "LENT", "已借出", "RETURNED", "已归还");

    private BorrowWorkbook() {
    }

    static void writeTemplate(OutputStream output) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("借阅导入模板");
            Row head = sheet.createRow(0);
            for (int i = 0; i < IMPORT_HEADERS.size(); i++) head.createCell(i).setCellValue(IMPORT_HEADERS.get(i));
            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("A02-2026-000001");
            sample.createCell(1).setCellValue("ry");
            sample.createCell(2).setCellValue("历史借阅记录");
            sample.createCell(3).setCellValue("2026-11-01");
            sample.createCell(4).setCellValue("2026-11-15");
            sample.createCell(5).setCellValue("RETURNED");
            for (int i = 0; i < IMPORT_HEADERS.size(); i++) sheet.setColumnWidth(i, 22 * 256);
            workbook.write(output);
        }
    }

    static void writeRecords(OutputStream output, List<BorrowRecord> rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("借阅记录");
            Row head = sheet.createRow(0);
            for (int i = 0; i < EXPORT_HEADERS.size(); i++) head.createCell(i).setCellValue(EXPORT_HEADERS.get(i));
            int index = 1;
            for (BorrowRecord record : rows) {
                Row row = sheet.createRow(index++);
                row.createCell(0).setCellValue(value(record.getBorrowNo()));
                row.createCell(1).setCellValue(value(record.getArchiveCode()));
                row.createCell(2).setCellValue(value(record.getArchiveTitle()));
                row.createCell(3).setCellValue(value(record.getApplicantName()));
                row.createCell(4).setCellValue(record.getStartDate() == null ? "" : record.getStartDate().format(DATE));
                row.createCell(5).setCellValue(record.getEndDate() == null ? "" : record.getEndDate().format(DATE));
                row.createCell(6).setCellValue(LABELS.getOrDefault(record.getBorrowStatus(), value(record.getBorrowStatus())));
                row.createCell(7).setCellValue(Boolean.TRUE.equals(record.getOverdue()) ? "已逾期" : "");
                row.createCell(8).setCellValue(value(record.getReason()));
            }
            for (int i = 0; i < EXPORT_HEADERS.size(); i++) sheet.setColumnWidth(i, 20 * 256);
            workbook.write(output);
        }
    }

    static List<List<String>> readRows(InputStream input) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < IMPORT_HEADERS.size(); c++) {
                    cells.add(formatter.formatCellValue(row.getCell(c)).trim());
                }
                if (cells.stream().allMatch(String::isEmpty)) continue;
                rows.add(cells);
            }
        }
        return rows;
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
