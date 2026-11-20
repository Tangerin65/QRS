package com.ruoyi.web.controller.archive;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.ArchiveRecord;
import com.ruoyi.system.service.ArchiveArchivingService.BatchItemRow;
import com.ruoyi.system.service.ArchiveArchivingService.BatchRow;
import com.ruoyi.system.service.ArchiveLocationService.LocationRow;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** M10 导出与模板。 */
final class ArchiveM10Workbook {
    private ArchiveM10Workbook() {}

    private static void row(Sheet sheet, int index, List<String> values) {
        Row row = sheet.createRow(index);
        for (int i = 0; i < values.size(); i++) row.createCell(i).setCellValue(values.get(i));
    }

    private static void widths(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) sheet.setColumnWidth(i, 6000);
    }

    static void writePending(OutputStream output, List<ArchiveRecord> rows) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("待归档档案");
            row(sheet, 0, List.of("档号", "题名", "形成日期", "分类", "密级", "保管期限", "状态", "登记人"));
            widths(sheet, 8);
            int index = 1;
            for (ArchiveRecord record : rows) {
                row(sheet, index++, List.of(
                    text(record.getArchiveCode()), text(record.getTitle()), text(record.getFormingDate()),
                    text(record.getCategoryName()), text(record.getSecretName()), text(record.getRetentionName()),
                    text(record.getArchiveStatus()), text(record.getCreateBy())));
            }
            sheet.createFreezePane(0, 1);
            workbook.write(output);
        } catch (IOException e) {
            throw new ServiceException("导出失败");
        }
    }

    static void writeBatchItems(OutputStream output, BatchRow batch, List<BatchItemRow> items) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet head = workbook.createSheet("批次");
            row(head, 0, List.of("批次号", "申请人", "总数", "成功", "失败", "状态", "提交时间", "完成时间"));
            widths(head, 8);
            row(head, 1, List.of(text(batch.batchNo()), text(batch.applicantName()), String.valueOf(batch.totalCount()),
                String.valueOf(batch.successCount()), String.valueOf(batch.failCount()), text(batch.batchStatus()),
                text(batch.submitTime()), text(batch.finishTime())));
            Sheet sheet = workbook.createSheet("明细");
            row(sheet, 0, List.of("档号", "题名", "结果", "原因编码", "原因"));
            widths(sheet, 5);
            int index = 1;
            for (BatchItemRow item : items) {
                row(sheet, index++, List.of(text(item.archiveCode()), text(item.title()), text(item.itemResult()),
                    text(item.reasonCode()), text(item.reason())));
            }
            workbook.write(output);
        } catch (IOException e) {
            throw new ServiceException("导出失败");
        }
    }

    static void writeLocations(OutputStream output, List<LocationRow> rows) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("库位");
            row(sheet, 0, List.of("库位编码", "库位名称", "层级", "完整路径", "容量", "已挂档案", "状态", "排序", "备注"));
            widths(sheet, 9);
            int index = 1;
            for (LocationRow item : rows) {
                row(sheet, index++, List.of(text(item.locationCode()), text(item.locationName()), text(item.levelName()),
                    text(item.levelPath()), item.capacity() == null ? "" : String.valueOf(item.capacity()),
                    String.valueOf(item.usedCount()), item.status() == 0 ? "启用" : "停用",
                    String.valueOf(item.sortOrder()), text(item.remark())));
            }
            sheet.createFreezePane(0, 1);
            workbook.write(output);
        } catch (IOException e) {
            throw new ServiceException("导出失败");
        }
    }

    static void writeLocationTemplate(OutputStream output) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("库位");
            row(sheet, 0, List.of("上级编码", "库位编码", "库位名称", "层级", "容量", "排序", "备注"));
            widths(sheet, 7);
            Sheet guide = workbook.createSheet("填写说明");
            row(guide, 0, List.of("层级填写 WAREHOUSE（库房）/ AREA（区域）/ RACK（柜架）"));
            row(guide, 1, List.of("库房上级留空；区域上级填库房编码；柜架上级填区域编码"));
            row(guide, 2, List.of("同批次中先填上级再填下级；库位编码全局唯一"));
            workbook.write(output);
        } catch (IOException e) {
            throw new ServiceException("生成失败");
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
