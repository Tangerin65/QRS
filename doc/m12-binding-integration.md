# M12 微信绑定与小程序自动登录 接入说明

模块二（W2，M12-B）。负责人：成员 6 KenChow。开发分支 `feature/member6-KenChow`，合入目标 `dev`。

本文面向两类读者：**运维/部署者**（怎么配、怎么装）与**成员 3**（小程序怎么接、契约字段是什么）。

## 1. 需求与边界

落实《小程序扫码捆绑用户账号》：平台预建账号 → 扫码一次绑定 → 之后打开小程序自动识别身份与角色。

钉死的边界：

- **OpenID 只由服务端用微信 code 换取**，客户端提交的 `openid` 一律不采信（详细设计 5.3.12）；
- `session_key` 不返回客户端、不写日志；
- 服务端签发**不透明随机令牌**，库里只存 SHA-256 摘要；
- 一个 openid 同时只绑一个平台账号，一个账号同时只有一条有效绑定；
- 解绑或停用账号 → `bind_version` 递增，该账号历史令牌**全部立即失效**；
- 权限数组缺失时默认**零权限**，只有明确返回 `*:*:*` 才是全权限。

不归本模块：PC 扫码登录（模块三）、小程序扫码页与公共请求封装（成员 3）、消息与待办（成员 4）。

## 2. 安装与配置

### 2.1 数据库

在 `sql/m12_account_001.sql` 之后执行：

~~~powershell
mysql -u root -p qrs < sql/m12_binding_001.sql
~~~

脚本可重复执行。它会建（或复用）两张表 `wx_binding`、`wx_token`。

### 2.2 微信凭证

在 `application.yml` 的 `wx.miniapp` 节点填写小程序凭证。
**不要把真实 AppSecret 提交到仓库**，本地用 `config/application-local.yml` 覆盖。

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `wx.miniapp.app-id` | 空 | 小程序 AppID |
| `wx.miniapp.app-secret` | 空 | 小程序 AppSecret |
| `wx.miniapp.code2session-url` | 微信官方地址 | 无外网时可指向本地桩服务做联调 |
| `wx.token.ttl-hours` | 168 | 小程序令牌有效期（小时），默认 7 天 |

未配置凭证时，`/api/mobile/auth/login` 返回业务码 `5001`（`SERVER_NOT_CONFIGURED`），
小程序端显示「微信小程序凭证未配置」，**不会伪造登录成功**。

### 2.3 启动

~~~powershell
mvn verify
java -jar ruoyi-admin/target/ruoyi-admin.jar --spring.profiles.active=druid,local
~~~

`/api/mobile/**` 已在 Shiro 过滤器链里整段放行（`anon`）。这是必须的：小程序没有 Shiro 会话，
若不放行会被 `user` 过滤器重定向到登录页，前端只会收到一段 HTML 而不是 JSON。
放行不代表不鉴权——**每个小程序接口都在控制器内用 Bearer 令牌逐次校验账号状态、绑定关系与有效期**。

## 3. 接口契约

与成员 3 已发布的建议契约一致（[小程序公共部分接入说明](../team/小程序公共部分接入说明.md)），字段名不改。

### 3.1 通用约定

- 成功：HTTP 200，响应体 `{"code":200,"msg":"操作成功","data":{...}}`；
- 失败：按业务码映射 HTTP 状态，响应体 `{"code":"ACCOUNT_UNBOUND","msg":"中文提示"}`；
- 服务端异常只回受控中文提示，**不把原始堆栈转给客户端**。

### 3.2 接口清单

| 方法与路径 | 请求 | 返回 |
| --- | --- | --- |
| `POST /api/mobile/auth/login` | `{"code":"wx.login 凭证"}` | 未绑定 `{"bound":false}`；已绑定返回会话对象 |
| `GET /api/mobile/auth/session` | `Authorization: Bearer <token>` | 会话对象（含**最新**权限） |
| `GET /api/mobile/auth/profile` | `Authorization: Bearer <token>` | 头像占位、基础资料、绑定状态 |
| `POST /api/mobile/auth/logout` | `Authorization: Bearer <token>` | `{"revoked":true}` |
| `POST /api/mobile/account/bind` | `{"code":"...","userNo":"0003","password":"..."}` | 会话对象 |

会话对象：

~~~json
{
  "token": "<服务端签发的不透明令牌>",
  "userId": "3",
  "userName": "KenChow",
  "roleName": "普通用户",
  "permissions": ["records:search:view"],
  "userNo": "0003"
}
~~~

说明：

- `token` **只在首次登录或绑定时返回**。`GET session` 与 `GET profile` 不重复下发 token，
  避免令牌在多次请求里反复出现在响应体和日志里。
- `permissions` 每次都取最新值。权限被收回后前端菜单随之收敛。
- 空权限返回 `[]` 而不是 `null`/缺字段，避免前端把「数组缺失」误读成「全权限」。
- `GET session` **必须带 Bearer 令牌**。前端「省略 token 沿用当前令牌」的写法在这里不被接受：
  服务端无法从空令牌推断身份，缺失即 401，让前端走重新登录流程。

### 3.3 绑定请求中的口令字段

`/api/mobile/account/bind` 需要平台账号口令，字段名用 `password`（兼容 `userPwd`）。

**为什么必须验口令**（关键规则 1）：二维码内容只是一个公开编号，若凭编号就能绑定，
任何人扫到或猜到编号即可占用他人账号。因此首次绑定必须证明绑定人知道该账号口令。

当前实现校验的是**平台 PC 登录口令**（与若依登录同一摘要算法 `MD5(loginName + password + salt)`）。
如果团队希望改为「一次性绑定票据」（管理员在后台生成短时票据、小程序用票据绑定），
只需替换 `ArchiveBindingServiceImpl#bind` 里 `matchesPassword` 那一步，
其余流程与测试用例不变。

### 3.4 错误码

| HTTP | 字符串错误码 | 触发场景 | 前端应做什么 |
| --- | --- | --- | --- |
| 401 | `ACCOUNT_UNBOUND` | 未登录 / 缺令牌 / 微信未绑定 | 清理本地会话，引导扫码绑定 |
| 401 | `TOKEN_INVALID` | 令牌伪造、已撤销、已过期、绑定版本变化 | 清理本地会话，重新登录 |
| 403 | `ACCOUNT_DISABLED` | 账号停用 | **不要**跳绑定页，提示联系管理员 |
| 403 | `PASSWORD_MISMATCH` | 绑定口令错误 | 让用户重新输入口令 |
| 404 | `USER_NO_NOT_FOUND` | 用户编号不存在 | 提示核对管理员给的编号 |
| 409 | `ALREADY_BOUND` | 微信已绑其他账号，或账号已绑其他微信 | 提示联系管理员解绑 |
| 400 | `WX_CODE_INVALID` / `WX_CODE_USED` | 微信 code 无效 / 已使用 | 重新 `wx.login` 取新 code |
| 429 | `RATE_LIMITED` | 频控 | 稍后重试 |
| 500 | `SERVER_NOT_CONFIGURED` | 未配 AppID/AppSecret | 提示联系管理员 |
| 500 | `WX_SERVICE_UNAVAILABLE` | 微信服务异常 / 未预期异常 | 稍后重试 |

**停用账号不会再被映射为「未绑定」。** 这一点很重要：如果把停用当成未绑定，
前端会引导用户去绑定，而绑定必然失败，形成死循环提示。

## 4. 安全设计要点

| 风险 | 处理 |
| --- | --- |
| 伪造 openid | 请求体里的 `openId`/`openid` 字段被完全忽略，只信服务端拿 code 换到的 openid |
| 令牌被拖库直接可用 | 库里只存 SHA-256 摘要（64 位 hex），明文只在签发时返回一次 |
| AppSecret 进日志 | `WxMiniappClient` **不使用**若依自带的 `HttpUtils.sendGet`——它在 INFO 级会把含 secret 的完整 URL 和响应体写日志；改为自带的静默 GET |
| `session_key` 泄露 | 不放入会话对象、不返回前端、不写日志（有断言测试覆盖） |
| 解绑后旧令牌继续用 | 令牌记录签发时的 `bind_version`；解绑/停用递增版本，旧令牌比对失败即 401 |
| 账号停用后仍能用 7 天 | 停用接口调用 `revokeForDisabledAccount`，递增版本并撤销该账号全部令牌 |
| 越权读他人资料 | 每个接口先校验令牌 → 账号状态 → 绑定版本 → 最新权限，再返回数据 |
| 明文手机号出现在个人中心 | `phonenumber` 由服务端掩码为 `156****6666` |
| OpenID 完整值出现在后台 | 后台指标只回掩码 `oX7***9k`（`bindingStatus` 的 `openIdMasked`） |

## 5. 数据结构

### 5.1 `wx_binding`（模块一已建，此处兜底）

`binding_id`、`user_id`、`open_id`（唯一）、`union_id`、`bind_version`、`status`、
`bound_at`、`unbound_at`、`create_by`、`update_time`。

### 5.2 `wx_token`

| 字段 | 说明 |
| --- | --- |
| `token_id` | 主键 |
| `token_digest` | 令牌明文的 SHA-256 摘要（唯一），**明文不落库** |
| `user_id` | 平台账号 ID |
| `bind_version` | 签发时的绑定版本，与 `wx_binding.bind_version` 比对 |
| `expires_at` | 到期时间 |
| `revoked_at` | 非空即已失效（主动退出或管理员强制下线） |
| `client_type` | `miniapp` / `pc`，为模块三区分来源预留 |

`purgeExpiredTokens()` 会清理过期超过 7 天的令牌记录，可挂到定时任务里。

## 6. 小程序端接入

### 6.1 成员 3 需要改动的文件

- `config/index.js`：**已由本模块补上** `profile: '/api/mobile/auth/profile'`。
  其余接口路径与成员 3 原有约定一致，无需改动。
- `utils/request.js`：无需改动。错误码 `ACCOUNT_UNBOUND`、`ACCOUNT_DISABLED`、
  `ALREADY_BOUND`、`TOKEN_INVALID` 等已在 `messages` 表里有中文提示；
  本模块新增的 `PASSWORD_MISMATCH`、`USER_NO_NOT_FOUND` 会走通用兜底文案，
  如需更精确提示可在 `messages` 里补两行。
- `pages/profile`：已由本模块接入头像、基础资料、绑定状态、退出/切换账号，
  **复用成员 3 的 `utils/session.js`，没有建第二套登录状态**。
- `pages/scan`：**已由本模块改造**（详见 6.2）。成员 3 原有扫码逻辑保持不动，
  只在「绑定」分支加了口令输入框，并把 `password` 一并提交。

### 6.2 扫码绑定为什么需要口令输入框

原先扫码页只提交 `{ code, userNo }`，而 `bind` 接口要求校验口令，
两者对不上会导致绑定必然失败（实测返回 `403 PASSWORD_MISMATCH`）。

改造点（方案 A：在小程序端补口令输入）：

| 位置 | 改动 |
| --- | --- |
| `pages/scan/scan.wxml` | 绑定确认区新增口令输入框（`password="{{true}}"` 遮蔽显示）与说明文案；口令为空时「确认绑定」按钮禁用 |
| `pages/scan/scan.js` | 新增 `onPasswordInput` / `requirePassword` / `clearPassword` / `setBindPassword`；`bindAccount` 提交 `{ code, userNo, password }` |
| `pages/scan/scan.wxss` | 新增 `.password-field` 等样式 |

口令的处理约定：

- 口令**只存在页面实例字段** `this._bindPassword` 上，**不进 `data`**
  （否则会被 `setData` 输出到调试器与日志）；
- 绑定成功、切换模式、重新扫码、页面卸载时都会清空；
- 前端**不做任何加密或摘要**，原样提交由后端按 `MD5(loginName + password + salt)` 校验，
  避免前后端两套算法不一致；
- 口令输入框未填时按钮禁用，且 `confirmBind` 二次校验，防止绕过 UI 直接调用。

> 若团队后续改成「一次性绑定票据」方案（见 3.3 的替换点），
> 这里可以去掉口令输入框，改为把票据随绑定请求一起提交。

### 6.3 页面行为

- 头像用姓名首字占位（`avatarText`），资料为空时不显示假数据；
- 资料读取失败只在卡片内提示并给「重新读取」，不影响退出等本地操作；
- 退出或账号停用后立即清空资料，不残留上一个账号信息；
- 底部「解绑说明」写明换绑需管理员解除旧绑定。

### 6.4 前端测试

新增 `tests/mini-program-profile.test.cjs`（13 个用例），与成员 3 的测试文件分开维护，
避免互相改动对方文件：

~~~powershell
node --test tests/mini-program.test.cjs tests/mini-program-profile.test.cjs
~~~

其中 5 个用例专测扫码绑定口令：未填口令不发请求、填了口令请求体带 `password`、
口令不进入 `data`、绑定成功与切换模式后口令被清空、WXML 含口令输入框且方法齐全。

> 成员 3 原有 2 个绑定用例（防重复点击、页面退出后不建立会话）因绑定新增了口令前置条件，
> 已改为先调用 `setBindPassword()` 再走原流程，断言本身未放松。

## 7. 第三方/管理员操作

- **解除绑定**：账号管理详情页点「解除微信绑定」→ `POST /archive/account/unbind/{userId}`。
  会递增绑定版本、撤销该账号全部令牌。
- **查看绑定状态**：详情页「微信绑定」一行，OpenID 只显示掩码。
- 解绑后用户回到「未绑定」状态，可用原账号重新扫码绑定。

## 8. 验收步骤

前置：小程序 AppID/AppSecret 已配置，或把 `code2session-url` 指到桩服务。

1. **未绑定免登**：用新微信打开小程序 → 首页显示未绑定 → 进入绑定引导。
2. **绑定**：扫管理员给的编号二维码 → 输入平台账号口令 → 绑定成功并进入业务页。
   - 故意输错口令 → 应提示「平台账号口令不正确」，不建立绑定。
   - 用一个已被其他微信绑定的账号 → 应提示「已绑定其他微信」。
3. **免登**：杀掉小程序进程重进 → 自动识别身份与角色，无需再扫码。
4. **权限**：管理员收回该角色某个菜单权限 → 用户重新进入小程序 → 该入口消失。
5. **退出**：个人中心点退出 → 令牌被撤销；再用旧令牌访问应 401。
6. **解绑**：管理员解绑 → 用户端下次请求立即 401 → 回到未绑定状态。
7. **停用**：管理员停用账号 → 已登录的小程序**立即**无法访问（不是 7 天后），
   且提示「账号已停用」而不是「未绑定」。
8. **越权**：用 A 的令牌访问 A 的资源正常；伪造/篡改令牌一律 401。

### 降级预案

若微信真机/HTTPS 域名受限，用 Postman 或 `curl` 配合本地桩服务跑通全链路并录屏，
文档写明「**真机待验收**」，**不要写成已通过**。

## 9. 自测记录

本地 MySQL 8 + JDK 17 + 微信桩服务下完成 **31 项接口检查**，另加 **15 项「扫码绑定 → 免登」整条链路检查**
与 **32 项小程序单测**，全部通过。覆盖：

- 未绑定免登、无口令/错口令绑定被拒、正确口令绑定成功；
- **伪造 openid 不被采信**（请求体塞 `openId` 后，落库值仍为服务端换取值）；
- 库中不存令牌明文，摘要长度为 64；
- 同一微信再绑他号、同账号换微信绑定均 409；
- 已绑定免登下发新令牌、`session` 不重复下发 token；
- 个人中心资料不含 openid 原文与 `session_key`，手机号已掩码；
- 伪造/篡改/空令牌均 401；
- 退出只撤销当前令牌，其他令牌不受影响；
- **停用后令牌立即失效**，停用账号免登/绑定都回 `ACCOUNT_DISABLED` 而非「未绑定」；
- 解绑后绑定版本递增、旧令牌失效、免登回到未绑定；
- 微信 40029/40163/其他错误码正确映射，错误响应不含堆栈；
- 不存在编号返回 404。

另外，`WxMiniappClient` 改造前后对比验证过日志：改造前日志里出现过 AppSecret、
`session_key` 与 openid 原文；改用静默 GET 后，日志中**不再包含**这些值。

**扫码绑定整条链路（15 项）**单独验证过一次，覆盖「小程序改造后的真实请求体」：
不传口令被拒 → 错口令被拒且不落库 → 正口令绑定成功（返回含 userId/userName/roleName/permissions）
→ 编号前导零保留 → 库中有绑定 → 口令与令牌明文均不落库 → 同一微信免登成功
→ `session`/`profile` 可读且不含敏感值 → 同微信改绑他号 409 → 停用后令牌立即失效。

## 10. 已知限制

- **未接入真机验收**：本机没有微信 AppID 与备案 HTTPS 域名，因此绑定链路是用桩服务验证的。
  真机、摄像头、真实 code 兑换仍需在具备条件时补测。
- 绑定校验的是平台 PC 登录口令，因此**小程序扫码后要手动输一次口令**（见 6.2）。
  如果团队改成一次性绑定票据，去掉口令框即可，替换点见 3.3。
- `utils/request.js` 会强制要求 `https://`，本地联调只能用桩服务走接口验证，
  页面级联调需要备案 HTTPS 域名或开发者工具「不校验合法域名」。
- 令牌没有做主动的滑动续期，7 天到期后需重新免登（免登本身是自动的，用户无感）。
