package com.ruoyi.system.service;

import com.ruoyi.common.exception.ServiceException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongPredicate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** M03 分类基础。后台管理授权由调用者检查，建档查询要求调用者传入分类范围。 */
@Service
public class ArchiveCatalogService {
    private static final Set<String> KINDS = Set.of("fond", "category", "collection", "folder");
    private static final Set<String> STATUSES = Set.of("active", "disabled");
    private static final String SELECT = """
        select id,code,name,kind,parent_id,status,sort_order,remark,revision from ams_catalog
        """;
    private static final RowMapper<Catalog> ROW = (rs, index) -> new Catalog(
        rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("kind"),
        rs.getObject("parent_id", Long.class), rs.getString("status"), rs.getInt("sort_order"),
        rs.getString("remark"), rs.getInt("revision"));

    public record Catalog(long id, String code, String name, String kind, Long parentId,
                          String status, int sortOrder, String remark, int revision) {}
    public record Change(Long id, String code, String name, String kind, Long parentId,
                         String status, int sortOrder, String remark, Integer revision) {}
    public record Choice(long id, String code, String name, String path) {}
    public record ImportRow(int row, String code, String name, String kind, String parentCode,
                            String status, int sortOrder, String remark) {}

    private final JdbcTemplate jdbc;

    public ArchiveCatalogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Catalog get(long id) {
        return find(id, false);
    }

    private Catalog find(long id, boolean forUpdate) {
        List<Catalog> rows = jdbc.query(SELECT + " where id=?" + (forUpdate ? " for update" : ""), ROW, id);
        if (rows.isEmpty()) throw new ServiceException("目录不存在，请刷新列表");
        return rows.get(0);
    }

    public List<Catalog> list(String keyword, String kind, String status, String sort) {
        String term = clean(keyword);
        String type = clean(kind);
        String state = clean(status);
        if (!type.isEmpty() && !KINDS.contains(type)) throw new ServiceException("目录类型无效");
        if (!state.isEmpty() && !STATUSES.contains(state)) throw new ServiceException("目录状态无效");
        String order = switch (clean(sort)) {
            case "name" -> "name,id";
            case "code" -> "code,id";
            case "newest" -> "create_time desc,id desc";
            default -> "sort_order,code,id";
        };
        return jdbc.query(SELECT + """
            where (?='' or instr(lower(name),lower(?))>0 or instr(lower(code),lower(?))>0)
              and (?='' or kind=?) and (?='' or status=?)
            order by
            """ + " " + order, ROW, term, term, term, type, type, state, state);
    }

    @Transactional
    public Catalog save(Change input, String actor) {
        String code = clean(input.code());
        String name = clean(input.name());
        String kind = clean(input.kind());
        String status = clean(input.status());
        String remark = clean(input.remark());
        if (!code.matches("[A-Za-z0-9_.-]{1,64}")) throw new ServiceException("编码须为1–64位字母、数字、点、横线或下划线");
        if (name.isEmpty() || name.length() > 120) throw new ServiceException("名称须为1–120个字符");
        if (!KINDS.contains(kind)) throw new ServiceException("请选择全宗、分类、档案集或逻辑文件夹");
        if (!STATUSES.contains(status)) throw new ServiceException("请选择启用或停用");
        if (input.sortOrder() < 0 || remark.length() > 1000) throw new ServiceException("排序须为非负数，备注最多1000个字符");

        jdbc.queryForObject("select id from ams_edit_lock where id=1 for update", Integer.class);
        Long id = input.id();
        if (id != null && !find(id, true).kind().equals(kind)) throw new ServiceException("已有目录的类型不可变更");
        Long parentId = Objects.equals(input.parentId(), 0L) ? null : input.parentId();
        validateHierarchy(id, parentId, kind, status);
        try {
            if (id == null) {
                jdbc.update("""
                    insert into ams_catalog(code,name,kind,parent_id,status,sort_order,remark,create_by)
                    values(?,?,?,?,?,?,?,?)
                    """, code, name, kind, parentId, status, input.sortOrder(), remark, actor);
                id = jdbc.queryForObject("select last_insert_id()", Long.class);
            } else {
                if (input.revision() == null) throw new ServiceException("缺少版本号，请重新加载目录");
                int changed = jdbc.update("""
                    update ams_catalog set code=?,name=?,parent_id=?,status=?,sort_order=?,remark=?,revision=revision+1
                    where id=? and revision=?
                    """, code, name, parentId, status, input.sortOrder(), remark, id, input.revision());
                if (changed != 1) throw new ServiceException("目录已被修改，请重新加载目录");
            }
        } catch (DuplicateKeyException e) {
            throw new ServiceException("目录编码已存在");
        }
        return find(id, true);
    }

    private void validateHierarchy(Long id, Long parentId, String kind, String status) {
        boolean root = "fond".equals(kind);
        if (root && parentId != null) throw new ServiceException("全宗应位于根目录");
        if (!root && parentId == null) throw new ServiceException("请选择上级全宗或目录");
        Set<Long> seen = new HashSet<>();
        if (id != null) seen.add(id);
        Long current = parentId;
        while (current != null) {
            if (!seen.add(current)) throw new ServiceException("上级目录不能是自身或自己的下级");
            Catalog ancestor = find(current, true);
            if (current.equals(parentId) && "category".equals(kind)
                && !Set.of("fond", "category").contains(ancestor.kind())) {
                throw new ServiceException("分类的上级必须是全宗或其他分类");
            }
            if ("active".equals(status) && !"active".equals(ancestor.status()))
                throw new ServiceException("启用目录的上级必须处于启用状态");
            current = ancestor.parentId();
        }
        if (id != null && "disabled".equals(status) && !jdbc.queryForList(
            "select id from ams_catalog where parent_id=? and status='active' limit 1 for update", Long.class, id).isEmpty())
            throw new ServiceException("请先停用该目录下的启用目录");
    }

    @Transactional
    public int importRows(List<ImportRow> rows, String actor) {
        if (rows.isEmpty() || rows.size() > 1000) throw new ServiceException("每次导入1至1000条目录");
        jdbc.queryForObject("select id from ams_edit_lock where id=1 for update", Integer.class);
        for (ImportRow row : rows) {
            try {
                Long parentId = clean(row.parentCode()).isEmpty() ? null : jdbc.query(
                    SELECT + " where code=? for update", ROW, clean(row.parentCode())).stream()
                    .findFirst().orElseThrow(() -> new ServiceException("找不到上级编码，请将上级记录放在下级记录之前")).id();
                save(new Change(null, row.code(), row.name(), row.kind(), parentId, row.status(),
                    row.sortOrder(), row.remark(), null), actor);
            } catch (ServiceException e) {
                throw new ServiceException("第" + row.row() + "行：" + e.getMessage() + "。整批未导入。");
            }
        }
        return rows.size();
    }

    @Transactional(readOnly = true)
    public Catalog requireRegistrationCategory(long id, LongPredicate allowed) {
        if (!allowed.test(id)) throw new ServiceException("没有此分类的建档权限");
        Catalog category = get(id);
        if (!"category".equals(category.kind()) || !"active".equals(category.status()) || countActiveChildren(id, "category") > 0)
            throw new ServiceException("请选择启用的末级档案分类");
        Set<Long> seen = new HashSet<>(Set.of(id));
        for (Long parent = category.parentId(); parent != null;) {
            if (!seen.add(parent)) throw new ServiceException("分类路径异常，请联系管理员");
            Catalog ancestor = get(parent);
            if (!"active".equals(ancestor.status())) throw new ServiceException("上级目录已停用");
            parent = ancestor.parentId();
        }
        return category;
    }

    public List<Choice> registrationChoices(String keyword, LongPredicate allowed) {
        List<Catalog> nodes = list(null, null, "active", null);
        Map<Long, Catalog> byId = new HashMap<>();
        Set<Long> categoryParents = new HashSet<>();
        for (Catalog node : nodes) {
            byId.put(node.id(), node);
            if ("category".equals(node.kind())) categoryParents.add(node.parentId());
        }
        String term = clean(keyword).toLowerCase(Locale.ROOT);
        List<Choice> choices = new ArrayList<>();
        for (Catalog node : nodes) {
            if (!"category".equals(node.kind()) || categoryParents.contains(node.id()) || !allowed.test(node.id())) continue;
            Deque<String> labels = new ArrayDeque<>();
            labels.add(node.name());
            Set<Long> seen = new HashSet<>(Set.of(node.id()));
            Long parent = node.parentId();
            while (parent != null && seen.add(parent) && byId.containsKey(parent)) {
                Catalog ancestor = byId.get(parent);
                labels.addFirst(ancestor.name());
                parent = ancestor.parentId();
            }
            if (parent != null) continue;
            String path = String.join(" / ", labels);
            if ((path + " " + node.code()).toLowerCase(Locale.ROOT).contains(term))
                choices.add(new Choice(node.id(), node.code(), node.name(), path));
        }
        return choices;
    }

    private long countActiveChildren(long id, String kind) {
        return jdbc.queryForObject("""
            select count(*) from ams_catalog where parent_id=? and status='active' and (?='' or kind=?)
            """, Long.class, id, kind, kind);
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
