package com.ruoyi.web.controller.archive;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.ArchiveQuery;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class ArchiveSearchPlans {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    public ArchiveSearchPlans(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record Filters(String keyword, String archiveCode, String title, String categoryId,
            String secretLevel, String archiveStatus, String dateFrom, String dateTo,
            String orderByColumn, String isAsc) {
        static Filters from(ArchiveQuery query) {
            return new Filters(query.getKeyword(), query.getArchiveCode(), query.getTitle(),
                    text(query.getCategoryId()), text(query.getSecretLevel()), query.getArchiveStatus(),
                    text(query.getDateFrom()), text(query.getDateTo()), query.getOrderByColumn(), query.getIsAsc());
        }

        private static String text(Object value) {
            return value == null ? "" : value.toString();
        }
    }

    record Plan(String id, String name, Filters filters) {}

    public List<Plan> list(long userId) {
        return jdbc.query("""
                select plan_id,plan_name,filters from arc_search_plan
                where user_id=? and active=1 order by plan_id desc
                """, (rs, row) -> new Plan(rs.getString("plan_id"), rs.getString("plan_name"),
                        json.readValue(rs.getString("filters"), Filters.class)), userId);
    }

    public void save(long userId, Long id, String name, ArchiveQuery query) {
        name = name.strip();
        if (name.isEmpty() || name.length() > 60) throw new ServiceException("方案名称须为1至60个字符");
        if (query.getDateFrom() != null && query.getDateTo() != null && query.getDateFrom().isAfter(query.getDateTo())) {
            throw new ServiceException("起始日期不能晚于结束日期");
        }
        String filters = json.writeValueAsString(Filters.from(query));
        if (id == null) {
            jdbc.update("insert into arc_search_plan(user_id,plan_name,filters) values(?,?,?)", userId, name, filters);
        } else {
            if (jdbc.update("update arc_search_plan set plan_name=?,filters=? where plan_id=? and user_id=? and active=1",
                    name, filters, id, userId) == 0
                    && jdbc.queryForObject("select count(*) from arc_search_plan where plan_id=? and user_id=? and active=1",
                            Integer.class, id, userId) == 0) {
                throw new ServiceException("方案不存在或已停用");
            }
        }
    }

    public boolean disable(long userId, long id) {
        return jdbc.update("update arc_search_plan set active=0 where plan_id=? and user_id=? and active=1", id, userId) > 0;
    }
}
