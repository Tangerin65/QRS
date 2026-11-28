# 档案账号模块修复说明（M12-A）



* 修改日期：2026-11-25

* 项目：`C:\Users\29903\Desktop\QRS`（若依 4.x/ Shiro / Spring Boot 3）

* 目标：修复「删号后编号仍被占用」「新增时角色选不上」「新账号登录后菜单全空」三个问题，并按确认过的方案落地编号自动取号、角色白名单、账号删除、1 号账号冻结。

* 编译验证：`mvn -o -q -DskipTests compile` 通过；`account.js` 通过 `node --check`。运行时功能需按第六节清单重启后自测。



***

## 一、改动总览

共修改 8 个文件、新增 1 个 SQL 脚本，合计 **+484 /-34 行**。



| # | 文件                                                                 | 类型 | +   | -  | 作用                                                             |
| - | ------------------------------------------------------------------ | -- | --- | -- | -------------------------------------------------------------- |
| 1 | `sql/m12_account_role_fix.sql`                                     | 新增 | 97  | 0  | 建「档案管理员」角色、收回普通角色的系统管理权限、清理历史超管角色                              |
| 2 | `ruoyi-system/.../mapper/SysUserMapper.java`                       | 修改 | 7   | 0  | 新增 `selectAllUserNos()` 声明                                     |
| 3 | `ruoyi-system/.../mapper/system/SysUserMapper.xml`                 | 修改 | 12  | 2  | 编号校验多返回登录名 / 删除标记；新增全量编号查询；档案清单排除 1 号账号                        |
| 4 | `ruoyi-system/.../service/impl/SysUserServiceImpl.java`            | 修改 | 62  | 2  | 拦截超级管理员角色非法分配；删除账号时联动解绑微信、撤销令牌                                 |
| 5 | `ruoyi-system/.../service/IArchiveAccountService.java`             | 修改 | 27  | 0  | 新增 `nextUserNo` / `explainUserNoConflict` / `deleteAccount` 声明 |
| 6 | `ruoyi-system/.../service/impl/ArchiveAccountServiceImpl.java`     | 修改 | 237 | 6  | 核心业务：角色白名单、1 号冻结、自动取号、删除账号、编号冲突提示、导入默认角色、清单排除 1 号              |
| 7 | `ruoyi-admin/.../controller/archive/ArchiveAccountController.java` | 修改 | 41  | 5  | 角色白名单接口、自动取号接口、编号校验返回文案、新增 DELETE 删除接口                         |
| 8 | `ruoyi-admin/.../static/js/archive/account.js`                     | 修改 | 89  | 19 | 角色勾选修复、自动取号与「重新取号」、角色必选校验、详情页删除按钮与自删退出                         |
| 9 | `ruoyi-admin/.../static/css/archive/account.css`                   | 修改 | 9   | 0  | 编号行、角色分组、冲突提示、危险按钮样式                                           |

> 说明：
>
> `application-druid.yml`
>
> 、
>
> `application.yml`
>
>  的本地未提交改动是你自己的数据库配置，本次
>
> **没有动**
>
> 。



***

## 二、逐个文件说明

### 1. `sql/m12_account_role_fix.sql`（新增，97 行）

这是本次修复的**数据前提**，不执行它，「档案管理员」角色不存在，新增弹窗里只会剩一个角色。脚本可重复执行，已用 `START TRANSACTION ... COMMIT` 包住。



| 段  | 内容                                                                                             |
| -- | ---------------------------------------------------------------------------------------------- |
| 1  | 幂等创建角色 `arc_admin`（名称「档案管理员」，`data_scope=1` 全部数据）                                              |
| 2  | 把 `common`（普通角色）数据范围从 `2`（自定义部门）改成 `1`（全部数据），并清空 `sys_role_dept`                               |
| 3  | 删除 `common` 除档案菜单外的所有 `sys_role_menu`（含 `system:user:add/edit/remove/import/resetPwd`）         |
| 4  | 给 `common` 只授档案页面 + 查看 / 导出按钮：`2000/2001/2002/2026/2027/2028/2036/2007/2033/2038`              |
| 5  | 给 `arc_admin` 授档案页面（`2000/2001/2002/2026/2027/2028/2036`）及其下全部按钮（`2003/2037/2038/2029/2030` 等） |
| 6  | `delete from sys_user_role where role_id=1 and user_id<>1`：清掉非 1 号账号身上的超级管理员角色（当前是 103）        |
| 7  | **可选**：把历史测试账号 103（登录名 `147258369`）改挂 `arc_admin`；不需要时删掉这 4 行                                  |
| 末尾 | 两条只读自检查询：各角色菜单数、是否还有非 1 号账号持有超管角色                                                              |

原有关键片段：



```
INSERT INTO sys_role(role_name, role_key, role_sort, data_scope, status, del_flag, create_by, create_time, remark)
SELECT '档案管理员', 'arc_admin', 3, '1', '0', '0', 'admin', NOW(), 'M12-A 档案账号维护角色'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'arc_admin' AND del_flag = '0');
```

**执行前请先备份&#x20;**`qrs`**&#x20;库**。执行方法见第五节。



***

### 2. `SysUserMapper.java`（+7 行）

新增一个查询声明，供自动取号使用：



```
/**
 * 查询全部用户编号（M12，含 del_flag=2 的历史账号，供“自动取号”跳过已占用编号）
 */
public List<String> selectAllUserNos();
```



***

### 3. `SysUserMapper.xml`（+12 /-2 行）

三处改动：

**(1)&#x20;**`checkUserNoUnique`**&#x20;多取两个字段**（原来只取 `user_id, user_no`，无法告诉用户编号被谁占了）：



```
<select id="checkUserNoUnique" parameterType="String" resultMap="SysUserResult">
    select user_id, user_no, login_name, del_flag from sys_user where user_no=#{userNo} limit 1
</select>
```

注意这条 SQL **刻意不带** `del_flag='0'`：`user_no` 上有整表唯一索引 `uk_sys_user_user_no`，已逻辑删除的行仍然占着编号，必须一起参与校验，否则会出现 “保存成功、插入时撞唯一索引”。

**(2) 新增&#x20;**`selectAllUserNos`：



```
<!-- M12：全部用户编号（含 del_flag=2），自动取号时用来跳过已占用编号 -->
<select id="selectAllUserNos" resultType="String">
    select user_no from sys_user where user_no is not null and user_no &lt;&gt; ''
</select>
```

**(3)&#x20;**`selectUserList`**&#x20;增加可选排除条件**（只由档案模块传入，若依用户管理页不受影响）：



```
<if test="params.excludeUserId != null">
    AND u.user_id &lt;&gt; #{params.excludeUserId}
</if>
```



***

### 4. `SysUserServiceImpl.java`（+62 /-2 行）

**(1) 常量**：



```
/** 系统内置超级管理员角色ID（只允许 1 号账号持有） */
private static final long SUPER_ADMIN_ROLE_ID = 1L;
```

**(2) 新增&#x20;**`checkRoleAssignable(Long userId, Long[] roleIds)`，在三个入口调用：



* `insertUser()`（新增用户）

* `updateUser()`（修改用户）

* `insertUserAuth()`（单独分配角色）



```
private void checkRoleAssignable(Long userId, Long[] roleIds)
{
    if (roleIds == null || roleIds.length == 0) { return; }
    for (Long roleId : roleIds)
    {
        if (roleId != null && roleId.longValue() == SUPER_ADMIN_ROLE_ID
                && (userId == null || userId.longValue() != 1L))
        {
            throw new ServiceException("超级管理员角色只能由系统内置 1 号账号持有");
        }
    }
}
```

这样即使有人绕过前端直接调若依的 `/system/user/add`、`/system/user/edit`、`/system/user/authRole`，也拿不到超管角色。

**(3) 新增&#x20;**`unbindOnDeletedAccount(Long userId)`，在 `deleteUserById()` 与 `deleteUserByIds()` 的删除动作之后调用：



```
private void unbindOnDeletedAccount(Long userId)
{
    SpringUtils.getBean(IArchiveBindingService.class).unbindByUser(userId, "账号删除");
}
```

用 `SpringUtils.getBean` 延迟取 Bean，避免 `SysUserServiceImpl` 与档案绑定服务形成构造器强依赖（也避免循环依赖）。效果：从若依用户管理删人时，微信绑定同步变「已解绑」，小程序令牌同步失效。



***

### 5. `IArchiveAccountService.java`（+27 行）

新增三个方法声明（含注释）：



```
String nextUserNo();                                  // 取下一个空号（含已删除账号占用的编号）
String explainUserNoConflict(String userNo, Long userId); // 编号冲突说明，可用返回 null
boolean deleteAccount(Long userId, String operator);  // 删除账号，返回 true 表示删的是自己
```



***

### 6. `ArchiveAccountServiceImpl.java`（+237 /-6 行，本次改动最大）

**常量与白名单**：



```
private static final Set<String> ASSIGNABLE_ROLE_KEYS = Set.of("common", "arc_admin");
private static final String ARCHIVE_ADMIN_ROLE_KEY = "arc_admin";
```

`saveAccount()`**&#x20;新增 4 道校验**（顺序在原有校验之前）：



1. 1 号账号禁改：`change.getUserId() == 1` → 抛「系统内置管理员账号不支持在档案模块修改」；

2. 角色必填：`roleIds == null || roleIds.length == 0` → 抛「请至少选择一个角色」（这就是 “不勾角色保存被拦”）；

3. 角色白名单：`checkRolesAssignable(roleIds)`，只允许 `common` / `arc_admin`，`role_id=1` 直接拒绝，角色被停用也拒绝；

4. 编号冲突改成带原因的富文本：`describeUserNoConflict(owner, userNo)`。



```
Long[] roleIds = change.getRoleIds();
if (roleIds == null || roleIds.length == 0)
{
    throw new ServiceException("请至少选择一个角色");
}
checkRolesAssignable(roleIds);
SysUser noOwner = userMapper.checkUserNoUnique(userNo);
if (noOwner != null && !Objects.equals(noOwner.getUserId(), change.getUserId()))
{
    throw new ServiceException(describeUserNoConflict(noOwner, userNo));
}
```

`changeStatus()`**&#x20;新增 1 号禁停**：



```
if (userId.longValue() == 1L)
{
    throw new ServiceException("系统内置管理员账号不允许停用");
}
```

**新增&#x20;**`deleteAccount(Long userId, String operator)`**（约 30 行）**：



```
if (userId.longValue() == 1L) throw new ServiceException("系统内置管理员账号不允许删除");
SysUser target = userMapper.selectUserById(userId);   // 不存在则提示刷新
userService.checkUserDataScope(userId);              // 数据范围校验
if (holdsArchiveAdmin(userId) && countEnabledArchiveAdmins() <= 1)
    throw new ServiceException("请先指定另一位档案管理员，再删除当前账号");
boolean self = Objects.equals(currentUserId(), userId);
userService.deleteUserById(userId);                  // 内部含：解绑微信 + 撤销令牌 + 清角色岗位 + 逻辑删除
return self;
```

三道保护对应你确认过的「1 号不可删」「最后一个档案管理员不可删」「可删自己，删完由前端退出登录」。

**新增&#x20;**`nextUserNo()`：从 `0001` 往上找第一个不在 `selectAllUserNos()` 结果里的编号，最多到 `9999`，用尽则提示手工指定更长编号。



```
for (int i = 1; i <= 9999; i++)
{
    String candidate = String.format("%04d", i);
    if (!taken.contains(candidate)) { return candidate; }
}
```

**新增&#x20;**`explainUserNoConflict()`：先校验格式，再查占用者；是自己则视为可用。

**新增内部方法**：



| 方法                                      | 作用                                                            |
| --------------------------------------- | ------------------------------------------------------------- |
| `checkRolesAssignable(Long[] roleIds)`  | 角色白名单 + 1 号角色拦截 + 角色状态校验                                      |
| `defaultCommonRoleIds()`                | 导入新增未填角色时，默认给「普通角色」                                           |
| `holdsArchiveAdmin(Long userId)`        | 查该账号是否持有 `arc_admin`                                          |
| `countEnabledArchiveAdmins()`           | 统计未删除且未停用的 `arc_admin` 账号数                                    |
| `describeUserNoConflict(owner, userNo)` | 区分两种冲突文案（见下）                                                  |
| `excludeBuiltInAdmin(query)`            | 档案清单 / 统计 / 导出统一排除 1 号账号                                      |
| `checkManageAllowed()` 调整               | 允许 `arc_admin` 维护他人账号（原逻辑只有 `userId==1` 能改他人，会让档案管理员点按钮就 403） |

冲突文案两种形态：



```
if ("2".equals(owner.getDelFlag()))
{
    return "用户编号「" + userNo + "」属于已删除账号（登录名 " + who + "）。编号不回收，请点“重新取号”或换一个编号";
}
return "用户编号「" + userNo + "」已被账号 " + who + " 使用，请换一个编号";
```

`importOne()`**&#x20;调整**：新增行若「角色名称」留空，自动补「普通角色」，避免导出一批零角色账号；更新行改用带 `del_flag` 语义的编号冲突判断。

`selectAccountList()`**&#x20;/&#x20;**`selectAccountStatistics()` 开头统一调用 `excludeBuiltInAdmin(query)`：



```
private void excludeBuiltInAdmin(SysUser query)
{
    ...
    params.put("excludeUserId", 1L);
}
```

清单、图表、导出因此同一口径，不会出现 “清单看不到 1 号、总数却多一个”。



***

### 7. `ArchiveAccountController.java`（+41 /-5 行）

新增 import：`DeleteMapping`、`SysRole`。

`GET /roles`**&#x20;改为白名单**（原先直接 `selectRoleAll()`，前端再过滤，等于把超管角色也发给了浏览器）：



```
List<SysRole> roles = roleService.selectRoleAll().stream()
        .filter(role -> role != null && role.getRoleKey() != null
                && ("common".equals(role.getRoleKey()) || "arc_admin".equals(role.getRoleKey())))
        .toList();
```

**新增&#x20;**`GET /nextUserNo`：



```
@GetMapping("/nextUserNo")
public AjaxResult nextUserNo()
{
    return AjaxResult.success(accountService.nextUserNo());
}
```

`GET /checkUserNo`**&#x20;改为返回可用性 + 文案**（原返回布尔值，前端无法说明原因）：



```
Long probeUserId = (userId == null || userId == 0L) ? null : userId;
String conflict = accountService.explainUserNoConflict(userNo, probeUserId);
Map<String, Object> result = new LinkedHashMap<>();
result.put("available", conflict == null);
result.put("message", conflict == null ? "" : conflict);
```

**新增&#x20;**`DELETE /{userId}`（日志类型 `BusinessType.DELETE`，需要 `archive:account:view` + `archive:account:manage`）：



```
boolean self = accountService.deleteAccount(userId, getLoginName());
return AjaxResult.success(self ? "当前账号已删除，正在退出登录" : "账号已删除", self);
```

`data` 返回 `true` 表示删的是自己，前端据此跳 `/logout`。



***

### 8. `account.js`（+89 /-19 行）

**(1) 角色选不上的真正原因与修复**

原代码把角色区当成普通 `field("角色", roleBoxes)`，生成 `<label>角色<label class="role-chip"><input…>`，即 **label 套 label**。点 chip 本身没事，点「角色」这行文字 / 空白会激活内层第一个复选框，表现就是 “勾上又弹回”“点了没反应”。另外新增时还有 `if (role.roleKey === "admin" && !row?.userId) continue;`，把超管过滤掉后只剩一个角色。

现在的写法（外层 `div.field-group`，chip 用 `label[for]` 独立绑定）：



```
state.roles.forEach((role, index) => {
  const boxId = "role-box-" + role.roleId + "-" + index;
  const box = document.createElement("input");
  box.type = "checkbox";
  box.id = boxId;
  box.value = String(role.roleId);
  box.checked = selected.has(role.roleId) || ...;
  const label = element("label", undefined, "role-chip");
  label.htmlFor = boxId;
  label.append(box, document.createTextNode(role.roleName));
  roleBoxes.append(label);
});
const roleField = element("div", undefined, "field-group");
roleField.append(element("span", "角色", "field-label"), roleBoxes);
```

同时去掉了 admin 过滤（后端已按白名单只下发两个角色），并新增保存前校验：



```
if (!roleIds.length) throw new Error("请至少选择一个角色");
```

**(2) 编号自动带出 + 重新取号 + 失焦校验**



```
const userNoRow = element("div", undefined, "number-row");
userNoRow.append(userNo);
const userNoField = element("div", undefined, "field-group");
userNoField.append(element("span", "用户编号", "field-label"), userNoRow, userNoHint);
if (creating) {
  userNoRow.append(button("重新取号", async () => {
    userNo.value = await request("nextUserNo");
  }, "small"));
  request("nextUserNo").then(no => { if (!userNo.value) userNo.value = no; });
  userNo.addEventListener("blur", async () => {
    const info = await request("checkUserNo?userId=0&userNo=" + encodeURIComponent(value));
    userNoHint.textContent = info && info.available === false ? info.message : "";
    userNoHint.hidden = !userNoHint.textContent;
  });
}
```

打开弹窗就带出空号（如 `0006`），不用用户自己猜；手动改号失焦即校验，冲突原因直接显示在输入框下面。

**(3) 详情页的操作按钮**



```
const builtInAdmin = detail.userId === 1;
if (!builtInAdmin) { /* 编辑、停用/启用 */ }
if (!builtInAdmin) { actions.append(button("删除账号", () => openDelete(detail), "danger")); }
```

**(4) 新增&#x20;**`openDelete(detail)`：二次确认要输入登录账号，输入不一致直接取消；删除成功后若返回 `self=true` 则设置 `state.redirect="/logout"`。

**(5) 提交处理器支持删除后退出**：



```
const done = await state.onSave();
dialog.close();
feedback(done);
if (state.redirect) { location.href = state.redirect; return; }
await load();
```



***

### 9. `account.css`（+9 行）



```
.field-group { display:grid; gap:4px; font-weight:600; }
.field-label { font-weight:600; }
.number-row { display:flex; align-items:center; gap:8px; }
.number-row input { flex:1 1 auto; min-width:0; }
.conflict-hint { color:#832525; }
.role-chip:has(input:checked) { border-color:var(--accent); background:var(--wash); color:#125948; }
.button.danger { color:#832525; border-color:#e6b8b2; }
.button.danger:hover { background:#fff0ee; }
```

选中态用 `:has` 高亮，能直观看出 “到底勾上没有”。



***

## 三、核心设计说明（为什么这样做）



1. **为什么删号后编号还被占**

   若依删除是逻辑删除（`update sys_user set del_flag='2'`），而 `user_no` 上有整表唯一索引，已删行仍然占号。所以编号策略定为 “不回收 + 自动取号”，而不是 “删号就释放编号”。

2. **为什么不硬删整行**

   技术上可行（当前无外键），但会丢掉审计痕迹，也可能让历史业务表里的 `user_id` 变成悬空引用；若依自身也按逻辑删除设计。删除时已经做了 “解绑微信 + 撤销令牌”，安全诉求（删了就不能再用）已经满足。

3. **为什么新账号登录没菜单**

   `sys_role_menu` 里超管角色 `role_id=1` 是 0 条（它的权限来自 `user_id==1` 的硬编码），所以把 `role_id=1` 发给别人，等于 “有角色、零菜单”。修复有两条：后端禁止非 1 号账号拿超管角色；普通角色 / 档案管理员各自获得真实菜单。

4. **为什么普通角色要收回系统管理菜单**

   原 `common` 角色挂着 85 条菜单，含 `system:user:remove`，不收回的话 “普通角色” 实际能删用户，和 “只读” 矛盾。

5. **1 号账号为什么冻结**

   `ShiroUtils.isAdmin()` 只认 `user_id==1`，它不依赖角色也不依赖菜单。档案模块对它做的是 “不展示、不可改、不可停、不可删”，避免把系统唯一的内置管理员弄坏。



***

## 四、改完后的行为对照（用户视角）



| 场景              | 改完后                                                                               |
| --------------- | --------------------------------------------------------------------------------- |
| 管理员点「新增账号」      | 用户编号自动带出（如 `0006`），旁边有「重新取号」；角色区两个选项：普通角色、档案管理员，都能正常勾选；不勾角色点保存被拦「请至少选择一个角色」       |
| 编号被已删除账号占用      | 输入框下方红字：「属于已删除账号（登录名 xxx），编号不回收，请点重新取号或换一个编号」                                     |
| 建「普通角色」账号 → 重登  | 左侧出现档案工作台、档案登记、档案分类管理、档案归档、归档批次、库位管理、档案账号管理；账号页只读，无新增 / 导入 / 停用按钮；系统管理入口消失（权限已收回） |
| 建「档案管理员」账号 → 重登 | 同样的档案菜单；账号页维护按钮齐全，可改他人资料、可删号、可导入                                                  |
| 在若依「用户管理」删掉一个账号 | 该账号微信绑定变「已解绑」、小程序令牌立即失效；再新增账号，自动号不会与它重复                                           |
| 档案管理员删除自己       | 二次确认（输入登录账号）；若是最后一个档案管理员 → 拒绝「请先指定另一位档案管理员」；确认后立即退出登录                             |
| 最后一个档案管理员       | 删除被拒，必须先加 / 指定另一位                                                                 |
| 1 号超管账号         | 档案清单里看不到（清单 / 统计 / 导出同一口径），也无法编辑、停用、删除                                            |
| 有人想给新账号塞「超级管理员」 | 前端不显示该角色；后端 `checkRoleAssignable` 直接拒绝                                            |
| 停用账号            | 保留微信绑定，只撤销令牌（与 “删除 = 解绑” 区分开）                                                     |



***

## 五、执行与部署步骤（请按顺序）



1. **备份数据库**（务必先做）



```
mysqldump -uroot -p qrs > qrs_before_m12_fix.sql
```



1. **执行修复脚本**



```
mysql -uroot -p qrs < sql/m12_account_role_fix.sql
```

执行后看末尾两条自检：`arc_admin` 的菜单数应明显大于 `common`（前者含维护按钮），`still_super_admin_outside_user1` 应为 `0`。

> 不执行这一步，「档案管理员」角色不存在，新增弹窗仍只有一个角色。



1. **重启后端**（`ArchiveAccountController`、Mapper 有新增接口，必须重启才生效）。

2. **强制刷新前端**：`Ctrl + F5`，避免浏览器继续用旧的 `account.js` / `account.css`。

3. 按第六节清单逐项验证。



***

## 六、验收清单（建议逐条点一遍）



* [ ] 新增弹窗：编号已自动填好；点「重新取号」会换一个号

* [ ] 角色区同时出现「普通角色」「档案管理员」；点文字、点空白、点 chip 都能正常勾 / 取消

* [ ] 不勾角色直接保存 → 提示「请至少选择一个角色」

* [ ] 手填一个已删除账号用过的编号 → 失焦后出现红字说明；保存被拦

* [ ] 新建普通角色账号 → 重登：有档案菜单，账号页无新增 / 导入 / 停用，系统管理菜单不见

* [ ] 新建档案管理员账号 → 重登：有档案菜单，账号页按钮齐全；能编辑其他账号

* [ ] 在若依用户管理删除某账号 → 小程序端该账号令牌失效、绑定状态为「已解绑」

* [ ] 再新增账号 → 自动编号跳过被删账号占用的号，保存成功

* [ ] 档案管理员删自己 → 二次确认 → 删后跳到登录页

* [ ] 若只剩一个档案管理员 → 删除自己被拒并提示指定另一位

* [ ] 1 号 admin 不在档案清单中出现；直接调接口改 / 停 / 删 1 号均被拒

* [ ] 通过若依「分配角色」尝试给非 1 号账号勾超管 → 保存时报「超级管理员角色只能由系统内置 1 号账号持有」



***

## 七、需要你知道的两点副作用



1. **普通角色的数据范围被改成「全部数据」**（脚本第 2 段）。原因：原来 `data_scope=2` 只挂了 100/101/105 三个部门，103 部门的账号在清单里看不见。副作用是：普通角色能看到所有部门的账号（但仍是只读）。若你希望按部门限制，把脚本第 2 段删掉，改为在「角色管理」里手工勾选部门即可。

2. **脚本第 7 段会把历史测试账号 103（**`147258369`**）从超管改挂「档案管理员」**，方便你直接用它验收。不需要就把那 4 行删掉；不处理的话它会被第 6 段清成零角色，登录后同样没有菜单。

另外：编号策略仍是「不回收」，所以已删除账号占用的 `0003/0004` 不会被重新分配，新账号会从 `0006` 往后取。这是为了避免 “旧二维码 / 旧档案记录指向新账号” 的错位。



***

## 八、本次没有改动的地方（避免误解）



* `ShiroUtils.isAdmin()` 未改（仍只认 `user_id==1`），避免影响若依全站鉴权。

* 没有给 `role_id=1` 补菜单（补了也没用，超管权限不读 `sys_role_menu`）。

* 没有把删除改成物理删除整行。

* 没有改 `application.yml` / `application-druid.yml` 的数据库配置。

* 没有改动若依原生的用户管理页面（`/system/user`），只在其 service 层加了角色拦截与删除联动。