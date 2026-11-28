package com.ruoyi.web.controller.weather;

import com.ruoyi.common.exception.ArchiveBizException;
import com.ruoyi.system.service.IWeatherService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * M08 天气采集上报接口（IF-060）。
 *
 * 采集器是独立 Qt 进程，没有 Shiro 会话，因此该路径在 ShiroConfig 中整段放行，
 * 鉴权由专用采集令牌（请求头 X-Collect-Token）完成；令牌只从配置/环境变量读取。
 * 错误码：7002 格式错误、7003 采集认证失败。
 */
@RestController
@RequestMapping("/api/weather")
public class WeatherReportController {

    private final IWeatherService weathers;

    @Value("${weather.collect-token:}")
    private String collectToken;

    public WeatherReportController(IWeatherService weathers) {
        this.weathers = weathers;
    }

    @PostMapping("/report")
    public ResponseEntity<Map<String, Object>> report(
            @RequestHeader(value = "X-Collect-Token", required = false) String token,
            @RequestBody(required = false) Map<String, Object> payload) {
        if (collectToken == null || collectToken.isBlank() || !collectToken.equals(token)) {
            return ResponseEntity.status(401).body(error(7003, "采集令牌无效"));
        }
        try {
            Map<String, Object> data = weathers.report(payload == null ? new LinkedHashMap<>() : payload);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", 0);
            body.put("msg", "操作成功");
            body.put("data", data);
            return ResponseEntity.ok(body);
        } catch (ArchiveBizException e) {
            return ResponseEntity.badRequest().body(error(e.getCode(), e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(500).body(error(5000, "采集处理失败，请稍后重试"));
        }
    }

    private Map<String, Object> error(int code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("msg", message == null ? "采集失败" : message);
        return body;
    }
}
