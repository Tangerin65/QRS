package com.ruoyi.system.service.impl;

import com.github.pagehelper.PageHelper;
import com.ruoyi.common.exception.ArchiveBizException;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.WeatherCity;
import com.ruoyi.system.domain.WeatherCollectLog;
import com.ruoyi.system.domain.WeatherQuery;
import com.ruoyi.system.domain.WeatherRecord;
import com.ruoyi.system.mapper.WeatherMapper;
import com.ruoyi.system.service.IWeatherService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** M08 天气采集与管理实现。错误码：7002 格式错误、7008 超频（设计 7.4 节）。 */
@Service
public class WeatherServiceImpl implements IWeatherService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Map<String, String> SORTS = new LinkedHashMap<>();

    static {
        SORTS.put("cityCode", "f.city_code");
        SORTS.put("forecastTime", "f.forecast_time");
        SORTS.put("collectTime", "f.collect_time");
        SORTS.put("weatherText", "f.weather_text");
        SORTS.put("tempMax", "f.temp_max");
        SORTS.put("tempMin", "f.temp_min");
    }

    private final WeatherMapper mapper;

    public WeatherServiceImpl(WeatherMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<WeatherRecord> selectRecordList(WeatherQuery query) {
        PageHelper.startPage(query.getPageNum() == null ? 1 : query.getPageNum(),
                query.getPageSize() == null ? 10 : query.getPageSize(), orderBy(query));
        return mapper.selectRecordList(query);
    }

    @Override
    public WeatherRecord selectRecordById(long forecastId) {
        WeatherRecord record = mapper.selectRecordById(forecastId);
        if (record == null) throw new ArchiveBizException(3001, "天气记录不存在");
        return record;
    }

    @Override
    @Transactional
    public int insertRecord(WeatherRecord record, String operator) {
        validate(record);
        record.setCollectTime(LocalDateTime.now());
        if (record.getDataSource() == null || record.getDataSource().isBlank()) record.setDataSource("manual");
        if (mapper.selectCity(record.getCityCode()) == null) {
            throw new ArchiveBizException(1001, "城市未配置：" + record.getCityCode());
        }
        int rows = mapper.insertRecord(record);
        auditCollect(record.getCityCode(), operator, rows, "manual");
        return rows;
    }

    @Override
    @Transactional
    public int updateRecord(WeatherRecord record, String operator) {
        if (record.getForecastId() == null) throw new ArchiveBizException(1001, "缺少记录主键");
        if (record.getVersion() == null) throw new ArchiveBizException(1001, "缺少版本号，无法校验并发修改");
        validate(record);
        int rows = mapper.updateRecord(record);
        if (rows == 0) throw new ArchiveBizException(4013, "记录已被修改或已撤销，请刷新后重试");
        return rows;
    }

    @Override
    @Transactional
    public int revokeRecord(long forecastId, String operator, String reason) {
        int rows = mapper.revokeRecord(forecastId, operator, reason == null ? "" : reason);
        if (rows == 0) throw new ArchiveBizException(4002, "记录不存在或已撤销");
        return rows;
    }

    @Override
    public List<WeatherCity> selectCityList() {
        return mapper.selectCityList();
    }

    @Override
    @Transactional
    public int saveCity(WeatherCity city, String operator) {
        if (city == null || city.getCityCode() == null || city.getCityCode().isBlank()) {
            throw new ArchiveBizException(1001, "城市编码不能为空");
        }
        if (city.getCityName() == null || city.getCityName().isBlank()) {
            throw new ArchiveBizException(1001, "城市名称不能为空");
        }
        WeatherCity exists = mapper.selectCity(city.getCityCode());
        if (exists == null) {
            city.setCreateBy(operator);
            return mapper.insertCity(city);
        }
        return mapper.updateCity(city);
    }

    @Override
    @Transactional
    public Map<String, Object> report(Map<String, Object> payload) {
        String cityCode = text(payload == null ? null : payload.get("cityCode"));
        String requestId = text(payload == null ? null : payload.get("requestId"));
        try {
            if (cityCode == null || cityCode.isBlank()) throw new ArchiveBizException(7002, "缺少城市编码 cityCode");
            WeatherCity city = mapper.selectCity(cityCode);
            if (city == null) throw new ArchiveBizException(7002, "城市未配置：" + cityCode);
            if (city.getEnabled() != null && city.getEnabled() == 0) {
                throw new ArchiveBizException(7002, "城市已停用：" + cityCode);
            }
            Object forecasts = payload.get("forecasts");
            if (!(forecasts instanceof List<?>) || ((List<?>) forecasts).isEmpty()) {
                throw new ArchiveBizException(7002, "forecasts 不能为空");
            }
            String dataSource = text(payload.get("dataSource"));
            if (dataSource == null || dataSource.isBlank()) dataSource = "open-meteo";
            LocalDateTime now = LocalDateTime.now();
            int accepted = 0;
            List<?> rows = (List<?>) forecasts;
            for (Object item : rows) {
                if (!(item instanceof Map<?, ?>)) throw new ArchiveBizException(7002, "forecasts 元素格式错误");
                @SuppressWarnings("unchecked")
                Map<String, Object> row = (Map<String, Object>) item;
                WeatherRecord record = toRecord(cityCode, row, dataSource, now);
                validate(record);
                mapper.upsertForecast(record);
                accepted++;
            }
            WeatherCollectLog log = new WeatherCollectLog();
            log.setCityCode(cityCode);
            log.setCollectStatus("SUCCESS");
            log.setForecastCount(rows.size());
            log.setAcceptedCount(accepted);
            log.setLastSuccessAt(now);
            log.setRequestId(requestId);
            mapper.insertCollectLog(log);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("cityCode", cityCode);
            result.put("accepted", accepted);
            result.put("collectTime", now);
            result.put("lastSuccessAt", now);
            return result;
        } catch (ArchiveBizException e) {
            writeFailedLog(cityCode, requestId, e.getCode(), e.getMessage());
            throw e;
        }
    }

    @Override
    public List<Map<String, Object>> currentWeather() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (WeatherCity city : mapper.selectCityList()) {
            if (city.getEnabled() != null && city.getEnabled() == 0) continue;
            WeatherRecord latest = mapper.selectLatestByCity(city.getCityCode());
            WeatherCollectLog log = mapper.selectLastCollect(city.getCityCode());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("cityCode", city.getCityCode());
            item.put("cityName", city.getCityName());
            if (latest != null) {
                item.put("weatherText", latest.getWeatherText());
                item.put("tempMin", latest.getTempMin());
                item.put("tempMax", latest.getTempMax());
                item.put("humidity", latest.getHumidity());
                item.put("windLevel", latest.getWindLevel());
                item.put("forecastTime", latest.getForecastTime());
                item.put("collectTime", latest.getCollectTime());
                item.put("dataSource", latest.getDataSource());
            }
            item.put("lastSuccessAt", log == null ? null : log.getLastSuccessAt());
            item.put("collectStatus", log == null ? "NO_DATA" : log.getCollectStatus());
            item.put("statusMessage", log == null ? "尚未采集" : log.getMessage());
            result.add(item);
        }
        return result;
    }

    @Override
    public Map<String, Object> statistics(WeatherQuery query) {
        return mapper.selectStatistics(query);
    }

    @Override
    public WeatherCollectLog lastCollect(String cityCode) {
        return mapper.selectLastCollect(cityCode);
    }

    private WeatherRecord toRecord(String cityCode, Map<String, Object> row, String dataSource, LocalDateTime now) {
        WeatherRecord record = new WeatherRecord();
        record.setCityCode(cityCode);
        record.setForecastTime(parseTime(row.get("forecastTime")));
        record.setForecastDate(record.getForecastTime() == null ? null : record.getForecastTime().toLocalDate());
        record.setWeatherText(text(row.get("weatherText")));
        record.setTempMin(decimal(row.get("tempMin")));
        record.setTempMax(decimal(row.get("tempMax")));
        record.setHumidity(decimal(row.get("humidity")));
        record.setWindLevel(text(row.get("windLevel")));
        record.setDataSource(dataSource);
        record.setCollectTime(now);
        return record;
    }

    private void validate(WeatherRecord record) {
        if (record.getCityCode() == null || record.getCityCode().isBlank()) {
            throw new ArchiveBizException(7002, "城市编码不能为空");
        }
        if (record.getForecastTime() == null) throw new ArchiveBizException(7002, "预报时间 forecastTime 缺失或格式错误");
        if (record.getWeatherText() == null || record.getWeatherText().isBlank()) {
            throw new ArchiveBizException(7002, "天气现象 weatherText 不能为空");
        }
        if (record.getTempMin() == null || record.getTempMax() == null) {
            throw new ArchiveBizException(7002, "温度 tempMin/tempMax 缺失");
        }
        if (record.getTempMax().compareTo(record.getTempMin()) < 0) {
            throw new ArchiveBizException(7002, "最高温不能低于最低温");
        }
        if (record.getTempMax().compareTo(BigDecimal.valueOf(70)) > 0
                || record.getTempMin().compareTo(BigDecimal.valueOf(-90)) < 0) {
            throw new ArchiveBizException(7002, "温度超出合理范围");
        }
        if (record.getHumidity() != null
                && (record.getHumidity().compareTo(BigDecimal.ZERO) < 0
                || record.getHumidity().compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new ArchiveBizException(7002, "湿度必须在 0-100 之间");
        }
    }

    private void auditCollect(String cityCode, String operator, int accepted, String dataSource) {
        WeatherCollectLog log = new WeatherCollectLog();
        log.setCityCode(cityCode);
        log.setCollectStatus("SUCCESS");
        log.setForecastCount(accepted);
        log.setAcceptedCount(accepted);
        log.setLastSuccessAt(LocalDateTime.now());
        log.setMessage("手工维护：" + operator + " / " + dataSource);
        mapper.insertCollectLog(log);
    }

    private void writeFailedLog(String cityCode, String requestId, int code, String message) {
        try {
            WeatherCollectLog log = new WeatherCollectLog();
            log.setCityCode(cityCode == null ? "" : cityCode);
            log.setCollectStatus("FAILED");
            log.setForecastCount(0);
            log.setAcceptedCount(0);
            log.setErrorCode(String.valueOf(code));
            log.setMessage(message == null ? "采集失败" : message);
            log.setRequestId(requestId);
            mapper.insertCollectLog(log);
        } catch (RuntimeException ignored) {
            // 采集日志失败不影响主流程的错误返回
        }
    }

    private String orderBy(WeatherQuery query) {
        String column = query.getOrderByColumn();
        if (column == null || column.isBlank()) column = query.getSortBy();
        String mapped = column == null ? null : SORTS.get(column);
        if (mapped == null) return "f.forecast_time desc";
        String direction = query.getIsAsc();
        boolean asc = "asc".equalsIgnoreCase(direction);
        return mapped + (asc ? " asc" : " desc");
    }

    private LocalDateTime parseTime(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return null;
        try {
            if (text.length() == 10) return LocalDate.parse(text).atStartOfDay();
            String normalized = text.replace('T', ' ');
            if (normalized.length() > 19) normalized = normalized.substring(0, 19);
            return LocalDateTime.parse(normalized, DATE_TIME);
        } catch (RuntimeException e) {
            throw new ArchiveBizException(7002, "forecastTime 格式应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
        }
    }

    private BigDecimal decimal(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return null;
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new ArchiveBizException(7002, "温度/湿度必须是数字");
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
