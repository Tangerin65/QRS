package com.ruoyi.web.controller.archive;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.service.ArchiveCatalogService.Catalog;
import com.ruoyi.system.service.ArchiveCatalogService.ImportRow;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

final class ArchiveCatalogWorkbook {
    private static final List<String> HEADERS = List.of("编码", "名称", "类型", "上级编码", "状态", "显示顺序", "备注");
    private static final Map<String, String> KINDS = Map.of("fond", "全宗", "category", "分类", "collection", "档案集", "folder", "逻辑文件夹");
    private static final Map<String, String> STATUSES = Map.of("active", "启用", "disabled", "停用");

    private ArchiveCatalogWorkbook() {}

    static List<ImportRow> read(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 5_000_000)
            throw new ServiceException("请选择 5 MB 以内的 .xlsx 文件");
        try (var input = file.getInputStream(); var workbook = new XSSFWorkbook(input)) {
            if (workbook.getNumberOfSheets() == 0) throw new ServiceException("找不到目录工作表");
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() > 1000) throw new ServiceException("目录工作表最多填写 1000 条记录");
            var formatter = new DataFormatter(Locale.ROOT);
            if (!Arrays.asList(cells(sheet.getRow(0), formatter, 1)).equals(HEADERS))
                throw new ServiceException("表头与目录模板不一致，请重新下载模板");
            List<ImportRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                int rowNumber = i + 1;
                String[] row = cells(sheet.getRow(i), formatter, rowNumber);
                if (Arrays.stream(row).allMatch(String::isEmpty)) continue;
                int sortOrder;
                try {
                    sortOrder = row[5].isEmpty() ? 0 : Integer.parseInt(row[5]);
                    if (sortOrder < 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    throw new ServiceException("第" + rowNumber + "行显示顺序须为非负整数");
                }
                rows.add(new ImportRow(rowNumber, row[0], row[1], code(KINDS, row[2]), row[3],
                    row[4].isEmpty() ? "active" : code(STATUSES, row[4]), sortOrder, row[6]));
            }
            return rows;
        } catch (ServiceException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new ServiceException("无法读取目录工作簿，请使用下载的 .xlsx 模板");
        }
    }

    private static String[] cells(Row row, DataFormatter formatter, int rowNumber) {
        String[] values = new String[HEADERS.size()];
        for (int col = 0; col < values.length; col++) {
            Cell cell = row == null ? null : row.getCell(col);
            if (cell != null && cell.getCellType() == CellType.FORMULA)
                throw new ServiceException("第" + rowNumber + "行含公式，请粘贴为值后导入");
            values[col] = formatter.formatCellValue(cell).strip();
        }
        return values;
    }

    private static String code(Map<String, String> labels, String value) {
        for (var entry : labels.entrySet()) if (entry.getValue().equals(value)) return entry.getKey();
        return value;
    }

    static void write(OutputStream output, List<Catalog> entries, List<Catalog> all, boolean template) throws IOException {
        Map<Long, String> codes = new LinkedHashMap<>();
        for (Catalog row : all) codes.put(row.id(), row.code());
        try (var workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("目录");
            writeRow(sheet, 0, HEADERS);
            var style = workbook.createCellStyle();
            var font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            for (int c = 0; c < HEADERS.size(); c++) {
                sheet.getRow(0).getCell(c).setCellStyle(style);
                sheet.setColumnWidth(c, c == 6 ? 12000 : 5600);
            }
            sheet.createFreezePane(0, 1);
            int index = 1;
            for (Catalog row : entries) {
                String parentCode = row.parentId() == null ? "" : codes.getOrDefault(row.parentId(), "");
                writeRow(sheet, index++, List.of(row.code(), row.name(), KINDS.get(row.kind()), parentCode,
                    STATUSES.get(row.status()), String.valueOf(row.sortOrder()), row.remark()));
            }
            if (template) {
                Sheet guide = workbook.createSheet("填写说明");
                guide.setColumnWidth(0, 23400);
                writeRow(guide, 0, List.of("请在「目录」工作表中填写数据；此页不参与导入。"));
                writeRow(guide, 1, List.of("类型填全宗、分类、档案集或逻辑文件夹；全宗的上级编码留空。"));
                writeRow(guide, 2, List.of("下级填已有上级编码；同批新增时，上级记录须排在下级前面。"));
                writeRow(guide, 3, List.of("状态可填启用、停用，留空视为启用。显示顺序留空视为 0。"));
                writeRow(guide, 4, List.of("每次最多导入 1000 条，出现重复或无效记录时整批不写入。"));
            }
            workbook.write(output);
        }
    }

    private static void writeRow(Sheet sheet, int rowIndex, List<String> values) {
        Row row = sheet.createRow(rowIndex);
        for (int c = 0; c < values.size(); c++) row.createCell(c).setCellValue(values.get(c));
    }
}
