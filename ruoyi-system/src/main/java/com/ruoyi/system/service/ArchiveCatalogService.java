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
    private static final Set<String> CATEGORY_PARENTS = Set.of("fond", "category");
    private static final Set<String> STATUSES = Set.of("active", "disabled");
    private static final String EDIT_LOCK = "select id from arc_category_edit_lock where id=1 for update";
    private static final int MAX_PATH_LENGTH = 255;
    private static final String SELECT = """
        select category_id,category_code,category_name,kind,parent_id,category_path,status,sort_no,remark,revision
        from arc_category
        """;
    private static final RowMapper<Catalog> ROW = (rs, index) -> new Catalog(
        rs.getLong("category_id"), rs.getString("category_code"), rs.getString("category_name"), rs.getString("kind"),
        rs.getObject("parent_id", Long.class), rs.getInt("status") == 0 ? "active" : "disabled",
        rs.getInt("sort_no"), rs.getString("remark"), rs.getInt("revision"), rs.getString("category_path"));

    public record Catalog(long id, String code, String name, String kind, Long parentId,
                          String status, int sortOrder, String remark, int revision, String categoryPath) {}
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
        List<Catalog> rows = jdbc.query(SELECT + " where category_id=?" + (forUpdate ? " for update" : ""), ROW, id);
        if (rows.isEmpty()) throw new ServiceException("目录不存在，请刷新列表");
        return rows.get(0);
    }

    public List<Catalog> list(String keyword, String kind, String status, String sort) {
        String term = clean(keyword);
        String type = clean(kind);
        String state = clean(status);
        if (!type.isEmpty() && !KINDS.contains(type)) throw new ServiceException("目录类型无效");
        if (!state.isEmpty() && !STATUSES.contains(state)) throw new ServiceException("目录状态无效");
        Integer statusCode = state.isEmpty() ? null : statusCode(state);
        String order = switch (clean(sort)) {
            case "name" -> "category_name,category_id";
            case "code" -> "category_code,category_id";
            case "newest" -> "create_time desc,category_id desc";
            default -> "sort_no,category_code,category_id";
        };
        return jdbc.query(SELECT + """
            where (?='' or instr(lower(category_name),lower(?))>0 or instr(lower(category_code),lower(?))>0)
              and (?='' or kind=?) and (? is null or status=?)
            order by
            """ + " " + order, ROW, term, term, term, type, type, statusCode, statusCode);
    }

    @Transactional
    public Catalog save(Change input, String actor) {
        String code = clean(input.code());
        String name = clean(input.name());
        String kind = clean(input.kind());
        String status = clean(input.status());
        String remark = clean(input.remark());
        if (!code.matches("[A-Za-z0-9_.-]{1,32}")) throw new ServiceException("编码须为1–32位字母、数字、点、横线或下划线");
        if (name.isEmpty() || name.length() > 120) throw new ServiceException("名称须为1–120个字符");
        if (!KINDS.contains(kind)) throw new ServiceException("请选择全宗、分类、档案集或逻辑文件夹");
        if (!STATUSES.contains(status)) throw new ServiceException("请选择启用或停用");
        if (input.sortOrder() < 0 || remark.length() > 1000) throw new ServiceException("排序须为非负数，备注最多1000个字符");

        jdbc.queryForObject(EDIT_LOCK, Integer.class);
        Long id = input.id();
        Catalog existing = id == null ? null : find(id, true);
        if (existing != null && !Objects.equals(input.revision(), existing.revision()))
            throw new ServiceException("目录已被修改，请重新加载目录");
        if (existing != null && !existing.kind().equals(kind)) throw new ServiceException("已有目录的类型不可变更");
        Long parentId = Objects.equals(input.parentId(), 0L) ? null : input.parentId();
        String parentPath = validateHierarchy(existing, parentId, kind, status);
        try {
            if (existing == null) {
                jdbc.update("""
                    insert into arc_category(category_code,category_name,kind,parent_id,category_path,status,sort_no,remark,create_by)
                    values(?,?,?,?,'',?,?,?,?)
                    """, code, name, kind, parentId, statusCode(status), input.sortOrder(), remark, actor);
                id = jdbc.queryForObject("select last_insert_id()", Long.class);
                String path = parentPath + "/" + id;
                validatePathLength(path.length());
                jdbc.update("update arc_category set category_path=? where category_id=?", path, id);
            } else {
                String path = parentPath + "/" + id;
                if (!path.equals(existing.categoryPath())) moveDescendants(existing.categoryPath(), path);
                jdbc.update("""
                    update arc_category
                    set category_code=?,category_name=?,parent_id=?,category_path=?,status=?,sort_no=?,remark=?,revision=revision+1
                    where category_id=?
                    """, code, name, parentId, path, statusCode(status), input.sortOrder(), remark, id);
            }
        } catch (DuplicateKeyException e) {
            throw new ServiceException("目录编码已存在");
        }
        return find(id, true);
    }

    private String validateHierarchy(Catalog existing, Long parentId, String kind, String status) {
        boolean root = "fond".equals(kind);
        if (root && parentId != null) throw new ServiceException("全宗应位于根目录");
        if (!root && parentId == null) throw new ServiceException("请选择上级全宗或目录");
        String parentPath = "";
        if (parentId != null) {
            Catalog parent = find(parentId, true);
            parentPath = parent.categoryPath();
            if (existing != null && (parentPath.equals(existing.categoryPath())
                || parentPath.startsWith(existing.categoryPath() + "/"))) {
                throw new ServiceException("上级目录不能是自身或自己的下级");
            }
            if ("category".equals(kind) && !CATEGORY_PARENTS.contains(parent.kind())) {
                throw new ServiceException("分类的上级必须是全宗或其他分类");
            }
            if ("active".equals(status) && !"active".equals(parent.status()))
                throw new ServiceException("启用目录的上级必须处于启用状态");
        }
        if (existing != null && "disabled".equals(status) && !jdbc.queryForList(
            "select category_id from arc_category where parent_id=? and status=0 limit 1 for update",
            Long.class, existing.id()).isEmpty()) {
            throw new ServiceException("请先停用该目录下的启用目录");
        }
        return parentPath;
    }

    private void moveDescendants(String previousPath, String path) {
        String subtree = previousPath + "/%";
        int longest = jdbc.queryForList("""
            select char_length(category_path) as path_length from arc_category where category_path like ?
            order by path_length desc limit 1 for update
            """, Integer.class, subtree).stream().findFirst().orElse(previousPath.length());
        validatePathLength(longest - previousPath.length() + path.length());
        jdbc.update("""
            update arc_category
            set category_path=concat(?, substring(category_path, ?)), revision=revision+1
            where category_path like ?
            """, path, previousPath.length() + 1, subtree);
    }

    private static void validatePathLength(int length) {
        if (length > MAX_PATH_LENGTH) throw new ServiceException("目录层级过深，请选择更靠上的目录");
    }

    private static int statusCode(String status) {
        return "active".equals(status) ? 0 : 1;
    }

    @Transactional
    public int importRows(List<ImportRow> rows, String actor) {
        if (rows.isEmpty() || rows.size() > 1000) throw new ServiceException("每次导入1至1000条目录");
        jdbc.queryForObject(EDIT_LOCK, Integer.class);
        for (ImportRow row : rows) {
            try {
                Long parentId = clean(row.parentCode()).isEmpty() ? null : jdbc.query(
                    SELECT + " where category_code=? for update", ROW, clean(row.parentCode())).stream()
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
        if (!"category".equals(category.kind()) || !"active".equals(category.status()) || hasActiveCategoryChild(id))
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

    private boolean hasActiveCategoryChild(long id) {
        return jdbc.queryForObject("""
            select exists(select 1 from arc_category where parent_id=? and status=0 and kind='category')
            """, Boolean.class, id);
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
