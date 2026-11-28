package com.ruoyi.web.controller.weather;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ArchiveBizException;
import com.ruoyi.system.domain.WeatherCity;
import com.ruoyi.system.domain.WeatherQuery;
import com.ruoyi.system.domain.WeatherRecord;
import com.ruoyi.system.service.IWeatherService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

/** M08 气象管理 PC 页面与接口。 */
@Controller
@RequestMapping("/weather")
public class WeatherController extends BaseController {

    private static final String QUERY = "weatherQuery";

    private static final String MANAGE = "weatherManage";

    private final IWeatherService weathers;

    public WeatherController(IWeatherService weathers) {
        this.weathers = weathers;
    }

    @GetMapping()
    @RequiresPermissions(QUERY)
    public String page(Model model) {
        model.addAttribute("operator", getLoginName());
        model.addAttribute("canManage", SecurityUtils.getSubject().isPermitted(MANAGE));
        model.addAttribute("canExport", SecurityUtils.getSubject().isPermitted("weatherExport"));
        return "weather/list";
    }

    @PostMapping("/list")
    @ResponseBody
    @RequiresPermissions(QUERY)
    public TableDataInfo list(WeatherQuery query) {
        List<WeatherRecord> rows = weathers.selectRecordList(query);
        return getDataTable(rows);
    }

    @GetMapping("/detail/{forecastId}")
    @ResponseBody
    @RequiresPermissions(QUERY)
    public AjaxResult detail(@PathVariable long forecastId) {
        return AjaxResult.success(weathers.selectRecordById(forecastId));
    }

    @PostMapping("/record")
    @ResponseBody
    @RequiresPermissions(MANAGE)
    @Log(title = "气象记录", businessType = BusinessType.INSERT)
    public AjaxResult add(@RequestBody WeatherRecord record) {
        weathers.insertRecord(record, getLoginName());
        return success();
    }

    @PutMapping("/record")
    @ResponseBody
    @RequiresPermissions(MANAGE)
    @Log(title = "气象记录", businessType = BusinessType.UPDATE)
    public AjaxResult edit(@RequestBody WeatherRecord record) {
        weathers.updateRecord(record, getLoginName());
        return success();
    }

    @DeleteMapping("/record/{forecastId}")
    @ResponseBody
    @RequiresPermissions(MANAGE)
    @Log(title = "气象记录", businessType = BusinessType.DELETE)
    public AjaxResult revoke(@PathVariable long forecastId, @RequestParam(required = false) String reason) {
        weathers.revokeRecord(forecastId, getLoginName(), reason);
        return success();
    }

    @GetMapping("/cities")
    @ResponseBody
    @RequiresPermissions(QUERY)
    public AjaxResult cities() {
        return AjaxResult.success(weathers.selectCityList());
    }

    @PostMapping("/city")
    @ResponseBody
    @RequiresPermissions(MANAGE)
    @Log(title = "气象城市", businessType = BusinessType.UPDATE)
    public AjaxResult saveCity(@RequestBody WeatherCity city) {
        weathers.saveCity(city, getLoginName());
        return success();
    }

    @GetMapping("/current")
    @ResponseBody
    @RequiresPermissions(QUERY)
    public AjaxResult current() {
        return AjaxResult.success(weathers.currentWeather());
    }

    @PostMapping("/statistics")
    @ResponseBody
    @RequiresPermissions(QUERY)
    public AjaxResult statistics(WeatherQuery query) {
        Map<String, Object> data = weathers.statistics(query);
        return AjaxResult.success(data);
    }

    @GetMapping("/export")
    @RequiresPermissions("weatherExport")
    public void export(WeatherQuery query, HttpServletResponse response) throws IOException {
        query.setPageNum(1);
        query.setPageSize(10000);
        List<WeatherRecord> rows = weathers.selectRecordList(query);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=weather.xlsx");
        WeatherWorkbook.write(rows, response.getOutputStream());
    }

    @GetMapping("/import/template")
    @RequiresPermissions(MANAGE)
    public void importTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=weather-template.xlsx");
        WeatherWorkbook.writeTemplate(response.getOutputStream());
    }

    @PostMapping("/import")
    @ResponseBody
    @RequiresPermissions(MANAGE)
    public AjaxResult importFile(@RequestParam("file") MultipartFile file) throws IOException {
        List<List<String>> rows = WeatherWorkbook.read(file.getInputStream());
        int success = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        int line = 1;
        for (List<String> row : rows) {
            line++;
            try {
                WeatherRecord record = new WeatherRecord();
                record.setCityCode(row.get(0));
                record.setForecastTime(parseTime(row.get(1)));
                record.setWeatherText(row.get(2));
                record.setTempMin(decimal(row.get(3)));
                record.setTempMax(decimal(row.get(4)));
                record.setHumidity(decimal(row.get(5)));
                record.setWindLevel(row.get(6));
                record.setDataSource(row.get(7) == null || row.get(7).isBlank() ? "import" : row.get(7));
                weathers.insertRecord(record, getLoginName());
                success++;
            } catch (ArchiveBizException e) {
                failed++;
                errors.add("第 " + line + " 行：" + e.getMessage());
            } catch (RuntimeException e) {
                failed++;
                errors.add("第 " + line + " 行：数据格式错误");
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("success", success);
        data.put("failed", failed);
        data.put("errors", errors);
        return AjaxResult.success(data);
    }

    @GetMapping("/print")
    @RequiresPermissions(QUERY)
    public String print(WeatherQuery query, Model model) {
        query.setPageNum(1);
        query.setPageSize(500);
        model.addAttribute("rows", weathers.selectRecordList(query));
        model.addAttribute("query", query);
        model.addAttribute("operator", getLoginName());
        model.addAttribute("printedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return "weather/print";
    }

    private LocalDateTime parseTime(String text) {
        if (text == null || text.isBlank()) throw new ArchiveBizException(7002, "预报时间不能为空");
        String normalized = text.trim().replace('T', ' ');
        if (normalized.length() > 19) normalized = normalized.substring(0, 19);
        try {
            if (normalized.length() == 10) return LocalDate.parse(normalized).atStartOfDay();
            return LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (RuntimeException e) {
            throw new ArchiveBizException(7002, "预报时间格式应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
        }
    }

    private BigDecimal decimal(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new ArchiveBizException(7002, "温度/湿度必须是数字：" + text);
        }
    }
}
