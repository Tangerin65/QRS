package com.ruoyi.system.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** M08 天气预报记录，对应 weather_forecast；cityName 为展示字段。 */
public class WeatherRecord {
    private Long forecastId;
    private String cityCode;
    private String cityName;
    private LocalDateTime forecastTime;
    private LocalDate forecastDate;
    private String weatherText;
    private BigDecimal tempMin;
    private BigDecimal tempMax;
    private BigDecimal humidity;
    private String windLevel;
    private String dataSource;
    private LocalDateTime collectTime;
    private Integer version;
    private Integer revoked;
    private LocalDateTime revokedAt;
    private String revokedBy;
    private String revokeReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getForecastId() { return forecastId; }
    public void setForecastId(Long forecastId) { this.forecastId = forecastId; }
    public String getCityCode() { return cityCode; }
    public void setCityCode(String cityCode) { this.cityCode = cityCode; }
    public String getCityName() { return cityName; }
    public void setCityName(String cityName) { this.cityName = cityName; }
    public LocalDateTime getForecastTime() { return forecastTime; }
    public void setForecastTime(LocalDateTime forecastTime) { this.forecastTime = forecastTime; }
    public LocalDate getForecastDate() { return forecastDate; }
    public void setForecastDate(LocalDate forecastDate) { this.forecastDate = forecastDate; }
    public String getWeatherText() { return weatherText; }
    public void setWeatherText(String weatherText) { this.weatherText = weatherText; }
    public BigDecimal getTempMin() { return tempMin; }
    public void setTempMin(BigDecimal tempMin) { this.tempMin = tempMin; }
    public BigDecimal getTempMax() { return tempMax; }
    public void setTempMax(BigDecimal tempMax) { this.tempMax = tempMax; }
    public BigDecimal getHumidity() { return humidity; }
    public void setHumidity(BigDecimal humidity) { this.humidity = humidity; }
    public String getWindLevel() { return windLevel; }
    public void setWindLevel(String windLevel) { this.windLevel = windLevel; }
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    public LocalDateTime getCollectTime() { return collectTime; }
    public void setCollectTime(LocalDateTime collectTime) { this.collectTime = collectTime; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Integer getRevoked() { return revoked; }
    public void setRevoked(Integer revoked) { this.revoked = revoked; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
    public String getRevokedBy() { return revokedBy; }
    public void setRevokedBy(String revokedBy) { this.revokedBy = revokedBy; }
    public String getRevokeReason() { return revokeReason; }
    public void setRevokeReason(String revokeReason) { this.revokeReason = revokeReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
