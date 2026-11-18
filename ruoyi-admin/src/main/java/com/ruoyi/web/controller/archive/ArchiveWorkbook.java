package com.ruoyi.web.controller.archive;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.ArchiveRecord;
import com.ruoyi.system.service.ArchiveImportValidator;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
//excel模板设置
final class ArchiveWorkbook {
    static final List<String> HEADERS = ArchiveImportValidator.HEADERS;

    private ArchiveWorkbook() {}

    static void writeTemplate(OutputStream output, Map<Integer, String> secretOptions) {
        //导入模板附上当前启用的密级编码与名称
        try (var workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("档案");
            writeRow(sheet, 0, HEADERS);
            sheet.createFreezePane(0, 1);
            for (int col = 0; col < HEADERS.size(); col++) sheet.setColumnWidth(col, col == 5 ? 11000 : 6000);
            Sheet guide = workbook.createSheet("填写说明");
            guide.setColumnWidth(0, 22000);
            writeRow(guide, 0, List.of("日期支持yyyy-MM-dd、yyyy/MM/dd、yyyy\\MM\\dd或Excel日期单元格"));
            writeRow(guide, 1, List.of("分类编码须对应启用的末级分类"));
            writeRow(guide, 2, List.of("保管期限填写字典 arc_retention_period 中的编码"));
            writeRow(guide, 3, List.of("密级填写字典 arc_secret_level 中启用的数字编码"));
            int guideRow = 4;
            for (var option : secretOptions.entrySet())
                writeRow(guide, guideRow++, List.of(option.getKey() + " = " + option.getValue()));
            workbook.write(output);
        } catch (IOException e) {
            throw new ServiceException("生成失败");
        }
    }

    static void writeArchives(OutputStream output, List<ArchiveRecord> records) {
        try (var workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("档案清单");
            writeRow(sheet, 0, List.of("档号", "题名", "形成日期", "分类", "密级", "保管期限", "状态", "登记人"));
            sheet.createFreezePane(0, 1);
            int rowNumber = 1;
            for (ArchiveRecord record : records) {
                Row row = sheet.createRow(rowNumber++);
                row.createCell(0).setCellValue(record.getArchiveCode());
                row.createCell(1).setCellValue(record.getTitle());
                LocalDate date = record.getFormingDate();
                row.createCell(2).setCellValue(date == null ? "" : date.toString());
                row.createCell(3).setCellValue(record.getCategoryName());
                row.createCell(4).setCellValue(record.getSecretName());
                row.createCell(5).setCellValue(record.getRetentionName());
                row.createCell(6).setCellValue(record.getArchiveStatus());
                row.createCell(7).setCellValue(record.getCreateBy());
            }
            for (int col = 0; col < 8; col++) sheet.setColumnWidth(col, col < 2 ? 9000 : 5200);
            workbook.write(output);
        } catch (IOException e) {
            throw new ServiceException("导出失败");
        }
    }

    private static void writeRow(Sheet sheet, int index, List<String> values) {
        Row row = sheet.createRow(index);
        for (int col = 0; col < values.size(); col++) row.createCell(col).setCellValue(values.get(col));
    }
}
