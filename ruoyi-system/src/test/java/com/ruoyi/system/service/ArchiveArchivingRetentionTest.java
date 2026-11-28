package com.ruoyi.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ruoyi.system.domain.ArchiveRecord;
import com.ruoyi.system.service.ArchiveArchivingService.CheckItem;
import com.ruoyi.system.service.ArchiveArchivingService.RetentionSpec;
import com.ruoyi.system.service.ArchiveArchivingService.SubmitResult;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * M10 保管期限识别单测：两套字典种子、两类失败原因、提交拦截。
 * 不连库、不读写 sys_dict_data：字典用覆写 dictLabel 的方式注入。
 */
class ArchiveArchivingRetentionTest {

    /** setup_qrs.sql 的键值：PERMANENT/Y30/Y20/Y10/Y5。 */
    private static final Map<String, String> SETUP_STYLE = Map.of(
        "PERMANENT", "永久", "Y30", "30年", "Y20", "20年", "Y10", "10年", "Y5", "5年");

    /** arc_m01_dictionary.sql 的键值：1..10。 */
    private static final Map<String, String> NUMERIC_STYLE = Map.of(
        "1", "永久", "2", "30年", "3", "20年", "4", "10年", "5", "5年",
        "6", "1年", "7", "3年", "8", "15年", "9", "25年", "10", "50年");

    private static ArchiveArchivingService service(Map<String, String> dictionary) {
        return new ArchiveArchivingService(null, null, null) {
            @Override
            protected String dictLabel(String dictValue) {
                return dictionary.get(dictValue);
            }
        };
    }

    @Test
    @DisplayName("setup_qrs 键值：PERMANENT/Y30/Y20/Y10/Y5 识别为永久与年限")
    void resolvesSetupStyleDictionary() {
        ArchiveArchivingService svc = service(SETUP_STYLE);
        assertTrue(svc.resolveRetention("PERMANENT", null).permanent());
        assertEquals(30, svc.resolveRetention("Y30", null).years());
        assertEquals(20, svc.resolveRetention("Y20", null).years());
        assertEquals(10, svc.resolveRetention("Y10", null).years());
        assertEquals(5, svc.resolveRetention("Y5", null).years());
        assertEquals("永久", svc.resolveRetention("PERMANENT", null).display());
        assertEquals("30 年", svc.resolveRetention("Y30", null).display());
    }

    @Test
    @DisplayName("M01 数字键值 1..10：按字典标签换算为永久与年限")
    void resolvesNumericDictionary() {
        ArchiveArchivingService svc = service(NUMERIC_STYLE);
        assertTrue(svc.resolveRetention("1", null).permanent());
        Map<String, Integer> years = Map.of(
            "2", 30, "3", 20, "4", 10, "5", 5, "6", 1, "7", 3, "8", 15, "9", 25, "10", 50);
        years.forEach((code, expected) ->
            assertEquals(expected, svc.resolveRetention(code, null).years(), "编码 " + code));
    }

    @Test
    @DisplayName("字典缺项时退回直接解析编码与名称快照")
    void fallsBackWhenDictionaryMissesKey() {
        ArchiveArchivingService svc = service(Map.of());
        assertEquals(30, svc.resolveRetention("Y30", null).years());
        assertTrue(svc.resolveRetention("PERMANENT", null).permanent());
        assertEquals(10, svc.resolveRetention("X-UNKNOWN", "10年").years());
    }

    @Test
    @DisplayName("编码不在字典里：提示「字典 arc_retention_period 中不存在该编码」")
    void reportsMissingDictionaryCode() {
        RetentionSpec spec = service(SETUP_STYLE).resolveRetention("X99", null);
        assertFalse(spec.recognized());
        assertTrue(spec.message().contains("字典 arc_retention_period 中不存在该编码 X99"), spec.message());
    }

    @Test
    @DisplayName("字典有编码但标签解析不出：提示「标签无法解析」")
    void reportsUnparseableLabel() {
        RetentionSpec spec = service(Map.of("9", "长期")).resolveRetention("9", null);
        assertFalse(spec.recognized());
        assertTrue(spec.message().contains("标签无法解析"), spec.message());
        assertTrue(spec.message().contains("长期"), spec.message());
    }

    @Test
    @DisplayName("未知保管期限：校验项 RETENTION 不通过，提交归档被拒")
    void rejectsUnknownRetentionOnSubmit() {
        ArchiveRecord record = submittedRecord("X99");
        ArchiveArchivingService svc = new ArchiveArchivingService(new FakeJdbc(), archiveService(record), null) {
            @Override
            protected String dictLabel(String dictValue) {
                return SETUP_STYLE.get(dictValue);
            }
        };

        CheckItem retention = svc.check(record, id -> true).stream()
            .filter(item -> "RETENTION".equals(item.code()))
            .findFirst()
            .orElseThrow();
        assertFalse(retention.passed());
        assertTrue(retention.detail().contains("字典 arc_retention_period 中不存在该编码"), retention.detail());

        SubmitResult result = svc.submit(List.of(7L), null, "tester", 1L, true, id -> true, 3);
        assertEquals(1, result.fail());
        assertEquals(0, result.success());
        assertTrue(result.items().get(0).reason().contains("保管期限"), result.items().get(0).reason());
    }

    @Test
    @DisplayName("介质为电子但未填电子存储位置：提交前拒绝，错误码 STORAGE_UNBOUND")
    void rejectsElectronicStorageWithoutLocation() {
        ArchiveArchivingService svc = new ArchiveArchivingService(new FakeJdbc(), archiveService(submittedRecord("Y30")),
            null) {
            @Override
            protected String dictLabel(String dictValue) {
                return SETUP_STYLE.get(dictValue);
            }
        };
        ArchiveArchivingService.ArchivingException error = assertThrows(
            ArchiveArchivingService.ArchivingException.class,
            () -> svc.submit(List.of(7L), null, new ArchiveArchivingService.Storage("ELECTRONIC", "  "), "MOBILE", "",
                "tester", 1L, true, id -> true, 3));
        assertEquals("STORAGE_UNBOUND", error.code());
        assertEquals("存储介质为电子或混合时，必须填写电子存储位置", error.getMessage());
    }

    @Test
    @DisplayName("介质取值越界：PARAM_INVALID，不进入归档流程")
    void rejectsUnknownStorageMedia() {
        ArchiveArchivingService svc = new ArchiveArchivingService(new FakeJdbc(), archiveService(submittedRecord("Y30")),
            null);
        ArchiveArchivingService.ArchivingException error = assertThrows(
            ArchiveArchivingService.ArchivingException.class,
            () -> svc.submit(List.of(7L), null, new ArchiveArchivingService.Storage("TAPE", ""), "MANUAL", "",
                "tester", 1L, true, id -> true, 3));
        assertEquals("PARAM_INVALID", error.code());
    }

    @Test
    @DisplayName("契约 §3 期限三态：PERMANENT / FIXED / UNKNOWN")
    void buildsRetentionThreeState() {
        ArchiveArchivingService svc = service(SETUP_STYLE);
        ArchiveRecord record = submittedRecord("Y30");
        Map<String, Object> fixed = svc.retentionView(record, SETUP_STYLE);
        assertEquals("FIXED", fixed.get("state"));
        assertEquals(30, fixed.get("years"));
        assertEquals("30 年", fixed.get("label"));
        assertEquals("", fixed.get("message"));

        record.setRetentionCode("PERMANENT");
        Map<String, Object> permanent = svc.retentionView(record, SETUP_STYLE);
        assertEquals("PERMANENT", permanent.get("state"));
        assertNull(permanent.get("years"));

        record.setRetentionCode("X99");
        Map<String, Object> unknown = svc.retentionView(record, SETUP_STYLE);
        assertEquals("UNKNOWN", unknown.get("state"));
        assertNull(unknown.get("years"));
        assertTrue(String.valueOf(unknown.get("message")).contains("X99"), String.valueOf(unknown.get("message")));
    }

    @Test
    @DisplayName("介质/来源/批次状态标签与契约 §4.6、§5 口径一致")
    void rendersStage2Labels() {
        assertEquals("纸质", ArchiveArchivingService.mediumLabel("PAPER"));
        assertEquals("电子", ArchiveArchivingService.mediumLabel("ELECTRONIC"));
        assertEquals("混合", ArchiveArchivingService.mediumLabel("MIXED"));
        assertEquals("未登记", ArchiveArchivingService.mediumLabel(""));
        assertEquals("小程序归档", ArchiveArchivingService.sourceLabel("MOBILE"));
        assertEquals("重新组批", ArchiveArchivingService.sourceLabel("REBATCH"));
        assertEquals("手工归档", ArchiveArchivingService.sourceLabel("MANUAL"));
        assertEquals("未登记", ArchiveArchivingService.sourceLabel(""));
        assertEquals("进行中", ArchiveArchivingService.batchStatusLabel("RUNNING"));
        assertEquals("已完成", ArchiveArchivingService.batchStatusLabel("SUCCESS"));
        assertEquals("部分失败", ArchiveArchivingService.batchStatusLabel("PARTIAL"));
        assertEquals("全部失败", ArchiveArchivingService.batchStatusLabel("FAILED"));
    }

    private static ArchiveRecord submittedRecord(String retentionCode) {        ArchiveRecord record = new ArchiveRecord();
        record.setArchiveId(7L);
        record.setArchiveCode("DA-2026-007");
        record.setTitle("测试档案");
        record.setArchiveStatus("SUBMITTED");
        record.setVersion(3);
        record.setRetentionCode(retentionCode);
        record.setRetentionName("");
        return record;
    }

    /** 只实现 selectArchiveDetail，其余方法在测试里不会被调用。 */
    private static IArchiveService archiveService(ArchiveRecord record) {
        return (IArchiveService) Proxy.newProxyInstance(IArchiveService.class.getClassLoader(),
            new Class<?>[] {IArchiveService.class},
            (proxy, method, args) -> "selectArchiveDetail".equals(method.getName()) ? record : null);
    }

    /** 只回答归档流程用到的几条 SQL；其余直接失败，避免测试悄悄放过新增查询。 */
    static class FakeJdbc extends JdbcTemplate {
        @Override
        public int update(String sql, Object... args) {
            return 1;
        }

            @Override
        public <T> T queryForObject(String sql, Class<T> requiredType) {
            // JdbcTemplate 的 (sql, Class) 与 (sql, Class, Object...) 是两个重载，两个都要接住。
            return queryForObject(sql, requiredType, new Object[0]);
        }

    @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("arc_destroy")) return requiredType.cast(0L);
            if (sql.contains("arc_file")) return requiredType.cast(1L);
            if (sql.contains("arc_archive_batch")) return requiredType.cast(1L);
            // 阶段2 列探测：假库按「未迁移」处理，归档语句走旧分支。
            if (sql.contains("information_schema.columns")) return requiredType.cast(0);
            throw new IllegalStateException("单测未覆盖的查询：" + sql);
        }

        @Override
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            return List.of();
        }
    }
}
