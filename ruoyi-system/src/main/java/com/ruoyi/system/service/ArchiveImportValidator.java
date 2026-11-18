package com.ruoyi.system.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.ArchiveRecord;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;

public final class ArchiveImportValidator {
    public record ImportLine(int row, ArchiveRecord record, String error) {}

    public static final List<String> HEADERS = List.of("题名", "形成日期", "分类编码", "密级编码", "保管期限编码", "摘要");

    private ArchiveImportValidator() {}

    public static List<ImportLine> read(MultipartFile file, Set<Integer> enabledSecretLevels) {
        //校验模板与启用密级，逐行返回导入问题
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) throw new ServiceException("导入文件没有工作表");
            var sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) throw new ServiceException("导入模板表头不正确");
            DataFormatter formatter = new DataFormatter();
            for (int index = 0; index < HEADERS.size(); index++) {
                String actual = value(header.getCell(index), formatter);
                //旧模板的密级列标题继续兼容，值仍按当前字典校验
                if (!HEADERS.get(index).equals(actual) && !(index == 3 && "密级(0-3)".equals(actual)))
                    throw new ServiceException("导入模板表头不正确，请下载最新模板");
            }
            List<ImportLine> rows = new ArrayList<>();
            if (sheet.getLastRowNum() > 1000) throw new ServiceException("每次最多导入1000条档案");
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null || empty(row, formatter)) continue;
                ArchiveRecord record = new ArchiveRecord();
                String title = value(row.getCell(0), formatter).strip();
                Cell dateCell = row.getCell(1);
                record.setCategoryCode(value(row.getCell(2), formatter).strip());
                String secret = value(row.getCell(3), formatter).strip();
                record.setRetentionCode(value(row.getCell(4), formatter).strip());
                Integer secretLevel = ArchiveSecretLevels.parseCode(secret);
                record.setSummary(value(row.getCell(5), formatter).strip());
                String error = "";
                if (title.isEmpty() || title.length() > 200) error = "题名必填且最多200个字符";
                else if (record.getCategoryCode().isEmpty()) error = "分类编码必填";
                else if (secretLevel == null || !enabledSecretLevels.contains(secretLevel)) error = "密级编码须对应已启用的密级字典项";
                else if (record.getSummary().length() > 1000) error = "摘要最多1000个字符";
                else {
                    try { record.setFormingDate(parseDate(dateCell, formatter)); }
                    catch (DateTimeParseException e) { error = "形成日期须为yyyy-MM-dd、yyyy/MM/dd或yyyy\\MM\\dd"; }
                }
                if (error.isEmpty()) {
                    record.setTitle(title);
                    record.setSecretLevel(secretLevel);
                }
                rows.add(new ImportLine(index + 1, record, error));
            }
            if (rows.isEmpty()) throw new ServiceException("文件中没有可导入的数据行");
            return rows;
        } catch (IOException e) {
            throw new ServiceException("无法读取Excel文件");
        }
    }

    private static LocalDate parseDate(Cell cell, DataFormatter formatter) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell))
            return DateUtil.getLocalDateTime(cell.getNumericCellValue()).toLocalDate();
        return LocalDate.parse(value(cell, formatter).strip().replace('/', '-').replace('\\', '-'));
    }

    private static boolean empty(Row row, DataFormatter formatter) {
        for (int index = 0; index < HEADERS.size(); index++) {
            if (!value(row.getCell(index), formatter).isBlank()) return false;
        }
        return true;
    }

    private static String value(Cell cell, DataFormatter formatter) {
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }
}
