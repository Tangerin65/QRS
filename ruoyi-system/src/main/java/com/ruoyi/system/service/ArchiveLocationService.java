package com.ruoyi.system.service;

import com.ruoyi.common.exception.ServiceException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** M10 库位：库房 / 区域 / 柜架三级维护，含被引用保护与 Excel 导入。 */
@Service
public class ArchiveLocationService {
    private static final List<String> LEVELS = List.of("WAREHOUSE", "AREA", "RACK");
    private static final Map<String, String> LEVEL_NAMES = Map.of("WAREHOUSE", "库房", "AREA", "区域", "RACK", "柜架");

    private final JdbcTemplate jdbc;

    public ArchiveLocationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record LocationRow(long locationId, Long parentId, String parentCode, String level, String levelName,
                              String levelPath, String locationCode, String locationName, Integer capacity,
                              int status, int sortOrder, String remark, long usedCount, int childCount) {}

    public record ImportLine(int row, String code, String name, String level, String parentCode, String result, String reason) {}

    private static final RowMapper<LocationRow> ROW = (rs, i) -> new LocationRow(
        rs.getLong("location_id"), rs.getObject("parent_id", Long.class), rs.getString("parent_code"),
        rs.getString("location_level"), LEVEL_NAMES.getOrDefault(rs.getString("location_level"), rs.getString("location_level")),
        rs.getString("level_path"), rs.getString("location_code"), rs.getString("location_name"),
        rs.getObject("capacity", Integer.class), rs.getInt("status"), rs.getInt("sort_no"),
        rs.getString("remark"), rs.getLong("used_count"), rs.getInt("child_count"));

    private static final String SELECT = "select l.location_id,l.parent_id,p.location_code parent_code,l.location_level,"
        + "l.location_code,l.location_name,l.capacity,l.status,l.sort_no,l.remark,"
        + "(select count(*) from arc_archive a where a.location_id=l.location_id and a.del_flag='0') used_count,"
        + "(select count(*) from arc_location c where c.parent_id=l.location_id) child_count,"
        + "concat_ws(' / ', gw.location_code, ga.location_code, p.location_code, l.location_code) level_path "
        + "from arc_location l left join arc_location p on p.location_id=l.parent_id "
        + "left join arc_location ga on ga.location_id=p.parent_id "
        + "left join arc_location gw on gw.location_id=ga.parent_id ";

    public List<LocationRow> tree(String keyword, String level, Integer status) {
        StringBuilder where = new StringBuilder(" where 1=1 ");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" and (l.location_code like ? or l.location_name like ?) ");
            args.add("%" + keyword.trim() + "%");
            args.add("%" + keyword.trim() + "%");
        }
        if (level != null && !level.isBlank()) {
            where.append(" and l.location_level=? ");
            args.add(level.trim());
        }
        if (status != null) {
            where.append(" and l.status=? ");
            args.add(status);
        }
        return jdbc.query(SELECT + where + " order by coalesce(gw.sort_no,0),coalesce(ga.sort_no,0),coalesce(p.sort_no,0),l.sort_no,l.location_id",
            ROW, args.toArray());
    }

    /** 归档时可选库位：只列启用的柜架。 */
    public List<LocationRow> choices() {
        return jdbc.query(SELECT + " where l.status=0 and l.location_level='RACK' order by l.location_code", ROW);
    }

    @Transactional
    public LocationRow save(Long locationId, Long parentId, String level, String code, String name,
                            Integer capacity, int status, int sortOrder, String remark, String actor) {
        String locationLevel = level == null ? "" : level.trim().toUpperCase();
        if (!LEVELS.contains(locationLevel)) throw new ServiceException("库位层级只能是库房/区域/柜架");
        String locationCode = code == null ? "" : code.trim();
        if (locationCode.isEmpty() || locationCode.length() > 32) throw new ServiceException("库位编码必填，最长 32 位");
        String locationName = name == null ? "" : name.trim();
        if (locationName.isEmpty() || locationName.length() > 120) throw new ServiceException("库位名称必填，最长 120 位");
        if (capacity != null && capacity < 0) throw new ServiceException("容量不能为负数");
        if (status != 0 && status != 1) throw new ServiceException("状态只能是启用或停用");
        int index = LEVELS.indexOf(locationLevel);
        if (index == 0) {
            if (parentId != null) throw new ServiceException("库房不能有上级");
        } else {
            if (parentId == null) throw new ServiceException("区域/柜架必须选择上级");
            List<String> parents = jdbc.queryForList("select location_level from arc_location where location_id=?", String.class, parentId);
            if (parents.isEmpty()) throw new ServiceException("上级库位不存在");
            if (!LEVELS.get(index - 1).equals(parents.get(0))) throw new ServiceException("上级库位层级不正确");
        }
        Long duplicate = jdbc.queryForObject("select count(*) from arc_location where location_code=? and location_id<>?",
            Long.class, locationCode, locationId == null ? -1L : locationId);
        if (duplicate != null && duplicate > 0) throw new ServiceException("库位编码已存在");
        if (locationId == null) {
            jdbc.update("insert into arc_location(parent_id,location_level,location_code,location_name,capacity,status,sort_no,remark,create_by) "
                    + "values(?,?,?,?,?,?,?,?,?)",
                parentId, locationLevel, locationCode, locationName, capacity, status, sortOrder, nullSafe(remark), actor);
            Long id = jdbc.queryForObject("select location_id from arc_location where location_code=?", Long.class, locationCode);
            return require(id);
        }
        require(locationId);
        if (parentId != null && parentId == locationId) throw new ServiceException("上级不能是自己");
        jdbc.update("update arc_location set parent_id=?,location_level=?,location_code=?,location_name=?,capacity=?,"
                + "status=?,sort_no=?,remark=?,update_by=?,update_time=now() where location_id=?",
            parentId, locationLevel, locationCode, locationName, capacity, status, sortOrder, nullSafe(remark), actor, locationId);
        return require(locationId);
    }

    @Transactional
    public void remove(long locationId) {
        require(locationId);
        Long children = jdbc.queryForObject("select count(*) from arc_location where parent_id=?", Long.class, locationId);
        if (children != null && children > 0) throw new ServiceException("存在下级库位，不能删除");
        Long used = jdbc.queryForObject("select count(*) from arc_archive where location_id=? and del_flag='0'", Long.class, locationId);
        if (used != null && used > 0) throw new ServiceException("库位已被档案引用，不能删除");
        jdbc.update("delete from arc_location where location_id=?", locationId);
    }

    public LocationRow require(long locationId) {
        List<LocationRow> rows = jdbc.query(SELECT + " where l.location_id=?", ROW, locationId);
        if (rows.isEmpty()) throw new ServiceException("库位不存在");
        return rows.get(0);
    }

    public Map<String, Object> statistics() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String level : LEVELS) {
            Long count = jdbc.queryForObject("select count(*) from arc_location where location_level=?", Long.class, level);
            result.put(level, count == null ? 0 : count);
            Long enabled = jdbc.queryForObject("select count(*) from arc_location where location_level=? and status=0", Long.class, level);
            result.put(level + "Enabled", enabled == null ? 0 : enabled);
        }
        Long used = jdbc.queryForObject("select count(*) from arc_archive where location_id is not null and del_flag='0'", Long.class);
        result.put("linkedArchives", used == null ? 0 : used);
        return result;
    }

    /** Excel 导入：上级编码/库位编码/库位名称/层级/容量/排序/备注，先整表校验再写入。 */
    @Transactional
    public List<ImportLine> importRows(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ServiceException("请选择导入文件");
        List<ImportLine> lines = new ArrayList<>();
        List<Object[]> pending = new ArrayList<>();
        try (InputStream input = file.getInputStream(); XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() < 1) throw new ServiceException("导入文件没有数据行");
            if (sheet.getLastRowNum() - 1 > 500) throw new ServiceException("单次最多导入 500 行");
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null) continue;
                String parentCode = text(row.getCell(0));
                String code = text(row.getCell(1));
                String name = text(row.getCell(2));
                String level = text(row.getCell(3)).toUpperCase();
                String capacityText = text(row.getCell(4));
                String sortText = text(row.getCell(5));
                String remark = text(row.getCell(6));
                int excelRow = index + 1;
                if (code.isEmpty() && name.isEmpty()) continue;
                String error = null;
                if (code.isEmpty()) error = "库位编码为空";
                else if (name.isEmpty()) error = "库位名称为空";
                else if (!LEVELS.contains(level)) error = "层级须为 WAREHOUSE/AREA/RACK";
                else if (LEVELS.indexOf(level) > 0 && parentCode.isEmpty()) error = "缺上级编码";
                else if (LEVELS.indexOf(level) > 0) {
                    List<String> parentLevel = jdbc.queryForList("select location_level from arc_location where location_code=?", String.class, parentCode);
                    if (parentLevel.isEmpty()) {
                        boolean inPending = pending.stream().anyMatch(item -> parentCode.equals(item[3]));
                        if (!inPending) error = "上级库位不存在：" + parentCode;
                    } else if (!LEVELS.get(LEVELS.indexOf(level) - 1).equals(parentLevel.get(0))) error = "上级层级不正确";
                }
                if (error == null) {
                    Long duplicate = jdbc.queryForObject("select count(*) from arc_location where location_code=?", Long.class, code);
                    if (duplicate != null && duplicate > 0) error = "库位编码已存在";
                }
                if (error == null && pending.stream().anyMatch(item -> code.equals(item[3]))) error = "同批编码重复";
                if (error != null) {
                    lines.add(new ImportLine(excelRow, code, name, level, parentCode, "FAIL", error));
                    continue;
                }
                Integer capacity = parseInteger(capacityText);
                Integer sortOrder = parseInteger(sortText);
                pending.add(new Object[] {LEVELS.indexOf(level), parentCode, name, code, level, capacity, sortOrder == null ? 0 : sortOrder, remark});
                lines.add(new ImportLine(excelRow, code, name, level, parentCode, "OK", ""));
            }
        } catch (IOException e) {
            throw new ServiceException("读取导入文件失败");
        }
        boolean hasError = lines.stream().anyMatch(line -> "FAIL".equals(line.result()));
        if (hasError) throw new ServiceException("导入校验未通过：" + lines.stream().filter(l -> "FAIL".equals(l.result())).count() + " 行需修正");
        for (Object[] item : pending) {
            Long parentId = null;
            if (!"".equals(item[1])) {
                parentId = jdbc.queryForObject("select location_id from arc_location where location_code=?", Long.class, (String) item[1]);
            }
            jdbc.update("insert into arc_location(parent_id,location_level,location_code,location_name,capacity,status,sort_no,remark,create_by) "
                    + "values(?,?,?,?,?,0,?,?,'import')",
                parentId, (String) item[4], (String) item[3], (String) item[2], item[5], item[6], nullSafe((String) item[7]));
        }
        return lines;
    }

    private static String text(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (value == Math.floor(value)) return String.valueOf((long) value);
            return String.valueOf(value);
        }
        if (cell.getCellType() == CellType.BOOLEAN) return String.valueOf(cell.getBooleanCellValue());
        return cell.getStringCellValue() == null ? "" : cell.getStringCellValue().trim();
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return (int) Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new ServiceException("数字格式不正确：" + value);
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
