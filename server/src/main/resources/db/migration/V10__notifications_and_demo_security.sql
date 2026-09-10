ALTER TABLE members
    ADD COLUMN student_id VARCHAR(36) NULL AFTER family_id;

UPDATE members
SET student_id = 'demo-student-xiaoman'
WHERE id = 'demo-student-member-xiaoman';

UPDATE members
SET student_id = 'demo-student-keke'
WHERE id = 'demo-student-member-keke';

ALTER TABLE members
    ADD CONSTRAINT fk_members_student
        FOREIGN KEY (student_id) REFERENCES students(id),
    ADD UNIQUE KEY uk_members_student (student_id);

CREATE TABLE member_sessions (
    id VARCHAR(36) PRIMARY KEY,
    member_id VARCHAR(36) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP NULL,
    revoked_at TIMESTAMP NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_member_sessions_member
        FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE,
    UNIQUE KEY uk_member_sessions_token_hash (token_hash),
    INDEX idx_member_sessions_member_active (member_id, revoked_at, expires_at)
);

INSERT INTO member_sessions (id, member_id, token_hash, expires_at) VALUES
    ('session-demo-parent-mom', 'demo-parent-mom',
     '597474c6302c5c1a9190a158f8621338988cdc2cb3ff256edc36e6e5974ff98a', '2036-01-01 00:00:00'),
    ('session-demo-parent-dad', 'demo-parent-dad',
     'd6b350e9b5e4188ec2b2fa4bec18ada718baa2b8604f5540e48794bb8170847f', '2036-01-01 00:00:00'),
    ('session-demo-student-xiaoman', 'demo-student-member-xiaoman',
     'e6524657fd0472ccf523d18adc0e8497f9e3356175f54e2e1838d39972baf5a4', '2036-01-01 00:00:00'),
    ('session-demo-student-keke', 'demo-student-member-keke',
     'f00999b331d66180543032ae4a966dc6719085860bed79b92f178af725ea88a2', '2036-01-01 00:00:00');

CREATE TABLE notifications (
    id VARCHAR(36) PRIMARY KEY,
    family_id VARCHAR(36) NOT NULL,
    student_id VARCHAR(36) NOT NULL,
    recipient_member_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    title VARCHAR(80) NOT NULL,
    message VARCHAR(255) NOT NULL,
    action_path VARCHAR(160) NULL,
    dedupe_key VARCHAR(160) NOT NULL,
    read_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_notifications_family
        FOREIGN KEY (family_id) REFERENCES families(id),
    CONSTRAINT fk_notifications_student
        FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_notifications_recipient
        FOREIGN KEY (recipient_member_id) REFERENCES members(id) ON DELETE CASCADE,
    UNIQUE KEY uk_notifications_recipient_dedupe (recipient_member_id, dedupe_key),
    INDEX idx_notifications_recipient_read_created (recipient_member_id, read_at, created_at),
    INDEX idx_notifications_student_created (student_id, created_at)
);
