ALTER TABLE homework_tasks
    ADD COLUMN estimate_source VARCHAR(24) NOT NULL DEFAULT 'GRADE_DEFAULT' AFTER estimated_minutes;

ALTER TABLE plans
    ADD COLUMN bedtime_buffer_minutes INT NOT NULL DEFAULT 45 AFTER planned_end_time,
    ADD COLUMN warning_message VARCHAR(255) NULL AFTER bedtime_buffer_minutes;

ALTER TABLE plan_items
    ADD COLUMN started_at TIMESTAMP(6) NULL AFTER actual_seconds,
    ADD COLUMN skipped_at TIMESTAMP(6) NULL AFTER completed_at,
    ADD COLUMN overrun_decision VARCHAR(20) NULL AFTER skipped_at;

CREATE TABLE weekly_comments (
    id VARCHAR(36) PRIMARY KEY,
    family_id VARCHAR(36) NOT NULL,
    child_id VARCHAR(36) NOT NULL,
    week_start DATE NOT NULL,
    actor_id VARCHAR(36) NOT NULL,
    actor_name VARCHAR(40) NOT NULL,
    actor_relation VARCHAR(20) NOT NULL,
    content VARCHAR(240) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_weekly_comments_family FOREIGN KEY (family_id) REFERENCES families(id),
    CONSTRAINT fk_weekly_comments_child FOREIGN KEY (child_id) REFERENCES children(id),
    CONSTRAINT fk_weekly_comments_actor FOREIGN KEY (actor_id) REFERENCES members(id),
    INDEX idx_weekly_comments_child_week (child_id, week_start, created_at)
);

CREATE TABLE reward_definitions (
    id VARCHAR(36) PRIMARY KEY,
    family_id VARCHAR(36) NOT NULL,
    name VARCHAR(80) NOT NULL,
    icon VARCHAR(12) NOT NULL,
    required_stars INT NOT NULL,
    category VARCHAR(24) NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    created_by VARCHAR(36) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_rewards_family FOREIGN KEY (family_id) REFERENCES families(id),
    CONSTRAINT fk_rewards_creator FOREIGN KEY (created_by) REFERENCES members(id),
    INDEX idx_rewards_family_active (family_id, active, required_stars)
);

CREATE TABLE reward_redemptions (
    id VARCHAR(36) PRIMARY KEY,
    reward_id VARCHAR(36) NOT NULL,
    child_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_by VARCHAR(36) NOT NULL,
    reviewed_by VARCHAR(36) NULL,
    requested_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    reviewed_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_redemptions_reward FOREIGN KEY (reward_id) REFERENCES reward_definitions(id),
    CONSTRAINT fk_redemptions_child FOREIGN KEY (child_id) REFERENCES children(id),
    CONSTRAINT fk_redemptions_requester FOREIGN KEY (requested_by) REFERENCES members(id),
    CONSTRAINT fk_redemptions_reviewer FOREIGN KEY (reviewed_by) REFERENCES members(id),
    INDEX idx_redemptions_child_status (child_id, status, requested_at)
);

CREATE TABLE star_transactions (
    id VARCHAR(36) PRIMARY KEY,
    child_id VARCHAR(36) NOT NULL,
    amount INT NOT NULL,
    reason VARCHAR(120) NOT NULL,
    related_id VARCHAR(36) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_star_transactions_child FOREIGN KEY (child_id) REFERENCES children(id),
    INDEX idx_star_transactions_child_created (child_id, created_at)
);

INSERT INTO reward_definitions
    (id, family_id, name, icon, required_stars, category, source_type, created_by)
VALUES
    ('reward-didi-mint', 'demo-family', '薄荷闪光皮肤', '🤖', 30, 'SKIN', 'BUILTIN', 'demo-parent-mom'),
    ('reward-dessert', 'demo-family', '周末甜点时光', '🧁', 50, 'WISH', 'BUILTIN', 'demo-parent-mom'),
    ('reward-movie', 'demo-family', '家庭电影选择权', '🎬', 80, 'WISH', 'BUILTIN', 'demo-parent-mom');
