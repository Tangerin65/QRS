package com.ruoyi.web.controller.archive;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.ArchiveQuery;
import com.ruoyi.system.service.ArchiveAttachmentIndexer;
import com.ruoyi.system.service.ArchiveCatalogService;
import com.ruoyi.system.service.ArchiveSecretLevels;
import com.ruoyi.system.service.IArchiveService;
import com.ruoyi.system.service.ISysMenuService;
import com.ruoyi.system.service.ISysUserService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Predicate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/archives")
public class MobileArchiveSearchController extends BaseController {
    private final JdbcTemplate jdbc;
    private final ISysUserService users;
    private final ISysMenuService menus;
    private final IArchiveService archives;
    private final ArchiveCatalogService catalog;
    private final ArchiveSecretLevels secretLevels;
    private final ArchiveAttachmentIndexer attachmentIndex;

    public MobileArchiveSearchController(JdbcTemplate jdbc, ISysUserService users, ISysMenuService menus,
            IArchiveService archives, ArchiveCatalogService catalog, ArchiveSecretLevels secretLevels,
            ArchiveAttachmentIndexer attachmentIndex) {
        this.jdbc = jdbc;
        this.users = users;
        this.menus = menus;
        this.archives = archives;
        this.catalog = catalog;
        this.secretLevels = secretLevels;
        this.attachmentIndex = attachmentIndex;
    }

    @InitBinder("mobileSearch")
    public void bindSearch(WebDataBinder binder) {
        binder.setAllowedFields("keyword", "categoryId", "secretLevel", "dateFrom", "dateTo",
                "pageNum", "pageSize", "orderByColumn", "isAsc");
    }

    @Anonymous
    @GetMapping("/search")
    public ResponseEntity<AjaxResult> search(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String header,
            @ModelAttribute("mobileSearch") ArchiveQuery query) throws NoSuchAlgorithmException {
        if (header == null || header.length() > 263 || !header.regionMatches(true, 0, "Bearer ", 0, 7)
                || header.substring(7).isBlank()) {
            return rejected(401, "请重新登录");
        }
        if (jdbc.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema=database() and table_name in ('wx_token','wx_binding')
                """, Integer.class) != 2) {
            return rejected(503, "小程序账号服务尚未接入");
        }
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(header.substring(7).strip().getBytes(StandardCharsets.UTF_8)));
        var identities = jdbc.queryForList("""
                select t.user_id from wx_token t
                where t.token_digest=? and t.client_type='miniapp' and t.revoked_at is null and t.expires_at>now()
                  and exists (select 1 from wx_binding b where b.user_id=t.user_id
                              and b.bind_version=t.bind_version and b.status=1)
                """, Long.class, digest);
        if (identities.isEmpty()) return rejected(401, "请重新登录");
        SysUser user = users.selectUserById(identities.get(0));
        if (user == null || !"0".equals(user.getDelFlag())) return rejected(401, "请重新登录");
        if (!"0".equals(user.getStatus())) {
            AjaxResult result = AjaxResult.error("账号已停用");
            result.put("code", "ACCOUNT_DISABLED");
            return ResponseEntity.status(403).body(result);
        }
        var permissions = menus.selectPermsByUserId(user.getUserId());
        Predicate<String> allowed = permission -> user.isAdmin() || permissions.contains(permission);
        if (!allowed.test("archiveSearch") && !allowed.test("records:search:view")) {
            return rejected(403, "当前账号没有查档权限");
        }
        var categories = catalog.registrationChoices("", id -> allowed.test("archiveCategoryAll")
                || allowed.test("archiveCategory_" + id));
        query.setOwnerName(user.getLoginName());
        query.setAdmin(user.isAdmin());
        query.setAllowedCategoryIds(categories.stream().map(ArchiveCatalogService.Choice::id).toList());
        query.setMaxSecretLevel(secretLevels.maxAllowed(user.isAdmin(), level -> allowed.test("archiveSecret" + level)));
        if (query.getKeyword() != null && query.getKeyword().length() > 200) {
            return rejected(400, "关键词最长为200个字符");
        }
        boolean attachmentSearch = allowed.test("archiveFileView") && attachmentIndex.isAvailable();
        query.setSearchAttachments(attachmentSearch && query.getKeyword() != null && !query.getKeyword().isBlank());
        query.setPageSize(query.getPageSize() == null ? 20 : Math.min(50, query.getPageSize()));
        var rows = archives.selectArchiveList(query);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(AjaxResult.success(Map.of(
                "total", getDataTable(rows).getTotal(),
                "rows", rows.stream().map(ArchiveController.SearchRow::from).toList(),
                "categories", categories.stream().map(item -> Map.of("id", Long.toString(item.id()), "name", item.path())).toList(),
                "attachmentSearch", attachmentSearch)));
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<AjaxResult> invalidQuery(ServiceException error) {
        return rejected(400, error.getMessage());
    }

    private ResponseEntity<AjaxResult> rejected(int status, String message) {
        AjaxResult result = AjaxResult.error(message);
        result.put("code", status);
        return ResponseEntity.status(status).body(result);
    }
}
