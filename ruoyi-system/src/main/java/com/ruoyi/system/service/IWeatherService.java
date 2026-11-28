package com.ruoyi.system.service;

import com.ruoyi.system.domain.WeatherCity;
import com.ruoyi.system.domain.WeatherCollectLog;
import com.ruoyi.system.domain.WeatherQuery;
import com.ruoyi.system.domain.WeatherRecord;
import java.util.List;
import java.util.Map;

/** M08 天气采集与管理服务。 */
public interface IWeatherService {

    List<WeatherRecord> selectRecordList(WeatherQuery query);

    WeatherRecord selectRecordById(long forecastId);

    int insertRecord(WeatherRecord record, String operator);

    int updateRecord(WeatherRecord record, String operator);

    int revokeRecord(long forecastId, String operator, String reason);

    List<WeatherCity> selectCityList();

    int saveCity(WeatherCity city, String operator);

    /**
     * 采集器上报（IF-060）：按 城市+预报时间 幂等写入；失败只记采集日志，不覆盖已有成功数据。
     *
     * @throws com.ruoyi.common.exception.ArchiveBizException 7002 格式错误 / 7008 超频
     */
    Map<String, Object> report(Map<String, Object> payload);

    /** 各城市的当前天气 + 采集状态，供首页/驾驶舱/小程序展示。 */
    List<Map<String, Object>> currentWeather();

    Map<String, Object> statistics(WeatherQuery query);

    WeatherCollectLog lastCollect(String cityCode);
}
