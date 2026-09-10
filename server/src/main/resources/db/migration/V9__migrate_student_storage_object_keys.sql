CREATE TABLE storage_key_migrations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id VARCHAR(36) NOT NULL,
    source_object_key VARCHAR(255) NOT NULL,
    target_object_key VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_storage_key_migrations_batch
        FOREIGN KEY (batch_id) REFERENCES homework_batches(id) ON DELETE CASCADE,
    UNIQUE KEY uk_storage_key_migrations_batch_source (batch_id, source_object_key),
    INDEX idx_storage_key_migrations_status (status, created_at)
);

INSERT INTO storage_key_migrations
    (batch_id, source_object_key, target_object_key, status)
SELECT id,
       source_object_key,
       CONCAT(student_id, SUBSTRING(source_object_key, LOCATE('/', source_object_key))),
       'PENDING'
FROM homework_batches
WHERE source_object_key IS NOT NULL
  AND LOCATE('/', source_object_key) > 0
  AND LOWER(SUBSTRING_INDEX(source_object_key, '/', 1)) LIKE '%child%'
  AND source_object_key <> CONCAT(student_id, SUBSTRING(source_object_key, LOCATE('/', source_object_key)));
