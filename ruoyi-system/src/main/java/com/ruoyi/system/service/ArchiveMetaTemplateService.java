package com.ruoyi.system.service;

import com.ruoyi.common.exception.ServiceException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongPredicate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArchiveMetaTemplateService {
    private static final int MAX_FIELDS = 100;
    private static final int MAX_LABEL_LENGTH = 120;
    private static final int MAX_TEXT_LENGTH = 1000;
    private static final Set<String> FIELD_TYPES = Set.of("text", "number", "date");

    private record Version(long id, int number, String name) {}

    public record Field(String key, String label, String type, boolean required) {}
    public record Template(long categoryId, int version, String name, List<Field> fields) {}
    public record Change(Integer expectedVersion, String name, List<Field> fields) {}
    public record Preview(List<Field> fields, Map<String, String> values) {}

    private static final RowMapper<Version> VERSION = (row, index) ->
        new Version(row.getLong("template_id"), row.getInt("version_no"), row.getString("template_name"));
    private static final RowMapper<Field> FIELD = (row, index) ->
        new Field(row.getString("field_key"), row.getString("field_name"), row.getString("field_type"),
            row.getBoolean("required"));

    private final JdbcTemplate jdbc;
    private final ArchiveCatalogService catalog;

    public ArchiveMetaTemplateService(JdbcTemplate jdbc, ArchiveCatalogService catalog) {
        this.jdbc = jdbc;
        this.catalog = catalog;
    }

    public Template get(long categoryId, Integer version) {
        if (version != null && version < 1) throw new ServiceException("版本号无效");
        Version selected = version == null ? latestVersion(categoryId) : jdbc.query("""
            select template_id, version_no, template_name from arc_meta_template
            where category_id=? and version_no=?
            """, VERSION, categoryId, version).stream().findFirst().orElse(null);
        if (selected == null) {
            if (version != null) throw new ServiceException("模板版本不存在");
            // 版本 0 表示尚未配置，首次保存时用它校验客户端读到的版本。
            return new Template(categoryId, 0, "", List.of());
        }
        List<Field> fields = jdbc.query("""
            select field_key, field_name, field_type, required from arc_meta_field
            where template_id=? order by sort_no, field_id
            """, FIELD, selected.id());
        return new Template(categoryId, selected.number(), selected.name(), fields);
    }

    @Transactional
    public Template save(long categoryId, Change change, String actor, LongPredicate allowed) {
        if (change.expectedVersion() == null || change.expectedVersion() < 0) {
            throw new ServiceException("请重新读取当前模板");
        }
        String name = clean(change.name());
        if (name.isEmpty() || name.length() > MAX_LABEL_LENGTH) {
            throw new ServiceException("模板名称须为 1 至 120 个字符");
        }
        List<Field> fields = checkFields(change.fields());
        if (!allowed.test(categoryId)) throw new ServiceException("没有此分类的维护权限");

        // 模板尚未创建时也有分类行可锁，用它串行化同一分类的保存。
        jdbc.queryForList("select category_id from arc_category where category_id=? for update", Long.class, categoryId);
        catalog.requireRegistrationCategory(categoryId, allowed);
        Version latest = latestVersion(categoryId);
        int currentVersion = latest == null ? 0 : latest.number();
        if (change.expectedVersion() != currentVersion) {
            throw new ServiceException("模板已被修改，请重新读取后再保存");
        }
        int nextVersion = currentVersion + 1;
        jdbc.update("""
            insert into arc_meta_template(category_id,version_no,template_name,create_by)
            values(?,?,?,?)
            """, categoryId, nextVersion, name, actor);
        long templateId = Objects.requireNonNull(jdbc.queryForObject("select last_insert_id()", Long.class));
        List<Object[]> parameters = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            parameters.add(new Object[]{templateId, field.key(), field.label(), field.type(), field.required(), i + 1});
        }
        if (!parameters.isEmpty()) {
            jdbc.batchUpdate("""
                insert into arc_meta_field(template_id,field_key,field_name,field_type,required,sort_no)
                values(?,?,?,?,?,?)
                """, parameters);
        }
        return new Template(categoryId, nextVersion, name, fields);
    }

    public Map<String, String> validateValues(Preview preview) {
        List<Field> fields = checkFields(preview.fields());
        Map<String, String> values = preview.values() == null ? Map.of() : preview.values();
        Set<String> keys = new HashSet<>();
        for (Field field : fields) keys.add(field.key());
        if (!keys.containsAll(values.keySet())) throw new ServiceException("存在未配置的字段");

        Map<String, String> errors = new LinkedHashMap<>();
        for (Field field : fields) {
            String value = clean(values.get(field.key()));
            if (value.isEmpty()) {
                if (field.required()) errors.put(field.key(), "请填写" + field.label());
                continue;
            }
            switch (field.type()) {
                case "text" -> {
                    if (value.length() > MAX_TEXT_LENGTH) errors.put(field.key(), "最多 1000 个字符");
                }
                case "number" -> {
                    if (!isNumber(value)) errors.put(field.key(), "请输入有效数字");
                }
                case "date" -> {
                    if (!isDate(value)) errors.put(field.key(), "请输入有效日期");
                }
            }
        }
        return errors;
    }

    private Version latestVersion(long categoryId) {
        return jdbc.query("""
            select template_id, version_no, template_name from arc_meta_template where category_id=?
            order by version_no desc limit 1
            """, VERSION, categoryId).stream().findFirst().orElse(null);
    }

    private static List<Field> checkFields(List<Field> fields) {
        if (fields == null || fields.size() > MAX_FIELDS) {
            throw new ServiceException("每个模板最多配置 100 个字段");
        }
        List<Field> normalized = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            String position = "第 " + (i + 1) + " 个字段";
            if (field == null) throw new ServiceException(position + "不能为空");
            String key = clean(field.key());
            String label = clean(field.label());
            String type = clean(field.type());
            if (!key.matches("[A-Za-z][A-Za-z0-9_]{0,31}")) {
                throw new ServiceException(position + "的字段键须以字母开头，最多 32 位字母、数字或下划线");
            }
            if (!keys.add(key.toLowerCase(Locale.ROOT))) throw new ServiceException("字段键重复：" + key);
            if (label.isEmpty() || label.length() > MAX_LABEL_LENGTH) {
                throw new ServiceException(position + "的名称须为 1 至 120 个字符");
            }
            if (!FIELD_TYPES.contains(type)) throw new ServiceException(position + "的类型无效");
            normalized.add(new Field(key, label, type, field.required()));
        }
        return normalized;
    }

    private static boolean isNumber(String value) {
        if (value.length() > 64) return false;
        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isDate(String value) {
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return false;
        try {
            return LocalDate.parse(value).getYear() > 0;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
