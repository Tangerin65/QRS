package com.ruoyi.web.controller.mobile;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import com.ruoyi.common.exception.ArchiveBizException;
import com.ruoyi.common.exception.ServiceException;

/**
 * M12 小程序接口的公共基类。
 *
 * 统一约定（与成员3 已发布的建议契约对齐，字段名不改）：
 *  - 成功：HTTP 200，响应体 {@code {"code":200,"msg":"操作成功","data":{...}}}；
 *  - 失败：按业务码映射到 HTTP 401/403/404/409/410/429，响应体
 *    {@code {"code":"ACCOUNT_UNBOUND","msg":"中文提示"}}，前端按字符串错误码取中文提示；
 *  - 绝不把原始堆栈或数据库错误转给客户端，只回受控提示；
 *  - 停用账号不映射为「未绑定」，避免前端把停用当成可重新绑定状态。
 *
 * 这里用类内 {@code @ExceptionHandler} 而不是全局处理器，是为了让小程序接口的
 * HTTP 状态码语义（401/403/409）与 PC 页面接口（统一 HTTP 200 + code）互不影响。
 *
 * @author member6
 */
public abstract class MobileApiSupport
{
    private static final Logger log = LoggerFactory.getLogger(MobileApiSupport.class);

    /**
     * 业务码 → 字符串错误码，字符串码必须与小程序 utils/request.js 的 messages 对应。
     * 用静态块初始化而不是 Map.of，因为条目超过 10 组（Map.of 最多 10 组）。
     */
    private static final Map<Integer, String> CODE_LABELS;

    static
    {
        Map<Integer, String> labels = new java.util.HashMap<>();
        labels.put(4001, "WX_CODE_EMPTY");
        labels.put(4002, "WX_CODE_INVALID");
        labels.put(4003, "WX_CODE_USED");
        labels.put(4004, "RATE_LIMITED");
        labels.put(4005, "WX_USER_RISK");
        labels.put(4011, "ACCOUNT_UNBOUND");
        labels.put(4012, "TOKEN_INVALID");
        labels.put(4013, "PASSWORD_MISMATCH");
        labels.put(4031, "ACCOUNT_DISABLED");
        labels.put(4041, "USER_NO_NOT_FOUND");
        labels.put(4091, "ALREADY_BOUND");
        labels.put(4101, "BIND_EXPIRED");
        labels.put(4291, "RATE_LIMITED");
        labels.put(5001, "SERVER_NOT_CONFIGURED");
        labels.put(5002, "WX_SERVICE_UNAVAILABLE");
        CODE_LABELS = java.util.Collections.unmodifiableMap(labels);
    }

    /**
     * 成功响应。
     *
     * @param data 业务数据
     * @return HTTP 200 + code 200
     */
    protected ResponseEntity<Map<String, Object>> ok(Object data)
    {
        return ok("操作成功", data);
    }

    protected ResponseEntity<Map<String, Object>> ok(String msg, Object data)
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 200);
        body.put("msg", msg);
        if (data != null)
        {
            body.put("data", data);
        }
        return ResponseEntity.ok(body);
    }

    /**
     * 失败响应：按业务码映射 HTTP 状态。
     *
     * @param bizCode 业务码
     * @param msg     中文提示（已确认不含敏感信息）
     * @return 受控的 HTTP 状态与错误体
     */
    protected ResponseEntity<Map<String, Object>> fail(int bizCode, String msg)
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", CODE_LABELS.getOrDefault(bizCode, String.valueOf(bizCode)));
        body.put("msg", msg);
        return ResponseEntity.status(httpStatusOf(bizCode)).body(body);
    }

    /**
     * 业务码 → HTTP 状态。
     *
     * 令牌无效/未绑定一律 401，让前端清理本地会话；账号停用与越权 403；
     * 编号不存在 404；重复绑定 409；频控 429。
     */
    private static HttpStatus httpStatusOf(int bizCode)
    {
        return switch (bizCode)
        {
            case 4011, 4012, 4101 -> HttpStatus.UNAUTHORIZED;
            case 4031, 4013, 4005 -> HttpStatus.FORBIDDEN;
            case 4041 -> HttpStatus.NOT_FOUND;
            case 4091 -> HttpStatus.CONFLICT;
            case 4004, 4291 -> HttpStatus.TOO_MANY_REQUESTS;
            case 4001, 4002, 4003 -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * 业务异常：转成受控的小程序错误体。
     */
    @ExceptionHandler(ArchiveBizException.class)
    public ResponseEntity<Map<String, Object>> handleBiz(ArchiveBizException e)
    {
        // 业务异常是预期路径，记 warn 即可，不打堆栈
        log.warn("小程序接口业务校验未通过，code={}，msg={}", e.getCode(), e.getMessage());
        return fail(e.getCode(), e.getMessage());
    }

    /**
     * 服务层异常：只回中文提示，不泄露内部细节。
     */
    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<Map<String, Object>> handleService(ServiceException e)
    {
        log.warn("小程序接口处理失败：{}", e.getMessage());
        return fail(5002, "操作未完成，请稍后重试或联系管理员");
    }

    /**
     * 兜底：任何未预期异常都不把堆栈转给客户端。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e)
    {
        log.error("小程序接口发生未预期异常", e);
        return fail(5002, "服务暂时不可用，请稍后重试");
    }
}
