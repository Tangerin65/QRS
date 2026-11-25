-- ============================================================
-- M12-B 模块二：微信绑定与小程序自动登录 增量脚本
-- 依赖：sql/ry_20260319.sql、sql/m12_account_001.sql
-- 特点：可重复执行；wx_binding 在模块一已建，这里用 IF NOT EXISTS 再兜一次，
--       保证本脚本单独执行也能跑通（团队成员按模块取用时不必回翻前一个脚本）
-- 执行者：单个部署者串行执行；执行前备份并确认目标库
--
-- 安全约定（与详细设计 5.3.12 一致）：
--   1) OpenID 只由服务端用微信 code 换取，客户端提交的任何 openid 一律不采信；
--   2) 会话令牌为不透明随机串，库里只存 SHA-256 摘要，原文只在签发时返回一次；
--   3) 解绑或停用账号时 bind_version 递增，该账号历史令牌全部立即失效。
-- ============================================================
START TRANSACTION;

-- ------------------------------------------------------------
-- 1. wx_binding 微信身份绑定（模块一已建；此处兜底）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wx_binding (
    binding_id   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '绑定ID',
    user_id      BIGINT       NOT NULL COMMENT '平台账号ID（sys_user.user_id）',
    open_id      VARCHAR(64)  NOT NULL COMMENT '微信 OpenID（仅服务端用 code 换取）',
    union_id     VARCHAR(64)  NULL COMMENT '微信 UnionID（同一开放平台下才有）',
    bind_version INT          NOT NULL DEFAULT 1 COMMENT '绑定版本，解绑/停用时递增',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '1 有效，0 已解绑',
    bound_at     DATETIME     NULL COMMENT '绑定时间',
    unbound_at   DATETIME     NULL COMMENT '解绑时间',
    create_by    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '创建者',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (binding_id),
    UNIQUE KEY uk_wx_binding_open_id (open_id),
    KEY idx_wx_binding_user (user_id, status),
    CONSTRAINT ck_wx_binding_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信身份绑定';

-- ------------------------------------------------------------
-- 2. wx_token 小程序会话令牌
--    token_digest 是令牌明文的 SHA-256 十六进制串（64 位），明文不落库；
--    bind_version 记录签发时的绑定版本，与 wx_binding.bind_version 比对即可让旧令牌失效；
--    revoked_at 非空表示已撤销（主动退出或管理员强制下线）。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wx_token (
    token_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '令牌ID',
    token_digest CHAR(64)     NOT NULL COMMENT '令牌明文的 SHA-256 摘要（十六进制）',
    user_id      BIGINT       NOT NULL COMMENT '平台账号ID（sys_user.user_id）',
    bind_version INT          NOT NULL COMMENT '签发时的绑定版本',
    expires_at   DATETIME     NOT NULL COMMENT '到期时间',
    revoked_at   DATETIME     NULL COMMENT '撤销时间，非空即已失效',
    client_type  VARCHAR(20)  NOT NULL DEFAULT 'miniapp' COMMENT '客户端类型（miniapp 小程序 / pc 电脑端）',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '签发时间',
    PRIMARY KEY (token_id),
    UNIQUE KEY uk_wx_token_digest (token_digest),
    KEY idx_wx_token_user (user_id, revoked_at),
    KEY idx_wx_token_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小程序会话令牌';

COMMIT;

-- 回退说明：
--   DROP TABLE wx_token;
--   DROP TABLE wx_binding;
