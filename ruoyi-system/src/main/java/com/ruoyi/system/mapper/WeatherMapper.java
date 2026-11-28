package com.ruoyi.system.mapper;

import com.ruoyi.system.domain.WeatherCity;
import com.ruoyi.system.domain.WeatherCollectLog;
import com.ruoyi.system.domain.WeatherQuery;
import com.ruoyi.system.domain.WeatherRecord;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/** M08 天气数据访问。 */
public interface WeatherMapper {

    List<WeatherRecord> selectRecordList(WeatherQuery query);

    WeatherRecord selectRecordById(@Param("forecastId") long forecastId);

    WeatherRecord selectLatestByCity(@Param("cityCode") String cityCode);

    List<WeatherCity> selectCityList();

    WeatherCity selectCity(@Param("cityCode") String cityCode);

    int insertCity(WeatherCity city);

    int updateCity(WeatherCity city);

    int insertRecord(WeatherRecord record);

    int updateRecord(WeatherRecord record);

    int revokeRecord(@Param("forecastId") long forecastId, @Param("operator") String operator,
                     @Param("reason") String reason);

    /** 按 (city_code, forecast_time) 幂等写入；已撤销记录会被同键新数据恢复。 */
    int upsertForecast(WeatherRecord record);

    int insertCollectLog(WeatherCollectLog log);

    WeatherCollectLog selectLastCollect(@Param("cityCode") String cityCode);

    Map<String, Object> selectStatistics(WeatherQuery query);
}
