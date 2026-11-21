package com.ruoyi.web.controller.archive;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.exception.ServiceException;

/**
 * M12-A 账号清单的 Excel 导入/导出工作簿。
 *
 * 与 M03 的分类工作簿保持同一套写法：表头严格校验、公式单元格拒绝、逐行校验后
 * 由调用方汇总「成功数 / 失败数 / 每行失败原因」，不做整批静默回滚。
 *
 * 列为：用户编号、登录账号、用户名称、部门名称、角色名称、手机号码、邮箱、是否档案员、状态。
 * 只用于账号「新增/修改基础资料」的批量场景，不承载口令与密级判定。
 *
 * @author member6
 */
final class ArchiveAccountWorkbook
{
    /** 导入模板与导出共用的表头 */
    private static final List<String> HEADERS = List.of(
            "用户编号", "登录账号", "用户名称", "部门名称", "角色名称", "手机号码", "邮箱", "是否档案员", "状态");

    /** 单次导入上限，避免一次提交过大导致事务过长 */
    private static final int MAX_ROWS = 500;

    /** 文件大小上限 5 MB，与 M03 保持一致 */
    private static final long MAX_SIZE = 5_000_000L;

    private static final Map<String, String> ARCHIVIST_LABELS = Map.of("1", "是", "0", "否");

    private static final Map<String, String> STATUS_LABELS = Map.of("0", "正常", "1", "停用");

    private ArchiveAccountWorkbook()
    {
    }

    /**
     * 一行导入数据。字段保持字符串原样，编号不转数字以保留前导零。
     */
    record ImportRow(int rowNo, String userNo, String loginName, String userName, String deptName,
                     String roleName, String phonenumber, String email, String archivist, String status)
    {
    }

    /**
     * 读取并做「格式层」校验；业务层校验（唯一性、部门是否存在等）由 Service 完成。
     *
     * @param file 上传的工作簿
     * @return 数据行
     */
    static List<ImportRow> read(MultipartFile file)
    {
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("请选择要导入的账号工作簿");
        }
        if (file.getSize() > MAX_SIZE)
        {
            throw new ServiceException("请选择 5 MB 以内的 .xlsx 文件");
        }
        try (var input = file.getInputStream(); var workbook = new XSSFWorkbook(input))
        {
            if (workbook.getNumberOfSheets() == 0)
            {
                throw new ServiceException("找不到账号工作表");
            }
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() > MAX_ROWS)
            {
                throw new ServiceException("账号工作表最多填写 " + MAX_ROWS + " 条记录");
            }
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            if (!Arrays.asList(cells(sheet.getRow(0), formatter, 1)).equals(HEADERS))
            {
                throw new ServiceException("表头与账号模板不一致，请重新下载模板");
            }
            List<ImportRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++)
            {
                int rowNumber = i + 1;
                String[] row = cells(sheet.getRow(i), formatter, rowNumber);
                if (Arrays.stream(row).allMatch(String::isEmpty))
                {
                    continue;
                }
                rows.add(new ImportRow(rowNumber, row[0], row[1], row[2], row[3], row[4], row[5], row[6],
                        archivistCode(row[7]), statusCode(row[8])));
            }
            if (rows.isEmpty())
            {
                throw new ServiceException("工作表中没有可导入的账号数据");
            }
            return rows;
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (IOException | RuntimeException e)
        {
            throw new ServiceException("无法读取账号工作簿，请使用下载的 .xlsx 模板");
        }
    }

    private static String[] cells(Row row, DataFormatter formatter, int rowNumber)
    {
        String[] values = new String[HEADERS.size()];
        for (int col = 0; col < values.length; col++)
        {
            Cell cell = row == null ? null : row.getCell(col);
            if (cell != null && cell.getCellType() == CellType.FORMULA)
            {
                throw new ServiceException("第" + rowNumber + "行含公式，请粘贴为值后导入");
            }
            values[col] = formatter.formatCellValue(cell).strip();
        }
        return values;
    }

    /** 是否档案员：接受「是/否」与 1/0，留空视为否 */
    private static String archivistCode(String value)
    {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty() || "否".equals(text) || "0".equals(text))
        {
            return "0";
        }
        if ("是".equals(text) || "1".equals(text))
        {
            return "1";
        }
        throw new ServiceException("「是否档案员」只能填 是 或 否");
    }

    /** 状态：接受「正常/停用」与 0/1，留空视为正常 */
    private static String statusCode(String value)
    {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty() || "正常".equals(text) || "0".equals(text))
        {
            return "0";
        }
        if ("停用".equals(text) || "1".equals(text))
        {
            return "1";
        }
        throw new ServiceException("「状态」只能填 正常 或 停用");
    }

    /**
     * 写出导出清单或导入模板。
     *
     * @param output    响应输出流
     * @param rows      导出的数据行（模板时传空列表）
     * @param template  true 表示生成导入模板（额外带一页填写说明）
     * @param deptNames 现有部门名称，用于模板下拉；可为空
     * @param roleNames 现有角色名称，用于模板下拉；可为空
     */
    static void write(OutputStream output, List<Map<String, Object>> rows, boolean template,
                      List<String> deptNames, List<String> roleNames) throws IOException
    {
        try (XSSFWorkbook workbook = new XSSFWorkbook())
        {
            Sheet sheet = workbook.createSheet("账号清单");
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            Row head = sheet.createRow(0);
            for (int c = 0; c < HEADERS.size(); c++)
            {
                Cell cell = head.createCell(c);
                cell.setCellValue(HEADERS.get(c));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(c, 4800);
            }
            sheet.createFreezePane(0, 1);

            int index = 1;
            for (Map<String, Object> row : rows)
            {
                Row line = sheet.createRow(index++);
                String status = String.valueOf(row.getOrDefault("status", "0"));
                String archivist = String.valueOf(row.getOrDefault("arcArchivist", "0"));
                List<String> values = List.of(
                        text(row.get("userNo")),
                        text(row.get("loginName")),
                        text(row.get("userName")),
                        text(row.get("deptName")),
                        text(row.get("roleNames")),
                        text(row.get("phonenumber")),
                        text(row.get("email")),
                        ARCHIVIST_LABELS.getOrDefault(archivist, "否"),
                        STATUS_LABELS.getOrDefault(status, "正常"));
                for (int c = 0; c < values.size(); c++)
                {
                    // 一律按文本写入，用户编号 0003 这类前导零在 Excel 里也不会被吃掉
                    line.createCell(c, CellType.STRING).setCellValue(values.get(c));
                }
            }
            if (template)
            {
                Sheet guide = workbook.createSheet("填写说明");
                guide.setColumnWidth(0, 24000);
                List<String> notes = List.of(
                        "请在「账号清单」工作表中填写数据；此页不参与导入。",
                        "用户编号只允许字母、数字、下划线、连字符，长度 1-64 位，须与账号管理中一致且唯一。",
                        "登录账号必填；已存在的登录账号按「修改基础资料」处理，不存在的按「新增账号」处理。",
                        "部门名称必须与系统管理 → 部门管理中已有部门完全一致；留空表示不调整部门。",
                        "角色名称必须与系统管理 → 角色管理中已有角色完全一致；多个角色用顿号、逗号或分号分隔。",
                        "是否档案员填 是 或 否，留空视为否；状态填 正常 或 停用，留空视为正常。",
                        "导入不修改口令、不修改密级上限；口令与密级由管理员在页面上单独处理。",
                        "每次最多导入 " + MAX_ROWS + " 条，导入完成后会返回成功数与逐行失败原因。",
                        "",
                        "常见错误：",
                        "  · 把数字填进了「角色名称」列 —— 角色要填角色名（如 普通角色），不是编号。",
                        "  · 部门名称写错或用简称 —— 必须与部门管理里的名称完全一致。",
                        "  · 直接粘贴到错误的列 —— 请核对每列标题后再粘贴数据。");
                for (int i = 0; i < notes.size(); i++)
                {
                    guide.createRow(i).createCell(0).setCellValue(notes.get(i));
                }
                // 给部门、角色、档案员、状态四列加下拉与批注，从源头减少「填错列/填错值」
                applyColumnHints(workbook, sheet, deptNames, roleNames);
                // 另开一页列出当前系统可选值，用户可以直接照着复制
                writeOptionsSheet(workbook, deptNames, roleNames);
            }
            workbook.write(output);
        }
    }

    /**
     * 生成「可选值」参考页。
     *
     * 把当前系统里真实存在的部门路径与角色名逐行列出，用户可以照着填或直接复制，
     * 比只在「填写说明」里写一句话更不容易填错。
     *
     * @param workbook  工作簿
     * @param deptNames 部门完整路径
     * @param roleNames 角色名称
     */
    private static void writeOptionsSheet(org.apache.poi.ss.usermodel.Workbook workbook,
                                          List<String> deptNames, List<String> roleNames)
    {
        Sheet options = workbook.createSheet("可选值");
        options.setColumnWidth(0, 9000);
        options.setColumnWidth(1, 9000);

        CellStyle bold = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        bold.setFont(font);

        Cell titleLeft = options.createRow(0).createCell(0);
        titleLeft.setCellValue("部门名称（可填完整路径或末尾名称）");
        titleLeft.setCellStyle(bold);
        Cell titleRight = options.getRow(0).createCell(1);
        titleRight.setCellValue("角色名称");
        titleRight.setCellStyle(bold);

        List<String> depts = deptNames == null ? List.of() : deptNames;
        List<String> roles = roleNames == null ? List.of() : roleNames;
        int rows = Math.max(depts.size(), roles.size());
        for (int i = 0; i < rows; i++)
        {
            Row row = options.createRow(i + 1);
            if (i < depts.size())
            {
                row.createCell(0).setCellValue(depts.get(i));
            }
            if (i < roles.size())
            {
                row.createCell(1).setCellValue(roles.get(i));
            }
        }
        if (depts.isEmpty() && roles.isEmpty())
        {
            options.createRow(1).createCell(0)
                    .setCellValue("系统里还没有部门或角色，请先在「系统管理」中创建后再导入账号。");
        }
    }

    /**
     * 在模板里给易错列加下拉与表头批注。
     *
     * 起因：有用户把「0」填进了角色名称列，导入时才报「找不到角色「0」」，
     * 而模板本身看不出该填什么。这里直接用系统里现有的部门/角色名称做下拉，
     * 从源头减少「填错列 / 填错值」。
     *
     * @param workbook  工作簿
     * @param sheet     账号清单表
     * @param deptNames 现有部门名称
     * @param roleNames 现有角色名称
     */
    private static void applyColumnHints(org.apache.poi.ss.usermodel.Workbook workbook, Sheet sheet,
                                         List<String> deptNames, List<String> roleNames)
    {
        var helper = workbook.getCreationHelper();
        // XSSF 新建的工作表还没有 drawing 容器，getDrawingPatriarch() 会返回 null，
        // 必须先 createDrawingPatriarch() 才能挂批注，否则批注会被静默丢弃。
        var drawing = sheet.getDrawingPatriarch() != null
                ? sheet.getDrawingPatriarch() : sheet.createDrawingPatriarch();
        String[] hints = {
            "必填，字母/数字/下划线/连字符，1-64 位；绑定二维码内容就是它",
            "必填；已存在该登录账号则更新基础资料",
            "建议填真实姓名",
            "填部门名称，须与「部门管理」中完全一致",
            "填角色名称（如 普通角色），不是角色编号；多个用、分隔",
            "可选，11 位手机号",
            "可选，邮箱",
            "填 是 / 否，留空视为否",
            "填 正常 / 停用，留空视为正常"
        };
        for (int c = 0; c < hints.length; c++)
        {
            Cell head = sheet.getRow(0).getCell(c);
            if (head == null)
            {
                continue;
            }
            var anchor = helper.createClientAnchor();
            anchor.setCol1(c);
            anchor.setCol2(c + 2);
            anchor.setRow1(0);
            anchor.setRow2(3);
            var note = drawing.createCellComment(anchor);
            note.setAuthor("档案管理系统");
            note.setString(helper.createRichTextString(hints[c]));
            head.setCellComment(note);
        }

        // 部门、角色、档案员、状态四列加下拉，限制只能选现有值
        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        addListValidation(sheet, dvHelper, 3, deptNames, MAX_ROWS);
        addListValidation(sheet, dvHelper, 4, roleNames, MAX_ROWS);
        addListValidation(sheet, dvHelper, 7, List.of("是", "否"), MAX_ROWS);
        addListValidation(sheet, dvHelper, 8, List.of("正常", "停用"), MAX_ROWS);
    }

    /**
     * 给某一列加下拉校验。
     *
     * Excel 的内联列表有 255 字符上限；部门或角色很多时会超，此时跳过下拉
     * （批注与填写说明里仍写清了规则），避免生成打不开的模板。
     */
    private static void addListValidation(Sheet sheet, DataValidationHelper helper, int column,
                                          List<String> values, int lastRow)
    {
        if (values == null || values.isEmpty())
        {
            return;
        }
        String[] items = values.toArray(new String[0]);
        if (String.join(",", items).length() > 250)
        {
            return;
        }
        DataValidationConstraint constraint = helper.createExplicitListConstraint(items);
        CellRangeAddressList range = new CellRangeAddressList(1, Math.max(1, lastRow), column, column);
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setShowErrorBox(true);
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
        validation.createErrorBox("取值不合法", "请从下拉列表中选择，不要手工输入数字或简称");
        validation.setSuppressDropDownArrow(true);
        sheet.addValidationData(validation);
    }

    private static String text(Object value)
    {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 把 Service 返回的账号视图映射为导出用的行结构，保证导出与页面使用同一批查询结果。
     *
     * @param users 账号清单（SysUser）
     * @return 导出行
     */
    static List<Map<String, Object>> toExportRows(List<com.ruoyi.common.core.domain.entity.SysUser> users)
    {
        List<Map<String, Object>> rows = new ArrayList<>(users.size());
        for (com.ruoyi.common.core.domain.entity.SysUser user : users)
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userNo", user.getUserNo());
            row.put("loginName", user.getLoginName());
            row.put("userName", user.getUserName());
            row.put("deptName", user.getDept() == null ? "" : user.getDept().getDeptName());
            row.put("roleNames", roleNames(user.getRoles()));
            row.put("phonenumber", user.getPhonenumber());
            row.put("email", user.getEmail());
            row.put("arcArchivist", user.getArcArchivist());
            row.put("status", user.getStatus());
            rows.add(row);
        }
        return rows;
    }

    private static String roleNames(List<com.ruoyi.common.core.domain.entity.SysRole> roles)
    {
        if (roles == null || roles.isEmpty())
        {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (com.ruoyi.common.core.domain.entity.SysRole role : roles)
        {
            if (role != null && role.getRoleName() != null && !role.getRoleName().isBlank()
                    && !names.contains(role.getRoleName()))
            {
                names.add(role.getRoleName());
            }
        }
        return String.join("、", names);
    }
}
