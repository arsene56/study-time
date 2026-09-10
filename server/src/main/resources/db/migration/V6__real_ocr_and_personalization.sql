ALTER TABLE homework_batches
    ADD COLUMN ocr_provider VARCHAR(30) NULL AFTER recognition_mode,
    ADD COLUMN ocr_request_id VARCHAR(80) NULL AFTER ocr_provider,
    ADD COLUMN ocr_raw_text MEDIUMTEXT NULL AFTER ocr_request_id,
    ADD COLUMN ocr_average_confidence DECIMAL(5,2) NULL AFTER ocr_raw_text,
    ADD COLUMN recognition_error VARCHAR(500) NULL AFTER ocr_average_confidence,
    ADD COLUMN recognized_at TIMESTAMP(6) NULL AFTER recognition_error;

ALTER TABLE homework_tasks
    ADD COLUMN base_estimated_minutes INT NULL AFTER estimated_minutes,
    ADD COLUMN estimate_sample_size INT NOT NULL DEFAULT 0 AFTER estimate_source,
    ADD COLUMN estimate_confidence VARCHAR(20) NOT NULL DEFAULT 'LOW' AFTER estimate_sample_size,
    ADD COLUMN estimate_reason VARCHAR(255) NULL AFTER estimate_confidence,
    ADD COLUMN ocr_confidence DECIMAL(5,2) NULL AFTER confidence,
    ADD COLUMN manually_edited BOOLEAN NOT NULL DEFAULT FALSE AFTER ocr_confidence;

UPDATE homework_tasks
SET base_estimated_minutes = estimated_minutes,
    estimate_sample_size = CASE WHEN estimate_source = 'HISTORY' THEN 2 ELSE 0 END,
    estimate_confidence = CASE WHEN estimate_source = 'HISTORY' THEN 'MEDIUM' ELSE 'LOW' END,
    estimate_reason = CASE
        WHEN estimate_source = 'HISTORY' THEN '由阶段 5 历史平均估算迁移'
        ELSE '按年级通用规则估算'
    END;

ALTER TABLE homework_tasks
    MODIFY base_estimated_minutes INT NOT NULL;

UPDATE homework_tasks
SET estimate_source = 'PERSONAL_HISTORY'
WHERE estimate_source = 'HISTORY';

UPDATE homework_batches
SET ocr_provider = 'MOCK',
    ocr_average_confidence = 98.00,
    recognized_at = created_at
WHERE recognition_mode = 'MOCK_OCR';
