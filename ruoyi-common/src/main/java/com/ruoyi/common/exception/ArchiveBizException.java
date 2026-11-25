package com.ruoyi.common.exception;

/**
 * 档案业务码异常
 *
 * 用于需要在响应体里返回明确业务码的场景（例如 M12 的 3001 越权访问），
 * 与 {@link ServiceException}（统一走 500 + 中文提示）区分开：
 * 全局异常处理器不处理本异常，由档案模块自己的 {@code ArchiveExceptionHandler} 映射为
 * {"code":业务码,"msg":提示}，前端按 code != 0 显示提示。
 *
 * 业务码取值以项目统一业务码表为准，新增码值前先与该表 owner 确认。
 *
 * @author member6
 */
public class ArchiveBizException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    /** 业务码 */
    private final int code;

    public ArchiveBizException(int code, String message)
    {
        super(message);
        this.code = code;
    }

    public int getCode()
    {
        return code;
    }
}
