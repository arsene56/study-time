CREATE TABLE families (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE members (
    id VARCHAR(36) PRIMARY KEY,
    family_id VARCHAR(36) NOT NULL,
    display_name VARCHAR(40) NOT NULL,
    relation_name VARCHAR(20) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_members_family FOREIGN KEY (family_id) REFERENCES families(id)
);

CREATE TABLE children (
    id VARCHAR(36) PRIMARY KEY,
    family_id VARCHAR(36) NOT NULL,
    name VARCHAR(40) NOT NULL,
    grade TINYINT NOT NULL,
    bedtime TIME NOT NULL,
    stars INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_children_family FOREIGN KEY (family_id) REFERENCES families(id),
    INDEX idx_children_family (family_id)
);

CREATE TABLE homework_batches (
    id VARCHAR(36) PRIMARY KEY,
    family_id VARCHAR(36) NOT NULL,
    child_id VARCHAR(36) NOT NULL,
    source_object_key VARCHAR(255),
    recognition_mode VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_batches_family FOREIGN KEY (family_id) REFERENCES families(id),
    CONSTRAINT fk_batches_child FOREIGN KEY (child_id) REFERENCES children(id),
    CONSTRAINT fk_batches_creator FOREIGN KEY (created_by) REFERENCES members(id),
    INDEX idx_batches_child_created (child_id, created_at)
);

CREATE TABLE homework_tasks (
    id VARCHAR(36) PRIMARY KEY,
    batch_id VARCHAR(36) NOT NULL,
    child_id VARCHAR(36) NOT NULL,
    subject VARCHAR(30) NOT NULL,
    title VARCHAR(160) NOT NULL,
    task_type VARCHAR(30) NOT NULL,
    icon VARCHAR(12) NOT NULL,
    estimated_minutes INT NOT NULL,
    difficulty VARCHAR(20) NOT NULL,
    eye_load VARCHAR(10) NOT NULL,
    confidence VARCHAR(10) NOT NULL,
    status VARCHAR(30) NOT NULL,
    sort_order INT NOT NULL,
    actual_seconds INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_tasks_batch FOREIGN KEY (batch_id) REFERENCES homework_batches(id),
    CONSTRAINT fk_tasks_child FOREIGN KEY (child_id) REFERENCES children(id),
    INDEX idx_tasks_batch_order (batch_id, sort_order)
);

CREATE TABLE plans (
    id VARCHAR(36) PRIMARY KEY,
    child_id VARCHAR(36) NOT NULL,
    plan_date DATE NOT NULL,
    start_time TIME NOT NULL,
    original_end_time TIME NOT NULL,
    planned_end_time TIME NOT NULL,
    status VARCHAR(30) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_plans_child FOREIGN KEY (child_id) REFERENCES children(id),
    UNIQUE KEY uk_plans_child_date (child_id, plan_date)
);

CREATE TABLE plan_items (
    id VARCHAR(36) PRIMARY KEY,
    plan_id VARCHAR(36) NOT NULL,
    homework_task_id VARCHAR(36),
    kind VARCHAR(20) NOT NULL,
    subject VARCHAR(30) NOT NULL,
    title VARCHAR(160) NOT NULL,
    task_type VARCHAR(30) NOT NULL,
    icon VARCHAR(12) NOT NULL,
    estimated_minutes INT NOT NULL,
    sort_order INT NOT NULL,
    planned_start TIME NOT NULL,
    planned_end TIME NOT NULL,
    status VARCHAR(20) NOT NULL,
    actual_seconds INT NOT NULL DEFAULT 0,
    completed_at TIMESTAMP NULL,
    CONSTRAINT fk_plan_items_plan FOREIGN KEY (plan_id) REFERENCES plans(id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_items_task FOREIGN KEY (homework_task_id) REFERENCES homework_tasks(id),
    INDEX idx_plan_items_plan_order (plan_id, sort_order)
);

CREATE TABLE activity_log (
    id VARCHAR(36) PRIMARY KEY,
    family_id VARCHAR(36) NOT NULL,
    child_id VARCHAR(36) NOT NULL,
    actor_id VARCHAR(36) NOT NULL,
    actor_name VARCHAR(40) NOT NULL,
    actor_relation VARCHAR(20) NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_activity_family FOREIGN KEY (family_id) REFERENCES families(id),
    CONSTRAINT fk_activity_child FOREIGN KEY (child_id) REFERENCES children(id),
    CONSTRAINT fk_activity_actor FOREIGN KEY (actor_id) REFERENCES members(id),
    INDEX idx_activity_child_created (child_id, created_at)
);
