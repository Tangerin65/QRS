package com.ruoyi.system.service;

import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import org.springframework.stereotype.Service;

@Service
public class ArchiveSecretLevels {
    private static final String DICT_TYPE = "arc_secret_level";
    private final ISysDictTypeService dictTypes;

    public ArchiveSecretLevels(ISysDictTypeService dictTypes) {
        this.dictTypes = dictTypes;
    }

    public Map<Integer, String> options() {
        //保留字典排序并限制键值与档案表的整数列一致
        Map<Integer, String> options = new LinkedHashMap<>();
        var items = dictTypes.selectDictDataByType(DICT_TYPE);
        if (items == null) return options;
        for (SysDictData item : items) {
            Integer level = parseCode(item.getDictValue());
            if (level != null && "0".equals(item.getStatus())) options.putIfAbsent(level, item.getDictLabel());
        }
        return options;
    }

    public int maxAllowed(boolean admin, IntPredicate permitted) {
        //读取权限按数字范围计算，停用密级选项不会改变已有授权
        if (admin) return Byte.MAX_VALUE;
        for (int level = Byte.MAX_VALUE; level >= 0; level--) {
            if (permitted.test(level)) return level;
        }
        return -1;
    }

    public void requireEnabled(Integer level) {
        //校验当前启用的字典值
        if (level == null || !options().containsKey(level)) throw new ServiceException("请选择已启用的密级");
    }

    public static Integer parseCode(String value) {
        //只接受可存入 TINYINT 的规范非负整数
        if (value == null || !value.matches("0|[1-9][0-9]{0,2}")) return null;
        int level = Integer.parseInt(value);
        return level <= Byte.MAX_VALUE ? level : null;
    }

    public static String label(Integer level, Map<Integer, String> options) {
        //已停用或删除的历史编码仍可显示
        return level == null ? "" : options.getOrDefault(level, String.valueOf(level));
    }
}
