package com.ruoyi.system.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysUser;

/**
 * M12-A 账号与组织底座 服务层
 *
 * 边界说明（模块拆分文档第 0 节）：
 *  - 复用若依 sys_user / sys_dept / sys_role，不新建平行账号体系；
 *  - 密级判定规则归 M13（成员1），本服务只保存 arcSecretLevel 引用，不做判定；
 *  - 「删除」一律实现为停用，保留记录与审计。
 *
 * @author member6
 */
public interface IArchiveAccountService
{
    /**
     * 账号清单组合查询：姓名 + 部门 + 角色 + 状态 + 用户编号 + 档案员标识。
     *
     * @param query 查询条件（SysUser，deptId 支持含下级）
     * @return 账号清单
     */
    List<SysUser> selectAccountList(SysUser query);

    /**
     * 账号详情：主字段 + 角色 + 绑定状态 + 最近登录。
     *
     * @param userId 账号ID
     * @return 账号详情视图
     */
    Map<String, Object> selectAccountDetail(Long userId);

    /**
     * 按当前查询条件统计账号数：总数、按部门、按角色、按状态。
     *
     * @param query 查询条件
     * @return 统计结果（图表与明细同源，均基于同一份查询条件）
     */
    Map<String, Object> selectAccountStatistics(SysUser query);

    /**
     * 生成账号绑定二维码：内容只放纯文本用户编号，不含姓名手机号等个人资料。
     *
     * @param userNo 用户编号
     * @param scale  模块像素边长
     * @return PNG 字节
     */
    byte[] buildBindQrCode(String userNo, int scale);

    /**
     * 校验用户编号的唯一性。
     *
     * @param user 含 userId 与 userNo
     * @return true 表示唯一
     */
    boolean checkUserNoUnique(SysUser user);

    /**
     * 是否为有效编号格式（字母、数字、下划线、连字符，1-64 位）。
     *
     * @param userNo 用户编号
     * @return true 表示格式合法
     */
    boolean isValidUserNo(String userNo);

    /**
     * 取下一个未占用的用户编号（从 0001 起，含 del_flag=2 的历史账号占用的编号）。
     *
     * @return 4 位编号，例如 0006
     */
    String nextUserNo();

    /**
     * 编号冲突说明：可用返回 null，被占用返回给用户看的原因。
     *
     * @param userNo 用户编号
     * @param userId 当前编辑的账号ID（新增传 null）
     * @return 冲突说明；无冲突返回 null
     */
    String explainUserNoConflict(String userNo, Long userId);

    /**
     * 查询账号当前的微信绑定状态。
     *
     * @param userId 账号ID
     * @return 已绑定返回 true
     */
    boolean isWxBound(Long userId);

    /**
     * 部门树（复用若依部门，按数据范围过滤）。
     *
     * @return 部门列表
     */
    List<SysDept> selectDeptTree();

    /**
     * 保存账号（新增或修改）。
     *
     * 新增：登录账号必填、用户编号必填且唯一、部门必选；
     * 修改：只更新基础资料、部门与角色，口令与密级由各自入口单独处理。
     *
     * @param change  表单内容
     * @param operator 操作者
     * @return 账号ID
     */
    Long saveAccount(SysUser change, String operator);

    /**
     * 启用/停用账号。
     *
     * 停用即等同「删除」：保留记录与审计，同时让该账号的微信会话立即失效。
     *
     * @param userId   账号ID
     * @param status   目标状态（0 正常，1 停用）
     * @param operator 操作者
     */
    void changeStatus(Long userId, String status, String operator);

    /**
     * 删除账号：逻辑删除 + 解除微信绑定 + 撤销小程序令牌。
     *
     * 仅档案模块提供删除入口；1 号内置管理员不可删除；最后一个档案管理员不可删除。
     *
     * @param userId   账号ID
     * @param operator 操作者
     * @return true 表示删除的是当前登录账号，调用方需要让前端退出登录
     */
    boolean deleteAccount(Long userId, String operator);

    /**
     * 批量导入账号。
     *
     * 逐行处理，返回成功数与每一行的失败原因；不做整批静默回滚，
     * 便于管理员按行修正后重试。
     *
     * @param rows     导入行
     * @param operator 操作者
     * @return 导入结果
     */
    Map<String, Object> importAccounts(List<ArchiveImportRow> rows, String operator);

    /**
     * 导入行（由 Controller 从工作簿解析后传入，保持编号原样字符串）。
     *
     * @param rowNo       工作簿行号
     * @param userNo      用户编号
     * @param loginName   登录账号
     * @param userName    用户名称
     * @param deptName    部门名称
     * @param roleName    角色名称（多个用顿号/逗号/分号分隔）
     * @param phonenumber 手机号码
     * @param email       邮箱
     * @param archivist   是否档案员（0/1）
     * @param status      状态（0/1）
     */
    record ArchiveImportRow(int rowNo, String userNo, String loginName, String userName, String deptName,
                            String roleName, String phonenumber, String email, String archivist, String status)
    {
    }
}
