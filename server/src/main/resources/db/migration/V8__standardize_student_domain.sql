RENAME TABLE children TO students,
             child_badges TO student_badges;

ALTER TABLE homework_batches RENAME COLUMN child_id TO student_id;
ALTER TABLE homework_tasks RENAME COLUMN child_id TO student_id;
ALTER TABLE plans RENAME COLUMN child_id TO student_id;
ALTER TABLE activity_log RENAME COLUMN child_id TO student_id;
ALTER TABLE weekly_comments RENAME COLUMN child_id TO student_id;
ALTER TABLE reward_redemptions RENAME COLUMN child_id TO student_id;
ALTER TABLE star_transactions RENAME COLUMN child_id TO student_id;
ALTER TABLE weekly_goals RENAME COLUMN child_id TO student_id;
ALTER TABLE student_badges RENAME COLUMN child_id TO student_id;

ALTER TABLE students
    RENAME INDEX idx_children_family TO idx_students_family,
    RENAME INDEX fk_children_equipped_skin TO fk_students_equipped_skin;
ALTER TABLE homework_batches
    RENAME INDEX idx_batches_child_created TO idx_batches_student_created;
ALTER TABLE homework_tasks
    RENAME INDEX fk_tasks_child TO fk_tasks_student;
ALTER TABLE plans
    RENAME INDEX uk_plans_child_date TO uk_plans_student_date;
ALTER TABLE activity_log
    RENAME INDEX idx_activity_child_created TO idx_activity_student_created;
ALTER TABLE weekly_comments
    RENAME INDEX idx_weekly_comments_child_week TO idx_weekly_comments_student_week;
ALTER TABLE reward_redemptions
    RENAME INDEX idx_redemptions_child_status TO idx_redemptions_student_status;
ALTER TABLE star_transactions
    RENAME INDEX idx_star_transactions_child_created TO idx_star_transactions_student_created;
ALTER TABLE weekly_goals
    RENAME INDEX uk_weekly_goals_child_week TO uk_weekly_goals_student_week;
ALTER TABLE student_badges
    RENAME INDEX uk_child_badges_child_code TO uk_student_badges_student_code,
    RENAME INDEX idx_child_badges_child_unlocked TO idx_student_badges_student_unlocked;

ALTER TABLE students
    DROP FOREIGN KEY fk_children_family,
    DROP FOREIGN KEY fk_children_equipped_skin,
    ADD CONSTRAINT fk_students_family
        FOREIGN KEY (family_id) REFERENCES families(id),
    ADD CONSTRAINT fk_students_equipped_skin
        FOREIGN KEY (equipped_skin_reward_id) REFERENCES reward_definitions(id);
ALTER TABLE homework_batches
    DROP FOREIGN KEY fk_batches_child,
    ADD CONSTRAINT fk_batches_student
        FOREIGN KEY (student_id) REFERENCES students(id);
ALTER TABLE homework_tasks
    DROP FOREIGN KEY fk_tasks_child,
    ADD CONSTRAINT fk_tasks_student
        FOREIGN KEY (student_id) REFERENCES students(id);
ALTER TABLE plans
    DROP FOREIGN KEY fk_plans_child,
    ADD CONSTRAINT fk_plans_student
        FOREIGN KEY (student_id) REFERENCES students(id);
ALTER TABLE activity_log
    DROP FOREIGN KEY fk_activity_child,
    ADD CONSTRAINT fk_activity_student
        FOREIGN KEY (student_id) REFERENCES students(id);
ALTER TABLE weekly_comments
    DROP FOREIGN KEY fk_weekly_comments_child,
    ADD CONSTRAINT fk_weekly_comments_student
        FOREIGN KEY (student_id) REFERENCES students(id);
ALTER TABLE reward_redemptions
    DROP FOREIGN KEY fk_redemptions_child,
    ADD CONSTRAINT fk_redemptions_student
        FOREIGN KEY (student_id) REFERENCES students(id);
ALTER TABLE star_transactions
    DROP FOREIGN KEY fk_star_transactions_child,
    ADD CONSTRAINT fk_star_transactions_student
        FOREIGN KEY (student_id) REFERENCES students(id);
ALTER TABLE weekly_goals
    DROP FOREIGN KEY fk_weekly_goals_child,
    ADD CONSTRAINT fk_weekly_goals_student
        FOREIGN KEY (student_id) REFERENCES students(id);
ALTER TABLE student_badges
    DROP FOREIGN KEY fk_child_badges_child,
    ADD CONSTRAINT fk_student_badges_student
        FOREIGN KEY (student_id) REFERENCES students(id);

UPDATE members
SET role = 'STUDENT'
WHERE role = 'CHILD';

SET FOREIGN_KEY_CHECKS = 0;

UPDATE homework_batches
SET student_id = REPLACE(student_id, 'demo-child-', 'demo-student-')
WHERE student_id LIKE 'demo-child-%';
UPDATE homework_tasks
SET student_id = REPLACE(student_id, 'demo-child-', 'demo-student-')
WHERE student_id LIKE 'demo-child-%';
UPDATE plans
SET student_id = REPLACE(student_id, 'demo-child-', 'demo-student-')
WHERE student_id LIKE 'demo-child-%';
UPDATE activity_log
SET student_id = REPLACE(student_id, 'demo-child-', 'demo-student-')
WHERE student_id LIKE 'demo-child-%';
UPDATE weekly_comments
SET student_id = REPLACE(student_id, 'demo-child-', 'demo-student-')
WHERE student_id LIKE 'demo-child-%';
UPDATE reward_redemptions
SET student_id = REPLACE(student_id, 'demo-child-', 'demo-student-')
WHERE student_id LIKE 'demo-child-%';
UPDATE star_transactions
SET student_id = REPLACE(student_id, 'demo-child-', 'demo-student-')
WHERE student_id LIKE 'demo-child-%';
UPDATE weekly_goals
SET student_id = REPLACE(student_id, 'demo-child-', 'demo-student-')
WHERE student_id LIKE 'demo-child-%';
UPDATE student_badges
SET student_id = REPLACE(student_id, 'demo-child-', 'demo-student-')
WHERE student_id LIKE 'demo-child-%';
UPDATE students
SET id = REPLACE(id, 'demo-child-', 'demo-student-')
WHERE id LIKE 'demo-child-%';

UPDATE homework_batches
SET created_by = REPLACE(created_by, 'demo-child-member-', 'demo-student-member-')
WHERE created_by LIKE 'demo-child-member-%';
UPDATE activity_log
SET actor_id = REPLACE(actor_id, 'demo-child-member-', 'demo-student-member-')
WHERE actor_id LIKE 'demo-child-member-%';
UPDATE weekly_comments
SET actor_id = REPLACE(actor_id, 'demo-child-member-', 'demo-student-member-')
WHERE actor_id LIKE 'demo-child-member-%';
UPDATE reward_definitions
SET created_by = REPLACE(created_by, 'demo-child-member-', 'demo-student-member-')
WHERE created_by LIKE 'demo-child-member-%';
UPDATE reward_redemptions
SET requested_by = REPLACE(requested_by, 'demo-child-member-', 'demo-student-member-'),
    reviewed_by = REPLACE(reviewed_by, 'demo-child-member-', 'demo-student-member-')
WHERE requested_by LIKE 'demo-child-member-%'
   OR reviewed_by LIKE 'demo-child-member-%';
UPDATE weekly_goals
SET created_by = REPLACE(created_by, 'demo-child-member-', 'demo-student-member-'),
    claimed_by = REPLACE(claimed_by, 'demo-child-member-', 'demo-student-member-')
WHERE created_by LIKE 'demo-child-member-%'
   OR claimed_by LIKE 'demo-child-member-%';
UPDATE members
SET id = REPLACE(id, 'demo-child-member-', 'demo-student-member-')
WHERE id LIKE 'demo-child-member-%';

SET FOREIGN_KEY_CHECKS = 1;
