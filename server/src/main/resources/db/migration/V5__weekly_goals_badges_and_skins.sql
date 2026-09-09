ALTER TABLE children
    ADD COLUMN equipped_skin_reward_id VARCHAR(36) NULL AFTER stars,
    ADD CONSTRAINT fk_children_equipped_skin
        FOREIGN KEY (equipped_skin_reward_id) REFERENCES reward_definitions(id);

CREATE TABLE weekly_goals (
    id VARCHAR(36) PRIMARY KEY,
    family_id VARCHAR(36) NOT NULL,
    child_id VARCHAR(36) NOT NULL,
    week_start DATE NOT NULL,
    target_tasks INT NOT NULL,
    target_focus_minutes INT NOT NULL,
    bonus_stars INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(36) NOT NULL,
    claimed_by VARCHAR(36) NULL,
    claimed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_weekly_goals_family FOREIGN KEY (family_id) REFERENCES families(id),
    CONSTRAINT fk_weekly_goals_child FOREIGN KEY (child_id) REFERENCES children(id),
    CONSTRAINT fk_weekly_goals_creator FOREIGN KEY (created_by) REFERENCES members(id),
    CONSTRAINT fk_weekly_goals_claimer FOREIGN KEY (claimed_by) REFERENCES members(id),
    UNIQUE KEY uk_weekly_goals_child_week (child_id, week_start)
);

CREATE TABLE child_badges (
    id VARCHAR(36) PRIMARY KEY,
    child_id VARCHAR(36) NOT NULL,
    badge_code VARCHAR(40) NOT NULL,
    unlocked_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_child_badges_child FOREIGN KEY (child_id) REFERENCES children(id),
    UNIQUE KEY uk_child_badges_child_code (child_id, badge_code),
    INDEX idx_child_badges_child_unlocked (child_id, unlocked_at)
);

INSERT INTO reward_definitions
    (id, family_id, name, icon, required_stars, category, source_type, created_by)
VALUES
    ('reward-didi-sunset', 'demo-family', '夕阳暖光皮肤', '🌅', 60, 'SKIN', 'BUILTIN', 'demo-parent-mom'),
    ('reward-didi-starlight', 'demo-family', '星河闪光皮肤', '🌌', 100, 'SKIN', 'BUILTIN', 'demo-parent-mom');

INSERT INTO weekly_goals
    (id, family_id, child_id, week_start, target_tasks, target_focus_minutes,
     bonus_stars, status, created_by)
VALUES
    ('goal-demo-xiaoman-current', 'demo-family', 'demo-child-xiaoman',
     CURRENT_DATE - INTERVAL WEEKDAY(CURRENT_DATE) DAY, 5, 60, 10, 'ACTIVE', 'demo-parent-mom'),
    ('goal-demo-keke-current', 'demo-family', 'demo-child-keke',
     CURRENT_DATE - INTERVAL WEEKDAY(CURRENT_DATE) DAY, 4, 40, 8, 'ACTIVE', 'demo-parent-mom');
