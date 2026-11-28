package com.ruoyi.system.domain;

import java.time.LocalDateTime;

/** M08 采集运行记录，对应 weather_collect_log。 */
public class WeatherCollectLog {
    private Long collectId;
    private String cityCode;
    private String collectStatus;
    private Integer forecastCount;
    private Integer acceptedCount;
    private LocalDateTime lastSuccessAt;
    private String errorCode;
    private String message;
    private String requestId;
    private LocalDateTime collectTime;

    public Long getCollectId() { return collectId; }
    public void setCollectId(Long collectId) { this.collectId = collectId; }
    public String getCityCode() { return cityCode; }
    public void setCityCode(String cityCode) { this.cityCode = cityCode; }
    public String getCollectStatus() { return collectStatus; }
    public void setCollectStatus(String collectStatus) { this.collectStatus = collectStatus; }
    public Integer getForecastCount() { return forecastCount; }
    public void setForecastCount(Integer forecastCount) { this.forecastCount = forecastCount; }
    public Integer getAcceptedCount() { return acceptedCount; }
    public void setAcceptedCount(Integer acceptedCount) { this.acceptedCount = acceptedCount; }
    public LocalDateTime getLastSuccessAt() { return lastSuccessAt; }
    public void setLastSuccessAt(LocalDateTime lastSuccessAt) { this.lastSuccessAt = lastSuccessAt; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public LocalDateTime getCollectTime() { return collectTime; }
    public void setCollectTime(LocalDateTime collectTime) { this.collectTime = collectTime; }
}
