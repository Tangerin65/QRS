package com.ruoyi.system.domain;

import java.time.LocalDate;

/** M08 天气记录查询条件。 */
public class WeatherQuery {
    private String cityCode;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String weatherText;
    private Integer revoked;
    private String orderByColumn;
    private String isAsc;
    private String sortBy;
    private Integer pageNum = 1;
    private Integer pageSize = 10;

    public String getCityCode() { return cityCode; }
    public void setCityCode(String cityCode) { this.cityCode = cityCode; }
    public LocalDate getDateFrom() { return dateFrom; }
    public void setDateFrom(LocalDate dateFrom) { this.dateFrom = dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
    public void setDateTo(LocalDate dateTo) { this.dateTo = dateTo; }
    public String getWeatherText() { return weatherText; }
    public void setWeatherText(String weatherText) { this.weatherText = weatherText; }
    public Integer getRevoked() { return revoked; }
    public void setRevoked(Integer revoked) { this.revoked = revoked; }
    public String getOrderByColumn() { return orderByColumn; }
    public void setOrderByColumn(String orderByColumn) { this.orderByColumn = orderByColumn; }
    public String getIsAsc() { return isAsc; }
    public void setIsAsc(String isAsc) { this.isAsc = isAsc; }
    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }
    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}
