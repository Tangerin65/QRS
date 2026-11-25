CREATE TABLE IF NOT EXISTS arc_search_plan (
    plan_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    plan_name VARCHAR(60) NOT NULL,
    filters JSON NOT NULL,
    active TINYINT NOT NULL DEFAULT 1,
    PRIMARY KEY (plan_id),
    KEY idx_arc_search_plan_user (user_id,active,plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
