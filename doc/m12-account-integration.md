# M12 账号与组织底座 接入说明

模块一（W1，M12-A）。负责人：成员 6 KenChow。开发分支 `feature/member6-KenChow`，合入目标 `dev`。

本文照 [M03 分类接入说明](m03-catalog-integration.md) 的结构写：安装 → 页面操作 → 接口与接入 → 验收步骤。

## 1. 安装

环境：JDK 17、Maven、MySQL 8。

1. 空库先依次导入 `sql/ry_20260319.sql`、`sql/quartz.sql`、`sql/records_framework_001.sql`。
2. 执行本模块两个脚本，**顺序不能反**：
   - `sql/m12_account_001.sql`：给 `sys_user` 增补档案业务字段与用户编号，并建 `wx_binding` 表。
   - `sql/m12_account_menu.sql`：菜单与权限点。

   两个脚本都可重复执行（DDL 用 `information_schema` 判断、菜单用 `WHERE NOT EXISTS` 防重），
   重复执行不会报错，也不会产生重复菜单。
3. 构建并启动：

   ~~~powershell
   mvn verify
   java -jar ruoyi-admin/target/ruoyi-admin.jar --spring.profiles.active=druid,local
   ~~~

4. 登录后在左侧打开「档案账号管理」。普通角色需在「系统管理 → 角色管理 → 修改 → 菜单权限」中
   勾选对应菜单与按钮，**改完权限要重新登录**才会生效。

## 2. 权限点

| 权限点 | 覆盖操作 |
| --- | --- |
| `arc:account:view` | 清单、组合查询、详情、账号统计、导出、打印、绑定状态查看 |
| `arc:account:manage` | 新增、修改、停用/启用、Excel 导入、下载模板、生成绑定二维码、解除微信绑定 |
| `arc:account:export` | 单独授予「导出」能力（只读角色也可以只给这一项） |

写操作接口同时要求 `view + manage`（`Logical.AND`），避免只授管理权限却看不到页面的异常组合。

## 3. 页面操作

> 排查提示：若页面清单区出现 `Cannot read properties of null (reading 'rows')`，
> 或点保存提示 `No static resource archive/account for request '/archive/account/'`，
> 见第 9 节的排查记录——这两个问题的成因都很隐蔽，本文档已记录修法与验证方式。

### 3.1 账号清单

- **组合查询**：姓名、用户编号、部门（含下级）、角色、状态、档案员六个条件任意组合。
  部门筛选走若依的 `find_in_set(ancestors)`，选中上级部门会带出其下所有子部门。
- **排序**：表格按若依分页参数支持白名单字段排序（编号、姓名、部门、状态、创建时间）。
- **分页**与**统计**分离：翻页只刷新明细，统计数字始终按当前查询条件计算，不随页码抖动。

### 3.2 账号统计

页面顶部五个数字（总数、正常、停用、档案员、已绑定微信）与三张图（按部门、按角色、按状态）
与明细表使用**同一个查询条件、同一份数据**，因此「图表数字 = 表格行数」。
口径说明写在图表区标题旁：一个账号可挂多个角色，所以按角色分布之和会大于账号总数，这是预期口径。

### 3.3 新增 / 修改

- **新增**：用户编号、登录账号、姓名、部门必填。初始口令等于登录账号，页面保存后会提示
  「请提醒本人首次登录后修改」。
- **修改**：可改姓名、部门、角色、手机、邮箱、档案员、密级上限引用、备注。
  登录账号创建后不可改（避免历史数据与审计断链）。
- **密级上限引用**只保存一个代码值，真正的密级判定规则属于 M13（成员 1），本模块不参与判定。

### 3.4 停用 / 启用（即「删除」）

「删除」一律实现为**停用**，不物理删除，记录与审计保留，可随时恢复：

- 停用后该账号无法登录 PC，小程序令牌**立即失效**（不是等 7 天自然过期）；
- 启用后恢复登录，微信绑定关系保持不变，绑定版本不回退；
- 不允许停用当前登录账号，后端会拒绝并提示。

### 3.5 导入

1. 页面点「下载模板」，得到 `archive-account-template.xlsx`。
2. 填写后上传。**逐行校验**，返回成功数、新增数、更新数、失败数与每行失败原因，
   不做「一条错就整批回滚」，方便按行修正后重试。

模板共三页，**请不要删页**（导入只读第一页「账号清单」）：

| 页 | 作用 |
| --- | --- |
| 账号清单 | 唯一参与导入的数据页 |
| 填写说明 | 每列口径 + 常见错误提示 |
| 可选值 | 列出当前系统**真实存在**的部门完整路径与角色名称，可照着填或直接复制 |

模板还做了两件事降低填错概率：

- 表头九个字段都带**批注**，鼠标悬停即可看到该列怎么填；
- 「部门名称」「角色名称」「是否档案员」「状态」四列带**下拉选择**，
  填了列表外的值会被 Excel 直接拦下（部门/角色过多超过 Excel 内联列表 255 字符上限时，
  下拉会自动省略，此时批注与「可选值」页仍然有效）。

导入规则：

- 用户编号按文本读写，`0001`、`0101` 这类前导零不会丢；
- 部门名称需与部门管理中已有名称一致，支持完整路径写法
  （`若依科技 / 深圳总公司 / 研发部门`）；
- **角色名称要填角色名（如 `普通角色`），不是角色编号**；多个用顿号、逗号、分号或空格分隔；
- **已存在的登录账号按「修改基础资料」处理，不存在的按「新增」处理**；
- 导入不修改口令、不修改密级上限、不改状态。

如果提示「找不到角色」，错误信息里会列出**当前可用的角色名**，照着重填即可；
若提示里出现「该列应填角色名称，不是编号」，说明把数字填进了名称列（这是最常见的填错方式）。

### 3.6 导出 / 打印

导出与打印都使用**当前查询结果**，与页面同一份数据。打印含标题、条目数、生成时间。

### 3.7 绑定二维码

详情页点「绑定二维码」，弹出预览图。

- 内容**只有纯文本用户编号**（例如 `0003`），不含姓名、手机号、令牌等任何个人资料；
- 编号按纯文本处理，**保留前导零**，不转数字、不丢精度；
- 响应头设 `Cache-Control: no-store`，避免中间层缓存到别人的编号二维码；
- 由于二维码只含公开编号，即使被他人看到，也只是泄露一个对外编号；
  **真正绑定仍需服务端校验平台账号口令**（见模块二）。

二维码生成使用 **ZXing**（`com.google.zxing:core`，Apache-2.0），没有自研编码器：

- 编码（Reed-Solomon 纠错、分块交织、掩码择优）全部交给 ZXing 的 `QRCodeWriter`，
  只需显式指定 `CHARACTER_SET=UTF-8`、`ERROR_CORRECTION=M`、`MARGIN=4`；
- 只引入 `core`，**不引 `javase`**——后者会额外带进 `jcommander` 与 `jai-imageio` 两个本项目用不到的依赖；
  PNG 渲染直接用 JDK 的 `ImageIO` + `BufferedImage`（灰度空间、不写 ICC，避免部分扫码工具解析异常）；
- 静区由 ZXing 按 `MARGIN=4` 生成，**渲染层不再二次加边距**，否则会出现双倍静区；
- 工具类 `QrCodeEncoder` 提供 `toPng(text, scale)` 与 `toBase64DataUri(text, scale)` 两个入口。

## 4. 接口

PC 走若依 MVC + Shiro 会话，与 M03 保持同一风格。

| 方法 | 路径 | 权限点 | 说明 |
| --- | --- | --- | --- |
| GET | `/archive/account` | `arc:account:view` | 页面 |
| GET | `/archive/account/list` | `arc:account:view` | 分页清单（组合查询） |
| GET | `/archive/account/all` | `arc:account:view` | 不分页完整结果（统计/打印用） |
| GET | `/archive/account/{userId}` | `arc:account:view` | 详情（含角色、绑定状态、在线会话） |
| POST | `/archive/account` | `view` + `manage` | 新增 |
| PUT | `/archive/account` | `view` + `manage` | 修改 |
| PUT | `/archive/account/status` | `view` + `manage` | 停用 / 启用 |
| POST | `/archive/account/import` | `view` + `manage` | Excel 导入 |
| GET | `/archive/account/export` | `arc:account:view` | 导出当前查询结果 |
| GET | `/archive/account/template` | `view` + `manage` | 下载导入模板 |
| GET | `/archive/account/statistics` | `arc:account:view` | 账号统计 |
| GET | `/archive/account/bindqrcode?userNo=` | `view` + `manage` | 绑定二维码 PNG（`format=base64` 可取内嵌形式） |
| GET | `/archive/account/deptTree` | `arc:account:view` | 部门树 |
| GET | `/archive/account/roles` | `arc:account:view` | 角色下拉 |
| POST | `/archive/account/unbind/{userId}` | `view` + `manage` | 管理员解除微信绑定 |
| GET | `/archive/account/binding/{userId}` | `arc:account:view` | 绑定状态（OpenID 只回掩码） |
| GET | `/archive/account/checkUserNo` | `arc:account:view` | 编号可用性校验 |

所有写接口都带 `@Log`，落到若依的 `sys_oper_log`；写请求需要页面里的 CSRF 令牌。

## 5. 数据结构

### 5.1 `sys_user` 增补字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `user_no` | VARCHAR(64) NOT NULL，唯一 | 对外业务编号，二维码内容。存量账号按 `LPAD(user_id,4,'0')` 补齐（1 → `0001`） |
| `arc_archivist` | CHAR(1) DEFAULT '0' | 是否档案员（0 否 1 是） |
| `arc_secret_level` | VARCHAR(20) DEFAULT '' | 密级上限**引用**，规则判定归 M13 |

另加索引 `idx_sys_user_dept_status (dept_id, status)` 支撑「部门 + 状态」组合筛选。

### 5.2 `wx_binding`

| 字段 | 说明 |
| --- | --- |
| `binding_id` | 主键 |
| `user_id` | 平台账号 ID |
| `open_id` | 微信 OpenID，**唯一**（一个微信同时只绑一个账号） |
| `union_id` | 微信 UnionID（可选） |
| `bind_version` | 绑定版本，解绑/停用时 +1，用于让旧令牌全部失效 |
| `status` | 1 有效 / 0 已解绑 |
| `bound_at` / `unbound_at` | 绑定 / 解绑时间 |

本模块（W1）**只建表并展示绑定状态**，绑定业务在模块二。

## 6. 给其他模块的接入说明

- **建档模块**：保存档案时若要记录责任账号，用 `sys_user.user_id`，不要新增平行账号表。
  对外展示编号用 `user_no`（字符串）。
- **M13 密级判定（成员 1）**：`sys_user.arc_secret_level` 只存放密级代码引用，
  判定规则请实现在你自己的 `PermissionService` 里，本模块不提供任何密级判断逻辑。
- **M14 审计（成员 6 自己的模块四）**：本模块的写操作已带 `@Log`。
  后续接入 `arc_audit_event` 时，`object_type` 用 `ACCOUNT`，`object_id` 用 `userId`。
- **不要**在本模块之外直接改 `sys_user.user_no`，编号变更会影响已发出的二维码。
- 数据范围沿用若依：清单查询加了 `@DataScope(deptAlias="d", userAlias="u")`，
  非管理员只能看到自己数据范围内的账号。
- 越权改他人资料返回业务码 **3001**，`response.bizCode` 与 `response.code` 都为 3001。

## 7. 验收步骤

1. 用管理员和只读角色分别从首页菜单打开页面。只读角色可查询、统计、导出、打印；
   直接提交写请求应被拒绝（权限不足）。
2. 组合查询：姓名 + 状态、部门 + 角色，两种组合各查一次，
   核对明细行数与顶部统计数字、图表数字一致。
3. 新增账号：重复用户编号应被拒绝；不选部门应被拒绝；保存后能查到，
   且初始口令 = 登录账号，能用它登录。
4. 停用该账号：立即无法登录；若是已绑定微信的账号，
   小程序再访问应立刻 401/403，而不是还能继续用。
5. 启用该账号：恢复登录，微信绑定关系仍在。
6. 导入：用模板造一条合法行 + 一条重复编号 + 一条不存在的部门 + 一条缺登录账号，
   上传后应看到成功 1、失败 3，且每行原因明确。
7. 导出：换一个查询条件再导出，Excel 行数应等于页面条目数。
8. 打印：标题、条目数、生成时间齐全。
9. 绑定二维码：用手机扫码或在线解码工具解析，结果应**只有**用户编号（含前导零），
   不含姓名手机号。

## 8. 已知限制

- 用户编号目前**不可在页面修改**（避免已发出的二维码失效）。若确需变更，
  请同时在账号管理与绑定流程里处理，并重新发放二维码。
- 导入不处理口令：新账号初始口令仍等于登录账号。

## 9. 排查记录：三个真实 bug 与修法

这三个问题都**没有**在纯接口测试里暴露，是实际打开页面才发现的。记录下来避免再犯。

### 9.1 尾斜杠导致「No static resource」

**现象**：页面点「保存」提示
`No static resource archive/account for request '/archive/account/'.`

**原因**：页面 `data-base` 是 `/archive/account/`（带尾斜杠），新增/编辑的相对路径为空，
拼出的地址是 `/archive/account/`。Spring Boot 3 起**默认关闭了尾斜杠匹配**，
该地址既匹配不上 `@PostMapping("/archive/account")`，也没有对应静态资源，于是抛
`NoResourceFoundException`，被全局异常处理器包装成 `{"code":500,"msg":"No static resource ..."}`。

注意 HTTP 状态是 **200** 而不是 404，所以只看状态码会误判为成功。

**修法**：前端统一用 `url(path)` 拼接，消掉末尾斜杠再做拼接：

~~~js
function url(path) {
  const root = base.replace(/\/+$/, "");
  const suffix = String(path || "").replace(/^\/+/, "");
  return suffix ? root + "/" + suffix : root;
}
~~~

**教训**：M03 分类页没踩到这个坑，是因为它所有请求都带非空子路径（`entries`、`export`…），
从不向 base 本身 POST。新增这类「向 base 发送 POST/PUT」的接口时要特别注意。

### 9.2 分页接口没有 `data` 字段

**现象**：清单区显示 `Cannot read properties of null (reading 'rows')`，统计数字全是 `—`。

**原因**：若依的两种响应结构不一样——
- `AjaxResult`（详情、统计、部门树）：业务数据在 `data` 字段；
- `TableDataInfo`（分页清单）：数据直接在 `rows` / `total`，**没有 `data` 字段**。

前端早先把两者都按 `answer.data ?? answer.msg` 取值，对分页接口得到 `null`，
随后 `page.rows` 抛异常，整个渲染中断——所以统计数字也没刷出来。

**修法**：拆成两个入口，`request()` 取 `data`，`requestTable()` 直接读 `rows` / `total` 并校验类型。

**教训**：接入若依分页接口必须单独处理，不能和普通 AjaxResult 共用一个解包函数。

### 9.3 清单「角色」列与「按角色」图表为空

**现象**：清单角色列全是 `—`，「按角色」图表全是「未分配角色」，但账号确实有角色。

**原因**：`SysUserMapper.selectUserList` 只 join 了 `sys_dept`，**没有 join `sys_user_role` / `sys_role`**，
所以返回的 `roles` 恒为 null。详情接口 `selectUserById` 才 join 角色表。

**为什么不直接在主查询里补 join**：`sys_user_role` 对多角色账号会放大行数，
会把 PageHelper 的 `total` 算多，还会让「一个账号一行」的清单变成多行。

**修法**：对当前页账号做一次批量角色查询再回填（`fillListExtras`），
既不多算分页，也不产生 N+1。**统计与清单都必须调用这个方法**，
否则会出现「明细有角色、图表没有」的不一致——这正是「图表与明细同源」要防的问题。

### 9.4 刷新后三张图变空白

**现象**：页面刚打开时图表正常；执行一次导入（或点一次查询）后，
「按部门 / 按角色 / 按状态」只剩标题，图形消失。

**原因**：`drawBar` / `drawPie` 里先 `node.replaceChildren()` 清空容器，再判断
`state.charts[id].getDom() === node` 决定是否复用旧实例。
但 ECharts 的 canvas 是它自己插进容器的，`replaceChildren()` 把 canvas 也删掉了；
而容器 div 没变，`getDom() === node` 依然成立，于是复用了**canvas 已脱离文档**的实例，
`setOption` 画在一块不在页面上的画布上 → 看起来就是一片空白。

**修法**：约定「有数据时只移除『无数据』提示，绝不整体清空容器」；
确实要清空（无数据）时先把实例 `dispose()` 再清，避免留下失效引用。
另外图表库加载失败时给出可见提示，而不是静默留白。

**教训**：只要把第三方组件的容器当普通 DOM 全量重建，就会破坏它自己维护的节点。
这类问题接口测试完全测不出来，必须在真实浏览器里「操作一次再检查」。

### 9.5 导入模板不够自解释

**现象**：用户把 `0` 填进「角色名称」列，导入时提示「找不到角色「0」」，
但模板本身看不出该填什么、有哪些可选值。

**这不是代码 bug，而是模板设计缺陷**（接口行为本身是对的：名称不匹配就该拒绝）。
修法见 3.5 节：模板增加「可选值」页、九列全部加表头批注、四个易错列加下拉，
错误信息里也补上「该列应填角色名称，不是编号」与可用角色列表。

## 10. 自测记录

本模块在本地 MySQL 8 + JDK 17 环境完成以下自测，全部通过：

| 范围 | 项数 | 说明 |
| --- | --- | --- |
| 接口检查 | 25 | 登录、组合查询、统计与明细同源、详情、导出、模板、二维码、编号唯一性、增改停启完整生命周期、编辑接口不夹带状态、不能停用当前账号 |
| Excel 导入 | 15 | 模板下载、混合场景（2 成功 4 失败）、4 类失败原因、编号前导零、部门按路径解析、档案员标记、导入账号可用初始口令登录、数字填角色列被拒且提示可用角色 |
| 导入模板结构 | 10 | 「可选值」页存在、部门/角色下拉含真实值、表头批注完整、常见错误说明 |
| 真实浏览器（Chrome + CDP） | 28 | 页面渲染、统计数字非空、清单出行、角色列有值、初次图表绘制、**保存后图表仍有图形**、**导入失败刷新后图表仍有图形**、再次查询后图表正常、点保存不再报 No static resource、详情弹窗、二维码图片加载、控制台无 null 异常与未捕获异常 |
| bug 复现对照 | 14 | 先按旧写法请求确认报错能复现，再按新写法确认修好 |
| 二维码解码 | 27 | 9 组内容 × 3 种缩放往返解码（含中文、64 位长编号、前导零），另验证 base64 data URI 与空内容拒绝 |
| SQL 脚本重复执行 | 3 个脚本 | 连续执行两遍均不报错 |
| 小程序单测 | 32 | 成员 3 原有 19 + 本模块 13 |

另外，`mvn clean compile` 通过。
